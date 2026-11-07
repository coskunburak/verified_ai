"""Sprint 7.3 adapter uses unchanged bounded v2 math; these are synthetic owner-run goldens."""
import json
from pathlib import Path
import pytest
from app.api.schemas.attempt_v2 import Request, Result
from app.attempt_verification_v2 import execute

ROOT = Path(__file__).resolve().parents[3]
CASES = json.loads((ROOT / 'packages/schemas/fixtures/attempt-evaluation-golden.json').read_text())['cases']

@pytest.mark.parametrize('case', CASES, ids=lambda c: c['id'])
def test_evaluation_math_never_promotes_uncertainty(case):
    request = Request.model_validate(case['request'])
    result = execute(request).validate_for(request)
    assert result.steps[0].verdict == case['expectedVerdict']
    assert len(result.steps) == len(request.steps)
    if case['id'] == 'ambiguous-ocr':
        assert result.steps[1].verdict == 'NOT_ASSESSED'
        assert result.steps[1].reasonCode == 'EARLIER_UNCERTAINTY'
        # Independent final intent can still be assessed, without rewriting that prefix.
        final = request.model_copy(update={'steps': [request.steps[-1]]})
        assert execute(final).steps[0].verdict == 'VALID_EQUIVALENT'
    if case['id'] == 'incomplete-final':
        assert result.outcome == 'INCOMPLETE_VALID_SO_FAR'
        assert not case['completeForm']
    if case['expectedVerdict'] == 'FIRST_WRONG_STEP':
        assert result.firstWrongStepIndex == 1
        assert result.steps[0].counterexampleStatus in {'AVAILABLE', 'COUNTEREXAMPLE_UNAVAILABLE'}
    for step in result.steps:
        if step.counterexample:
            assert step.counterexample.domainSatisfied
            assert step.counterexample.previousStepEvaluation != step.counterexample.submittedStepEvaluation


def test_execution_and_count_binding_rejects_foreign_evidence():
    request = Request.model_validate(CASES[0]['request'])
    result = execute(request)
    from uuid import uuid4
    with pytest.raises(ValueError):
        result.validate_for(request.model_copy(update={'executionId': uuid4()}))
    with pytest.raises(ValueError):
        result.validate_for(request.model_copy(update={'steps': request.steps[:1]}))


def test_false_verified_and_out_of_order_response_rejected():
    request = Request.model_validate(CASES[0]['request'])
    body = execute(request).model_dump(mode='json')
    body['outcome'] = 'VERIFIED'
    with pytest.raises(ValueError): Result.model_validate(body)
    body = execute(request).model_dump(mode='json')
    body['steps'][0]['index'] = 2
    with pytest.raises(ValueError): Result.model_validate(body)
