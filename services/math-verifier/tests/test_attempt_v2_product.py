"""Synthetic owner-run regressions. Authoring is not execution evidence."""
from uuid import UUID
import pytest
from app.api.schemas.attempt_v2 import Request, Node, Statement, Step, Restriction
from app.attempt_verification_v2 import execute
from app.verifiers.attempt_learning import num, variable, binary, fn, expr, eq, members, template
from app.api.schemas.attempt_learning import Request as LearningRequest
from app.verifiers.attempt_learning import execute as learning

ID=UUID('00000000-0000-0000-0000-000000000042')
x=variable()
add=lambda a,b:binary('ADD',a,b)
sub=lambda a,b:binary('SUBTRACT',a,b)
mul=lambda a,b:binary('MULTIPLY',a,b)
div=lambda a,b:binary('DIVIDE',a,b)
pow2=lambda a:binary('POWER',a,num(2))
neg=lambda a:Node(kind='UNARY',operator='NEGATE',operand=a)


def request(original,*steps,restrictions=None,basis='SOURCE_EXPLICIT'):
    return Request(schemaVersion='attempt-verification-v2',executionId=ID,correlationId=str(ID),original=original,
        variables=[{'symbol':'x','domain':'REAL'}],restrictions=restrictions or [],domainBasis=basis,
        steps=[Step(statement=step) for step in steps])

CASES=[
('ALG_SIGN_FLIP_DISTRIBUTE',expr(neg(sub(x,num(2)))),expr(sub(neg(x),num(2)))),
('ALG_FRESHMAN_DREAM_POWER',expr(pow2(add(x,num(2)))),expr(add(pow2(x),num(4)))),
('ALG_FRACTION_ADD_DENOM',expr(add(div(num(1),num(2)),div(num(1),num(3)))),expr(div(add(num(1),num(1)),add(num(2),num(3))))),
('ALG_ROOT_LOST_BY_DIVISION',eq(mul(x,sub(x,num(2))),mul(x,num(0))),eq(sub(x,num(2)),num(0))),
('DOM_EXTRANEOUS_ROOT_DENOM',eq(div(sub(pow2(x),num(1)),sub(x,num(1))),num(2)),eq(x,num(1))),
('DOM_EVEN_ROOT_NEGATIVE',eq(fn('SQRT',x),num(-2)),eq(x,num(4))),
('DOM_LOG_NON_POSITIVE',eq(fn('LOG',x),num(0)),eq(pow2(x),num(1))),
('INEQ_NEGATIVE_MULT_NO_FLIP',eq(mul(num(-2),x),num(0),'GREATER_THAN'),eq(x,num(0),'GREATER_THAN')),
('INEQ_CROSS_MULT_VARIABLE',eq(div(num(1),x),num(0),'LESS_THAN'),eq(num(1),mul(num(0),x),'LESS_THAN')),
('ABS_SINGLE_BRANCH_ONLY',eq(fn('ABS',x),num(2)),eq(x,num(2))),
('FUNC_COMPOSITION_ORDER',expr(fn('LOG',fn('EXP',x))),expr(fn('EXP',fn('LOG',x)))),
('CALC_LIMIT_INDETERMINATE_SUB',expr(fn('LIMIT',div(sub(pow2(x),num(4)),sub(x,num(2))),num(2))),expr(num(0))),
('CALC_CHAIN_RULE_OMITTED',expr(fn('DIFF',pow2(add(mul(num(3),x),num(1))))),expr(mul(num(2),add(mul(num(3),x),num(1))))),
('CALC_PRODUCT_QUOTIENT_NAIVE',expr(fn('DIFF',mul(x,add(x,num(3))))),expr(num(1))),
('CALC_INTEGRAL_BOUNDS_SWAPPED',expr(fn('INTEGRAL',x,num(0),num(3))),expr(fn('INTEGRAL',x,num(3),num(0)))),
('WORD_VARIABLE_MISASSIGNMENT',expr(fn('TOTAL',num(3),num(4))),expr(num(3))),
]

@pytest.mark.parametrize('code,original,wrong',CASES,ids=[c[0] for c in CASES])
def test_specific_diagnosis_requires_independent_disproof(code,original,wrong):
    result=execute(request(original,wrong,wrong))
    assert result.firstWrongStepIndex==1
    assert result.steps[0].mistakeCode==code
    assert result.steps[0].diagnosis.evidenceId==str(ID)+':1'
    assert result.steps[0].diff.supportingEvidenceId==str(ID)+':1'
    assert result.steps[1].verdict=='NOT_ASSESSED'
    if code=='ALG_FRESHMAN_DREAM_POWER':
        explicit_squares=expr(add(pow2(x),pow2(num(2))))
        assert execute(request(original,explicit_squares)).steps[0].mistakeCode==code
        unrelated_constant=expr(add(pow2(x),num(5)))
        assert execute(request(original,unrelated_constant)).steps[0].mistakeCode=='INVALID_TRANSFORMATION'
        expansion=expr(add(add(pow2(x),mul(num(4),x)),num(4)))
        valid=execute(request(original,expansion))
        assert valid.steps[0].verdict=='VALID_EQUIVALENT' and valid.steps[0].diagnosis is None

