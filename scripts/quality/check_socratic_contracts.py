#!/usr/bin/env python3
"""Owner-only contract reconciliation. Does not access secrets or execute inference."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def main():
    schema = json.loads((ROOT / 'packages/schemas/tutor-turn-v1.schema.json').read_text())
    api = (ROOT / 'packages/contracts/openapi/public-api.yaml').read_text()
    marker = '    TutorTurn: '
    embedded, _ = json.JSONDecoder().raw_decode(api.split(marker, 1)[1])
    comparable = {k: v for k, v in schema.items() if k not in ('$schema', '$id', 'title')}
    if embedded != comparable:
        raise SystemExit('TutorTurn OpenAPI and versioned schema differ')
    if '/tutor-sessions/{tutorSessionId}/turns:' not in api:
        raise SystemExit('Missing turn operation')
    if not {'correctness', 'referenceStatus', 'uncertain', 'provenance', 'progress'} <= set(schema['required']):
        raise SystemExit('Missing authoritative boundary fields')
    config = (ROOT / 'services/api/src/main/resources/application.yml').read_text()
    if not re.search(r'TUTOR:\s*\n\s+enabled: false', config):
        raise SystemExit('TUTOR provider must remain disabled')
    if 'socratic-enabled: ${TUTOR_SOCRATIC_ENABLED:false}' not in config:
        raise SystemExit('Deterministic policy must default disabled')
    for path in ('SPRINT_6.5_IMPLEMENTATION_MAP.md', 'SPRINT_6.5_EXECUTION_REPORT.md'):
        doc = ROOT / 'docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring' / path
        if not doc.is_file():
            raise SystemExit('Missing sprint documentation: ' + path)
    print('Socratic schema/OpenAPI/disabled-route/documentation contracts reconciled.')


if __name__ == '__main__':
    main()
