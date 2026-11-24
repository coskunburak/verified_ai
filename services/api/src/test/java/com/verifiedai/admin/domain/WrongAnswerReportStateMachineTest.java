package com.verifiedai.admin.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

final class WrongAnswerReportStateMachineTest {

    @Test
    void allowsLegalTransitionsAndRejectsIllegalTerminalTransitions() {
        assertThatCode(() -> WrongAnswerReportStateMachine.validateTransition(
            WrongAnswerReportStatus.OPEN,
            WrongAnswerReportStatus.IN_REVIEW,
            null,
            null
        )).doesNotThrowAnyException();

        assertThatCode(() -> WrongAnswerReportStateMachine.validateTransition(
            WrongAnswerReportStatus.OPEN,
            WrongAnswerReportStatus.RESOLVED_CONFIRMED_DEFECT,
            WrongAnswerResolutionCode.CONFIRMED_SOLVER_DEFECT,
            "Confirmed sign error in final simplification step"
        )).doesNotThrowAnyException();

        assertThatCode(() -> WrongAnswerReportStateMachine.validateTransition(
            WrongAnswerReportStatus.IN_REVIEW,
            WrongAnswerReportStatus.RESOLVED_NOT_A_DEFECT,
            WrongAnswerResolutionCode.VERIFIED_CORRECT_NO_DEFECT,
            "Independent verifier run matches solution step by step"
        )).doesNotThrowAnyException();

        assertThatCode(() -> WrongAnswerReportStateMachine.validateTransition(
            WrongAnswerReportStatus.IN_REVIEW,
            WrongAnswerReportStatus.DISMISSED,
            WrongAnswerResolutionCode.INSUFFICIENT_EVIDENCE,
            "Learner did not attach complete derivation context"
        )).doesNotThrowAnyException();

        for (WrongAnswerReportStatus terminal : new WrongAnswerReportStatus[] {
            WrongAnswerReportStatus.RESOLVED_CONFIRMED_DEFECT,
            WrongAnswerReportStatus.RESOLVED_NOT_A_DEFECT,
            WrongAnswerReportStatus.RESOLVED_DUPLICATE,
            WrongAnswerReportStatus.DISMISSED
        }) {
            assertThatThrownBy(() -> WrongAnswerReportStateMachine.validateTransition(
                terminal,
                WrongAnswerReportStatus.IN_REVIEW,
                null,
                null
            ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    var problem = (ApiProblemException) ex;
                    org.assertj.core.api.Assertions.assertThat(problem.status()).isEqualTo(HttpStatus.CONFLICT);
                    org.assertj.core.api.Assertions.assertThat(problem.code())
                        .isEqualTo(ApiErrorCode.ADMIN_INVALID_TRANSITION);
                });
        }
    }

    @Test
    void requiresResolutionCodeAndNoteWhenResolvingReport() {
        assertThatThrownBy(() -> WrongAnswerReportStateMachine.validateTransition(
            WrongAnswerReportStatus.OPEN,
            WrongAnswerReportStatus.RESOLVED_CONFIRMED_DEFECT,
            null,
            "Valid note without code"
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                var problem = (ApiProblemException) ex;
                org.assertj.core.api.Assertions.assertThat(problem.status())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                org.assertj.core.api.Assertions.assertThat(problem.code())
                    .isEqualTo(ApiErrorCode.ADMIN_VALIDATION_FAILED);
            });

        assertThatThrownBy(() -> WrongAnswerReportStateMachine.validateTransition(
            WrongAnswerReportStatus.IN_REVIEW,
            WrongAnswerReportStatus.RESOLVED_DUPLICATE,
            WrongAnswerResolutionCode.DUPLICATE_REPORT,
            "ok"
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                var problem = (ApiProblemException) ex;
                org.assertj.core.api.Assertions.assertThat(problem.status())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                org.assertj.core.api.Assertions.assertThat(problem.code())
                    .isEqualTo(ApiErrorCode.ADMIN_VALIDATION_FAILED);
            });

        assertThatThrownBy(() -> WrongAnswerReportStateMachine.validateTransition(
            WrongAnswerReportStatus.OPEN,
            WrongAnswerReportStatus.IN_REVIEW,
            WrongAnswerResolutionCode.CONFIRMED_PARSER_DEFECT,
            null
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                var problem = (ApiProblemException) ex;
                org.assertj.core.api.Assertions.assertThat(problem.code())
                    .isEqualTo(ApiErrorCode.ADMIN_VALIDATION_FAILED);
            });
    }
}
