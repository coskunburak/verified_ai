#!/usr/bin/env python3
"""Owner-only critical learning-path catalog inventory and placeholder gate."""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PREFIXES = ('solve.', 'verification.', 'explanation.', 'tutor.', 'attempt.', 'visualization.')
TOKEN = re.compile(r'%(?:(\d+)\$)?[-+ #0]*(?:\d+|\*)?(?:\.(?:\d+|\*))?(hh|ll|[hlLzjt])?([@diuoxXfFeEgGaAcCsSp])')


def placeholders(text):
    result = []
    index = 0
    for match in TOKEN.finditer(text.replace('%%', '')):
        index += 1
        result.append((int(match[1] or index), (match[2] or '') + match[3]))
    return sorted(result)


def units(node):
    if isinstance(node, dict):
        if 'stringUnit' in node:
            yield node['stringUnit']
        for key, child in node.items():
            if key != 'stringUnit':
                yield from units(child)


def inspect_catalog(strings):
    errors = []
    keys = sorted(k for k in strings if k.startswith(PREFIXES))
    for key in keys:
        locales = strings[key].get('localizations', {})
        english = list(units(locales.get('en', {})))
        expected = placeholders(english[0].get('value', '')) if english else []
        for language in ('en', 'tr'):
            translated = list(units(locales.get(language, {})))
            if not translated:
                errors.append(f'{key}:{language}:missing')
            for unit in translated:
                value = unit.get('value', '')
                if not value.strip() or value == key or unit.get('state') != 'translated':
                    errors.append(f'{key}:{language}:untranslated')
                if placeholders(value) != expected:
                    errors.append(f'{key}:{language}:placeholder-mismatch')
    return keys, errors


def main(output):
    strings = json.loads((ROOT / 'apps/ios/VerifiedAI/Resources/Localizable.xcstrings').read_text())['strings']
    keys, errors = inspect_catalog(strings)
    # Literal UI calls only: identifiers and internal enum values are not localization keys.
    references = {}
    pattern = re.compile(r'(?:Text|Label|Button|Menu|Picker|Toggle|ProgressView|LocalizedStringKey|NSLocalizedString)\(\s*"((?:solve|verification|explanation|tutor|attempt|visualization)\.[^"\\]*)"')
    for feature in ('ProblemSolving', 'Tutor', 'AttemptAnalysis'):
        for path in (ROOT / 'apps/ios/VerifiedAI/Features' / feature).rglob('*.swift'):
            for key in pattern.findall(path.read_text()):
                if key.endswith('.'):
                    continue  # Dynamic enum families are checked through the complete prefix inventory.
                references.setdefault(key, []).append(str(path.relative_to(ROOT)))
                if key not in strings:
                    errors.append(f'{key}:missing-source-reference')
    result = {'status': 'FAIL' if errors or not keys or not references else 'PASS',
              'keys': keys, 'literalReferences': references, 'failedCases': sorted(set(errors)),
              'manualReview': 'EN/TR mathematical glossary and pronunciation require owner review'}
    Path(output).write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    return 0 if result['status'] == 'PASS' else 1


if __name__ == '__main__':
    sys.exit(main(sys.argv[1]))
