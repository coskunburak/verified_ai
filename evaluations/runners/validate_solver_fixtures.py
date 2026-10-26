#!/usr/bin/env python3
"""Owner-only independent JSON Schema fixture gate. No provider, inference or math verification."""
import json
import hashlib
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[2]

def main():
    schema = json.loads((ROOT / 'packages/schemas/solution-candidate.schema.json').read_text())
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    path = ROOT / 'evaluations/fixtures/solver/cases.json'
    raw = path.read_bytes()
    cases = json.loads(raw)['cases']
    failures = []
    for case in cases:
        actual = validator.is_valid(case['output'])
        if actual != case['schemaValid']:
            failures.append(case['id'])
    report = {
        'schemaVersion': 'solver-contract-check-v1',
        'decision': 'FAIL' if failures else 'PASS',
        'datasetSha256': hashlib.sha256(raw).hexdigest(),
        'cases': len(cases),
        'unexpectedResults': failures,
        'realModelEvidence': 'NONE',
        'semanticAcceptance': 'RUN SolutionCandidateContractTest SEPARATELY',
        'answerAccuracy': None, 'stepCorrectness': None,
        'p50LatencyMs': None, 'p95LatencyMs': None, 'tokenUsage': None,
        'inferenceCost': None, 'acceptedCandidateCost': None,
    }
    dest = ROOT / '.generated/evaluations/solver'
    dest.mkdir(parents=True, exist_ok=True)
    (dest / 'schema-report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
    return 1 if failures else 0

if __name__ == '__main__':
    raise SystemExit(main())
