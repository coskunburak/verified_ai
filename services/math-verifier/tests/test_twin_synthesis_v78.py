from app.twin_synthesizer import DeterministicTwinSynthesizer


def test_deterministic_reproducibility_and_isomorphic_divergence():
    source_ast = {"coeff_a": 3, "const_b": 4, "rhs_c": 19}
    attempt_id = "88888888-4444-4444-8888-111111111111"

    r1 = DeterministicTwinSynthesizer.synthesize(
        curriculum_code="MATH_GRADE_09",
        target_skill_code="ALG_LIN_EQ_02",
        root_cause_skill_code="ALG_LIN_ISO_01",
        source_problem_ast=source_ast,
        attempt_id=attempt_id,
    )
    r2 = DeterministicTwinSynthesizer.synthesize(
        curriculum_code="MATH_GRADE_09",
        target_skill_code="ALG_LIN_EQ_02",
        root_cause_skill_code="ALG_LIN_ISO_01",
        source_problem_ast=source_ast,
        attempt_id=attempt_id,
    )

    assert r1 == r2
    assert r1.problemStatementLatex != "3x + 4 = 19"
    assert len(r1.verifierProofHash) == 64
    assert len(r1.solutionSteps) >= 2
    assert DeterministicTwinSynthesizer.verify_symbolic_equivalence(
        f"x = {r1.expectedCanonicalExpr}", r1.expectedCanonicalExpr
    )
    assert not DeterministicTwinSynthesizer.verify_symbolic_equivalence(
        "999999", r1.expectedCanonicalExpr
    )
