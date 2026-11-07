"""Synthetic classification goldens. Owner execution only; no provider/model calls."""
import copy
import json
from pathlib import Path
import pytest
from pydantic import ValidationError
from app.api.schemas.attempt_v2 import Request
from app.attempt_verification_v2 import execute

ROOT = Path(__file__).resolve().parents[3]
GOLDEN = json.loads((ROOT / 'packages/schemas/fixtures/attempt-mistake-golden-v1.json').read_text())['cases']

@pytest.mark.parametrize('case', GOLDEN, ids=lambda c: c['id'])
def test_signature_has_disproof_bound_to_first_causal_step(case):
    request = Request.model_validate(case['request'])
    result = execute(request)
    assert case['evidenceJustification']
    assert result.firstWrongStepIndex == 1
    first = result.steps[0]
    assert first.verdict == 'FIRST_WRONG_STEP' and first.reasonCode == 'NON_EQUIVALENT'
    assert first.mistakeCode == case['code']
    assert first.diagnosis.evidenceId == str(request.executionId) + ':1'
    assert first.diagnosis.classifierPolicyVersion == 'structural-signatures-v1'
    assert result.steps[1].verdict == 'NOT_ASSESSED'
    if first.counterexample:
        assert first.counterexample.domainSatisfied
        assert first.counterexample.previousStepEvaluation != first.counterexample.submittedStepEvaluation

@pytest.mark.parametrize('case', GOLDEN, ids=lambda c: c['id'])
def test_identity_and_unknown_scope_never_produce_category_evidence(case):
    request = copy.deepcopy(case['request'])
    request['steps'] = [{'statement': request['original']}]
    result = execute(Request.model_validate(request))
    assert result.firstWrongStepIndex is None
    assert result.steps[0].verdict == 'VALID_EQUIVALENT'
    assert result.steps[0].diagnosis is None
    request['domainBasis'] = 'UNRESOLVED'
    result = execute(Request.model_validate(request))
    assert result.steps[0].verdict == 'UNCERTAIN' and result.steps[0].reasonCode == 'UNKNOWN_DOMAIN'
    assert result.steps[0].diagnosis is None


def test_unconfirmed_line_stops_causal_classification_before_later_error():
    request = copy.deepcopy(GOLDEN[0]['request'])
    request['steps'].insert(0, {'statement': None})
    result = execute(Request.model_validate(request))
    assert [s.verdict for s in result.steps] == ['UNCERTAIN', 'NOT_ASSESSED', 'NOT_ASSESSED']
    assert all(s.diagnosis is None for s in result.steps)


def test_alternative_syntax_and_near_match_do_not_inherit_signature():
    request = copy.deepcopy(GOLDEN[1]['request'])
    # Near miss: x^2+5 does not equal squaring each original addend.
    request['steps'][0]['statement']['expression']['right']['value'] = '5'
    assert execute(Request.model_validate(request)).steps[0].mistakeCode == 'INVALID_TRANSFORMATION'
    original = request['original']['expression']['left']
    request['steps'] = [{'statement': {'kind':'EXPRESSION', 'expression': {
        'kind':'BINARY','operator':'MULTIPLY','left':original,'right':original}}}]
    assert execute(Request.model_validate(request)).steps[0].verdict == 'VALID_EQUIVALENT'


def test_ambiguous_structural_causes_keep_disproof_without_specific_label(monkeypatch):
    import app.verifiers.attempt_diagnosis as diagnosis
    monkeypatch.setattr(diagnosis, '_matches', lambda *args: iter([
        ('ALG_SIGN_FLIP_DISTRIBUTE','/expression'), ('ALG_FRESHMAN_DREAM_POWER','/expression')]))
    result = execute(Request.model_validate(GOLDEN[0]['request']))
    assert result.steps[0].verdict == 'FIRST_WRONG_STEP'
    assert result.steps[0].mistakeCode == 'INVALID_TRANSFORMATION'


def test_unbounded_periods_and_oversized_ast_fail_closed():
    request = copy.deepcopy(next(c['request'] for c in GOLDEN if c['code'] == 'TRIG_PERIOD_OMITTED'))
    request['restrictions'] = []
    result = execute(Request.model_validate(request))
    assert result.steps[0].verdict == 'UNCERTAIN'
    request = copy.deepcopy(GOLDEN[0]['request'])
    request['steps'] *= 7
    with pytest.raises(ValidationError): Request.model_validate(request)


def test_explicit_denominator_restriction_is_not_an_extraneous_root():
    from test_attempt_v2_product import request, eq, expr, div, sub, pow2, x, num, Restriction
    original = expr(div(sub(pow2(x),num(1)),sub(x,num(1))))
    simplified = expr(sub(x,num(-1)))
    scoped = request(original, simplified, restrictions=[Restriction(id='hole',relation='NOT_EQUALS',left=x,right=num(1))])
    result = execute(scoped)
    assert result.firstWrongStepIndex is None and result.steps[0].diagnosis is None


def test_multiple_locations_of_one_signature_use_step_level_evidence(monkeypatch):
    import app.verifiers.attempt_diagnosis as diagnosis
    monkeypatch.setattr(diagnosis, '_matches', lambda *args: iter([
        ('ALG_SIGN_FLIP_DISTRIBUTE','/expression/left'), ('ALG_SIGN_FLIP_DISTRIBUTE','/expression/right')]))
    result = execute(Request.model_validate(GOLDEN[0]['request']))
    assert result.steps[0].mistakeCode == 'ALG_SIGN_FLIP_DISTRIBUTE'
    assert result.steps[0].diagnosis.changedExpressionPath == '/'
