import Foundation

/// Deterministic, bounded Turkish (`tr-TR`) Spoken Math formatter for exam stems,
/// options, and raw LaTeX expressions per ADR-010 & Sprint 9.10 INV-EXAM-006.
/// Presentation-side accessibility transformation only; does not alter server truth
/// or invoke external math verifiers/LLMs.
public enum SpokenMathTurkishAccessibilityFormatter {

    public struct SpokenMathResult: Sendable, Equatable {
        public let spokenText: String
        public let hasUnsupportedToken: Bool
        public let warnings: [String]

        public init(spokenText: String, hasUnsupportedToken: Bool, warnings: [String]) {
            self.spokenText = spokenText
            self.hasUnsupportedToken = hasUnsupportedToken
            self.warnings = warnings
        }
    }

    public static let maxMarkdownLength = 8_192
    public static let maxLatexLength = 1_024
    public static let maxNestingDepth = 16
    public static let maxTokens = 512

    public static func formatMarkdown(_ rawMarkdown: String) -> String {
        if rawMarkdown.count > maxMarkdownLength {
            return "anlaşılamayan matematik ifadesi; içerik uzunluk sınırını aştı"
        }
        let segments = tokenizeMarkdownSegments(rawMarkdown)
        var out: [String] = []
        for segment in segments {
            switch segment {
            case .prose(let text):
                let cleaned = normalizeTurkishProsePunctuation(text)
                if !cleaned.isEmpty {
                    out.append(cleaned)
                }
            case .math(let latex):
                let formatted = formatLatex(latex)
                if !formatted.spokenText.isEmpty {
                    out.append(formatted.spokenText)
                }
            case .malformed(let hint):
                out.append(hint)
            }
        }
        return normalizeSpacing(out.joined(separator: " "))
    }

