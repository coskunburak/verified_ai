package com.verifiedai.identity.application;

import com.verifiedai.identity.domain.model.AppleIdentityVerifier;
import com.verifiedai.identity.domain.model.VerifiedAppleIdentity;
import com.verifiedai.identity.infrastructure.configuration.IdentityAuthProperties;
import com.verifiedai.identity.infrastructure.persistence.RefreshTokenJpaEntity;
import com.verifiedai.identity.infrastructure.persistence.RefreshTokenJpaRepository;
import com.verifiedai.identity.infrastructure.persistence.SessionJpaEntity;
import com.verifiedai.identity.infrastructure.persistence.SessionJpaRepository;
import com.verifiedai.identity.infrastructure.persistence.UserIdentityJpaEntity;
import com.verifiedai.identity.infrastructure.persistence.UserIdentityJpaRepository;
import com.verifiedai.identity.infrastructure.persistence.UserJpaEntity;
import com.verifiedai.identity.infrastructure.persistence.UserJpaRepository;
import com.verifiedai.identity.infrastructure.security.AccessTokenIssuer;
import com.verifiedai.identity.infrastructure.security.RefreshTokenGenerator;
import com.verifiedai.identity.infrastructure.security.RefreshTokenHasher;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityApplicationService {
    private final AppleIdentityVerifier appleIdentityVerifier;
    private final UserJpaRepository userRepository;
    private final UserIdentityJpaRepository identityRepository;
    private final SessionJpaRepository sessionRepository;
    private final RefreshTokenJpaRepository refreshTokenRepository;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final IdentityAuthProperties properties;
    private final Clock clock;
    private final JdbcTemplate jdbcTemplate;
    private final AuthMetrics metrics;
    private final AuthSecurityEventRecorder securityEvents;

    IdentityApplicationService(
        AppleIdentityVerifier appleIdentityVerifier,
        UserJpaRepository userRepository,
        UserIdentityJpaRepository identityRepository,
        SessionJpaRepository sessionRepository,
        RefreshTokenJpaRepository refreshTokenRepository,
        AccessTokenIssuer accessTokenIssuer,
        RefreshTokenGenerator refreshTokenGenerator,
        RefreshTokenHasher refreshTokenHasher,
        IdentityAuthProperties properties,
        Clock clock,
        JdbcTemplate jdbcTemplate,
        AuthMetrics metrics,
        AuthSecurityEventRecorder securityEvents
    ) {
        this.appleIdentityVerifier = appleIdentityVerifier;
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.refreshTokenHasher = refreshTokenHasher;
        this.properties = properties;
        this.clock = clock;
        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
        this.securityEvents = securityEvents;
    }

    @Transactional
    public AuthSessionResult signInWithApple(AppleSignInCommand command) {
        metrics.loginAttempt();
        try {
            VerifiedAppleIdentity verifiedIdentity = appleIdentityVerifier.verify(command.identityToken(), command.nonce());
            lockIdentitySubject(verifiedIdentity.providerSubject());
            UUID userId = findOrCreateAppleUser(verifiedIdentity.providerSubject());
            requireActiveUser(userId);
            AuthSessionResult result = issueSession(userId);
            metrics.loginSuccess();
            securityEvents.record("LOGIN_SUCCESS", userId, result.sessionId(), "APPLE");
            return result;
        } catch (RuntimeException exception) {
            metrics.loginFailure();
            if (exception instanceof ApiProblemException) {
                securityEvents.record("LOGIN_FAILURE", null, null, "APPLE_IDENTITY_INVALID");
            }
            throw exception;
        }
    }

    @Transactional(noRollbackFor = ApiProblemException.class)
    public AuthSessionResult refresh(String refreshTokenValue) {
        Instant now = clock.instant();
        String tokenHash = refreshTokenHasher.hash(refreshTokenValue);
        RefreshTokenJpaEntity presentedToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
            .orElseThrow(this::refreshInvalid);
        SessionJpaEntity session = sessionRepository.findByIdForUpdate(presentedToken.sessionId())
            .orElseThrow(this::refreshInvalid);
        UserJpaEntity user = userRepository.findByIdForUpdate(presentedToken.userId())
            .orElseThrow(this::refreshInvalid);
        if (!user.active()) {
            revokeSession(session, now, "ACCOUNT_NOT_ACTIVE");
            metrics.refreshFailure();
            securityEvents.record("TOKEN_REFRESH_FAILED", presentedToken.userId(), presentedToken.sessionId(), "ACCOUNT_NOT_ACTIVE");
            throw refreshInvalid();
        }

        if (presentedToken.consumedOrReused()) {
            revokeSession(session, now, "REFRESH_REUSE_DETECTED");
            metrics.refreshReuseDetected();
            metrics.refreshFailure();
            securityEvents.record("REFRESH_REUSE_DETECTED", presentedToken.userId(), presentedToken.sessionId(), "TOKEN_REPLAY");
            throw refreshInvalid();
        }

        if (!presentedToken.validAt(now) || !session.activeAt(now)) {
            presentedToken.revoke(now);
            metrics.refreshFailure();
            securityEvents.record("TOKEN_REFRESH_FAILED", presentedToken.userId(), presentedToken.sessionId(), "REFRESH_INVALID");
            throw refreshInvalid();
        }

        String replacementTokenValue = refreshTokenGenerator.generate();
        RefreshTokenJpaEntity replacement = RefreshTokenJpaEntity.issue(
            presentedToken.userId(),
            presentedToken.sessionId(),
            presentedToken.familyId(),
            refreshTokenHasher.hash(replacementTokenValue),
            now,
            presentedToken.expiresAt()
        );
        refreshTokenRepository.save(replacement);
        presentedToken.consume(replacement.id(), now);
        session.touch(now);

        AccessTokenIssuer.IssuedAccessToken accessToken = accessTokenIssuer.issue(presentedToken.userId(), presentedToken.sessionId());
        metrics.refreshSuccess();
        securityEvents.record("TOKEN_REFRESHED", presentedToken.userId(), presentedToken.sessionId(), "ROTATED");
        return new AuthSessionResult(
            presentedToken.userId(),
            presentedToken.sessionId(),
            accessToken.token(),
            accessToken.expiresAt(),
            replacementTokenValue,
            replacement.expiresAt()
        );
    }

    @Transactional
    public void logout(UUID sessionId) {
        Instant now = clock.instant();
        sessionRepository.findByIdForUpdate(sessionId).ifPresent(session -> {
            revokeSession(session, now, "LOGOUT");
            metrics.sessionRevoked();
            securityEvents.record("LOGOUT", session.userId(), session.id(), "CURRENT_SESSION");
        });
    }

    public CurrentSessionResult currentSession(UUID userId, UUID sessionId) {
        requireActiveUser(userId);
        return new CurrentSessionResult(userId, sessionId);
    }

    private UUID findOrCreateAppleUser(String providerSubject) {
        return identityRepository.findByProviderAndProviderSubject("APPLE", providerSubject)
            .map(identity -> {
                UUID userId = identity.userId();
                UserJpaEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> accountNotActive("MISSING"));
                if (!user.active()) {
                    securityEvents.record("LOGIN_BLOCKED", userId, null, user.status());
                    throw accountNotActive(user.status());
                }
                return userId;
            })
            .orElseGet(() -> {
                Instant now = clock.instant();
                UserJpaEntity user = userRepository.save(UserJpaEntity.active(UUID.randomUUID(), now));
                identityRepository.saveAndFlush(UserIdentityJpaEntity.apple(user.id(), providerSubject, now));
                securityEvents.record("USER_CREATED", user.id(), null, "APPLE");
                return user.id();
            });
    }

    private AuthSessionResult issueSession(UUID userId) {
        requireActiveUser(userId);
        Instant now = clock.instant();
        Instant refreshExpiresAt = now.plus(properties.refreshToken().ttl());
        SessionJpaEntity session = sessionRepository.save(SessionJpaEntity.active(userId, now, refreshExpiresAt));
        String refreshTokenValue = refreshTokenGenerator.generate();
        RefreshTokenJpaEntity refreshToken = refreshTokenRepository.save(
            RefreshTokenJpaEntity.issue(
                userId,
                session.id(),
                UUID.randomUUID(),
                refreshTokenHasher.hash(refreshTokenValue),
                now,
                refreshExpiresAt
            )
        );
        AccessTokenIssuer.IssuedAccessToken accessToken = accessTokenIssuer.issue(userId, session.id());
        securityEvents.record("SESSION_CREATED", userId, session.id(), "APPLE");
        return new AuthSessionResult(
            userId,
            session.id(),
            accessToken.token(),
            accessToken.expiresAt(),
            refreshTokenValue,
            refreshToken.expiresAt()
        );
    }

    private void revokeSession(SessionJpaEntity session, Instant now, String reason) {
        session.revoke(now, reason);
        refreshTokenRepository.revokeActiveBySessionId(session.id(), now);
        securityEvents.record("SESSION_REVOKED", session.userId(), session.id(), reason);
    }

    private void lockIdentitySubject(String providerSubject) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtextextended(?, 3101))",
            preparedStatement -> preparedStatement.setString(1, "APPLE:" + providerSubject),
            resultSet -> {
            }
        );
    }

    private ApiProblemException refreshInvalid() {
        return new ApiProblemException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.AUTH_REFRESH_REVOKED,
            "Refresh session is invalid",
            false,
            "SIGN_IN"
        );
    }

    private void requireActiveUser(UUID userId) {
        UserJpaEntity user = userRepository.findById(userId)
            .orElseThrow(() -> accountNotActive("MISSING"));
        if (!user.active()) {
            throw accountNotActive(user.status());
        }
    }

    private ApiProblemException accountNotActive(String status) {
        ApiErrorCode code = "DELETED".equals(status) ? ApiErrorCode.ACCOUNT_DELETED : ApiErrorCode.ACCOUNT_NOT_ACTIVE;
        return new ApiProblemException(
            HttpStatus.FORBIDDEN,
            code,
            "Account is not active",
            false,
            "SIGN_IN"
        );
    }
}
