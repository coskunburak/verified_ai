"""Synthetic owner-run proof and versioned contract tests. Never executed by the authoring agent."""
import json
from pathlib import Path
from uuid import UUID
import pytest
import sympy as s
from jsonschema import Draft202012Validator
from pydantic import ValidationError
from app.api.schemas.method_comparison import Request, Result
from app.verifiers.attempt_learning import execute_comparison, _method_structure
from app.verifiers.canonical_v2 import DomainConstraintGuard, compare, concrete
from app.policies.process_executor import ProcessExecutor

ROOT=Path(__file__).resolve().parents[3]
DATA=json.loads((ROOT/'evaluations/golden-datasets/methods/rational-method-comparison-v2.json').read_text())
ID=UUID('00000000-0000-0000-0000-000000000068')

def request(case): return Request(executionId=ID,correlationId=str(ID),source=case['source'])

@pytest.mark.parametrize('case',DATA['cases'],ids=lambda c:c['id'])
def test_golden_complete_outcomes_transitions_domains_and_compact(case):
    req=request(case); result=execute_comparison(req)
    assert result.status==case['status']
    assert execute_comparison(req)==result
    assert Result.model_validate_json(result.model_dump_json()).validate_for(req)==result
    schema=json.loads((ROOT/'packages/schemas/method-comparison-result-v2.schema.json').read_text())
    Draft202012Validator(schema).validate(json.loads(result.model_dump_json()))
    if result.status!='VERIFIED':
        assert not result.methods
        return
    guard=DomainConstraintGuard(s.Symbol('x',real=True)); restrictions=s.S.Reals
    from app.api.schemas.attempt_v2 import Statement
    for r in req.source.restrictions:
        restrictions=concrete(restrictions.intersect(guard.statement(Statement(kind='RELATION',left=r.left,right=r.right,relation=r.relation))[1]))
    domain=guard.statement(req.source.original)[2]
    scope=concrete(restrictions.intersect(domain))
    guard.scope=scope
    signatures=[]
    for method in result.methods:
        previous=req.source.original
        for step in method.steps:
            assert compare(guard,previous,step,scope)[0]
            previous=step
        assert compare(guard,req.source.original,method.steps[-1],restrictions)[0]
        compact=[method.steps[i] for i in method.comparison.compactStepIndices]
        assert compact[-1]==method.steps[-1]
        previous=req.source.original
        for step in compact:
            assert compare(guard,previous,step,scope)[0]
            previous=step
        if 'roots' in case:
            actual=guard.statement(method.steps[-1])[1]
            # Expected exact strings are synthetic constants in a checked-in fixture, never learner input.
            assert {s.sstr(v) for v in actual}==set(case['roots'])
        signatures.append(tuple(_method_structure(v) for v in method.steps))
    assert len(signatures)==len(set(signatures))
    if 'methodIds' in case: assert [m.methodId for m in result.methods]==case['methodIds']

@pytest.mark.parametrize('reason',['TIMEOUT','RESOURCE_FAILURE','WORKER_FAILURE','ADMISSION_LIMIT','INVALID_RESPONSE','DISABLED'])
def test_worker_failure_is_never_a_proof(reason):
    result=ProcessExecutor.failure(request(DATA['cases'][0]),reason)
    assert result.status=='UNAVAILABLE' and not result.methods and result.reasonCode==reason

@pytest.mark.parametrize('field',['owner','verdict','trustStatus','steps'])
def test_source_rejects_authority_and_learner_fields(field):
    source={**DATA['cases'][0]['source'],field:'VERIFIED'}
    with pytest.raises(ValidationError): Request(executionId=ID,correlationId=str(ID),source=source)

def test_response_cannot_bind_another_source_or_execution():
    req=request(DATA['cases'][0]); result=execute_comparison(req)
    with pytest.raises(ValueError): result.validate_for(request(DATA['cases'][1]))
    with pytest.raises(ValueError): result.validate_for(req.model_copy(update={'executionId':UUID(int=1)}))
    malformed=result.model_dump(mode='json'); malformed['status']='UNSUPPORTED'
    with pytest.raises(ValueError): Result.model_validate(malformed)

def test_reviewed_real_scope_and_exact_rational_coefficients():
    raw=json.loads(json.dumps(DATA['cases'][0]))
    raw['source']['domainBasis']='REVIEWED_REAL_TASK'
    assert execute_comparison(request(raw)).status=='VERIFIED'
    # Divide the whole polynomial by two; exact roots are unchanged.
    original=raw['source']['original']
    original['left']={'kind':'BINARY','operator':'DIVIDE','left':original['left'],'right':{'kind':'NUMBER','value':'2','numericType':'INTEGER'}}
    assert execute_comparison(request(raw)).status=='VERIFIED'

def test_v1_evidence_remains_independent():
    from app.api.schemas.attempt_learning import Method
    m=Method(methodId='FACTORING',steps=[request(DATA['cases'][0]).source.original])
    assert m.evidenceVersion=='verified-method-v1' and 'comparison' not in m.model_dump()

def test_shared_public_fixture_proves_each_transition():
    fixture=json.loads((ROOT/'packages/schemas/fixtures/method-comparison-public-v2.json').read_text())
    Draft202012Validator(json.loads((ROOT/'packages/schemas/method-comparison-public-v2.schema.json').read_text())).validate(fixture)
    result=Result.model_validate(fixture['evidence']); guard=DomainConstraintGuard(s.Symbol('x',real=True))
    for method in result.methods:
        previous=result.source.original
        for step in method.steps:
            assert compare(guard,previous,step,s.S.Reals)[0]
            previous=step


