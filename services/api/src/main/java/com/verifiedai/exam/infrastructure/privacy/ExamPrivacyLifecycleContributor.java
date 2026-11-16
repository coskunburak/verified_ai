package com.verifiedai.exam.infrastructure.privacy;

import com.verifiedai.exam.domain.ExamScoreRegradeAudit;
import com.verifiedai.exam.domain.ExamSessionScoreSnapshot;
import com.verifiedai.exam.infrastructure.persistence.ExamScoringRepository;

import com.verifiedai.exam.application.CandidateProfileRepository;
import com.verifiedai.exam.application.ExamRepository;
import com.verifiedai.exam.domain.AssembledAssessment;
import com.verifiedai.exam.domain.AssembledAssessmentItem;
import com.verifiedai.exam.domain.AssembledAssessmentRepository;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateHorizonHistoryEntry;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileRecord;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusUploadRecord;
import com.verifiedai.exam.domain.ExamModels.StudentExamTargetRecord;
import com.verifiedai.exam.domain.ExamScratchpadAsset;
import com.verifiedai.exam.domain.ExamSession;
import com.verifiedai.exam.infrastructure.persistence.ExamSessionRepository;
import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * GDPR / KVKK Article 17 lifecycle contributor for exam targets, pinned snapshots,
 * candidate profiles, horizon history, ephemeral syllabus ingestion metadata,
 * assembled assessments, and timed exam sessions / scratchpad assets (V057).
 */
@Component
public class ExamPrivacyLifecycleContributor implements AccountDataLifecycleContributor {

    private static final Logger LOG = LoggerFactory.getLogger(ExamPrivacyLifecycleContributor.class);
    private static final int EXPORT_HISTORY_LIMIT = 100;

    private final ExamRepository examRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final AssembledAssessmentRepository assembledAssessmentRepository;
    private final ExamSessionRepository examSessionRepository;
    private final Clock clock;
    private ExamScoringRepository examScoringRepository;

    @Autowired(required = false)
    public void setExamScoringRepository(ExamScoringRepository examScoringRepository) {
        this.examScoringRepository = examScoringRepository;
    }

    @Autowired
    public ExamPrivacyLifecycleContributor(
            ExamRepository examRepository,
            ObjectProvider<CandidateProfileRepository> candidateProfileRepositoryProvider,
            ObjectProvider<AssembledAssessmentRepository> assembledAssessmentRepositoryProvider,
            ObjectProvider<ExamSessionRepository> examSessionRepositoryProvider,
            ObjectProvider<Clock> clockProvider
    ) {
        this(
                examRepository,
                candidateProfileRepositoryProvider.getIfAvailable(),
                assembledAssessmentRepositoryProvider.getIfAvailable(),
                examSessionRepositoryProvider.getIfAvailable(),
                clockProvider.getIfAvailable(Clock::systemUTC)
        );
    }

