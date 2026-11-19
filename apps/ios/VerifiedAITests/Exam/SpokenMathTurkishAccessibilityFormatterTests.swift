import Foundation
import XCTest
@testable import VerifiedAI

final class SpokenMathTurkishAccessibilityFormatterTests: XCTestCase {

    private struct GoldenFormulaCase {
        let id: String
        let category: String
        let latex: String
        let expected: String
    }

    func testAll50TurkishMathematicalFormulasProduceExactSemanticVoiceOverStrings() {
        let fixtures: [GoldenFormulaCase] = [
            // Category 1 (8): İslam/İşaret, Değişken, Parantez, Doğrusal/Karesel Denklem ve Eşitsizlikler
            .init(id: "C1-01", category: "algebra", latex: "-x", expected: "eksi x"),
            .init(id: "C1-02", category: "algebra", latex: "(-2)^3", expected: "parantez açık eksi 2 parantez kapalı küp"),
            .init(id: "C1-03", category: "algebra", latex: "2(x+1)=6", expected: "2 parantez açık x artı 1 parantez kapalı eşittir 6"),
            .init(id: "C1-04", category: "algebra", latex: "x^2-5x+6=0", expected: "x kare eksi 5 x artı 6 eşittir 0"),
            .init(id: "C1-05", category: "algebra", latex: "3x - 7 < 8", expected: "3 x eksi 7 küçüktür 8"),
            .init(id: "C1-06", category: "algebra", latex: "2x + 1 \\geq 9", expected: "2 x artı 1 büyük eşittir 9"),
            .init(id: "C1-07", category: "algebra", latex: "a \\neq b", expected: "a eşit değildir b"),
            .init(id: "C1-08", category: "algebra", latex: "x = \\pm 4", expected: "x eşittir artı eksi 4"),

            // Category 2 (10): Rasyonel, Kök, Mutlak Değer, Üs ve Logaritma
            .init(id: "C2-01", category: "rational_log", latex: "\\frac{1}{2}", expected: "1 bölü 2"),
            .init(id: "C2-02", category: "rational_log", latex: "\\frac{a+b}{c}", expected: "pay: a artı b; payda: c"),
            .init(id: "C2-03", category: "rational_log", latex: "\\frac{1}{x+2}", expected: "pay: 1; payda: x artı 2"),
            .init(id: "C2-04", category: "rational_log", latex: "\\sqrt{x}", expected: "karekök x"),
            .init(id: "C2-05", category: "rational_log", latex: "\\sqrt{x^2+1}", expected: "karekök içinde x kare artı 1"),
            .init(id: "C2-06", category: "rational_log", latex: "\\sqrt[3]{8}=2", expected: "3. dereceden kök içinde 8 eşittir 2"),
            .init(id: "C2-07", category: "rational_log", latex: "|x-3| \\leq 5", expected: "mutlak değer x eksi 3 küçük eşittir 5"),
            .init(id: "C2-08", category: "rational_log", latex: "\\log_2 8 = 3", expected: "2 tabanında logaritma 8 eşittir 3"),
            .init(id: "C2-09", category: "rational_log", latex: "\\ln x = 1", expected: "doğal logaritma x eşittir 1"),
            .init(id: "C2-10", category: "rational_log", latex: "2^{n+1}", expected: "2 üssü n artı 1"),

            // Category 3 (8): Trigonometri, Geometri ve Diziler
            .init(id: "C3-01", category: "trig_geom_seq", latex: "\\sin(2x)", expected: "sinüs parantez açık 2 x parantez kapalı"),
            .init(id: "C3-02", category: "trig_geom_seq", latex: "\\cos^2 x + \\sin^2 x = 1", expected: "kosinüs kare x artı sinüs kare x eşittir 1"),
            .init(id: "C3-03", category: "trig_geom_seq", latex: "\\tan\\left(\\frac{\\pi}{4}\\right)=1", expected: "tanjant parantez açık pi bölü 4 parantez kapalı eşittir 1"),
            .init(id: "C3-04", category: "trig_geom_seq", latex: "\\cot 45^\\circ = 1", expected: "kotanjant 45 derece eşittir 1"),
            .init(id: "C3-05", category: "trig_geom_seq", latex: "\\alpha + \\beta = 90^\\circ", expected: "alfa artı beta eşittir 90 derece"),
            .init(id: "C3-06", category: "trig_geom_seq", latex: "a_n = a_1 + (n-1)r", expected: "a alt indis n eşittir a alt indis 1 artı parantez açık n eksi 1 parantez kapalı r"),
            .init(id: "C3-07", category: "trig_geom_seq", latex: "\\sum_{k=1}^{n} k", expected: "k eşittir 1 ile n arasında toplam k"),
            .init(id: "C3-08", category: "trig_geom_seq", latex: "A \\cup B = B \\cap C", expected: "A birleşim B eşittir B kesişim C"),

            // Category 4 (6): Limit ve Süreklilik
            .init(id: "C4-01", category: "limit", latex: "\\lim_{x\\to 0} \\frac{\\sin x}{x} = 1", expected: "x sıfıra yaklaşırken limit sinüs x bölü x eşittir 1"),
            .init(id: "C4-02", category: "limit", latex: "\\lim_{x\\to 0^+} \\frac{1}{x}", expected: "x sıfıra sağdan yaklaşırken limit 1 bölü x"),
            .init(id: "C4-03", category: "limit", latex: "\\lim_{x\\to 0^-} \\frac{1}{x}", expected: "x sıfıra soldan yaklaşırken limit 1 bölü x"),
            .init(id: "C4-04", category: "limit", latex: "\\lim_{x\\to \\infty} \\frac{1}{x} = 0", expected: "x sonsuza yaklaşırken limit 1 bölü x eşittir 0"),
            .init(id: "C4-05", category: "limit", latex: "\\lim_{x\\to 2} (3x-1) = 5", expected: "x ikiye yaklaşırken limit parantez açık 3 x eksi 1 parantez kapalı eşittir 5"),
            .init(id: "C4-06", category: "limit", latex: "x \\in \\mathbb{R}", expected: "x elemanıdır reel sayılar"),

            // Category 5 (6): Türev ve Zincir Kuralı
            .init(id: "C5-01", category: "derivative", latex: "f'(x) = 2x", expected: "f türev parantez açık x parantez kapalı eşittir 2 x"),
            .init(id: "C5-02", category: "derivative", latex: "f''(x) = 6x", expected: "f ikinci türev parantez açık x parantez kapalı eşittir 6 x"),
            .init(id: "C5-03", category: "derivative", latex: "\\frac{d}{dx} x^3 = 3x^2", expected: "d bölü d x x küp eşittir 3 x kare"),
            .init(id: "C5-04", category: "derivative", latex: "\\frac{dy}{dx} = 4x", expected: "d y bölü d x eşittir 4 x"),
            .init(id: "C5-05", category: "derivative", latex: "y' = 2u \\cdot u'", expected: "y türev eşittir 2 u çarpı u türev"),
            .init(id: "C5-06", category: "derivative", latex: "f'(1) = 0", expected: "f türev parantez açık 1 parantez kapalı eşittir 0"),

            // Category 6 (6): Belirli ve Belirsiz İntegral
            .init(id: "C6-01", category: "integral", latex: "\\int_0^1 x^2\\,dx", expected: "sıfırdan bire x kare d x integrali"),
            .init(id: "C6-02", category: "integral", latex: "\\int_{-1}^{1} (x^2+1)\\,dx", expected: "eksi birden bire parantez açık x kare artı 1 parantez kapalı d x integrali"),
            .init(id: "C6-03", category: "integral", latex: "\\int_0^{\\infty} e^{-x}\\,dx", expected: "sıfırdan sonsuza e üssü eksi x d x integrali"),
            .init(id: "C6-04", category: "integral", latex: "\\int x\\,dx", expected: "x d x integrali"),
            .init(id: "C6-05", category: "integral", latex: "\\int_1^2 3x^2\\,dx = 7", expected: "birden ikiye 3 x kare d x integrali eşittir 7"),
            .init(id: "C6-06", category: "integral", latex: "\\int_a^b f(t)\\,dt", expected: "a'dan b'ye f parantez açık t parantez kapalı d t integrali"),

            // Category 7 (6): TYT / AYT Yeni Nesil Soru, Şık ve V060 Matematik Parçaları
            .init(id: "C7-01", category: "yks_v060", latex: "\\frac{2}{4} + \\frac{t}{10} = 1", expected: "2 bölü 4 artı t bölü 10 eşittir 1"),
            .init(id: "C7-02", category: "yks_v060", latex: "0.2x = 12", expected: "0 virgül 2 x eşittir 12"),
            .init(id: "C7-03", category: "yks_v060", latex: "\\left|x-4\\right| > 2", expected: "mutlak değer x eksi 4 büyüktür 2"),
            .init(id: "C7-04", category: "yks_v060", latex: "\\frac{x^2-9}{x-3} = 6", expected: "pay: x kare eksi 9; payda: x eksi 3 eşittir 6"),
            .init(id: "C7-05", category: "yks_v060", latex: "A \\times B = 24", expected: "A çarpı B eşittir 24"),
            .init(id: "C7-06", category: "yks_v060", latex: "x \\notin \\emptyset", expected: "x elemanı değildir boş küme"),
        ]

        XCTAssertEqual(fixtures.count, 50)
        for fixture in fixtures {
            let result = SpokenMathTurkishAccessibilityFormatter.formatLatex(fixture.latex)
            XCTAssertFalse(
                result.hasUnsupportedToken,
                "Unexpected unsupported token in \(fixture.id) (\(fixture.latex)): \(result.warnings)"
            )
            XCTAssertEqual(
                result.spokenText,
                fixture.expected,
                "Mismatch for golden formula \(fixture.id) [\(fixture.category)]"
            )
        }
    }