@pytest.mark.parametrize('code,original,wrong',CASES,ids=[c[0] for c in CASES])
def test_matching_input_is_not_accused(code,original,wrong):
    result=execute(request(original,original))
    assert result.firstWrongStepIndex is None
    assert result.steps[0].verdict=='VALID_EQUIVALENT'
    assert result.steps[0].diagnosis is None


def test_period_omission_only_in_proved_finite_source_interval():
    pi=Node(kind='CONSTANT',value='PI')
    restrictions=[Restriction(id='lower',relation='GREATER_THAN_OR_EQUAL',left=x,right=num(0)),
        Restriction(id='upper',relation='LESS_THAN_OR_EQUAL',left=x,right=mul(num(2),pi))]
    result=execute(request(eq(fn('SIN',x),num(0)),members(num(0)),restrictions=restrictions))
    assert result.steps[0].mistakeCode=='TRIG_PERIOD_OMITTED'
    unbounded=execute(request(eq(fn('SIN',x),num(0)),members(num(0))))
    assert unbounded.steps[0].verdict=='UNCERTAIN'


def test_unknown_scope_is_not_promoted_and_excluded_root_is_not_a_witness():
    assert execute(request(expr(x),expr(x),basis='UNRESOLVED')).steps[0].reasonCode=='UNKNOWN_DOMAIN'
    original=eq(div(sub(pow2(x),num(1)),sub(x,num(1))),num(2))
    result=execute(request(original,eq(x,num(1))))
    assert result.steps[0].counterexampleStatus=='COUNTEREXAMPLE_UNAVAILABLE'
    assert result.naturalDomain['kind']=='UNION'


def test_valid_numeric_probe_is_not_identity_proof():
    # Agreement at zero cannot establish equality elsewhere.
    result=execute(request(expr(pow2(x)),expr(x)))
    assert result.steps[0].verdict=='FIRST_WRONG_STEP'
    assert result.steps[0].mistakeCode=='INVALID_TRANSFORMATION'


def test_semantic_diff_never_invents_token_offsets():
    result=execute(request(expr(neg(sub(x,num(2)))),expr(sub(neg(x),num(2)))))
    diff=result.steps[0].diff
    assert diff.confidence=='EXACT_STRUCTURAL'
    assert diff.originalTokenRange is None and diff.submittedTokenRange is None


def test_source_domains_checked_before_cancellation():
    result=execute(request(expr(div(x,x)),expr(num(1))))
    assert result.steps[0].verdict=='FIRST_WRONG_STEP'
    assert result.steps[0].counterexample is None


def test_proper_integral_rejects_singularity_crossing():
    result=execute(request(expr(fn('INTEGRAL',div(num(1),x),num(-1),num(1))),expr(num(0))))
    assert result.steps[0].verdict=='UNCERTAIN'

ALL_CODES=[c[0] for c in CASES]+['TRIG_PERIOD_OMITTED']
@pytest.mark.parametrize('code',ALL_CODES)
@pytest.mark.parametrize('seed',[0,1,2,3,4])
def test_all_twin_variants_pass_independent_option_gate(code,seed):
    identity=UUID(int=seed+1)
    result=learning(LearningRequest(executionId=identity,correlationId=str(identity),action='TWIN',mistakeCode=code))
    assert result.status=='VERIFIED'
    assert len(result.twin.options)==4
    assert result.twin.mistakeCode==code
    assert learning(LearningRequest(executionId=identity,correlationId=str(identity),action='TWIN',mistakeCode=code))==result


def test_alternative_methods_are_equivalent_not_learner_evidence():
    source=request(eq(sub(pow2(x),num(4)),num(0)),members(num(-2),num(2)))
    result=learning(LearningRequest(executionId=ID,correlationId=str(ID),action='METHODS',source=source))
    assert result.status=='VERIFIED'
    assert {m.methodId for m in result.methods}=={'FACTORING','COMPLETING_SQUARE','QUADRATIC_FORMULA'}
    for method in result.methods:
        assert execute(request(source.original,*method.steps)).outcome=='INCOMPLETE_VALID_SO_FAR'