def stalled_comparison_worker(connection,payload,budget):
    import time
    time.sleep(10)


def resource_comparison_worker(connection,payload,budget):
    connection.send_bytes(b'RESOURCE_FAILURE')

@pytest.mark.parametrize('worker,reason',[(stalled_comparison_worker,'TIMEOUT'),(resource_comparison_worker,'RESOURCE_FAILURE')])
def test_isolated_comparison_failures_release_admission(worker,reason):
    from app.policies.process_executor import ProcessBudget
    executor=ProcessExecutor(ProcessBudget(elapsed_seconds=0.2 if reason=='TIMEOUT' else 3),worker)
    result=executor.execute(request(DATA['cases'][0]))
    assert result.status=='UNAVAILABLE' and result.reasonCode==reason and not result.methods
    assert executor._slots.acquire(blocking=False)
    executor._slots.release()

def test_real_worker_dispatch_and_compact_wire_stay_within_existing_limit():
    from app.policies.process_executor import MAX_RESPONSE_BYTES
    result=ProcessExecutor().execute(request(DATA['cases'][0]))
    assert result.status=='VERIFIED'
    assert len(result.model_dump_json().encode())<=MAX_RESPONSE_BYTES


def test_repeated_root_factor_and_square_are_not_duplicate_cards():
    result=execute_comparison(request(next(c for c in DATA['cases'] if c['id']=='repeated')))
    ids={m.methodId for m in result.methods}
    assert not {'FACTORING','COMPLETING_SQUARE'}<=ids

@pytest.mark.parametrize('leading',[-2,1,3])
@pytest.mark.parametrize('roots',[(-3,-1),(-2,2),(0,3),(2,2)])
def test_quadratic_family_exact_root_set_property(leading,roots):
    from app.verifiers.attempt_learning import eq,num,variable,binary
    x=variable(); r1,r2=roots
    polynomial=binary('ADD',binary('ADD',binary('MULTIPLY',num(leading),binary('POWER',x,num(2))),
        binary('MULTIPLY',num(-leading*(r1+r2)),x)),num(leading*r1*r2))
    req=Request(executionId=ID,correlationId=str(ID),source={
        'original':eq(polynomial,num(0)), 'variables':[{'symbol':'x','domain':'REAL'}],
        'restrictions':[], 'domainBasis':'SOURCE_EXPLICIT'})
    result=execute_comparison(req)
    assert result.status=='VERIFIED'
    guard=DomainConstraintGuard(s.Symbol('x',real=True))
    expected=s.FiniteSet(*roots)
    for method in result.methods:
        assert guard.statement(method.steps[-1])[1]==expected
        assert all(compare(guard,req.source.original,step,s.S.Reals)[0] for step in method.steps)


def test_direct_algebra_linear_and_identity():
    deg_case = next(c for c in DATA['cases'] if c['id'] == 'degenerate')
    res_deg = execute_comparison(request(deg_case))
    assert res_deg.status == 'VERIFIED'
    assert len(res_deg.methods) == 1 and res_deg.methods[0].methodId == 'DIRECT_ALGEBRA'
    assert res_deg.methods[0].comparison.applicability == 'RATIONAL_LINEAR'

    ident_case = next(c for c in DATA['cases'] if c['id'] == 'identity-equation')
    res_ident = execute_comparison(request(ident_case))
    assert res_ident.status == 'VERIFIED'
    assert len(res_ident.methods) == 1 and res_ident.methods[0].methodId == 'DIRECT_ALGEBRA'
    assert res_ident.methods[0].comparison.applicability == 'CONSTANT_EQUATION'


def test_rational_clearing_preserves_domain_holes():
    case = next(c for c in DATA['cases'] if c['id'] == 'denominator')
    res = execute_comparison(request(case))
    assert res.status == 'VERIFIED'
    assert len(res.methods) == 1 and res.methods[0].methodId == 'RATIONAL_CLEARING'
    assert 'NATURAL_DOMAIN_EXCLUSIONS' in res.methods[0].comparison.domainCautions
    guard = DomainConstraintGuard(s.Symbol('x', real=True))
    assert guard.statement(res.methods[0].steps[-1])[1] == s.S.EmptySet


def test_absolute_cases_and_polynomial_expansion():
    abs_case = next(c for c in DATA['cases'] if c['id'] == 'absolute-cases')
    res_abs = execute_comparison(request(abs_case))
    assert res_abs.status == 'VERIFIED'
    assert len(res_abs.methods) == 1 and res_abs.methods[0].methodId == 'ABSOLUTE_CASES'
    guard = DomainConstraintGuard(s.Symbol('x', real=True))
    assert guard.statement(res_abs.methods[0].steps[-1])[1] == s.FiniteSet(-2, 4)

    exp_case = next(c for c in DATA['cases'] if c['id'] == 'expression-expansion')
    res_exp = execute_comparison(request(exp_case))
    assert res_exp.status == 'VERIFIED'
    assert len(res_exp.methods) == 1 and res_exp.methods[0].methodId == 'POLYNOMIAL_EXPANSION'
    assert res_exp.methods[0].outcomeStatus == 'EXPRESSION_IDENTITY'

