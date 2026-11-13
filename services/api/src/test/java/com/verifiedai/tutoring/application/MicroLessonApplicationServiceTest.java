package com.verifiedai.tutoring.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.mistake.MistakeTaxonomy;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.tutoring.domain.MicroLessonCard;
import com.verifiedai.tutoring.domain.MicroLessonCompletionRequest;
import com.verifiedai.tutoring.domain.MicroLessonCompletionResult;
import com.verifiedai.tutoring.domain.MicroLessonCoreRule;
import com.verifiedai.tutoring.domain.MicroLessonHashVerifier;
import com.verifiedai.tutoring.domain.MicroLessonTriggerSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MicroLessonApplicationServiceTest {

    private static final UUID USER_A = UUID.fromString("00000000-0000-4000-8610-000000000001");
    private static final UUID USER_B = UUID.fromString("00000000-0000-4000-8610-000000000002");
    private static final UUID ATTEMPT_A = UUID.fromString("00000000-0000-4000-8610-000000000101");
    private static final UUID ATTEMPT_B = UUID.fromString("00000000-0000-4000-8610-000000000102");

    private InMemoryMicroLessonRepository repository;
    private MicroLessonHashVerifier hashVerifier;
    private SimpleMeterRegistry meterRegistry;
    private Clock fixedClock;
    private MicroLessonApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryMicroLessonRepository();
        hashVerifier = new MicroLessonHashVerifier();
        meterRegistry = new SimpleMeterRegistry();
        fixedClock = Clock.fixed(Instant.parse("2026-11-13T09:30:00Z"), ZoneOffset.UTC);
        service = new MicroLessonApplicationService(
                repository,
                hashVerifier,
                meterRegistry,
                fixedClock,
                true,
                1000,
                600000
        );
    }

    @Test
    void servesAll17CanonicalMistakesInBothLocalesWhenDiagnosed() {
        for (String code : MistakeTaxonomy.CATEGORIES.keySet()) {
            repository.grantActiveDiagnosis(USER_A, code);
            MicroLessonCard trCard = service.getTriggeredMicroLesson(USER_A, code, "tr-TR", "COACH_REPAIR_DRAWER", null);
            MicroLessonCard enCard = service.getTriggeredMicroLesson(USER_A, code, "en-US", "COACH_REPAIR_DRAWER", null);
            assertThat(trCard.locale()).isEqualTo("tr-TR");
            assertThat(enCard.locale()).isEqualTo("en-US");
            assertThat(trCard.verificationStatus()).isEqualTo("VERIFIED");
            assertThat(enCard.verificationStatus()).isEqualTo("VERIFIED");
        }
        assertThat(meterRegistry.counter("verifiedai.micro_lesson.served.total").count()).isEqualTo(34.0);
    }

    @Test
    void rejectsOpenBrowsingWithoutTriggerOrDiagnosisWith422() {
        assertThatThrownBy(() -> service.getTriggeredMicroLesson(
                USER_A, "ALG_SIGN_FLIP_DISTRIBUTE", "tr-TR", "COACH_REPAIR_DRAWER", null
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException apiEx = (ApiProblemException) ex;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(apiEx.code()).isEqualTo(ApiErrorCode.MICRO_LESSON_TRIGGER_NOT_ELIGIBLE);
                });
    }

    @Test
    void crossUserAttemptBolaReturnsIndistinguishable404() {
        repository.grantActiveDiagnosis(USER_A, "ALG_SIGN_FLIP_DISTRIBUTE");
        repository.registerAttempt(USER_B, ATTEMPT_B, "ALG_SIGN_FLIP_DISTRIBUTE");

        assertThatThrownBy(() -> service.getTriggeredMicroLesson(
                USER_A, "ALG_SIGN_FLIP_DISTRIBUTE", "tr-TR", "TWIN_SECOND_FAILURE", ATTEMPT_B
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException apiEx = (ApiProblemException) ex;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.code()).isEqualTo(ApiErrorCode.MISTAKE_DIAGNOSIS_NOT_FOUND);
                });
    }

    @Test
    void featureFlagDisabledReturns503() {
        MicroLessonApplicationService disabled = new MicroLessonApplicationService(
                repository, hashVerifier, meterRegistry, fixedClock, false, 1000, 600000
        );
        assertThatThrownBy(() -> disabled.getTriggeredMicroLesson(
                USER_A, "ALG_SIGN_FLIP_DISTRIBUTE", "tr-TR", "SOCRATIC_RUNG_2", null
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException apiEx = (ApiProblemException) ex;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(apiEx.code()).isEqualTo(ApiErrorCode.MICRO_LESSON_FEATURE_DISABLED);
                });
    }

    @Test
    void tamperedCardTriggers500AndVerificationFailureCounter() {
        repository.grantActiveDiagnosis(USER_A, "ALG_SIGN_FLIP_DISTRIBUTE");
        MicroLessonCard valid = repository.findVerifiedCard("ALG_SIGN_FLIP_DISTRIBUTE", "tr-TR").orElseThrow();
        MicroLessonCard tampered = new MicroLessonCard(
                valid.schemaVersion(),
                valid.cardId(),
                valid.mistakeCode(),
                valid.locale(),
                valid.title(),
                valid.focusBadgeLabel(),
                valid.estimatedDurationSeconds(),
                null,
                null,
                new MicroLessonCoreRule(
                        valid.coreRule().ruleTitle(),
                        valid.coreRule().conciseExplanation(),
                        "tampered-formula",
                        valid.coreRule().sympyCheckExpr(),
                        valid.coreRule().estimatedSeconds()
                ),
                valid.brokenVsValidCounterexample(),
                valid.interactiveQuickCheck(),
                valid.verificationStatus(),
                valid.verifierVersion(),
                valid.verificationHash()
        );
        repository.putCatalogCardOverride(tampered);

        assertThatThrownBy(() -> service.getTriggeredMicroLesson(
                USER_A, "ALG_SIGN_FLIP_DISTRIBUTE", "tr-TR", "COACH_REPAIR_DRAWER", null
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException apiEx = (ApiProblemException) ex;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(apiEx.code()).isEqualTo(ApiErrorCode.MICRO_LESSON_VERIFICATION_FAILED);
                });
        assertThat(meterRegistry.counter("verifiedai.micro_lesson.verification_failure.total").count()).isEqualTo(1.0);
    }

    @Test
    void completionIsServerGradedIdempotentAndTrainingIneligible() {
        repository.registerAttempt(USER_A, ATTEMPT_A, "ALG_SIGN_FLIP_DISTRIBUTE");
        repository.grantContextualTrigger(USER_A, MicroLessonTriggerSource.SOCRATIC_RUNG_2);

        MicroLessonCompletionRequest wrongReq = new MicroLessonCompletionRequest(
                "tr-TR", "SOCRATIC_RUNG_2", ATTEMPT_A, "A", 42_000, "idem-wrong-8601"
        );
        MicroLessonCompletionResult wrongRes = service.completeMicroLesson(USER_A, "ALG_SIGN_FLIP_DISTRIBUTE", wrongReq);
        assertThat(wrongRes.quickCheckPassed()).isFalse();
        assertThat(wrongRes.idempotentReplay()).isFalse();
        assertThat(wrongRes.trainingEligible()).isFalse();

        MicroLessonCompletionRequest correctReq = new MicroLessonCompletionRequest(
                "en-US", "TWIN_SECOND_FAILURE", ATTEMPT_A, "B", 61_500, "idem-pass-8602"
        );
        MicroLessonCompletionResult first = service.completeMicroLesson(USER_A, "ALG_SIGN_FLIP_DISTRIBUTE", correctReq);
        assertThat(first.quickCheckPassed()).isTrue();
        assertThat(first.idempotentReplay()).isFalse();
        assertThat(first.trainingEligible()).isFalse();

        MicroLessonCompletionResult replay = service.completeMicroLesson(USER_A, "ALG_SIGN_FLIP_DISTRIBUTE", correctReq);
        assertThat(replay.completionId()).isEqualTo(first.completionId());
        assertThat(replay.idempotentReplay()).isTrue();
        assertThat(replay.trainingEligible()).isFalse();
        assertThat(meterRegistry.counter("verifiedai.micro_lesson.idempotent_replay.total").count()).isEqualTo(1.0);
    }

    @Test
    void rejectsOutOfBoundsDwellTimeAndReusedIdempotencyKey() {
        assertThatThrownBy(() -> service.completeMicroLesson(
                USER_A,
                "ALG_SIGN_FLIP_DISTRIBUTE",
                new MicroLessonCompletionRequest("tr-TR", "COACH_REPAIR_DRAWER", null, "B", 500, "idem-short-8603")
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        service.completeMicroLesson(
                USER_A,
                "ALG_SIGN_FLIP_DISTRIBUTE",
                new MicroLessonCompletionRequest("tr-TR", "COACH_REPAIR_DRAWER", null, "B", 15_000, "idem-reused-8604")
        );
        assertThatThrownBy(() -> service.completeMicroLesson(
                USER_A,
                "ALG_FRESHMAN_DREAM_POWER",
                new MicroLessonCompletionRequest("tr-TR", "COACH_REPAIR_DRAWER", null, "C", 15_000, "idem-reused-8604")
        ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException apiEx = (ApiProblemException) ex;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.code()).isEqualTo(ApiErrorCode.IDEMPOTENCY_KEY_REUSED);
                });
    }
}
