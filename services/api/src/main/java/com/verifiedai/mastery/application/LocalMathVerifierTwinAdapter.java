package com.verifiedai.mastery.application;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Component
public class LocalMathVerifierTwinAdapter implements MathVerifierTwinPort {

    private static final byte[] DOMAIN_KEY = "VERIFIED_AI_SPRINT_7_8_TWIN_SYNTHESIS_KEY_V1".getBytes(StandardCharsets.UTF_8);

    @Override
    public GeneratedTwinSpec synthesizeIsomorphicTwin(
            String curriculumCode,
            String targetSkillCode,
            String rootCauseSkillCode,
            Map<String, Object> canonicalProblemAst,
            String attemptId) {

        try {
            String seedInput = curriculumCode + ":" + targetSkillCode + ":" + rootCauseSkillCode + ":" + attemptId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(DOMAIN_KEY, "HmacSHA256"));
            byte[] hmacBytes = mac.doFinal(seedInput.getBytes(StandardCharsets.UTF_8));
            String generatorSeed = bytesToHex(hmacBytes);

            int srcA = 2;
            int srcB = 5;
            int srcRhs = 17;
            if (canonicalProblemAst != null) {
                if (canonicalProblemAst.get("coeff_a") instanceof Number n) srcA = n.intValue();
                if (canonicalProblemAst.get("const_b") instanceof Number n) srcB = n.intValue();
                if (canonicalProblemAst.get("rhs_c") instanceof Number n) srcRhs = n.intValue();
            }

            long seedSub = Long.parseLong(generatorSeed.substring(0, 8), 16);
            int rawA = (int) (seedSub % 7) + 2;
            if (rawA == srcA) {
                rawA = (rawA % 7) + 2;
            }

            BigInteger fullSeed = new BigInteger(generatorSeed, 16);
            int rawX = fullSeed.mod(BigInteger.valueOf(9)).intValue() + 2;
            int rawB = fullSeed.mod(BigInteger.valueOf(11)).intValue() + 1;
            if (rawB == srcB) {
                rawB += 1;
            }

            String problemLatex;
            String expectedCanonical;
            String stepsJson;

            if (rootCauseSkillCode.toUpperCase().contains("QUAD")) {
                int r1 = rawX;
                int r2 = rawX + fullSeed.mod(BigInteger.valueOf(4)).intValue() + 1;
                int bCoeff = -(r1 + r2);
                int cCoeff = r1 * r2;
                problemLatex = String.format("x^2 %s %dx + %d = 0", bCoeff < 0 ? "-" : "+", Math.abs(bCoeff), cCoeff);
                int minR = Math.min(r1, r2);
                int maxR = Math.max(r1, r2);
                expectedCanonical = String.format("x in {%d, %d}", minR, maxR);
                stepsJson = String.format(
                        "[{\"stepIndex\":1,\"explanation\":\"Factor the quadratic expression into two linear binomials.\",\"latexExpression\":\"(x - %d)(x - %d) = 0\"}," +
                                "{\"stepIndex\":2,\"explanation\":\"Apply the zero-product property to isolate each root.\",\"latexExpression\":\"x_1 = %d, \\\\quad x_2 = %d\"}]",
                        r1, r2, minR, maxR
                );
            } else {
                int rhsVal = rawA * rawX + rawB;
                if (rhsVal == srcRhs) {
                    rawB += 2;
                    rhsVal = rawA * rawX + rawB;
                }
                problemLatex = String.format("%dx + %d = %d", rawA, rawB, rhsVal);
                expectedCanonical = String.valueOf(rawX);
                stepsJson = String.format(
                        "[{\"stepIndex\":1,\"explanation\":\"Subtract %d from both sides to isolate the variable term.\",\"latexExpression\":\"%dx = %d\"}," +
                                "{\"stepIndex\":2,\"explanation\":\"Divide both sides by %d to solve for x.\",\"latexExpression\":\"x = %d\"}]",
                        rawB, rawA, rhsVal - rawB, rawA, rawX
                );
            }

            // Compact JSON format for proof hash
            String proofPayload = String.format(
                    "{\"canonical\":\"%s\",\"problem\":\"%s\",\"seed\":\"%s\",\"steps\":%s}",
                    expectedCanonical, problemLatex, generatorSeed, stepsJson
            );
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] proofHashBytes = sha256.digest(proofPayload.getBytes(StandardCharsets.UTF_8));
            String proofHash = bytesToHex(proofHashBytes);

            return new GeneratedTwinSpec(problemLatex, expectedCanonical, stepsJson, generatorSeed, proofHash);
        } catch (Exception e) {
            throw new RuntimeException("Twin synthesis failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifySymbolicEquivalence(String submittedLatex, String expectedCanonicalExpr) {
        if (submittedLatex == null || expectedCanonicalExpr == null) {
            return false;
        }
        String cleanSub = submittedLatex.trim().replace("x=", "").replace("x =", "").trim();
        String cleanExp = expectedCanonicalExpr.trim().replace("x=", "").replace("x =", "").trim();
        if (cleanSub.equalsIgnoreCase(cleanExp)) {
            return true;
        }
        try {
            double v1 = Double.parseDouble(cleanSub);
            double v2 = Double.parseDouble(cleanExp);
            return Math.abs(v1 - v2) < 1e-6;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