    public ExamPrivacyLifecycleContributor(
            ExamRepository examRepository,
            CandidateProfileRepository candidateProfileRepository,
            AssembledAssessmentRepository assembledAssessmentRepository,
            ExamSessionRepository examSessionRepository,
            Clock clock
    ) {
        this.examRepository = Objects.requireNonNull(examRepository, "examRepository cannot be null");
        this.candidateProfileRepository = candidateProfileRepository;
        this.assembledAssessmentRepository = assembledAssessmentRepository;
        this.examSessionRepository = examSessionRepository;
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public ExamPrivacyLifecycleContributor(
            ExamRepository examRepository,
            CandidateProfileRepository candidateProfileRepository,
            AssembledAssessmentRepository assembledAssessmentRepository,
            Clock clock
    ) {
        this(examRepository, candidateProfileRepository, assembledAssessmentRepository, null, clock);
    }

    public ExamPrivacyLifecycleContributor(
            ExamRepository examRepository,
            CandidateProfileRepository candidateProfileRepository,
            Clock clock
    ) {
        this(examRepository, candidateProfileRepository, null, null, clock);
    }

    public ExamPrivacyLifecycleContributor(
            ExamRepository examRepository,
            CandidateProfileRepository candidateProfileRepository
    ) {
        this(examRepository, candidateProfileRepository, null, null, Clock.systemUTC());
    }

    public ExamPrivacyLifecycleContributor(ExamRepository examRepository) {
        this(examRepository, null, null, null, Clock.systemUTC());
    }

    @Override
    public String category() {
        return "exam";
    }

    @Override
    public Map<String, Object> exportUserData(UUID userId) {
        List<StudentExamTargetRecord> targets = examRepository.findTargetsByUserId(userId);
        List<Map<String, Object>> exportedTargets = targets.stream()
                .map(target -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("targetId", target.targetId().toString());
                    entry.put("examCode", target.examCode());
                    entry.put("examVersion", target.examVersion());
                    entry.put("blueprintHash", target.blueprintHash());
                    entry.put("targetScore", target.targetScore());
                    entry.put("targetDate", target.targetDate().toString());
                    entry.put("priorityRank", target.priorityRank());
                    entry.put("targetStatus", target.targetStatus().name());
                    entry.put("isPrimary", target.isPrimary());
                    entry.put("llmTokensUsed", target.llmTokensUsed());
                    entry.put("trainingEligible", target.trainingEligible());
                    entry.put("supersededAt", target.supersededAt() != null ? target.supersededAt().toString() : null);
                    entry.put("createdAt", target.createdAt().toString());
                    entry.put("updatedAt", target.updatedAt().toString());
                    return entry;
                })
                .toList();

        Map<String, Object> exportMap = new LinkedHashMap<>();
        exportMap.put("category", category());
        exportMap.put("studentTargetCount", exportedTargets.size());
        exportMap.put("studentTargets", exportedTargets);

        if (candidateProfileRepository != null) {
            Optional<CandidateProfileRecord> profileOpt = candidateProfileRepository.findProfileByUserId(userId);
            exportMap.put("candidateProfile", profileOpt.map(ExamPrivacyLifecycleContributor::mapCandidateProfile).orElse(null));

            List<CandidateHorizonHistoryEntry> history =
                    candidateProfileRepository.findHorizonHistoryByUserId(userId, EXPORT_HISTORY_LIMIT);
            List<Map<String, Object>> exportedHistory = history.stream()
                    .map(ExamPrivacyLifecycleContributor::mapHistoryEntry)
                    .toList();
            exportMap.put("candidateHorizonHistoryCount", exportedHistory.size());
            exportMap.put("candidateHorizonHistory", exportedHistory);

            List<SyllabusUploadRecord> uploads =
                    candidateProfileRepository.findSyllabusUploadsByUserId(userId, EXPORT_HISTORY_LIMIT);
            List<Map<String, Object>> exportedUploads = uploads.stream()
                    .map(ExamPrivacyLifecycleContributor::mapSanitizedSyllabusUpload)
                    .toList();
            exportMap.put("syllabusUploadCount", exportedUploads.size());
            exportMap.put("syllabusUploads", exportedUploads);
        }

        if (assembledAssessmentRepository != null) {
            List<AssembledAssessment> assembled = assembledAssessmentRepository.findByUserId(userId, EXPORT_HISTORY_LIMIT);
            List<Map<String, Object>> exportedAssembled = assembled.stream()
                    .map(ExamPrivacyLifecycleContributor::mapAssembledAssessment)
                    .toList();
            exportMap.put("assembledAssessmentCount", exportedAssembled.size());
            exportMap.put("assembledAssessments", exportedAssembled);
        }

        if (examSessionRepository != null) {
            List<ExamSession> sessions = examSessionRepository.findByUserId(userId);
            List<Map<String, Object>> exportedSessions = sessions.stream()
                    .limit(EXPORT_HISTORY_LIMIT)
                    .map(ExamPrivacyLifecycleContributor::mapExamSession)
                    .toList();
            exportMap.put("examSessionCount", exportedSessions.size());
            exportMap.put("examSessions", exportedSessions);
        }

        if (examScoringRepository != null) {
            List<Map<String, Object>> exportedScores = new java.util.ArrayList<>();
            for (ExamSessionScoreSnapshot score : examScoringRepository.findLatestScoresByUserId(userId, 200)) {
                Map<String, Object> scoreEntry = new java.util.LinkedHashMap<>();
                scoreEntry.put("scoreId", score.scoreId());
                scoreEntry.put("sessionId", score.sessionId());
                scoreEntry.put("examCode", score.examCode());
                scoreEntry.put("scoringFormulaType", score.scoringFormulaType().name());
                scoreEntry.put("wrongPenaltyRatio", score.wrongPenaltyRatio());
                scoreEntry.put("totalQuestions", score.totalQuestions());
                scoreEntry.put("correctCount", score.correctCount());
                scoreEntry.put("wrongCount", score.wrongCount());
                scoreEntry.put("blankCount", score.blankCount());
                scoreEntry.put("rawCorrectScore", score.rawCorrectScore());
                scoreEntry.put("wrongPenaltyDeduction", score.wrongPenaltyDeduction());
                scoreEntry.put("netScore", score.netScore());
                scoreEntry.put("normalizedPercentageScore", score.normalizedPercentageScore());
                scoreEntry.put("accuracyPercentage", score.accuracyPercentage());
                scoreEntry.put("scoreVersion", score.scoreVersion());
                scoreEntry.put("scoredAt", score.scoredAt());
                scoreEntry.put("lastRegradedAt", score.lastRegradedAt());
                scoreEntry.put("llmTokensUsed", 0);
                scoreEntry.put("inferenceCostUsd", score.inferenceCostUsd());
                scoreEntry.put("trainingEligible", false);
                exportedScores.add(scoreEntry);
            }
            exportMap.put("examSessionScores", exportedScores);

            List<Map<String, Object>> exportedAudits = new java.util.ArrayList<>();
            for (ExamScoreRegradeAudit audit : examScoringRepository.findRegradeAuditsByUserId(userId)) {
                Map<String, Object> auditEntry = new java.util.LinkedHashMap<>();
                auditEntry.put("regradeAuditId", audit.regradeAuditId());
                auditEntry.put("scoreId", audit.scoreId());
                auditEntry.put("sessionId", audit.sessionId());
                auditEntry.put("previousScoreVersion", audit.previousScoreVersion());
                auditEntry.put("newScoreVersion", audit.newScoreVersion());
                auditEntry.put("previousNetScore", audit.previousNetScore());
                auditEntry.put("newNetScore", audit.newNetScore());
                auditEntry.put("reasonCode", audit.reasonCode().name());
                auditEntry.put("reasonDetail", audit.reasonDetail());
                auditEntry.put("triggeredBy", audit.triggeredBy());
                auditEntry.put("regradedAt", audit.regradedAt());
                auditEntry.put("trainingEligible", false);
                exportedAudits.add(auditEntry);
            }
            exportMap.put("examScoreRegradeAudits", exportedAudits);
        } else {
            exportMap.put("examSessionScores", List.of());
            exportMap.put("examScoreRegradeAudits", List.of());
        }

        exportMap.put("llmTokensUsed", 0);
        exportMap.put("trainingEligible", false);
        return exportMap;
    }