    public static func formatOption(
        key: String,
        contentMarkdown: String,
        contentLatex: String? = nil,
        isSelected: Bool,
        isEliminated: Bool
    ) -> String {
        var parts: [String] = ["\(key) şıkkı"]
        let trimmedMd = contentMarkdown.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmedMd.isEmpty {
            parts.append(formatMarkdown(trimmedMd))
        } else if let latex = contentLatex, !latex.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            parts.append(formatLatex(latex).spokenText)
        }
        if isSelected {
            parts.append("seçili")
        }
        if isEliminated {
            parts.append("elenmiş")
        }
        return normalizeSpacing(parts.joined(separator: ", "))
    }

    public static func formatLatex(_ rawLatex: String) -> SpokenMathResult {
        let trimmed = rawLatex.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return SpokenMathResult(spokenText: "", hasUnsupportedToken: false, warnings: [])
        }
        guard trimmed.count <= maxLatexLength else {
            return SpokenMathResult(
                spokenText: "anlaşılamayan matematik ifadesi",
                hasUnsupportedToken: true,
                warnings: ["LATEX_LENGTH_EXCEEDED"]
            )
        }

        let lexResult = lexLatex(trimmed)
        if lexResult.malformed {
            return SpokenMathResult(
                spokenText: "anlaşılamayan matematik ifadesi",
                hasUnsupportedToken: true,
                warnings: lexResult.warnings
            )
        }
        var parser = LatexParser(tokens: lexResult.tokens, maxDepth: maxNestingDepth)
        let rendered = parser.parseSequence(stopAt: nil, depth: 0)
        var warnings = lexResult.warnings + parser.warnings
        if parser.malformed {
            let safePart = normalizeSpacing(rendered)
            let fallback = safePart.isEmpty
                ? "anlaşılamayan matematik ifadesi"
                : "anlaşılamayan matematik ifadesi: \(safePart)"
            return SpokenMathResult(
                spokenText: fallback,
                hasUnsupportedToken: true,
                warnings: Array(Set(warnings)).sorted()
            )
        }
        if let leftover = parser.peek(), leftover.kind != .eof {
            warnings.append("UNCONSUMED_TOKENS")
            return SpokenMathResult(
                spokenText: "anlaşılamayan matematik ifadesi",
                hasUnsupportedToken: true,
                warnings: Array(Set(warnings)).sorted()
            )
        }
        return SpokenMathResult(
            spokenText: normalizeSpacing(rendered),
            hasUnsupportedToken: parser.hasUnsupported || !warnings.isEmpty,
            warnings: Array(Set(warnings)).sorted()
        )
    }

    // MARK: - Markdown Delimiter Segmenter

    private enum MarkdownSegment {
        case prose(String)
        case math(String)
        case malformed(String)
    }

    private static func tokenizeMarkdownSegments(_ input: String) -> [MarkdownSegment] {
        var segments: [MarkdownSegment] = []
        var buffer = ""
        let chars = Array(input)
        var i = 0

        func flushProse() {
            if !buffer.isEmpty {
                segments.append(.prose(buffer))
                buffer = ""
            }
        }

        while i < chars.count {
            let ch = chars[i]

            if ch == "\\" && i + 1 < chars.count {
                let next = chars[i + 1]
                if next == "$" {
                    buffer.append("$")
                    i += 2
                    continue
                }
                if next == "(" || next == "[" {
                    let closeChar: Character = (next == "(") ? ")" : "]"
                    flushProse()
                    i += 2
                    var math = ""
                    var closed = false
                    while i < chars.count {
                        if chars[i] == "\\" && i + 1 < chars.count && chars[i + 1] == closeChar {
                            closed = true
                            i += 2
                            break
                        }
                        math.append(chars[i])
                        i += 1
                    }
                    if closed {
                        segments.append(.math(math))
                    } else {
                        segments.append(.malformed("anlaşılamayan matematik ifadesi"))
                    }
                    continue
                }
            }

            if ch == "$" {
                let isDouble = (i + 1 < chars.count && chars[i + 1] == "$")
                let delimiterLen = isDouble ? 2 : 1
                var scan = i + delimiterLen
                var math = ""
                var closed = false
                while scan < chars.count {
                    if chars[scan] == "\\" && scan + 1 < chars.count && chars[scan + 1] == "$" {
                        math.append("\\$")
                        scan += 2
                        continue
                    }
                    if isDouble {
                        if chars[scan] == "$" && scan + 1 < chars.count && chars[scan + 1] == "$" {
                            closed = true
                            scan += 2
                            break
                        }
                    } else if chars[scan] == "$" {
                        closed = true
                        scan += 1
                        break
                    }
                    math.append(chars[scan])
                    scan += 1
                }

                if closed && !math.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    flushProse()
                    segments.append(.math(math))
                    i = scan
                    continue
                } else if !closed && math.contains("\\") {
                    flushProse()
                    segments.append(.malformed("anlaşılamayan matematik ifadesi"))
                    break
                } else {
                    // Single standalone dollar sign in prose without matching LaTeX
                    buffer.append("dolar")
                    i += delimiterLen
                    continue
                }
            }

            buffer.append(ch)
            i += 1
        }
        flushProse()
        return segments
    }

    private static func normalizeTurkishProsePunctuation(_ raw: String) -> String {
        raw.replacingOccurrences(of: "\n", with: " ")
            .replacingOccurrences(of: "\\cdot", with: " çarpı ")
    }

    // MARK: - Bounded LaTeX Lexer

    private enum TokenKind: Equatable {
        case number(String)
        case identifier(String)
        case command(String)
        case symbol(Character)
        case openBrace
        case closeBrace
        case openBracket
        case closeBracket
        case openParen
        case closeParen
        case pipe
        case caret
        case underscore
        case prime
        case comma
        case eof
    }

    private struct Token {
        let kind: TokenKind
    }

    private struct LexOutput {
        let tokens: [Token]
        let malformed: Bool
        let warnings: [String]
    }

    private static func lexLatex(_ latex: String) -> LexOutput {
        var tokens: [Token] = []
        var warnings: [String] = []
        let chars = Array(latex)
        var i = 0
        var braceBalance = 0

        while i < chars.count {
            if tokens.count >= maxTokens {
                warnings.append("TOKEN_BUDGET_EXCEEDED")
                return LexOutput(tokens: tokens + [Token(kind: .eof)], malformed: true, warnings: warnings)
            }
            let ch = chars[i]
            if ch.isWhitespace {
                i += 1
                continue
            }

            switch ch {
            case "{":
                braceBalance += 1
                tokens.append(Token(kind: .openBrace))
                i += 1
            case "}":
                braceBalance -= 1
                if braceBalance < 0 {
                    warnings.append("UNBALANCED_CLOSE_BRACE")
                    return LexOutput(tokens: tokens + [Token(kind: .eof)], malformed: true, warnings: warnings)
                }
                tokens.append(Token(kind: .closeBrace))
                i += 1
            case "[":
                tokens.append(Token(kind: .openBracket))
                i += 1
            case "]":
                tokens.append(Token(kind: .closeBracket))
                i += 1
            case "(":
                tokens.append(Token(kind: .openParen))
                i += 1
            case ")":
                tokens.append(Token(kind: .closeParen))
                i += 1
            case "|":
                tokens.append(Token(kind: .pipe))
                i += 1
            case "^":
                tokens.append(Token(kind: .caret))
                i += 1
            case "_":
                tokens.append(Token(kind: .underscore))
                i += 1
            case "'":
                tokens.append(Token(kind: .prime))
                i += 1
            case ",":
                tokens.append(Token(kind: .comma))
                i += 1
            case "\\":
                i += 1
                if i >= chars.count {
                    warnings.append("TRAILING_BACKSLASH")
                    return LexOutput(tokens: tokens + [Token(kind: .eof)], malformed: true, warnings: warnings)
                }
                let next = chars[i]
                if !next.isLetter {
                    // Spacing or escaped delimiter: \, \; \: \! \{ \} \| \% \$
                    if next == "," || next == ";" || next == ":" || next == "!" {
                        i += 1
                        continue
                    }
                    tokens.append(Token(kind: .command(String(next))))
                    i += 1
                } else {
                    var cmd = ""
                    while i < chars.count && chars[i].isLetter {
                        cmd.append(chars[i])
                        i += 1
                    }
                    tokens.append(Token(kind: .command(cmd)))
                }
            default:
                if ch.isNumber {
                    var num = ""
                    while i < chars.count && (chars[i].isNumber || chars[i] == ".") {
                        num.append(chars[i])
                        i += 1
                    }
                    tokens.append(Token(kind: .number(num)))
                } else if ch.isLetter {
                    tokens.append(Token(kind: .identifier(String(ch))))
                    i += 1
                } else {
                    tokens.append(Token(kind: .symbol(ch)))
                    i += 1
                }
            }
        }

        if braceBalance != 0 {
            warnings.append("UNCLOSED_BRACE")
            return LexOutput(tokens: tokens + [Token(kind: .eof)], malformed: true, warnings: warnings)
        }
        tokens.append(Token(kind: .eof))
        return LexOutput(tokens: tokens, malformed: false, warnings: warnings)
    }

    // MARK: - Bounded Recursive Parser & Renderer

    private struct LatexParser {
        let tokens: [Token]
        let maxDepth: Int
        var index: Int = 0
        var malformed: Bool = false
        var hasUnsupported: Bool = false
        var warnings: [String] = []
        private var pipeDepth: Int = 0

        init(tokens: [Token], maxDepth: Int) {
            self.tokens = tokens
            self.maxDepth = maxDepth
        }

        func peek(offset: Int = 0) -> Token? {
            let target = index + offset
            guard target >= 0 && target < tokens.count else { return nil }
            return tokens[target]
        }

        mutating func consume() -> Token {
            let t = tokens[min(index, tokens.count - 1)]
            if index < tokens.count - 1 {
                index += 1
            }
            return t
        }

        mutating func parseSequence(stopAt: TokenKind?, depth: Int) -> String {
            guard depth <= maxDepth else {
                malformed = true
                warnings.append("NESTING_DEPTH_EXCEEDED")
                return ""
            }
            var parts: [String] = []
            var expectUnarySign = true

            while let tok = peek(), tok.kind != .eof {
                if let stopAt, tok.kind == stopAt {
                    break
                }
                if stopAt == nil && tok.kind == .pipe && pipeDepth > 0 {
                    break
                }

                // Detect right-delimiter after \left|
                if stopAt == .pipe, tok.kind == .command("right"), peek(offset: 1)?.kind == .pipe {
                    break
                }
                if case .closeParen = stopAt, tok.kind == .command("right"), peek(offset: 1)?.kind == .closeParen {
                    break
                }
                if case .closeBracket = stopAt, tok.kind == .command("right"), peek(offset: 1)?.kind == .closeBracket {
                    break
                }

                let rendered = parseItem(depth: depth, unaryContext: expectUnarySign)
                if !rendered.isEmpty {
                    parts.append(rendered)
                }

                // Update unary context based on last token kind
                if case .symbol(let s) = tok.kind, ["=", "<", ">", "+", "-", "*", "/"].contains(s) {
                    expectUnarySign = true
                } else if case .command(let cmd) = tok.kind,
                          ["leq", "le", "geq", "ge", "neq", "to", "rightarrow", "approx", "equiv", "in", "notin"].contains(cmd) {
                    expectUnarySign = true
                } else if tok.kind == .openParen || tok.kind == .openBracket || tok.kind == .comma || tok.kind == .pipe {
                    expectUnarySign = true
                } else {
                    expectUnarySign = false
                }
            }
            return normalizeSpacing(parts.joined(separator: " "))
        }

        mutating func parseArgument(depth: Int) -> String {
            guard depth <= maxDepth else {
                malformed = true
                warnings.append("NESTING_DEPTH_EXCEEDED")
                return ""
            }
            guard let tok = peek() else { return "" }
            if tok.kind == .openBrace {
                _ = consume()
                let content = parseSequence(stopAt: .closeBrace, depth: depth + 1)
                if peek()?.kind == .closeBrace {
                    _ = consume()
                } else {
                    malformed = true
                    warnings.append("MISSING_CLOSE_BRACE")
                }
                return content
            }
            return parseAtom(depth: depth + 1, unaryContext: true)
        }

        mutating func parseOptionalBracketArgument(depth: Int) -> String? {
            guard peek()?.kind == .openBracket else { return nil }
            _ = consume()
            let content = parseSequence(stopAt: .closeBracket, depth: depth + 1)
            if peek()?.kind == .closeBracket {
                _ = consume()
            } else {
                malformed = true
                warnings.append("MISSING_CLOSE_BRACKET")
            }
            return content
        }

        private mutating func parseItem(depth: Int, unaryContext: Bool) -> String {
            guard depth <= maxDepth else {
                malformed = true
                warnings.append("NESTING_DEPTH_EXCEEDED")
                return ""
            }
            guard let tok = peek() else { return "" }

            // Check special constructs handled with scripts before generic base
            if case .command(let cmd) = tok.kind {
                switch cmd {
                case "int", "oint":
                    return parseIntegral(depth: depth)
                case "lim":
                    return parseLimit(depth: depth)
                case "sum":
                    return parseBigOperator(word: "toplam", depth: depth)
                case "prod":
                    return parseBigOperator(word: "çarpım", depth: depth)
                case "log":
                    return parseLogarithm(depth: depth)
                case "ln":
                    _ = consume()
                    return appendScriptsIfAny("doğal logaritma", depth: depth)
                default:
                    break
                }
            }

            let baseText = parseAtom(depth: depth, unaryContext: unaryContext)
            return appendScriptsIfAny(baseText, depth: depth)
        }

        private mutating func appendScriptsIfAny(_ base: String, depth: Int) -> String {
            var current = base
            var subscriptText: String?
            var superscriptText: String?

            // Support prime notation f'(x), f''(x)
            var primeCount = 0
            while peek()?.kind == .prime {
                _ = consume()
                primeCount += 1
            }
            if primeCount == 1 {
                current = "\(current) türev"
            } else if primeCount == 2 {
                current = "\(current) ikinci türev"
            } else if primeCount > 2 {
                current = "\(current) \(primeCount). türev"
            }

            while let next = peek()?.kind, next == .underscore || next == .caret {
                if next == .underscore {
                    _ = consume()
                    subscriptText = parseArgument(depth: depth + 1)
                } else {
                    _ = consume()
                    superscriptText = parseArgument(depth: depth + 1)
                }
            }

            if let sub = subscriptText, !sub.isEmpty {
                current = "\(current) alt indis \(sub)"
            }
            if let sup = superscriptText, !sup.isEmpty {
                if sup == "2" {
                    current = "\(current) kare"
                } else if sup == "3" {
                    current = "\(current) küp"
                } else if sup == "derece" {
                    current = "\(current) derece"
                } else {
                    current = "\(current) üssü \(sup)"
                }
            }
            return normalizeSpacing(current)
        }

        private mutating func parseAtom(depth: Int, unaryContext: Bool) -> String {
            guard let tok = peek() else { return "" }
            switch tok.kind {
            case .number(let val):
                _ = consume()
                return formatNumber(val)
            case .identifier(let ident):
                _ = consume()
                if peek()?.kind == .openParen {
                    // Preserve spacing cleanly for functions like f(x)
                    return ident
                }
                return ident
            case .openBrace:
                _ = consume()
                let inner = parseSequence(stopAt: .closeBrace, depth: depth + 1)
                if peek()?.kind == .closeBrace {
                    _ = consume()
                } else {
                    malformed = true
                }
                return inner
            case .openParen:
                _ = consume()
                let inner = parseSequence(stopAt: .closeParen, depth: depth + 1)
                if peek()?.kind == .closeParen {
                    _ = consume()
                } else {
                    malformed = true
                    warnings.append("MISSING_CLOSE_PAREN")
                }
                return "parantez açık \(inner) parantez kapalı"
            case .openBracket:
                _ = consume()
                let inner = parseSequence(stopAt: .closeBracket, depth: depth + 1)
                if peek()?.kind == .closeBracket {
                    _ = consume()
                } else {
                    malformed = true
                    warnings.append("MISSING_CLOSE_BRACKET")
                }
                return "köşeli parantez açık \(inner) köşeli parantez kapalı"
            case .pipe:
                _ = consume()
                pipeDepth += 1
                let inner = parseSequence(stopAt: .pipe, depth: depth + 1)
                pipeDepth = max(0, pipeDepth - 1)
                if peek()?.kind == .pipe {
                    _ = consume()
                } else {
                    malformed = true
                    warnings.append("UNCLOSED_ABS_PIPE")
                }
                return "mutlak değer \(inner)"
            case .comma:
                _ = consume()
                return "virgül"
            case .symbol(let sym):
                _ = consume()
                return mapSymbol(sym, unaryContext: unaryContext)
            case .command(let cmd):
                return parseCommand(cmd, depth: depth)
            case .closeBrace, .closeBracket, .closeParen:
                _ = consume()
                malformed = true
                warnings.append("UNEXPECTED_CLOSING_DELIMITER")
                return ""
            case .caret, .underscore, .prime:
                _ = consume()
                malformed = true
                warnings.append("DANGLING_SCRIPT_TOKEN")
                return ""
            case .eof:
                return ""
            }
        }

        private mutating func parseCommand(_ cmd: String, depth: Int) -> String {
            switch cmd {
            case "frac":
                _ = consume()
                let num = parseArgument(depth: depth + 1)
                let den = parseArgument(depth: depth + 1)
                let numIsSimple = !containsArithmeticWords(num) && num.split(separator: " ").count <= 2
                let denIsSimple = !containsArithmeticWords(den) && den.split(separator: " ").count <= 2
                if numIsSimple && denIsSimple {
                    return "\(num) bölü \(den)"
                }
                return "pay: \(num); payda: \(den)"
            case "sqrt":
                _ = consume()
                let degree = parseOptionalBracketArgument(depth: depth + 1)
                let body = parseArgument(depth: depth + 1)
                if let degree, !degree.isEmpty {
                    return "\(degree). dereceden kök içinde \(body)"
                }
                let isCompound = body.contains(" ")
                return isCompound ? "karekök içinde \(body)" : "karekök \(body)"
            case "left":
                _ = consume()
                guard let delim = peek() else {
                    malformed = true
                    return ""
                }
                switch delim.kind {
                case .openParen:
                    _ = consume()
                    let inner = parseSequence(stopAt: .closeParen, depth: depth + 1)
                    if peek()?.kind == .command("right") && peek(offset: 1)?.kind == .closeParen {
                        _ = consume()
                        _ = consume()
                    } else {
                        malformed = true
                    }
                    return "parantez açık \(inner) parantez kapalı"
                case .openBracket:
                    _ = consume()
                    let inner = parseSequence(stopAt: .closeBracket, depth: depth + 1)
                    if peek()?.kind == .command("right") && peek(offset: 1)?.kind == .closeBracket {
                        _ = consume()
                        _ = consume()
                    } else {
                        malformed = true
                    }
                    return "köşeli parantez açık \(inner) köşeli parantez kapalı"
                case .pipe:
                    _ = consume()
                    let inner = parseSequence(stopAt: .pipe, depth: depth + 1)
                    if peek()?.kind == .command("right") && peek(offset: 1)?.kind == .pipe {
                        _ = consume()
                        _ = consume()
                    } else {
                        malformed = true
                    }
                    return "mutlak değer \(inner)"
                default:
                    _ = consume()
                    return ""
                }
            case "right":
                _ = consume()
                if peek() != nil {
                    _ = consume()
                }
                malformed = true
                warnings.append("UNMATCHED_RIGHT_DELIMITER")
                return ""
            case "mathbb":
                _ = consume()
                let sym = parseArgument(depth: depth + 1)
                switch sym {
                case "R": return "reel sayılar"
                case "Z": return "tam sayılar"
                case "N": return "doğal sayılar"
                case "Q": return "rasyonel sayılar"
                default: return sym
                }
            case "text", "mathrm", "operatorname":
                _ = consume()
                return parseArgument(depth: depth + 1)
            default:
                _ = consume()
                if let mapped = staticCommandMap[cmd] {
                    return mapped
                }
                hasUnsupported = true
                warnings.append("UNSUPPORTED_COMMAND_\(cmd)")
                return "anlaşılamayan matematik ifadesi \(cmd)"
            }
        }

        private mutating func parseIntegral(depth: Int) -> String {
            _ = consume() // \int
            var lower: String?
            var upper: String?
            while let next = peek()?.kind, next == .underscore || next == .caret {
                if next == .underscore {
                    _ = consume()
                    lower = parseArgument(depth: depth + 1)
                } else {
                    _ = consume()
                    upper = parseArgument(depth: depth + 1)
                }
            }
            var parts: [String] = []
            var expectUnary = true
            while let tok = peek(), tok.kind != .eof, tok.kind != .closeBrace, tok.kind != .closeParen, tok.kind != .closeBracket, !isRelationToken(tok) {
                let item = parseItem(depth: depth + 1, unaryContext: expectUnary)
                if !item.isEmpty {
                    parts.append(item)
                }
                if case .symbol(let s) = tok.kind, ["+", "-", "*", "/"].contains(s) {
                    expectUnary = true
                } else {
                    expectUnary = false
                }
            }
            let integrand = normalizeSpacing(parts.joined(separator: " "))
            var prefix = ""
            if let lower, let upper {
                prefix = "\(turkishAblative(lower)) \(turkishDative(upper)) "
            }
            let body = integrand.isEmpty ? "" : "\(integrand) "
            return normalizeSpacing("\(prefix)\(body)integrali")
        }

        private func isRelationToken(_ tok: Token) -> Bool {
            switch tok.kind {
            case .symbol(let s):
                return s == "=" || s == "<" || s == ">"
            case .command(let cmd):
                return ["leq", "le", "geq", "ge", "neq", "approx", "equiv", "to", "rightarrow"].contains(cmd)
            default:
                return false
            }
        }

        private mutating func parseLimit(depth: Int) -> String {
            _ = consume() // \lim
            guard peek()?.kind == .underscore else {
                return "limit"
            }
            _ = consume()
            let bound = parseArgument(depth: depth + 1)
            // bound commonly parses into e.g. "x gider 0" or "x gider 0 üssü artı"
            let cleanBound = bound
                .replacingOccurrences(of: " üssü artı", with: " sağdan")
                .replacingOccurrences(of: " üssü eksi", with: " soldan")
            if let range = cleanBound.range(of: " gider ") {
                let variable = String(cleanBound[..<range.lowerBound])
                let rawTarget = String(cleanBound[range.upperBound...])
                let directionSuffix: String
                let targetExpr: String
                if rawTarget.hasSuffix(" sağdan") {
                    directionSuffix = " sağdan"
                    targetExpr = String(rawTarget.dropLast(" sağdan".count))
                } else if rawTarget.hasSuffix(" soldan") {
                    directionSuffix = " soldan"
                    targetExpr = String(rawTarget.dropLast(" soldan".count))
                } else {
                    directionSuffix = ""
                    targetExpr = rawTarget
                }
                return "\(variable) \(turkishDative(targetExpr))\(directionSuffix) yaklaşırken limit"
            }
            return "\(cleanBound) iken limit"
        }

        private mutating func parseBigOperator(word: String, depth: Int) -> String {
            _ = consume()
            var lower: String?
            var upper: String?
            while let next = peek()?.kind, next == .underscore || next == .caret {
                if next == .underscore {
                    _ = consume()
                    lower = parseArgument(depth: depth + 1)
                } else {
                    _ = consume()
                    upper = parseArgument(depth: depth + 1)
                }
            }
            if let lower, let upper {
                return "\(lower) ile \(upper) arasında \(word)"
            }
            if let lower {
                return "\(lower) üzerinden \(word)"
            }
            return word
        }

        private mutating func parseLogarithm(depth: Int) -> String {
            _ = consume()
            if peek()?.kind == .underscore {
                _ = consume()
                let base = parseArgument(depth: depth + 1)
                let arg = parseArgument(depth: depth + 1)
                if arg.isEmpty {
                    return "\(base) tabanında logaritma"
                }
                return "\(base) tabanında logaritma \(arg)"
            }
            return "logaritma"
        }

        private func mapSymbol(_ ch: Character, unaryContext: Bool) -> String {
            switch ch {
            case "+": return "artı"
            case "-": return unaryContext ? "eksi" : "eksi"
            case "*": return "çarpı"
            case "/": return "bölü"
            case "=": return "eşittir"
            case "<": return "küçüktür"
            case ">": return "büyüktür"
            case "!": return "faktöriyel"
            case ";": return "noktalı virgül"
            case ":": return "iki nokta"
            case "°": return "derece"
            case "%": return "yüzde"
            default: return String(ch)
            }
        }

        private func formatNumber(_ raw: String) -> String {
            if raw.contains(".") {
                return raw.replacingOccurrences(of: ".", with: " virgül ")
            }
            return raw
        }

        private func containsArithmeticWords(_ text: String) -> Bool {
            let tokens = Set(text.split(separator: " ").map(String.init))
            return !tokens.isDisjoint(with: ["artı", "eksi", "çarpı", "bölü", "eşittir"])
        }
    }

    private static let staticCommandMap: [String: String] = [
        "sin": "sinüs",
        "cos": "kosinüs",
        "tan": "tanjant",
        "cot": "kotanjant",
        "sec": "sekant",
        "csc": "kosekant",
        "arcsin": "ark sinüs",
        "arccos": "ark kosinüs",
        "arctan": "ark tanjant",
        "cdot": "çarpı",
        "times": "çarpı",
        "div": "bölü",
        "pm": "artı eksi",
        "mp": "eksi artı",
        "leq": "küçük eşittir",
        "le": "küçük eşittir",
        "geq": "büyük eşittir",
        "ge": "büyük eşittir",
        "neq": "eşit değildir",
        "approx": "yaklaşık eşittir",
        "equiv": "denktir",
        "to": "gider",
        "rightarrow": "gider",
        "infty": "sonsuz",
        "pi": "pi",
        "theta": "teta",
        "alpha": "alfa",
        "beta": "beta",
        "gamma": "gama",
        "Delta": "delta",
        "delta": "delta",
        "lambda": "lambda",
        "circ": "derece",
        "in": "elemanıdır",
        "notin": "elemanı değildir",
        "cup": "birleşim",
        "cap": "kesişim",
        "subset": "alt kümesidir",
        "subseteq": "alt kümesi veya eşittir",
        "emptyset": "boş küme",
        "forall": "her",
        "exists": "en az bir",
        "angle": "açı",
        "triangle": "üçgen",
        "perp": "diktir",
        "parallel": "paraleldir",
        "%": "yüzde",
        "$": "dolar",
        "{": "küme açık",
        "}": "küme kapalı",
        "|": "mutlak"
    ]

    private static let ablativeTable: [String: String] = [
        "0": "sıfırdan",
        "1": "birden",
        "2": "ikiden",
        "3": "üçten",
        "4": "dörtten",
        "5": "beşten",
        "6": "altıdan",
        "7": "yediden",
        "8": "sekizden",
        "9": "dokuzdan",
        "10": "ondan",
        "eksi 1": "eksi birden",
        "eksi 2": "eksi ikiden",
        "sonsuz": "sonsuzdan",
        "eksi sonsuz": "eksi sonsuzdan",
        "pi": "pi'den",
        "a": "a'dan",
        "b": "b'den"
    ]

    private static let dativeTable: [String: String] = [
        "0": "sıfıra",
        "1": "bire",
        "2": "ikiye",
        "3": "üçe",
        "4": "dörde",
        "5": "beşe",
        "6": "altıya",
        "7": "yediye",
        "8": "sekize",
        "9": "dokuza",
        "10": "ona",
        "eksi 1": "eksi bire",
        "eksi 2": "eksi ikiye",
        "sonsuz": "sonsuza",
        "eksi sonsuz": "eksi sonsuza",
        "pi": "pi'ye",
        "a": "a'ya",
        "b": "b'ye"
    ]

    private static func turkishAblative(_ expr: String) -> String {
        let key = normalizeSpacing(expr)
        return ablativeTable[key] ?? "\(key) değerinden"
    }

    private static func turkishDative(_ expr: String) -> String {
        let key = normalizeSpacing(expr)
        return dativeTable[key] ?? "\(key) değerine"
    }

    private static func normalizeSpacing(_ input: String) -> String {
        input.split(whereSeparator: { $0.isWhitespace })
            .map(String.init)
            .joined(separator: " ")
    }
}
