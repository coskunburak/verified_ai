import SwiftUI
import SwiftMath

/// Data-only recursive JSON. Rendering below admits only the canonical mathematical vocabulary.
indirect enum AttemptJSON: Codable, Sendable {
    case object([String: AttemptJSON]), array([AttemptJSON]), string(String), number(Double), bool(Bool), null
    init(from decoder: Decoder) throws {
        guard decoder.codingPath.count <= 80 else { throw NetworkError.invalidResponse }
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null }
        else if let v = try? c.decode(Bool.self) { self = .bool(v) }
        else if let v = try? c.decode(String.self) { self = .string(v) }
        else if let v = try? c.decode(Double.self) { self = .number(v) }
        else if let v = try? c.decode([AttemptJSON].self) { self = .array(v) }
        else { self = .object(try c.decode([String: AttemptJSON].self)) }
    }
    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .object(let v): try c.encode(v)
        case .array(let v): try c.encode(v)
        case .string(let v): try c.encode(v)
        case .number(let v): try c.encode(v)
        case .bool(let v): try c.encode(v)
        case .null: try c.encodeNil()
        }
    }
    subscript(_ key: String) -> Self { if case .object(let v) = self { return v[key] ?? .null }; return .null }
    var text: String { if case .string(let v) = self { return v }; return "" }
    var items: [Self] { if case .array(let v) = self { return v }; return [] }
    var flag: Bool { if case .bool(let v) = self { return v }; return false }
}

