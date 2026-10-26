import unittest
import json
from pathlib import Path
from summarize_qwen_evaluation import equivalent, rational


def number(value):
    return {"kind": "NUMBER", "value": str(value)}


class IndependentNumericGradingTest(unittest.TestCase):
    def test_supported_sources_match_independent_canonical_schema(self):
        from jsonschema import Draft202012Validator, FormatChecker
        from referencing import Registry, Resource
        root = Path(__file__).resolve().parents[2]
        schemas = root / "packages/schemas"
        canonical = json.loads((schemas / "canonical-problem.schema.json").read_text())
        verifier = json.loads((schemas / "verifier-input.schema.json").read_text())
        registry = Registry().with_resource(verifier["$id"], Resource.from_contents(verifier))
        validator = Draft202012Validator(canonical, registry=registry, format_checker=FormatChecker())
        corpus = json.loads((root / "evaluations/fixtures/solver/qwen-smoke-v1.json").read_text())
        for case in corpus["cases"]:
            if case["expectedDisposition"] == "CANDIDATE":
                with self.subTest(case=case["id"]):
                    self.assertEqual(list(validator.iter_errors(case["source"])), [])

    def test_decimal_arithmetic_is_exact(self):
        value = {"kind": "BINARY", "operator": "ADD", "left": number("0.1"), "right": number("0.2")}
        self.assertEqual(rational(value), rational(number("0.3")))

    def test_well_formed_wrong_answer_is_not_correct(self):
        self.assertFalse(equivalent({"kind": "EXPRESSION", "expression": number(999)},
                                    {"kind": "EXPRESSION", "expression": number(1)}))

    def test_symbolic_equivalence_is_unknown_without_review(self):
        self.assertIsNone(equivalent({"kind": "EXPRESSION", "expression": {"kind": "VARIABLE", "symbol": "x"}},
                                     {"kind": "EXPRESSION", "expression": number(1)}))

    def test_no_executable_strings_and_bounded_exponents(self):
        for value in ["__import__('os')", "NaN", "1e999999"]:
            with self.assertRaises(ValueError):
                rational(number(value))
        with self.assertRaises(ValueError):
            rational({"kind": "BINARY", "operator": "POWER", "left": number(2), "right": number(100000)})


if __name__ == "__main__":
    unittest.main()
