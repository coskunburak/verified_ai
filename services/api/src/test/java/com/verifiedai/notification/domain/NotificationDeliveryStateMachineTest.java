package com.verifiedai.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

final class NotificationDeliveryStateMachineTest {

    @Test
    void allowsAllDocumentedInitialAndLifecycleTransitions() {
        for (NotificationDeliveryStatus initial : List.of(
            NotificationDeliveryStatus.SCHEDULED,
            NotificationDeliveryStatus.DEFERRED_QUIET_HOURS,
            NotificationDeliveryStatus.SUPPRESSED
        )) {
            assertThat(NotificationDeliveryStateMachine.canTransition(null, initial)).isTrue();
            assertThatCode(() -> NotificationDeliveryStateMachine.assertTransition(null, initial))
                .doesNotThrowAnyException();
        }

        for (NotificationDeliveryStatus target : List.of(
            NotificationDeliveryStatus.DISPATCHING,
            NotificationDeliveryStatus.DEFERRED_QUIET_HOURS,
            NotificationDeliveryStatus.SUPPRESSED,
            NotificationDeliveryStatus.CANCELLED
        )) {
            assertThat(NotificationDeliveryStateMachine.canTransition(NotificationDeliveryStatus.SCHEDULED, target))
                .isTrue();
        }

        for (NotificationDeliveryStatus target : List.of(
            NotificationDeliveryStatus.DISPATCHING,
            NotificationDeliveryStatus.SUPPRESSED,
            NotificationDeliveryStatus.CANCELLED
        )) {
            assertThat(NotificationDeliveryStateMachine.canTransition(
                NotificationDeliveryStatus.DEFERRED_QUIET_HOURS,
                target
            )).isTrue();
        }

        for (NotificationDeliveryStatus target : List.of(
            NotificationDeliveryStatus.SENT,
            NotificationDeliveryStatus.RETRY_SCHEDULED,
            NotificationDeliveryStatus.SUPPRESSED,
            NotificationDeliveryStatus.FAILED_PERMANENT,
            NotificationDeliveryStatus.SCHEDULED
        )) {
            assertThat(NotificationDeliveryStateMachine.canTransition(NotificationDeliveryStatus.DISPATCHING, target))
                .isTrue();
        }

        for (NotificationDeliveryStatus target : List.of(
            NotificationDeliveryStatus.DISPATCHING,
            NotificationDeliveryStatus.DEFERRED_QUIET_HOURS,
            NotificationDeliveryStatus.SUPPRESSED,
            NotificationDeliveryStatus.FAILED_PERMANENT,
            NotificationDeliveryStatus.CANCELLED
        )) {
            assertThat(NotificationDeliveryStateMachine.canTransition(
                NotificationDeliveryStatus.RETRY_SCHEDULED,
                target
            )).isTrue();
        }
    }

    @Test
    void rejectsAllTerminalAndIllegalTransitionsWithConflictErrorCode() {
        Set<NotificationDeliveryStatus> terminalStatuses = EnumSet.of(
            NotificationDeliveryStatus.SENT,
            NotificationDeliveryStatus.SUPPRESSED,
            NotificationDeliveryStatus.FAILED_PERMANENT,
            NotificationDeliveryStatus.CANCELLED
        );

        for (NotificationDeliveryStatus terminal : terminalStatuses) {
            assertThat(terminal.isTerminal()).isTrue();
            for (NotificationDeliveryStatus target : NotificationDeliveryStatus.values()) {
                assertThat(NotificationDeliveryStateMachine.canTransition(terminal, target)).isFalse();
                assertThatThrownBy(() -> NotificationDeliveryStateMachine.assertTransition(terminal, target))
                    .isInstanceOf(ApiProblemException.class)
                    .satisfies(ex -> {
                        ApiProblemException problem = (ApiProblemException) ex;
                        assertThat(problem.status()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(problem.code()).isEqualTo(ApiErrorCode.NOTIFICATION_ILLEGAL_STATE_TRANSITION);
                    });
            }
        }

        // Illegal initial transitions
        for (NotificationDeliveryStatus illegalInit : List.of(
            NotificationDeliveryStatus.DISPATCHING,
            NotificationDeliveryStatus.SENT,
            NotificationDeliveryStatus.RETRY_SCHEDULED,
            NotificationDeliveryStatus.FAILED_PERMANENT,
            NotificationDeliveryStatus.CANCELLED
        )) {
            assertThatThrownBy(() -> NotificationDeliveryStateMachine.assertTransition(null, illegalInit))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                    .isEqualTo(ApiErrorCode.NOTIFICATION_ILLEGAL_STATE_TRANSITION));
        }

        // Illegal non-terminal transitions
        assertThatThrownBy(() -> NotificationDeliveryStateMachine.assertTransition(
            NotificationDeliveryStatus.DISPATCHING,
            NotificationDeliveryStatus.CANCELLED
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.NOTIFICATION_ILLEGAL_STATE_TRANSITION));

        assertThatThrownBy(() -> NotificationDeliveryStateMachine.assertTransition(
            NotificationDeliveryStatus.DISPATCHING,
            NotificationDeliveryStatus.DEFERRED_QUIET_HOURS
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.NOTIFICATION_ILLEGAL_STATE_TRANSITION));
    }
}
