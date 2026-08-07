from __future__ import annotations

import sympy as sp

ALLOWED_SYMBOL_NAMES = {
    "a",
    "b",
    "c",
    "d",
    "m",
    "n",
    "t",
    "x",
    "y",
    "z",
}

ALLOWED_FUNCTIONS = {
    "sqrt": sp.sqrt,
    "sin": sp.sin,
    "cos": sp.cos,
    "tan": sp.tan,
    "log": sp.log,
    "exp": sp.exp,
}


def allowed_locals(variable_names: list[str] | None = None) -> dict[str, object]:
    symbols = {name: sp.Symbol(name) for name in ALLOWED_SYMBOL_NAMES}
    for name in variable_names or []:
        if name in ALLOWED_SYMBOL_NAMES:
            symbols[name] = sp.Symbol(name)
    return {**ALLOWED_FUNCTIONS, **symbols}

