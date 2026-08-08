package com.verifiedai.identity.application;

import com.verifiedai.identity.infrastructure.persistence.SessionJpaRepository;
import com.verifiedai.identity.infrastructure.persistence.UserJpaEntity;
import com.verifiedai.identity.infrastructure.persistence.UserJpaRepository;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountSessionAccessValidator {
    private final UserJpaRepository userRepository;
    private final SessionJpaRepository sessionRepository;
    private final Clock clock;

    AccountSessionAccessValidator(UserJpaRepository userRepository, SessionJpaRepository sessionRepository, Clock clock) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountSessionAccessValidationResult validate(UUID userId, UUID sessionId, String requestPath) {
        Optional<UserJpaEntity> user = userRepository.findById(userId);
        boolean activeSession = sessionRepository.findById(sessionId)
            .map(session -> session.activeAt(clock.instant()))
            .orElse(false);

        if (user.isEmpty() || !activeSession) {
            return AccountSessionAccessValidationResult.rejected(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCode.AUTH_TOKEN_EXPIRED,
                "Authentication token is invalid or expired",
                "SIGN_IN"
            );
        }
        if (user.get().active() || allowedDuringDeletionRequest(requestPath, user.get())) {
            return AccountSessionAccessValidationResult.allow();
        }

        ApiErrorCode code = user.get().deleted() ? ApiErrorCode.ACCOUNT_DELETED : ApiErrorCode.ACCOUNT_NOT_ACTIVE;
        return AccountSessionAccessValidationResult.rejected(
            HttpStatus.FORBIDDEN,
            code,
            "Account is not active",
            "SIGN_IN"
        );
    }

    private static boolean allowedDuringDeletionRequest(String requestPath, UserJpaEntity user) {
        return user.deletionRequested()
            && (requestPath.equals("/api/v1/me/account")
                || requestPath.equals("/api/v1/me/deletion-request")
                || requestPath.equals("/api/v1/me/deletion-request/confirm")
                || requestPath.equals("/api/v1/auth/logout"));
    }
}