    func testMarkdownExtractionMalformedGuardsAndBoundedDepth() {
        // Stem markdown extraction transforms LaTeX segments while keeping plain Turkish prose
        let stem = "Sürücü $\\frac{2}{4} + \\frac{t}{10} = 1$ denklemini çözer; indirim 50\\%."
        let spokenStem = SpokenMathTurkishAccessibilityFormatter.formatMarkdown(stem)
        XCTAssertTrue(spokenStem.contains("2 bölü 4 artı t bölü 10 eşittir 1"))
        XCTAssertFalse(spokenStem.contains("\\frac"))

        // Malformed braces produce explicit warning instead of dropping expression silently
        let malformed = SpokenMathTurkishAccessibilityFormatter.formatLatex("\\frac{1}{2")
        XCTAssertTrue(malformed.hasUnsupportedToken)
        XCTAssertTrue(malformed.spokenText.contains("anlaşılamayan matematik ifadesi"))

        // Unknown command sets unsupportedToken warning rather than inventing math semantics
        let unknown = SpokenMathTurkishAccessibilityFormatter.formatLatex("\\foobar{x}")
        XCTAssertTrue(unknown.hasUnsupportedToken)
        XCTAssertTrue(unknown.spokenText.contains("anlaşılamayan matematik ifadesi"))

        // Excess length bounded guard
        let oversized = String(repeating: "x+", count: 600) + "1"
        let bounded = SpokenMathTurkishAccessibilityFormatter.formatLatex(oversized)
        XCTAssertTrue(bounded.hasUnsupportedToken)

        // Nesting beyond maxNestingDepth terminates cleanly without crashing
        let deepNested = String(repeating: "{", count: 20) + "x" + String(repeating: "}", count: 20)
        let deepResult = SpokenMathTurkishAccessibilityFormatter.formatLatex(deepNested)
        XCTAssertTrue(deepResult.hasUnsupportedToken)
    }

