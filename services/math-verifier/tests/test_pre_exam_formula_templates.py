from app.pre_exam_formula_templates import load_catalog, verify_catalog, CATALOG_PATH
from hashlib import sha256


def test_catalog_identities_counterexamples_and_rendered_math():
    assert verify_catalog() == sha256(CATALOG_PATH.read_bytes()).hexdigest()


def test_catalog_canonical_codes_and_domains():
    catalog, digest = load_catalog()
    assert digest == CATALOG_PATH.with_suffix('.sha256').read_text().strip()
    assert {c['skillCode'] for c in catalog['cards']} == {'MATH.ALGEBRA.FACTORING'}
    assert {c['mistakeCode'] for c in catalog['cards']} == {
        'ALG_SIGN_FLIP_DISTRIBUTE', 'ALG_FRESHMAN_DREAM_POWER'}
    assert all(c['domainTr'] == 'a ve b gerçek sayılardır.' for c in catalog['cards'])
