"""Synthetic source/domain properties. Owner executes; no sampled proof assertions."""
import copy
import json
from pathlib import Path
import pytest
import sympy as s
from pydantic import ValidationError
from jsonschema import Draft202012Validator
from app.api.schemas.math_visualization import Request, Result, Viewport
from app.verifiers.math_visualization import execute, model
from app.verifiers.canonical_v2 import DomainConstraintGuard
from app.policies.process_executor import ProcessExecutor

ROOT=Path(__file__).resolve().parents[3]
DATA=json.loads((ROOT/'evaluations/golden-datasets/visualizations/bounded-real-v1.json').read_text())

@pytest.mark.parametrize('case',DATA['cases'],ids=lambda c:c['id'])
def test_semantic_golden(case):
    request=Request.model_validate(case['request']); result=execute(request); expected=case['expected']
    assert result.status==expected['status']
    assert result==execute(request)
    assert Result.model_validate_json(result.model_dump_json()).validate_for(request)==result
    Draft202012Validator(json.loads((ROOT/'packages/schemas/math-visualization-result-v1.schema.json').read_text())).validate(json.loads(result.model_dump_json()))
    assert len(result.model_dump_json().encode())<=16384
    if 'reason' in expected: assert result.reasonCode==expected['reason']
    if result.status!='SUPPORTED':
        assert not result.segments and not result.features
        return
    guard,expression,natural,restrictions,solution=model(request)
    assert guard.set(result.naturalDomain)==natural
    assert guard.set(result.sourceDomain)==restrictions
    if 'solutionKind' in expected: assert result.solutionSet.kind==expected['solutionKind']
    if 'roots' in expected: assert [f.x for f in result.features if f.code=='ROOT_INTERCEPT']==expected['roots']
    if 'slope' in expected: assert next(f.y for f in result.features if f.code=='SLOPE')==expected['slope']
    if 'bands' in expected: assert [[b.lower,b.upper,b.leftOpen,b.rightOpen] for b in result.numberLine]==expected['bands']
    for excluded in expected.get('excluded',[]):
        hole=s.Rational(excluded)
        assert natural.contains(hole) is s.false
        for segment in result.segments:
            assert all(p.x!=float(hole) for p in segment.points)
            assert not segment.points[0].x < float(hole) < segment.points[-1].x
    for segment in result.segments:
        actual=expression if segment.curve=='FUNCTION' else s.diff(expression,guard.x)
        for point in segment.points:
            assert point.y==pytest.approx(float(actual.subs(guard.x,s.Rational(str(point.x)))),abs=1e-9)
        if segment.curve=='DERIVATIVE' and 'derivative' in expected:
            assert all(p.y==float(expected['derivative']) for p in segment.points)

@pytest.mark.parametrize('change',[{'xMin':float('nan')},{'xMax':float('inf')},{'xMin':-1001},{'xMin':0,'xMax':.001},{'yMin':1,'yMax':0}])
def test_bad_viewports(change):
    with pytest.raises(ValidationError): Viewport(**{**DATA['cases'][0]['request']['viewport'],**change})

@pytest.mark.parametrize('field,value',[('resolution',65),('resolution',True),('schemaVersion','v0'),('correlationId','wrong'),('unexpected',1)])
def test_request_rejections(field,value):
    req=copy.deepcopy(DATA['cases'][0]['request']); req[field]=value
    with pytest.raises(ValidationError): Request.model_validate(req)

def test_malformed_ast_and_multivariate():
    req=copy.deepcopy(DATA['cases'][0]['request']); req['source']['original']['left']={'kind':'BINARY','operator':'ADD'}
    with pytest.raises(ValidationError): Request.model_validate(req)
    req=copy.deepcopy(DATA['cases'][0]['request']); req['source']['variables'].append({'symbol':'y','domain':'REAL'})
    with pytest.raises(ValidationError): Request.model_validate(req)

def test_symbolic_depth_is_unsupported_with_no_graph():
    req=copy.deepcopy(DATA['cases'][0]['request']); node=req['source']['original']['left']
    # Within Source's structural depth cap (40), outside DomainConstraintGuard's
    # expression depth cap (24). Transport admission is not math eligibility.
    for _ in range(30): node={'kind':'BINARY','operator':'MULTIPLY','left':node,'right':{'kind':'VARIABLE','symbol':'x'}}
    req['source']['original']['left']=node
    request=Request.model_validate(req)
    result=execute(request)
    assert result.status=='UNSUPPORTED' and result.reasonCode=='UNSUPPORTED_SOURCE'
    assert result.expression is None and result.naturalDomain is None
    assert not result.segments and not result.features and not result.numberLine
    result.validate_for(request)