    @Override
    public void deleteUserData(UUID userId, Instant now) {
        // Strict FK-safe child-first purge order:
        // V058 scoring snapshots / item evaluations / regrade audits
        // -> V057 scratchpad assets / idempotency / session items / exam sessions
        // -> V056 assembled assessments & items -> V055 candidate profiles -> V054 exam targets
        int deletedScoringRows = examScoringRepository == null
                ? 0
                : examScoringRepository.deleteByUserId(userId);
        int deletedSessionRows = examSessionRepository == null
                ? 0
                : examSessionRepository.deleteByUserId(userId);
        int deletedAssembled = assembledAssessmentRepository == null
                ? 0
                : assembledAssessmentRepository.deleteByUserId(userId);
        int deletedCandidateRows = candidateProfileRepository == null
                ? 0
                : candidateProfileRepository.deleteAllCandidateDataForUser(userId);
        int deletedTargets = examRepository.deleteTargetsByUserId(userId);
        LOG.info(
                "Privacy purge completed for exam module: deletedSessionRows={} deletedAssembled={} deletedCandidateRows={} deletedTargets={}",
                deletedSessionRows,
                deletedAssembled,
                deletedCandidateRows,
                deletedTargets
        );
    }

    @Scheduled(cron = "0 15 3 * * *")
    public void purgeExpiredSyllabusArtifacts() {
        purgeExpiredSyllabusArtifacts(Instant.now(clock));
    }