def test_piecewise_and_set_membership_use_exact_domains():
    from app.api.schemas.attempt_v2 import Branch
    absolute=Node(kind='PIECEWISE',branches=[Branch(expression=neg(x),condition=eq(x,num(0),'LESS_THAN')),
        Branch(expression=x,condition=None)])
    result=execute(request(expr(absolute),expr(fn('ABS',x))))
    assert result.steps[0].verdict=='VALID_EQUIVALENT'
    union=Node(kind='UNION',args=[Node(kind='FINITE_SET',args=[num(-2)]),Node(kind='FINITE_SET',args=[num(2)])])
    result=execute(request(eq(pow2(x),num(4)),eq(x,union,'MEMBER_OF')))
    assert result.steps[0].verdict=='VALID_EQUIVALENT'
    # A proof of the open branches must not overlook a different boundary value.
    wrong_boundary=Node(kind='PIECEWISE',branches=[Branch(expression=neg(x),condition=eq(x,num(0),'LESS_THAN')),
        Branch(expression=x,condition=eq(x,num(0),'GREATER_THAN')),Branch(expression=num(1),condition=None)])
    wrong=execute(request(expr(fn('ABS',x)),expr(wrong_boundary)))
    assert wrong.steps[0].verdict=='FIRST_WRONG_STEP'
    assert wrong.steps[0].counterexample.variableValues['x'].numerator=='0'
    # A partially defined branch does not become a total function by simplification.
    partial=Node(kind='PIECEWISE',branches=[Branch(expression=neg(x),condition=eq(x,num(0),'LESS_THAN'))])
    assert execute(request(expr(fn('ABS',x)),expr(partial))).steps[0].verdict=='FIRST_WRONG_STEP'


def test_tangent_preserves_poles_in_explicit_finite_domain():
    restrictions=[Restriction(id='lo',relation='GREATER_THAN',left=x,right=num(0)),
        Restriction(id='hi',relation='LESS_THAN',left=x,right=num(2))]
    result=execute(request(expr(fn('TAN',x)),expr(div(fn('SIN',x),fn('COS',x))),restrictions=restrictions))
    # A concrete finite domain must be proved; no passing numerical probe is accepted instead.
    assert result.steps[0].verdict=='VALID_EQUIVALENT'
    assert result.naturalDomain['kind']=='UNION'


def test_process_failure_is_a_typed_unavailable_learning_result():
    from app.policies.process_executor import ProcessExecutor
    value=LearningRequest(executionId=ID,correlationId=str(ID),action='TWIN',mistakeCode='ALG_FRACTION_ADD_DENOM')
    result=ProcessExecutor.failure(value,'TIMEOUT')
    assert result.status=='UNAVAILABLE' and result.twin is None and result.methods==[]


def test_additive_endpoints_require_internal_auth_and_matching_correlation():
    from fastapi.testclient import TestClient
    from app.main import app
    client=TestClient(app)
    payload=request(expr(x),expr(x)).model_dump(mode='json')
    assert client.post('/internal/v1/verify/attempt-v2',json=payload).status_code==401
    result=client.post('/internal/v1/verify/attempt-v2',json=payload,headers={'X-Internal-Token':'local_math_verifier_token_change_me','X-Request-Id':'wrong'})
    assert result.status_code==422


def test_uncertain_result_cannot_carry_diagnosis_or_malformed_set_evidence():
    from app.api.schemas.attempt_v2 import Result
    result=execute(request(expr(x),None)).model_dump(mode='json')
    result['steps'][0]['diagnosis']={'code':'ALG_SIGN_FLIP_DISTRIBUTE','evidenceId':str(ID)+':1',
        'explanationKey':'attempt.mistake.ALG_SIGN_FLIP_DISTRIBUTE','changedExpressionPath':'/','displayFallback':'STEP_LEVEL'}
    with pytest.raises(ValueError): Result.model_validate(result)
    result['steps'][0]['diagnosis']=None
    result['naturalDomain']={'kind':'INTERVAL','start':'0','end':'1','leftOpen':'false','rightOpen':False}
    with pytest.raises(ValueError): Result.model_validate(result)


def test_methods_do_not_claim_factoring_without_presenting_root_factors():
    source=request(eq(sub(pow2(x),num(2)),num(0)),members(fn('SQRT',num(2)),neg(fn('SQRT',num(2)))))
    result=learning(LearningRequest(executionId=ID,correlationId=str(ID),action='METHODS',source=source))
    assert result.status=='VERIFIED'
    factored=next(method for method in result.methods if method.methodId=='FACTORING')
    assert factored.steps[0].left.operator=='MULTIPLY'
    for method in result.methods:
        assert execute(request(source.original,*method.steps)).outcome=='INCOMPLETE_VALID_SO_FAR'
