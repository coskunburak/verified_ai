package com.verifiedai.growth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

final class GrowthContentSanitizerAndStateMachineTest {

    @Test
    void validatesAllFourArtifactTypesAndRejectsPiiRawContentAndBannedTerms() {
        Map<String, Object> weekly = GrowthContentSanitizer.sanitizeSummaryPayload(
            ShareableArtifactType.WEEKLY_PROGRESS_SUMMARY,
            Map.of(
                "weekIso", "2026-W47",
                "verifiedSessionsCount", 5,
                "activeDaysCount", 4,
                "topSkillDomain", "ALGEBRA",
                "milestoneBadge", "CONSISTENT_PRACTICE"
            )
        );
        assertThat(weekly).containsEntry("weekIso", "2026-W47");

        Map<String, Object> mastery = GrowthContentSanitizer.sanitizeSummaryPayload(
            ShareableArtifactType.MASTERY_MILESTONE,
            Map.of(
                "skillCode", "MATH.ALG.LINEAR_EQ",
                "skillDomain", "ALGEBRA",
                "milestoneBadge", "TRAP_CONQUERED",
                "conqueredTrapCount", 3
            )
        );
        assertThat(mastery).containsEntry("conqueredTrapCount", 3L);

        Map<String, Object> challenge = GrowthContentSanitizer.sanitizeSummaryPayload(
            ShareableArtifactType.COUNTERFACTUAL_CHALLENGE_CARD,
            Map.of(
                "mistakeCode", "SIGN_FLIP_LINEAR",
                "skillCode", "MATH.ALG.LINEAR_EQ",
                "challengeSummaryKey", "challenge.sign_flip.v1",
                "locale", "en",
                "difficultyBand", "FOUNDATION"
            )
        );
        assertThat(challenge).containsEntry("mistakeCode", "SIGN_FLIP_LINEAR");

        Map<String, Object> readiness = GrowthContentSanitizer.sanitizeSummaryPayload(
            ShareableArtifactType.EXAM_READINESS_MILESTONE,
            Map.of(
                "examCode", "TR_YKS_TYT_MATH",
                "confidenceTier", "STEADY",
                "readinessBand", "ON_TRACK",
                "milestoneBadge", "TYT_READY"
            )
        );
        assertThat(readiness).containsEntry("examCode", "TR_YKS_TYT_MATH");

        // Reject email in displayAlias
        assertThatThrownBy(() -> GrowthContentSanitizer.sanitizeDisplayAlias("alex@example.com"))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                ApiProblemException ape = (ApiProblemException) ex;
                assertThat(ape.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(ape.code()).isEqualTo(ApiErrorCode.GROWTH_PII_OR_RAW_CONTENT_REJECTED);
            });

        // Reject forbidden raw key
        assertThatThrownBy(() -> GrowthContentSanitizer.sanitizeSummaryPayload(
            ShareableArtifactType.COUNTERFACTUAL_CHALLENGE_CARD,
            Map.of("rawProblemText", "Solve 2x + 3 = 7", "skillCode", "MATH.ALG.LINEAR_EQ")
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.GROWTH_PII_OR_RAW_CONTENT_REJECTED));

        // Reject URL, JWT, LaTeX, and stigmatizing lexicon inside values
        for (String unsafe : new String[] {
            "https://evil.example/leak",
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ",
            "\\frac{1}{2} + x",
            "You are a lazy slacker"
        }) {
            assertThatThrownBy(() -> GrowthContentSanitizer.sanitizeSummaryPayload(
                ShareableArtifactType.COUNTERFACTUAL_CHALLENGE_CARD,
                Map.of("challengeSummaryKey", unsafe)
            ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                    .isEqualTo(ApiErrorCode.GROWTH_PII_OR_RAW_CONTENT_REJECTED));
        }
    }

    @Test
    void referralCodeGeneratorProducesCrockfordSafeCodesAndValidatesStateMachineTransitions() {
        for (int i = 0; i < 20; i++) {
            String code = ReferralCodeGenerator.generateReferralCode();
            assertThat(code).matches("^[A-Z2-9]{8}$");
            assertThat(code).doesNotContain("0", "O", "1", "I");
            assertThat(ReferralCodeGenerator.isValidFormat(code)).isTrue();
        }
        assertThat(ReferralCodeGenerator.generateShareToken()).matches("^[A-Za-z0-9_-]{32}$");

        // Legal transitions
        ReferralAttributionStateMachine.assertTransitionAllowed(
            ReferralAttributionStatus.ATTRIBUTED,
            ReferralAttributionStatus.QUALIFIED
        );
        ReferralAttributionStateMachine.assertTransitionAllowed(
            ReferralAttributionStatus.QUALIFIED,
            ReferralAttributionStatus.REWARD_GRANTED
        );
        ReferralAttributionStateMachine.assertTransitionAllowed(
            ReferralAttributionStatus.ATTRIBUTED,
            ReferralAttributionStatus.REJECTED_ABUSE
        );

        // Terminal states reject any further transition
        assertThat(ReferralAttributionStatus.REWARD_GRANTED.isTerminal()).isTrue();
        assertThat(ReferralAttributionStatus.REJECTED_ABUSE.isTerminal()).isTrue();
        assertThat(ReferralAttributionStatus.EXPIRED.isTerminal()).isTrue();

        for (ReferralAttributionStatus terminal : new ReferralAttributionStatus[] {
            ReferralAttributionStatus.REWARD_GRANTED,
            ReferralAttributionStatus.REJECTED_ABUSE,
            ReferralAttributionStatus.EXPIRED
        }) {
            assertThatThrownBy(() -> ReferralAttributionStateMachine.assertTransitionAllowed(
                terminal,
                ReferralAttributionStatus.QUALIFIED
            ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException ape = (ApiProblemException) ex;
                    assertThat(ape.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ape.code()).isEqualTo(ApiErrorCode.GROWTH_INVALID_TRANSITION);
                });
        }
    }
}