enum AttemptMathPresentation {
    static func setText(_ n: AttemptJSON, depth: Int = 0) -> String {
        guard depth < 24 else { return "?" }
        switch n["kind"].text {
        case "REALS": return "ℝ"
        case "EMPTY_SET": return "∅"
        case "FINITE_SET": return "{" + n["elements"].items.map(\.text).joined(separator: ", ") + "}"
        case "INTERVAL": return (n["leftOpen"].flag ? "(" : "[") + n["start"].text + ", " + n["end"].text + (n["rightOpen"].flag ? ")" : "]")
        case "UNION", "INTERSECTION": return n["sets"].items.map { setText($0, depth: depth + 1) }.joined(separator: n["kind"].text == "UNION" ? " ∪ " : " ∩ ")
        default: return "?"
        }
    }
    static func spoken(_ n: AttemptJSON, depth: Int = 0) -> String {
        guard latex(n, depth: depth) != nil else {
            return NSLocalizedString("attempt.math.unsupported", comment: "Unsupported speech")
        }
        return speech(n, depth: depth)
    }
    private static func speech(_ n: AttemptJSON, depth: Int = 0) -> String {
        guard depth < 32 else { return NSLocalizedString("attempt.math.unsupported", comment: "Unsupported speech") }
        func child(_ key: String) -> String { speech(n[key], depth: depth + 1) }
        func word(_ key: String) -> String {
            let resource = "attempt.math." + key
            let value = NSLocalizedString(resource, comment: "Spoken mathematical structure")
            return value == resource ? NSLocalizedString("attempt.math.unsupported", comment: "Unsupported speech") : value
        }
        func group(_ value: String) -> String { word("OPEN") + " " + value + " " + word("CLOSE") }
        switch n["kind"].text {
        case "NUMBER": return n["value"].text.replacingOccurrences(of: "-", with: word("NEGATE") + " ")
        case "VARIABLE": return n["symbol"].text
        case "CONSTANT": return word(n["value"].text)
        case "EXPRESSION": return child("expression")
        case "RELATION": return child("left") + " " + word(n["relation"].text) + " " + child("right")
        case "UNARY": return word("NEGATE") + " " + group(child("operand"))
        case "BINARY": return group(group(child("left")) + " " + word(n["operator"].text) + " " + group(child("right")))
        case "FUNCTION": return word(n["function"].text) + " " + n["args"].items.map { group(speech($0, depth: depth + 1)) }.joined(separator: ", ")
        case "FINITE_SET", "UNION", "INTERSECTION": return word(n["kind"].text) + " " + n["args"].items.map { speech($0, depth: depth + 1) }.joined(separator: ", ")
        case "INTERVAL": return word("INTERVAL") + " " + child("left") + ", " + child("right") + ", " + word(n["leftOpen"].flag ? "LEFT_OPEN" : "LEFT_CLOSED") + ", " + word(n["rightOpen"].flag ? "RIGHT_OPEN" : "RIGHT_CLOSED")
        case "PIECEWISE": return word("PIECEWISE") + " " + n["branches"].items.map { speech($0["expression"], depth: depth + 1) + ", " + conditionSpeech($0["condition"], depth: depth + 1) }.joined(separator: "; ")
        case "EMPTY_SET", "REALS": return word(n["kind"].text)
        default: return word("unsupported")
        }
    }
    private static func conditionSpeech(_ node: AttemptJSON, depth: Int) -> String {
        if case .null = node { return NSLocalizedString("attempt.math.OTHERWISE", comment: "Otherwise branch") }
        return speech(node, depth: depth)
    }
    private static func variableLatex(_ n: AttemptJSON, depth: Int = 0) -> String? {
        guard depth < 32 else { return nil }
        if n["kind"].text == "VARIABLE" { return latex(n) }
        switch n {
        case .object(let values):
            for key in values.keys.sorted() { if let value = variableLatex(values[key] ?? .null, depth: depth + 1) { return value } }
        case .array(let values):
            for child in values { if let value = variableLatex(child, depth: depth + 1) { return value } }
        default: break
        }
        return nil
    }
    static func latex(_ n: AttemptJSON, path: String = "", highlight: String? = nil, depth: Int = 0) -> String? {
        guard depth < 32 else { return nil }
        func child(_ key: String) -> String? { latex(n[key], path: path + "/" + key, highlight: highlight, depth: depth + 1) }
        func args() -> [String]? {
            let values = n["args"].items.enumerated().map { latex($0.element, path: path + "/args/\($0.offset)", highlight: highlight, depth: depth + 1) }
            return values.allSatisfy { $0 != nil } ? values.compactMap { $0 } : nil
        }
        let value: String
        switch n["kind"].text {
        case "NUMBER":
            let v = n["value"].text
            guard v.range(of: #"^-?(0|[1-9][0-9]*)(\.[0-9]+)?$"#, options: .regularExpression) != nil, v.count <= 64 else { return nil }
            value = v
        case "VARIABLE":
            let v = n["symbol"].text
            guard v.range(of: #"^[A-Za-z][A-Za-z0-9_]{0,15}$"#, options: .regularExpression) != nil else { return nil }
            value = v.replacingOccurrences(of: "_", with: "\\_")
        case "CONSTANT":
            guard let v = ["PI": "\\pi", "POSITIVE_INFINITY": "\\infty", "NEGATIVE_INFINITY": "-\\infty"][n["value"].text] else { return nil }; value = v
        case "EXPRESSION": guard let v = child("expression") else { return nil }; value = v
        case "RELATION":
            guard let a = child("left"), let b = child("right"), let op = ["EQUALS":"=", "NOT_EQUALS":"\\ne", "LESS_THAN":"<", "LESS_THAN_OR_EQUAL":"\\leq", "GREATER_THAN":">", "GREATER_THAN_OR_EQUAL":"\\geq", "MEMBER_OF":"\\in"][n["relation"].text] else { return nil }
            value = a + op + b
        case "UNARY": guard n["operator"].text == "NEGATE", let a = child("operand") else { return nil }; value = "-\\left(" + a + "\\right)"
        case "BINARY":
            guard let a = child("left"), let b = child("right") else { return nil }
            switch n["operator"].text {
            case "DIVIDE": value = "\\frac{" + a + "}{" + b + "}"
            case "POWER": value = "{\\left(" + a + "\\right)}^{" + b + "}"
            case "ADD", "SUBTRACT", "MULTIPLY":
                let op = ["ADD":"+", "SUBTRACT":"-", "MULTIPLY":"\\cdot "][n["operator"].text]!
                value = "\\left(" + a + op + b + "\\right)"
            default: return nil
            }
        case "FUNCTION":
            guard let a = args(), let first = a.first else { return nil }
            let symbol = variableLatex(n["args"].items.first ?? .null) ?? "x"
            switch n["function"].text {
            case "SQRT": value = "\\sqrt{" + first + "}"
            case "ABS": value = "\\left|" + first + "\\right|"
            case "TOTAL": guard a.count == 2 else { return nil }; value = "\\mathrm{total}\\left(" + a.joined(separator: ",") + "\\right)"
            case "DIFF": value = "\\left(" + first + "\\right)'"
            case "LIMIT": guard a.count == 2 else { return nil }; value = "\\lim_{" + symbol + "\\to " + a[1] + "}" + first
            case "INTEGRAL": guard a.count == 3 else { return nil }; value = "\\int_{" + a[1] + "}^{" + a[2] + "}" + first + "\\,d" + symbol
            case "SIN", "COS", "TAN", "LOG", "EXP":
                value = "\\" + n["function"].text.lowercased() + (a.count == 2 ? "_{" + a[1] + "}" : "") + "\\left(" + first + "\\right)"
            default: return nil
            }
        case "PIECEWISE":
            var rows: [String] = []
            for (index, branch) in n["branches"].items.enumerated() {
                let branchPath = path + "/branches/\(index)"
                guard let expression = latex(branch["expression"], path: branchPath + "/expression", highlight: highlight, depth: depth + 1) else { return nil }
                let condition: String
                if case .null = branch["condition"] { condition = "\\mathrm{otherwise}" }
                else {
                    guard let rendered = latex(branch["condition"], path: branchPath + "/condition", highlight: highlight, depth: depth + 1) else { return nil }
                    condition = rendered
                }
                rows.append(expression + " & " + condition)
            }
            value = "\\begin{cases}" + rows.joined(separator: "\\\\") + "\\end{cases}"
        case "EMPTY_SET": value = "\\emptyset"
        case "REALS": value = "\\mathbb{R}"
        case "FINITE_SET": guard let a = args() else { return nil }; value = "\\{" + a.joined(separator: ",") + "\\}"
        case "INTERVAL":
            guard let a = child("left"), let b = child("right") else { return nil }
            value = (n["leftOpen"].flag ? "(" : "[") + a + "," + b + (n["rightOpen"].flag ? ")" : "]")
        case "UNION", "INTERSECTION": guard let a = args() else { return nil }; value = a.joined(separator: n["kind"].text == "UNION" ? "\\cup" : "\\cap")
        default: return nil
        }
        return path == highlight ? "\\textcolor{#C62828}{" + value + "}" : value
    }
}

struct AttemptMathView: View {
    let json: String
    let fallback: String
    var highlight: String? = nil
    @ScaledMetric(relativeTo: .title3) private var pointSize = 22
    var body: some View {
        if json.utf8.count <= 12000, let node = try? JSONDecoder().decode(AttemptJSON.self, from: Data(json.utf8)),
           let latex = AttemptMathPresentation.latex(node, highlight: highlight) {
            ScrollView(.horizontal) {
                NativeMathLabel(latex: latex, fallback: fallback, pointSize: pointSize)
                    .fixedSize().padding(.vertical, 4)
            }.accessibilityElement(children: .ignore).accessibilityLabel(Text(verbatim: AttemptMathPresentation.spoken(node)))
        } else {
            Text(verbatim: fallback).font(.body.monospaced()).textSelection(.enabled)
                .accessibilityLabel("attempt.math.unsupported")
        }
    }
}

struct NativeMathLabel: UIViewRepresentable {
    let latex: String
    let fallback: String
    let pointSize: CGFloat
    final class Container: UIView {
        let math = MTMathUILabel()
        let plain = UILabel()
        override init(frame: CGRect) {
            super.init(frame: frame); addSubview(math); addSubview(plain)
            math.displayErrorInline = false; math.textAlignment = .left; plain.numberOfLines = 0
        }
        required init?(coder: NSCoder) { return nil }
        override func layoutSubviews() { super.layoutSubviews(); math.frame = bounds; plain.frame = bounds }
        override var intrinsicContentSize: CGSize { math.isHidden ? plain.intrinsicContentSize : math.intrinsicContentSize }
    }
    func makeUIView(context: Context) -> Container { Container(frame: .zero) }
    func updateUIView(_ view: Container, context: Context) {
        // SwiftMath reparses when latex is assigned. Keep only this view's current content;
        // no cross-account/global mathematical cache is introduced.
        if view.math.latex != latex { view.math.latex = latex }
        if view.math.fontSize != pointSize { view.math.fontSize = pointSize }
        view.math.textColor = .label
        view.math.isHidden = view.math.error != nil; view.plain.isHidden = !view.math.isHidden
        view.plain.text = fallback; view.plain.font = .monospacedSystemFont(ofSize: pointSize, weight: .regular)
        view.invalidateIntrinsicContentSize()
    }
    func sizeThatFits(_ proposal: ProposedViewSize, uiView: Container, context: Context) -> CGSize? { uiView.intrinsicContentSize }
}
