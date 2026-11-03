import SwiftUI

/// Explicit parentheses preserve AST grouping, including negative bases and exponents.
/// Plain Text only: no Markdown, links, WebKit, LaTeX engine or code execution.
enum MathPresentation {
    static func expression(_ node: MathExpression, spoken: Bool = false) -> String {
        func word(_ key: String) -> String { NSLocalizedString("solve.math." + key, comment: "Spoken mathematics") }
        func group(_ text: String) -> String { spoken ? "\(word("open")) \(text) \(word("close"))" : "(\(text))" }
        switch node {
        case .number(let value, _):
            return spoken ? value.replacingOccurrences(of: "-", with: word("NEGATE") + " ") : value
        case .variable(let symbol): return symbol
        case .negate(let x): return (spoken ? word("NEGATE") + " " : "−") + group(expression(x, spoken: spoken))
        case .binary(let op, let a, let b):
            if op == .power {
                // Parenthesize the entire base, including a negative numeric literal or unary minus.
                return group(group(expression(a, spoken: spoken)) + " " + (spoken ? word(op.rawValue) : op.symbol)
                    + " " + group(expression(b, spoken: spoken)))
            }
            return group(expression(a, spoken: spoken) + " " + (spoken ? word(op.rawValue) : op.symbol) + " " + expression(b, spoken: spoken))
        case .function(let f, let x):
            return (spoken ? word(f.rawValue) : (f == .sqrt ? "√" : f.rawValue.lowercased())) + group(expression(x, spoken: spoken))
        }
    }
    static func statement(_ value: MathStatement, spoken: Bool = false) -> String {
        switch value {
        case .expression(let x): return expression(x, spoken: spoken)
        case .relation(let op, let a, let b):
            let relation = spoken ? NSLocalizedString("solve.math." + op.rawValue, comment: "Spoken relation") : op.symbol
            return expression(a, spoken: spoken) + " " + relation + " " + expression(b, spoken: spoken)
        }
    }
}

struct MathematicalExpressionView: View {
    enum Style {
        case prominent
        case step
    }

    let statement: MathStatement
    var style: Style = .step

    @ScaledMetric(relativeTo: .title3) private var pointSize = 22
    var body: some View {
        ScrollView(.horizontal) {
            NativeMathLabel(latex: latex(statement), fallback: MathPresentation.statement(statement), pointSize: pointSize)
                .fixedSize().padding(.vertical, 4)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: MathPresentation.statement(statement, spoken: true)))
    }
    private func latex(_ statement: MathStatement) -> String {
        switch statement {
        case .expression(let n): return latex(n)
        case .relation(let op, let a, let b):
            let relation: String
            switch op { case .equal: relation = "="; case .less: relation = "<"; case .lessEqual: relation = "\\leq"; case .greater: relation = ">"; case .greaterEqual: relation = "\\geq" }
            return latex(a) + relation + latex(b)
        }
    }
    private func latex(_ n: MathExpression) -> String {
        switch n {
        case .number(let value, _): return value
        case .variable(let symbol): return symbol.replacingOccurrences(of: "_", with: "\\_")
        case .negate(let n): return "-\\left(" + latex(n) + "\\right)"
        case .function(let f, let n): return f == .sqrt ? "\\sqrt{" + latex(n) + "}" : "\\" + f.rawValue.lowercased() + "\\left(" + latex(n) + "\\right)"
        case .binary(let op, let a, let b):
            switch op {
            case .divide: return "\\frac{" + latex(a) + "}{" + latex(b) + "}"
            case .power: return "{\\left(" + latex(a) + "\\right)}^{" + latex(b) + "}"
            default: return "\\left(" + latex(a) + (op == .multiply ? "\\cdot " : op.symbol) + latex(b) + "\\right)"
            }
        }
    }
}