def test_transport_depth_excess_is_rejected_before_execution():
    req=copy.deepcopy(DATA['cases'][0]['request']); node=req['source']['original']['left']
    for _ in range(45): node={'kind':'BINARY','operator':'MULTIPLY','left':node,'right':{'kind':'VARIABLE','symbol':'x'}}
    req['source']['original']['left']=node
    with pytest.raises(ValidationError,match='depth'): Request.model_validate(req)


def test_extreme_finite_viewport_keeps_samples_clipped():
    req=copy.deepcopy(DATA['cases'][0]['request']);req['viewport']={'xMin':-1000,'xMax':1000,'yMin':-1000,'yMax':1000}
    result=execute(Request.model_validate(req)); assert result.status=='SUPPORTED'
    assert all(-1000<=p.x<=1000 and -1000<=p.y<=1000 for segment in result.segments for p in segment.points)
    assert [f.x for f in result.features if f.code=='ROOT_INTERCEPT']==['-2','2']


def test_source_binding_and_payload_rejection():
    request=Request.model_validate(DATA['cases'][0]['request']); result=execute(request)
    changed=request.model_copy(update={'type':'FUNCTION'})
    with pytest.raises(ValueError): result.validate_for(changed)
    wire=result.model_dump(); wire['segments'][0]['points'][0]['y']=float('inf')
    with pytest.raises(ValidationError): Result.model_validate(wire)

@pytest.mark.parametrize('reason',['TIMEOUT','RESOURCE_FAILURE','WORKER_FAILURE','ADMISSION_LIMIT','INVALID_RESPONSE','DISABLED'])
def test_worker_failures_are_empty(reason):
    req=Request.model_validate(DATA['cases'][0]['request']); result=ProcessExecutor.failure(req,reason)
    assert result.status=='UNAVAILABLE' and result.reasonCode==reason and not result.segments

def test_admission_refusal():
    executor=ProcessExecutor(); req=Request.model_validate(DATA['cases'][0]['request'])
    for _ in range(executor.budget.concurrency): assert executor._slots.acquire(False)
    try: assert executor.execute(req).reasonCode=='ADMISSION_LIMIT'
    finally:
        for _ in range(executor.budget.concurrency): executor._slots.release()

# Top-level functions are spawn-pickleable and never import provider adapters.
def slow_worker(connection,payload,budget):
    import time
    time.sleep(1)

def oversized_worker(connection,payload,budget):
    connection.send_bytes(b'x'*16385); connection.close()

def corrupt_worker(connection,payload,budget):
    connection.send_bytes(b'{"unknown":true}'); connection.close()

@pytest.mark.parametrize('worker,reason',[(slow_worker,'TIMEOUT'),(oversized_worker,'WORKER_FAILURE'),(corrupt_worker,'INVALID_RESPONSE')])
def test_real_isolated_failure_paths(worker,reason):
    from app.policies.process_executor import ProcessBudget
    budget=ProcessBudget(elapsed_seconds=.15 if worker==slow_worker else 3)
    result=ProcessExecutor(budget,worker).execute(Request.model_validate(DATA['cases'][0]['request']))
    assert result.status=='UNAVAILABLE' and result.reasonCode==reason and not result.segments

def test_internal_auth_correlation_and_default_off(monkeypatch):
    from fastapi.testclient import TestClient
    from app.main import app
    monkeypatch.setenv('APP_ENV','test')
    monkeypatch.setenv('MATH_VERIFIER_INTERNAL_TOKEN','local_math_verifier_token_change_me')
    monkeypatch.setenv('MATH_VERIFIER_VISUALIZATION_ENABLED','false')
    payload=DATA['cases'][0]['request']
    with TestClient(app) as client:
        assert client.post('/internal/v1/verify/visualization',json=payload).status_code==401
        headers={'X-Internal-Token':'local_math_verifier_token_change_me','X-Request-Id':'wrong'}
        assert client.post('/internal/v1/verify/visualization',json=payload,headers=headers).status_code==422
        headers['X-Request-Id']=payload['correlationId']
        response=client.post('/internal/v1/verify/visualization',json=payload,headers=headers)
        assert response.status_code==200 and response.json()['reasonCode']=='DISABLED'
