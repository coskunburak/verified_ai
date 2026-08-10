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
import com.verifiedai.identity.infrastructure.persistence.UserPasswordCredentialJpaEntity;
import com.verifiedai.identity.infrastructure.persistence.UserPasswordCredentialJpaRepository;
import com.verifiedai.identity.infrastructure.security.AccessTokenIssuer;
import com.verifiedai.identity.infrastructure.security.PasswordHasher;
import com.verifiedai.identity.infrastructure.security.RefreshTokenGenerator;
import com.verifiedai.identity.infrastructure.security.RefreshTokenHasher;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
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
    private final UserPasswordCredentialJpaRepository passwordCredentialRepository;
    private final SessionJpaRepository sessionRepository;
    private final RefreshTokenJpaRepository refreshTokenRepository;
    private final AccessTokenIssuer accessTokenIssuer;
    private final PasswordHasher passwordHasher;
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
        UserPasswordCredentialJpaRepository passwordCredentialRepository,
        SessionJpaRepository sessionRepository,
        RefreshTokenJpaRepository refreshTokenRepository,
        AccessTokenIssuer accessTokenIssuer,
        PasswordHasher passwordHasher,
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
        this.passwordCredentialRepository = passwordCredentialRepository;
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenIssuer = accessTokenIssuer;
        this.passwordHasher = passwordHasher;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.refreshTokenHasher = refreshTokenHasher;
        this.properties = properties;
        this.clock = clock;
        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
        this.securityEvents = securityEvents;
    }

    @Transactional(noRollbackFor = ApiProblemException.class)
    public AuthSessionResult signInWithApple(AppleSignInCommand command) {
        metrics.loginAttempt();
        try {
            VerifiedAppleIdentity verifiedIdentity = appleIdentityVerifier.verify(command.identityToken(), command.nonce());
            lockIdentitySubject("APPLE", verifiedIdentity.providerSubject());
            UUID userId = findOrCreateAppleUser(verifiedIdentity.providerSubject());
            requireActiveUser(userId);
            AuthSessionResult result = issueSession(userId, "APPLE");
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
    public AuthSessionResult signUpWithEmail(EmailSignUpCommand command) {
        metrics.loginAttempt();
        try {
            String email = normalizeEmail(command.email());
            requireAcceptablePassword(command.password());
            lockIdentitySubject("EMAIL", email);

            if (identityRepository.findByProviderAndProviderSubject("EMAIL", email).isPresent()
                || passwordCredentialRepository.findByEmailNormalized(email).isPresent()) {
                throw emailAlreadyRegistered();
            }

            Instant now = clock.instant();
            UserJpaEntity user = userRepository.save(UserJpaEntity.active(UUID.randomUUID(), now));
            identityRepository.saveAndFlush(UserIdentityJpaEntity.email(user.id(), email, now));
            passwordCredentialRepository.saveAndFlush(
                UserPasswordCredentialJpaEntity.issue(user.id(), email, passwordHasher.hash(command.password()), now)
            );
            securityEvents.record("USER_CREATED", user.id(), null, "EMAIL");

            AuthSessionResult result = issueSession(user.id(), "EMAIL");
            metrics.loginSuccess();
            securityEvents.record("LOGIN_SUCCESS", user.id(), result.sessionId(), "EMAIL");
            return result;
        } catch (RuntimeException exception) {
            metrics.loginFailure();
            if (exception instanceof ApiProblemException) {
                securityEvents.record("LOGIN_FAILURE", null, null, "EMAIL_SIGN_UP_FAILED");
            }
            throw exception;
        }
    }

    @Transactional(noRollbackFor = ApiProblemException.class)
    public AuthSessionResult signInWithEmail(EmailSignInCommand command) {
        metrics.loginAttempt();
        try {
            String email = normalizeEmail(command.email());
            UserPasswordCredentialJpaEntity credential = passwordCredentialRepository.findByEmailNormalized(email)
                .orElseThrow(this::credentialsInvalid);
            if (!passwordHasher.matches(command.password(), credential.passwordHash())) {
                throw credentialsInvalid();
            }

            UUID userId = credential.userId();
            requireActiveUser(userId);
            credential.markUsed(clock.instant());

            AuthSessionResult result = issueSession(userId, "EMAIL");
            metrics.loginSuccess();
            securityEvents.record("LOGIN_SUCCESS", userId, result.sessionId(), "EMAIL");
            return result;
        } catch (RuntimeException exception) {
            metrics.loginFailure();
            if (exception instanceof ApiProblemException) {
                securityEvents.record("LOGIN_FAILURE", null, null, "EMAIL_CREDENTIAL_INVALID");
            }
            throw exception;
        }
    }

    @Transactional(noRollbackFor = ApiProblemException.class)
    public AuthSessionResult continueAsGuest() {
        metrics.loginAttempt();
        try {
            Instant now = clock.instant();
            UserJpaEntity user = userRepository.save(UserJpaEntity.active(UUID.randomUUID(), now));
            identityRepository.saveAndFlush(UserIdentityJpaEntity.guest(user.id(), UUID.randomUUID().toString(), now));
            securityEvents.record("USER_CREATED", user.id(), null, "GUEST");

            AuthSessionResult result = issueSession(user.id(), "GUEST");
            metrics.loginSuccess();
            securityEvents.record("LOGIN_SUCCESS", user.id(), result.sessionId(), "GUEST");
            return result;
        } catch (RuntimeException exception) {
            metrics.loginFailure();
            if (exception instanceof ApiProblemException) {
                securityEvents.record("LOGIN_FAILURE", null, null, "GUEST_FAILED");
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

    private AuthSessionResult issueSession(UUID userId, String provider) {
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
        securityEvents.record("SESSION_CREATED", userId, session.id(), provider);
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

    private void lockIdentitySubject(String provider, String providerSubject) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtextextended(?, 3101))",
            preparedStatement -> preparedStatement.setString(1, provider + ":" + providerSubject),
            resultSet -> {
            }
        );
    }

    private String normalizeEmail(String email) {
        String normalized = Normalizer.normalize(email == null ? "" : email, Normalizer.Form.NFKC)
            .trim()
            .toLowerCase(Locale.ROOT);
        int atIndex = normalized.indexOf('@');
        if (normalized.length() < 3
            || normalized.length() > 320
            || atIndex <= 0
            || atIndex == normalized.length() - 1
            || normalized.contains(" ")) {
            throw requestValidationFailed("Email address is invalid");
        }
        return normalized;
    }

    private void requireAcceptablePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128 || password.isBlank()) {
            throw passwordRejected();
        }
        boolean hasLetter = password.codePoints().anyMatch(Character::isLetter);
        boolean hasDigit = password.codePoints().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw passwordRejected();
        }
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

    private ApiProblemException credentialsInvalid() {
        return new ApiProblemException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.AUTH_CREDENTIALS_INVALID,
            "Email or password is invalid",
            true,
            "RETRY"
        );
    }

    private ApiProblemException emailAlreadyRegistered() {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            ApiErrorCode.AUTH_EMAIL_ALREADY_REGISTERED,
            "Email address is already registered",
            true,
            "SIGN_IN"
        );
    }

    private ApiProblemException passwordRejected() {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.AUTH_PASSWORD_REJECTED,
            "Password does not meet requirements",
            true,
            "RETRY"
        );
    }

    private ApiProblemException requestValidationFailed(String title) {
        return new ApiProblemException(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.REQUEST_VALIDATION_FAILED,
            title,
            true,
            "RETRY"
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
