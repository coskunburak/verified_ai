package com.verifiedai.profile.application;

import com.verifiedai.profile.domain.model.EducationLevel;
import com.verifiedai.profile.domain.model.ExplanationDepth;
import com.verifiedai.profile.domain.model.OnboardingStatus;
import com.verifiedai.profile.infrastructure.persistence.LearningProfileJpaEntity;
import com.verifiedai.profile.infrastructure.persistence.LearningProfileJpaRepository;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningProfileApplicationService {
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "tr");
    private final LearningProfileJpaRepository profileRepository;
    private final Clock clock;
    private final JdbcTemplate jdbcTemplate;
    private final ProfileMetrics metrics;

    LearningProfileApplicationService(
        LearningProfileJpaRepository profileRepository,
        Clock clock,
        JdbcTemplate jdbcTemplate,
        ProfileMetrics metrics
    ) {
        this.profileRepository = profileRepository;
        this.clock = clock;
        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public LearningProfileResult getCurrent(UUID userId) {
        requireActiveAccount(userId);
        LearningProfileResult result = profileRepository.findByUserId(userId)
            .map(this::toResult)
            .orElseGet(() -> LearningProfileResult.notStarted(userId));
        metrics.loadSuccess();
        return result;
    }

    @Transactional
    public LearningProfileResult updateCurrent(UUID userId, UpdateLearningProfileCommand command) {
        try {
            Instant now = clock.instant();
            requireActiveAccount(userId);
            lockUserProfile(userId);
            LearningProfileJpaEntity profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> LearningProfileJpaEntity.create(userId, now));
            verifyExpectedVersion(profile, command.expectedVersion());

            ProfileValues values = validate(command).mergeOnto(currentValues(profile));
            OnboardingStatus currentStatus = OnboardingStatus.valueOf(profile.onboardingStatus());
            OnboardingStatus status = command.completeOnboarding() ? OnboardingStatus.COMPLETED : currentStatus;
            if (status == OnboardingStatus.COMPLETED) {
                requireComplete(values);
            }

            profile.update(
                values.educationLevel(),
                values.preferredLanguage(),
                values.explanationDepth(),
                values.dailyStudyMinutes(),
                values.timezone(),
                values.goalContext(),
                status,
                now
            );
            LearningProfileResult result = toResult(profileRepository.saveAndFlush(profile));
            metrics.saveSuccess();
            if (status == OnboardingStatus.COMPLETED) {
                metrics.onboardingCompleted();
            }
            return result;
        } catch (ApiProblemException exception) {
            metrics.saveFailure();
            throw exception;
        }
    }

    private ProfileValues validate(UpdateLearningProfileCommand command) {
        EducationLevel educationLevel = enumValue(EducationLevel.class, command.educationLevel(), "education level");
        ExplanationDepth explanationDepth = enumValue(ExplanationDepth.class, command.explanationDepth(), "explanation depth");
        String preferredLanguage = normalizeLanguage(command.preferredLanguage());
        Integer dailyStudyMinutes = validateDailyStudyMinutes(command.dailyStudyMinutes());
        String timezone = validateTimezone(command.timezone());
        String goalContext = normalizeGoalContext(command.goalContext());
        return new ProfileValues(educationLevel, preferredLanguage, explanationDepth, dailyStudyMinutes, timezone, goalContext);
    }

    private ProfileValues currentValues(LearningProfileJpaEntity profile) {
        return new ProfileValues(
            enumValue(EducationLevel.class, profile.educationLevel(), "education level"),
            profile.preferredLanguage(),
            enumValue(ExplanationDepth.class, profile.explanationDepth(), "explanation depth"),
            profile.dailyStudyMinutes(),
            profile.timezone(),
            profile.goalContext()
        );
    }

    private void requireComplete(ProfileValues values) {
        if (
            values.educationLevel() == null ||
            values.preferredLanguage() == null ||
            values.explanationDepth() == null ||
            values.dailyStudyMinutes() == null ||
            values.timezone() == null
        ) {
            throw validationProblem("Completed onboarding requires education level, language, explanation depth, study minutes and timezone");
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String label) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw validationProblem("Unsupported " + label);
        }
    }

    private static String normalizeLanguage(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_LANGUAGES.contains(normalized)) {
            throw validationProblem("Unsupported preferred language");
        }
        return normalized;
    }

    private static Integer validateDailyStudyMinutes(Integer value) {
        if (value == null) {
            return null;
        }
        if (value < 5 || value > 240) {
            throw validationProblem("Daily study minutes must be between 5 and 240");
        }
        return value;
    }

    private static String validateTimezone(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 64) {
            throw validationProblem("Timezone is too long");
        }
        try {
            ZoneId.of(normalized);
            return normalized;
        } catch (RuntimeException exception) {
            throw validationProblem("Unsupported timezone");
        }
    }

    private static String normalizeGoalContext(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 160) {
            throw validationProblem("Learning goal is too long");
        }
        return normalized;
    }

    private static void verifyExpectedVersion(LearningProfileJpaEntity profile, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(profile.version())) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.OPTIMISTIC_CONFLICT,
                "Learning profile changed since it was loaded",
                true,
                "RETRY"
            );
        }
    }

    private void lockUserProfile(UUID userId) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtextextended(?, 3303))",
            preparedStatement -> preparedStatement.setString(1, userId.toString()),
            resultSet -> {
            }
        );
    }

    private void requireActiveAccount(UUID userId) {
        String status = jdbcTemplate.query(
            "select status from users where id = ?",
            preparedStatement -> preparedStatement.setObject(1, userId),
            resultSet -> resultSet.next() ? resultSet.getString("status") : null
        );
        if (!"ACTIVE".equals(status)) {
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.ACCOUNT_NOT_ACTIVE,
                "Account is not active",
                false,
                "SIGN_IN"
            );
        }
    }

    private LearningProfileResult toResult(LearningProfileJpaEntity entity) {
        return new LearningProfileResult(
            true,
            entity.id(),
            entity.userId(),
            entity.educationLevel(),
            entity.preferredLanguage(),
            entity.explanationDepth(),
            entity.dailyStudyMinutes(),
            entity.timezone(),
            entity.goalContext(),
            OnboardingStatus.valueOf(entity.onboardingStatus()),
            entity.version(),
            entity.createdAt(),
            entity.updatedAt()
        );
    }

    private static ApiProblemException validationProblem(String title) {
        return new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.PROFILE_VALIDATION_FAILED, title, true, "RETRY");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ProfileValues(
        EducationLevel educationLevel,
        String preferredLanguage,
        ExplanationDepth explanationDepth,
        Integer dailyStudyMinutes,
        String timezone,
        String goalContext
    ) {
        ProfileValues mergeOnto(ProfileValues existing) {
            return new ProfileValues(
                educationLevel == null ? existing.educationLevel() : educationLevel,
                preferredLanguage == null ? existing.preferredLanguage() : preferredLanguage,
                explanationDepth == null ? existing.explanationDepth() : explanationDepth,
                dailyStudyMinutes == null ? existing.dailyStudyMinutes() : dailyStudyMinutes,
                timezone == null ? existing.timezone() : timezone,
                goalContext == null ? existing.goalContext() : goalContext
            );
        }
    }
}
