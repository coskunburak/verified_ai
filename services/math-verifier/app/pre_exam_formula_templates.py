"""Offline proofs over the exact bundled Java catalog; never evaluates arbitrary expressions."""
from hashlib import sha256
from pathlib import Path
import json
import sympy as sp

CATALOG_PATH = Path(__file__).resolve().parents[3] / 'services/api/src/main/resources/exam/pre-exam-catalog-v1.json'


def load_catalog():
    raw = CATALOG_PATH.read_bytes()
    return json.loads(raw), sha256(raw).hexdigest()


def verify_catalog():
    catalog, digest = load_catalog()
    assert catalog['catalogVersion'] == 'pre-exam-catalog-v1'
    a, b = sp.symbols('a b', real=True)
    proofs = {
        'difference-squares': ((a-b)*(a+b), a*a-b*b, a*a+b*b, '5',
                               r'(a-b)(a+b)=a^2-b^2', '(a − b)(a + b) = a² − b²',
                               '(3 − 2)(3 + 2) = 5 ≠ 13'),
        'square-sum': ((a+b)**2, a*a+2*a*b+b*b, a*a+b*b, '25',
                       r'(a+b)^2=a^2+2ab+b^2', '(a + b)² = a² + 2ab + b²',
                       '(3 + 2)² = 25 ≠ 13'),
    }
    assert {c['catalogId'] for c in catalog['cards']} == set(proofs)
    assert len(catalog['cards']) == len(proofs)
    for card in catalog['cards']:
        lhs, rhs, wrong, answer, latex, display, counter = proofs[card['catalogId']]
        assert sp.simplify(lhs-rhs) == 0
        bindings = {a: sp.Integer(3), b: sp.Integer(2)}
        assert lhs.subs(bindings) != wrong.subs(bindings)
        assert str(lhs.subs(bindings)) == answer == card['answerDisplay']
        assert card['ruleLatex'] == latex
        assert card['ruleDisplay'] == display
        assert card['counterExampleDisplay'] == counter == card['counterExampleLatex']
        assert card['domainEn'] == 'a and b are real numbers.'
        # These polynomial identities are valid for all real inputs, including zero and negatives.
        for av, bv in [(0, 0), (-3, 2), (sp.Rational(1, 2), -2)]:
            assert (lhs-rhs).subs({a: av, b: bv}) == 0
    return digest
