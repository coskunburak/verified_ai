import pytest

from app.domain.errors import UnsafeExpressionError
from app.parsing.safe_parser import parse_safe_expression


def test_parser_accepts_supported_expression() -> None:
    parsed = parse_safe_expression("2*x + 3", ["x"])

    assert str(parsed) == "2*x + 3"


def test_parser_rejects_unknown_identifier() -> None:
    with pytest.raises(UnsafeExpressionError):
        parse_safe_expression("__import__('os').system('id')", [])


def test_parser_rejects_excessive_length() -> None:
    with pytest.raises(UnsafeExpressionError):
        parse_safe_expression("x" * 300, ["x"])