    public int purgeExpiredSyllabusArtifacts(Instant now) {
        if (candidateProfileRepository == null) {
            return 0;
        }
        Instant effectiveCutoff = now == null ? Instant.now(clock) : now;
        int purged = candidateProfileRepository.purgeExpiredSyllabusUploads(effectiveCutoff);
        if (purged > 0) {
            LOG.info("Purged expired syllabus upload metadata rows={}", purged);
        }
        return purged;
    }

    private static Map<String, Object> mapCandidateProfile(CandidateProfileRecord profile) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profileId", profile.profileId().toString());
        m.put("activeExamCode", profile.activeExamCode());
        m.put("pinnedExamVersion", profile.pinnedExamVersion());
        m.put("jurisdiction", profile.jurisdiction().name());
        m.put("targetDate", profile.targetDate().toString());
        m.put("targetScore", profile.targetScore());
        m.put("dailyStudyMinutes", profile.dailyStudyMinutes());
        m.put("weeklyStudyMinutes", profile.weeklyStudyMinutes());
        m.put("extraTimeMultiplier", profile.accommodations().extraTimeMultiplier());
        m.put("highContrastEnabled", profile.accommodations().highContrastEnabled());
        m.put("screenReaderOptimized", profile.accommodations().screenReaderOptimized());
        m.put("reducedMotionEnabled", profile.accommodations().reducedMotionEnabled());
        m.put("requiredDailyMinutes", profile.requiredDailyMinutes());
        m.put("feasibilityStatus", profile.feasibilityStatus().name());
        m.put("trainingEligible", false);
        m.put("createdAt", profile.createdAt().toString());
        m.put("updatedAt", profile.updatedAt().toString());
        return m;
    }

    private static Map<String, Object> mapHistoryEntry(CandidateHorizonHistoryEntry h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("historyId", h.historyId().toString());
        m.put("examCode", h.examCode());
        m.put("pinnedExamVersion", h.pinnedExamVersion());
        m.put("previousTargetDate", h.previousTargetDate() != null ? h.previousTargetDate().toString() : null);
        m.put("newTargetDate", h.newTargetDate().toString());
        m.put("newDailyMinutes", h.newDailyMinutes());
        m.put("newWeeklyMinutes", h.newWeeklyMinutes());
        m.put("newTargetScore", h.newTargetScore());
        m.put("extraTimeMultiplier", h.extraTimeMultiplier());
        m.put("requiredDailyMinutes", h.requiredDailyMinutes());
        m.put("feasibilityStatus", h.feasibilityStatus().name());
        m.put("changeReason", h.changeReason());
        m.put("changedAt", h.changedAt().toString());
        return m;
    }

    private static Map<String, Object> mapSanitizedSyllabusUpload(SyllabusUploadRecord upload) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uploadId", upload.uploadId().toString());
        m.put("examCode", upload.examCode());
        m.put("examVersion", upload.examVersion());
        m.put("imageHash", upload.imageHash());
        m.put("mimeType", upload.mimeType());
        m.put("byteSize", upload.byteSize());
        m.put("status", upload.status().name());
        m.put("deduplicatedFromCache", upload.deduplicatedFromCache());
        m.put("parsedTopicCount", upload.parsedTopics().size());
        m.put("parsedTopics", upload.parsedTopics().stream()
                .map(t -> Map.of(
                        "rawTopicLabel", t.rawTopicLabel(),
                        "matchedSkillId", t.matchedSkillId() != null ? t.matchedSkillId() : "",
                        "autoConfirmed", t.autoConfirmed(),
                        "userConfirmed", t.userConfirmed()
                ))
                .toList());
        m.put("trainingEligible", false);
        m.put("expiresAt", upload.expiresAt().toString());
        m.put("confirmedAt", upload.confirmedAt() != null ? upload.confirmedAt().toString() : null);
        m.put("createdAt", upload.createdAt().toString());
        return m;
    }

    private static Map<String, Object> mapAssembledAssessment(AssembledAssessment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assessmentId", a.id().toString());
        m.put("blueprintId", a.blueprintId().toString());
        m.put("blueprintCode", a.blueprintCode());
        m.put("examDefinitionId", a.examDefinitionId().toString());
        m.put("examVersionNumber", a.examVersionNumber());
        m.put("assemblySeed", a.assemblySeed());
        m.put("reproducibilityHash", a.reproducibilityHash());
        m.put("totalQuestionCount", a.totalQuestionCount());
        m.put("baseDurationSeconds", a.baseDurationSeconds());
        m.put("timeMultiplier", a.timeMultiplier());
        m.put("effectiveDurationSeconds", a.effectiveDurationSeconds());
        m.put("status", a.status().name());
        m.put("assembledAt", a.assembledAt().toString());
        m.put("expiresAt", a.expiresAt().toString());
        m.put("trainingEligible", false);
        List<Map<String, Object>> itemList = a.items().stream()
                .map(ExamPrivacyLifecycleContributor::mapAssembledItem)
                .toList();
        m.put("items", itemList);
        return m;
    }

    private static Map<String, Object> mapAssembledItem(AssembledAssessmentItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("itemId", item.id().toString());
        m.put("questionItemId", item.questionItemId().toString());
        m.put("questionCode", item.questionCode());
        m.put("sequenceNumber", item.sequenceNumber());
        m.put("topicCode", item.topicCode());
        m.put("difficultyBand", item.difficultyBand().name());
        m.put("cognitiveLevel", item.cognitiveLevel().name());
        m.put("optionOrder", item.optionOrder());
        m.put("allocatedPoints", item.allocatedPoints());
        m.put("trainingEligible", false);
        return m;
    }

    private static Map<String, Object> mapExamSession(ExamSession session) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", session.id().toString());
        m.put("assemblyId", session.assemblyId().toString());
        m.put("blueprintId", session.blueprintId().toString());
        m.put("examCode", session.examCode());
        m.put("examVersionNumber", session.examVersionNumber());
        m.put("status", session.status().name());
        m.put("startedAt", session.startedAt().toString());
        m.put("serverDeadlineAt", session.serverDeadlineAt().toString());
        m.put("submittedAt", session.submittedAt() != null ? session.submittedAt().toString() : null);
        m.put("submissionReason", session.submissionReason());
        m.put("totalItemCount", session.totalItemCount());
        m.put("answeredItemCount", session.answeredItemCount());
        m.put("flaggedItemCount", session.flaggedItemCount());
        m.put("omittedItemCount", session.omittedItemCount());
        m.put("scratchpadAttachmentCount", session.scratchpadAttachmentCount());
        m.put("trainingEligible", false);
        List<Map<String, Object>> scratchpadSummaries = session.items().stream()
                .flatMap(item -> item.scratchpadAssets().stream())
                .map(ExamPrivacyLifecycleContributor::mapScratchpadSummary)
                .toList();
        m.put("scratchpadAssets", scratchpadSummaries);
        return m;
    }

    private static Map<String, Object> mapScratchpadSummary(ExamScratchpadAsset asset) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assetId", asset.id().toString());
        m.put("itemPosition", asset.itemPosition());
        m.put("pageNumber", asset.pageNumber());
        m.put("captureSource", asset.captureSource());
        m.put("bookletPublisher", asset.bookletPublisher());
        m.put("f28VerificationStatus", asset.f28VerificationStatus());
        m.put("sha256Checksum", asset.sha256Checksum());
        m.put("trainingEligible", false);
        return m;
    }
}
