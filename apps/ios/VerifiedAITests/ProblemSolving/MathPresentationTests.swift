import XCTest
@testable import VerifiedAI

final class MathPresentationTests: XCTestCase {
    private let x = MathExpression.variable("x")
    private let one = MathExpression.number("1", .integer)
    private let two = MathExpression.number("2", .integer)
    func testGroupingNegativeBasesAndNegativeExponentsRemainDistinct() {
        XCTAssertEqual(MathPresentation.expression(.negate(.binary(.power, x, two))), "−(((x) ^ (2)))")
        XCTAssertEqual(MathPresentation.expression(.binary(.power, .negate(x), two)), "((−(x)) ^ (2))")
        XCTAssertEqual(MathPresentation.expression(.binary(.power, .number("-2", .integer), two)), "((-2) ^ (2))")
        XCTAssertEqual(MathPresentation.expression(.binary(.power, x, .negate(two))), "((x) ^ (−(2)))")
        XCTAssertEqual(MathPresentation.expression(.binary(.divide, one, .binary(.add, x, one))), "(1 / (x + 1))")
        XCTAssertEqual(MathPresentation.expression(.binary(.subtract, x, .binary(.subtract, two, one))), "(x − (2 − 1))")
    }
    func testRelationsAndExactNumericStrings() {
        XCTAssertEqual(MathPresentation.statement(.relation(.lessEqual, x, two)), "x ≤ 2")
        XCTAssertEqual(MathPresentation.statement(.relation(.less, x, two)), "x < 2")
        XCTAssertEqual(MathPresentation.expression(.number("0.1000000000000000001", .decimal)), "0.1000000000000000001")
        XCTAssertEqual(MathPresentation.expression(.function(.sqrt, x)), "√(x)")
        XCTAssertEqual(MathPresentation.expression(.binary(.divide, one, two)), "(1 / 2)")
    }
    func testUnsupportedAndCodeBearingNodesAreRejected() {
        for json in [#"{"kind":"HTML","value":"<script>alert(1)</script>"}"#,
                     #"{"kind":"NUMBER","numericType":"INTEGER","value":"__import__('os')"}"#,
                     #"{"kind":"VARIABLE","symbol":"https://bad.example"}"#,
                     #"{"kind":"FUNCTION","function":"EXEC","args":[]}"#] {
            XCTAssertThrowsError(try JSONDecoder().decode(MathExpression.self, from: Data(json.utf8)))
        }
    }
    func testLongExpressionRemainsCompleteAndNeverEvaluates() throws {
        let literal = "0." + String(repeating: "1", count: 64)
        let value = MathExpression.binary(.divide, .number(literal, .decimal), .binary(.add, x, one))
        _ = try value.validate()
        XCTAssertEqual(MathPresentation.expression(value), "(\(literal) / (x + 1))")
        XCTAssertTrue(MathPresentation.expression(value, spoken: true).contains(literal))
    }
    func testDepthAndNodeLimitsAreBounded() throws {
        var value = x
        for _ in 0..<32 { value = .negate(value) }
        XCTAssertThrowsError(try value.validate())
        var wide = x
        for _ in 0..<7 { wide = .binary(.add, wide, wide) }
        XCTAssertThrowsError(try wide.validate())
    }
}