    func testMatchesAll10CrossPlatformCanonicalHkdfAndHmacVectors() throws {
        struct Vector {
            let keyId: String
            let reason: ExamSessionSubmissionReason
            let idemp: String
            let ts: String
            let nonce: String
            let expectedBodySha256: String
            let expectedProofKeyHex: String
            let expectedSigHex: String
        }

        let masterKeyData = Data("synthetic-test-master-key-minimum-32-bytes-0123456789".utf8)
        let userId = UUID(uuidString: "91000000-0000-4000-8000-000000000a01")!
        let sessionId = UUID(uuidString: "95000000-0000-4000-8000-000000000001")!
        let startedAt = "2026-11-18T10:00:00.000Z"

        let vectors: [Vector] = [
            .init(keyId: "exam-k1", reason: .userInitiated, idemp: "idemp-v1-0001", ts: "2026-11-18T10:15:00.000Z", nonce: "nonce_vec_0000000001",
                  expectedBodySha256: "69127b8f22be1e5948c8de321dc1a1a546555e277780638df7f14104312db3c7",
                  expectedProofKeyHex: "f9e76f355883e9520cb3f6375a697b72851b42b2aecd6c2b03a5371c046bd034",
                  expectedSigHex: "1491766360640074b34ee3c6c684618d94c4589185430351d525ad530696cf88"),
            .init(keyId: "exam-k1", reason: .userInitiated, idemp: "idemp-v1-0002", ts: "2026-11-18T10:15:01.250Z", nonce: "nonce_vec_0000000002",
                  expectedBodySha256: "53baa063ae1c4f5ccba0eb0605fd2c0e80384b472e51e454644c17638dc742ea",
                  expectedProofKeyHex: "f9e76f355883e9520cb3f6375a697b72851b42b2aecd6c2b03a5371c046bd034",
                  expectedSigHex: "b28a8194464e18bbda2f9a485fbafd59dfdb3ea033f9c1aa732ba42d3fdcf3d2"),
            .init(keyId: "exam-k1", reason: .serverDeadlineExpired, idemp: "idemp-v1-0003", ts: "2026-11-18T10:24:00.999Z", nonce: "nonce_vec_0000000003",
                  expectedBodySha256: "c1932c455b25115794d9edfedbf25a525e35cc41a208a7162960e43ed3bd2b5f",
                  expectedProofKeyHex: "f9e76f355883e9520cb3f6375a697b72851b42b2aecd6c2b03a5371c046bd034",
                  expectedSigHex: "818fcac843a290cca817987705acbb955021356842f64ad692db90d807e49255"),
            .init(keyId: "exam-k1", reason: .offlineGraceExhausted, idemp: "idemp-v1-0004", ts: "2026-11-18T10:26:00.001Z", nonce: "nonce_vec_0000000004",
                  expectedBodySha256: "c316cae22810d9dae60eeaaf43c02d1716715900642f9366999e4d16e6d12801",
                  expectedProofKeyHex: "f9e76f355883e9520cb3f6375a697b72851b42b2aecd6c2b03a5371c046bd034",
                  expectedSigHex: "51eec75b3b2cc57b25c0c0a82e819e84ec950600bcc15d50e4976666a00d1701"),
            .init(keyId: "exam-k1", reason: .userAbandoned, idemp: "idemp-v1-0005", ts: "2026-11-18T10:05:30.500Z", nonce: "nonce_vec_0000000005",
                  expectedBodySha256: "a2506d83b6361a7ac46ed2d1599450e34375b47c10672c8e86b381e024696229",
                  expectedProofKeyHex: "f9e76f355883e9520cb3f6375a697b72851b42b2aecd6c2b03a5371c046bd034",
                  expectedSigHex: "385e96e70c1ac869b41c657bbc1aab61fa3e1fc75cc87a10bf9bf15270fd1b80"),
            .init(keyId: "exam-k2", reason: .userInitiated, idemp: "idemp-v1-0006", ts: "2026-11-18T10:15:00.123Z", nonce: "nonce_vec_0000000006",
                  expectedBodySha256: "20d8055391c2db4dd443f575c9350dc73f88ac5753fe24d9fbc980b3d8ffda67",
                  expectedProofKeyHex: "4e6df0aea348a008077e6a89b3c898d967043e41c8d9537b78f0692ca2576388",
                  expectedSigHex: "38128b16f20e866b193888752c3fae083a5ade3fdd52e2a51eb0f083422f48a0"),
            .init(keyId: "exam-k1", reason: .userInitiated, idemp: "submit-key-alpha-beta", ts: "2026-11-18T10:16:00.000Z", nonce: "nonce_Alpha_00000007",
                  expectedBodySha256: "f0e4473aa63a065303f2bf9e1184fd8f7466ba0ad6fae9079cd0a11664a5f577",
                  expectedProofKeyHex: "f9e76f355883e9520cb3f6375a697b72851b42b2aecd6c2b03a5371c046bd034",
                  expectedSigHex: "5b591141484de1004abec2461b0d16acf5325015f637c3ac51d0c87cb36b4509"),
            .init(keyId: "exam-k1", reason: .serverDeadlineExpired, idemp: "submit-key-deadline-08", ts: "2026-11-18T10:24:00.000Z", nonce: "nonce_Beta__00000008",
                  expectedBodySha256: "46124aa45eff5e8c6502465ca305bdc57b6ac27d96ef6481efca2f87628b9965",
                  expectedProofKeyHex: "f9e76f355883e9520cb3f6375a697b72851b42b2aecd6c2b03a5371c046bd034",
                  expectedSigHex: "99b92deb697416ff310c4144c83e45d830699cf309bca97528546da57484f134"),
            .init(keyId: "exam-k1", reason: .offlineGraceExhausted, idemp: "submit-key-grace-09", ts: "2026-11-18T10:25:59.999Z", nonce: "nonce_Gamma_00000009",
                  expectedBodySha256: "c1e2deb1dab0b005a9a810c6543fcb964a0d91c8ffc200123684cc3988322221",
                  expectedProofKeyHex: "f9e76f355883e9520cb3f6375a697b72851b42b2aecd6c2b03a5371c046bd034",
                  expectedSigHex: "a243aee599585cab6d88df3807b4bdb362349a253862ddd1913e5e58acfa6490"),
            .init(keyId: "exam-k2", reason: .userInitiated, idemp: "submit-key-rot-10", ts: "2026-11-18T10:19:45.042Z", nonce: "nonce_Delta_00000010",
                  expectedBodySha256: "4cde2cd7cd9c55ad065c305cb134218099222e22cdd517ed998ace13b3faa535",
                  expectedProofKeyHex: "4e6df0aea348a008077e6a89b3c898d967043e41c8d9537b78f0692ca2576388",
                  expectedSigHex: "dc74e35bcf67d60541de1c9d4c8a590a90d75338cc13439e498540450f5a5197"),
        ]

        XCTAssertEqual(vectors.count, 10)
        for v in vectors {
            let bodyHash = ExamIntegrityCanonicalizer.computeCanonicalBodySha256Hex(
                submissionReason: v.reason,
                canonicalTimestamp: v.ts
            )
            XCTAssertEqual(bodyHash, v.expectedBodySha256)

            let proofHex = ExamIntegrityCanonicalizer.deriveSessionProofKeyHex(
                masterKeyData: masterKeyData,
                keyId: v.keyId,
                userId: userId,
                sessionId: sessionId,
                startedAtTimestamp: startedAt
            )
            XCTAssertEqual(proofHex, v.expectedProofKeyHex)

            let canonical = try ExamIntegrityCanonicalizer.buildCanonicalSigningString(
                userId: userId,
                sessionId: sessionId,
                submissionReason: v.reason,
                idempotencyKey: v.idemp,
                canonicalTimestamp: v.ts,
                integrityKeyId: v.keyId,
                submissionNonce: v.nonce
            )
            let sig = try ExamIntegrityCanonicalizer.signCanonicalString(canonical, proofKeyHex: proofHex)
            XCTAssertEqual(sig, v.expectedSigHex)
        }
    }
}
