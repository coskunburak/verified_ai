"""Representation-only cross-runtime contract goldens; mathematical authority stays in existing verifier tests."""
import json
from pathlib import Path
from copy import deepcopy
import pytest
from jsonschema import Draft202012Validator, FormatChecker

SCHEMAS = Path(__file__).resolve().parents[3] / 'packages/schemas'


def test_synthetic_goldens_do_not_claim_correctness_or_repair_notation():
    golden = json.loads((SCHEMAS / 'fixtures/attempt-structured-work-golden.json').read_text())
    assert golden['synthetic']
    indexed = {case['id']: case for case in golden['cases']}
    for key in ('domain-hole', 'root-loss', 'radical', 'log-domain'):
        assert indexed[key]['status'] == 'NORMALIZED'  # Syntax only; Java executes actual parser fixtures.
    for key in ('domain-hole-unicode', 'root-loss-unicode', 'absolute-bars', 'calculus', 'injection'):
        assert indexed[key]['status'] == 'UNSUPPORTED'
    assert indexed['fraction-layout']['status'] == indexed['unconfirmed']['status'] == 'REVIEW_REQUIRED'
    assert all(not {'verified', 'mastery', 'firstWrongStep'} & case.keys() for case in golden['cases'])


@pytest.mark.parametrize('name', ['attempt-structured-work-request-v1', 'attempt-structured-work-v1'])
def test_versioned_fixture_preserves_exact_text_and_strict_boundaries(name):
    schema = json.loads((SCHEMAS / (name + '.schema.json')).read_text())
    value = json.loads((SCHEMAS / 'fixtures' / (name + '.json')).read_text())
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    validator.validate(value)
    for field in ('verified', 'correctness', 'mastery'):
        assert list(validator.iter_errors({**value, field: True}))
    bad = deepcopy(value); bad['steps'] *= 7
    assert list(validator.iter_errors(bad))
