package com.verifiedai.exam.infrastructure.persistence;

import com.verifiedai.exam.domain.AppliedAccommodationSnapshot;
import com.verifiedai.exam.domain.ExamItemResponseState;
import com.verifiedai.exam.domain.ExamScratchpadAsset;
import com.verifiedai.exam.domain.ExamSession;
import com.verifiedai.exam.domain.ExamSession.F28VerificationBatchManifest;
import com.verifiedai.exam.domain.ExamSessionItem;
import com.verifiedai.exam.domain.ExamSessionItem.SessionItemOptionSnapshot;
import com.verifiedai.exam.domain.ExamSessionStatus;
import com.verifiedai.exam.domain.QuestionDifficultyBand;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcExamSessionRepository implements ExamSessionRepository {

    private static final TypeReference<AppliedAccommodationSnapshot> ACCOMMODATIONS_TYPE = new TypeReference<>() {};
    private static final TypeReference<F28VerificationBatchManifest> MANIFEST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<SessionItemOptionSnapshot>> OPTIONS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public JdbcExamSessionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
        this.jsonMapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Override
    public Optional<ExamSession> findById(UUID sessionId) {
        return jdbcClient.sql("""
                SELECT id, user_id, assembly_id, blueprint_id, exam_code, exam_version_number,
                       status, navigation_policy, no_hint_policy_enforced,
                       accommodations_snapshot::text AS accommodations_snapshot_text,
                       base_duration_seconds, extra_time_multiplier, effective_duration_seconds,
                       offline_grace_buffer_seconds, started_at, server_deadline_at, grace_deadline_at,
                       last_heartbeat_at, offline_detected_at, submitted_at, submission_reason,
                       submit_idempotency_key, total_item_count, answered_item_count,
                       flagged_item_count, omitted_item_count, scratchpad_attachment_count,
                       client_clock_skew_ms,
                       f28_verification_batch_manifest::text AS f28_manifest_text,
                       training_eligible, created_at, updated_at
                FROM exam_sessions
                WHERE id = :sessionId
                """)
                .param("sessionId", sessionId)
                .query(this::mapSessionRow)
                .optional();
    }

    @Override
    public Optional<ExamSession> findActiveByUserIdAndAssemblyId(UUID userId, UUID assemblyId) {
        return jdbcClient.sql("""
                SELECT id, user_id, assembly_id, blueprint_id, exam_code, exam_version_number,
                       status, navigation_policy, no_hint_policy_enforced,
                       accommodations_snapshot::text AS accommodations_snapshot_text,
                       base_duration_seconds, extra_time_multiplier, effective_duration_seconds,
                       offline_grace_buffer_seconds, started_at, server_deadline_at, grace_deadline_at,
                       last_heartbeat_at, offline_detected_at, submitted_at, submission_reason,
                       submit_idempotency_key, total_item_count, answered_item_count,
                       flagged_item_count, omitted_item_count, scratchpad_attachment_count,
                       client_clock_skew_ms,
                       f28_verification_batch_manifest::text AS f28_manifest_text,
                       training_eligible, created_at, updated_at
                FROM exam_sessions
                WHERE user_id = :userId
                  AND assembly_id = :assemblyId
                  AND status IN ('IN_PROGRESS', 'OFFLINE_GRACE_BUFFER')
                """)
                .param("userId", userId)
                .param("assemblyId", assemblyId)
                .query(this::mapSessionRow)
                .optional();
    }

    @Override
    public List<ExamSession> findByUserId(UUID userId) {
        return jdbcClient.sql("""
                SELECT id, user_id, assembly_id, blueprint_id, exam_code, exam_version_number,
                       status, navigation_policy, no_hint_policy_enforced,
                       accommodations_snapshot::text AS accommodations_snapshot_text,
                       base_duration_seconds, extra_time_multiplier, effective_duration_seconds,
                       offline_grace_buffer_seconds, started_at, server_deadline_at, grace_deadline_at,
                       last_heartbeat_at, offline_detected_at, submitted_at, submission_reason,
                       submit_idempotency_key, total_item_count, answered_item_count,
                       flagged_item_count, omitted_item_count, scratchpad_attachment_count,
                       client_clock_skew_ms,
                       f28_verification_batch_manifest::text AS f28_manifest_text,
                       training_eligible, created_at, updated_at
                FROM exam_sessions
                WHERE user_id = :userId
                ORDER BY created_at DESC
                """)
                .param("userId", userId)
                .query(this::mapSessionRow)
                .list();
    }

    @Override
    @Transactional
    public ExamSession saveSession(ExamSession session) {
        jdbcClient.sql("""
                INSERT INTO exam_sessions (
                    id, user_id, assembly_id, blueprint_id, exam_code, exam_version_number,
                    status, navigation_policy, no_hint_policy_enforced, accommodations_snapshot,
                    base_duration_seconds, extra_time_multiplier, effective_duration_seconds,
                    offline_grace_buffer_seconds, started_at, server_deadline_at, grace_deadline_at,
                    last_heartbeat_at, offline_detected_at, submitted_at, submission_reason,
                    submit_idempotency_key, total_item_count, answered_item_count,
                    flagged_item_count, omitted_item_count, scratchpad_attachment_count,
                    client_clock_skew_ms, f28_verification_batch_manifest,
                    training_eligible, created_at, updated_at
                ) VALUES (
                    :id, :userId, :assemblyId, :blueprintId, :examCode, :examVersionNumber,
                    :status, :navigationPolicy, TRUE, CAST(:accommodationsSnapshot AS JSONB),
                    :baseDurationSeconds, :extraTimeMultiplier, :effectiveDurationSeconds,
                    :offlineGraceBufferSeconds, :startedAt, :serverDeadlineAt, :graceDeadlineAt,
                    :lastHeartbeatAt, :offlineDetectedAt, :submittedAt, :submissionReason,
                    :submitIdempotencyKey, :totalItemCount, :answeredItemCount,
                    :flaggedItemCount, :omittedItemCount, :scratchpadAttachmentCount,
                    :clientClockSkewMs, CAST(:f28Manifest AS JSONB),
                    FALSE, :createdAt, :updatedAt
                )
                """)
                .param("id", session.id())
                .param("userId", session.userId())
                .param("assemblyId", session.assemblyId())
                .param("blueprintId", session.blueprintId())
                .param("examCode", session.examCode())
                .param("examVersionNumber", session.examVersionNumber())
                .param("status", session.status().name())
                .param("navigationPolicy", session.navigationPolicy())
                .param("accommodationsSnapshot", writeJson(session.accommodationsSnapshot()))
                .param("baseDurationSeconds", session.baseDurationSeconds())
                .param("extraTimeMultiplier", session.extraTimeMultiplier())
                .param("effectiveDurationSeconds", session.effectiveDurationSeconds())
                .param("offlineGraceBufferSeconds", session.offlineGraceBufferSeconds())
                .param("startedAt", Timestamp.from(session.startedAt()))
                .param("serverDeadlineAt", Timestamp.from(session.serverDeadlineAt()))
                .param("graceDeadlineAt", Timestamp.from(session.graceDeadlineAt()))
                .param("lastHeartbeatAt", Timestamp.from(session.lastHeartbeatAt()))
                .param("offlineDetectedAt", toTimestamp(session.offlineDetectedAt()))
                .param("submittedAt", toTimestamp(session.submittedAt()))
                .param("submissionReason", session.submissionReason())
                .param("submitIdempotencyKey", session.submitIdempotencyKey())
                .param("totalItemCount", session.totalItemCount())
                .param("answeredItemCount", session.answeredItemCount())
                .param("flaggedItemCount", session.flaggedItemCount())
                .param("omittedItemCount", session.omittedItemCount())
                .param("scratchpadAttachmentCount", session.scratchpadAttachmentCount())
                .param("clientClockSkewMs", session.clientClockSkewMs())
                .param("f28Manifest", session.f28VerificationBatchManifest() == null
                        ? null
                        : writeJson(session.f28VerificationBatchManifest()))
                .param("createdAt", Timestamp.from(session.createdAt()))
                .param("updatedAt", Timestamp.from(session.updatedAt()))
                .update();

        for (ExamSessionItem item : session.items()) {
            jdbcClient.sql("""
                    INSERT INTO exam_session_items (
                        id, session_id, item_position, question_item_id, question_code,
                        section_code, section_order, topic_code, item_format, difficulty_band,
                        stem_markdown, options_snapshot, figure_alt_text, max_points,
                        negative_marking_weight, response_state, selected_option_key,
                        numeric_response_value, flagged_for_review, eliminated_option_keys,
                        time_spent_seconds, autosave_version, client_autosave_seq,
                        last_autosaved_at, scratchpad_required, training_eligible,
                        created_at, updated_at
                    ) VALUES (
                        :id, :sessionId, :itemPosition, :questionItemId, :questionCode,
                        :sectionCode, :sectionOrder, :topicCode, :itemFormat, :difficultyBand,
                        :stemMarkdown, CAST(:optionsSnapshot AS JSONB), :figureAltText, :maxPoints,
                        :negativeMarkingWeight, :responseState, :selectedOptionKey,
                        :numericResponseValue, :flaggedForReview, CAST(:eliminatedOptionKeys AS JSONB),
                        :timeSpentSeconds, :autosaveVersion, :clientAutosaveSeq,
                        :lastAutosavedAt, :scratchpadRequired, FALSE,
                        :createdAt, :updatedAt
                    )
                    """)
                    .param("id", item.id())
                    .param("sessionId", item.sessionId())
                    .param("itemPosition", item.itemPosition())
                    .param("questionItemId", item.questionItemId())
                    .param("questionCode", item.questionCode())
                    .param("sectionCode", item.sectionCode())
                    .param("sectionOrder", item.sectionOrder())
                    .param("topicCode", item.topicCode())
                    .param("itemFormat", item.itemFormat())
                    .param("difficultyBand", item.difficultyBand().name())
                    .param("stemMarkdown", item.stemMarkdown())
                    .param("optionsSnapshot", writeJson(item.optionsSnapshot()))
                    .param("figureAltText", item.figureAltText())
                    .param("maxPoints", item.maxPoints())
                    .param("negativeMarkingWeight", item.negativeMarkingWeight())
                    .param("responseState", item.responseState().name())
                    .param("selectedOptionKey", item.selectedOptionKey())
                    .param("numericResponseValue", item.numericResponseValue())
                    .param("flaggedForReview", item.flaggedForReview())
                    .param("eliminatedOptionKeys", writeJson(item.eliminatedOptionKeys()))
                    .param("timeSpentSeconds", item.timeSpentSeconds())
                    .param("autosaveVersion", item.autosaveVersion())
                    .param("clientAutosaveSeq", item.clientAutosaveSeq())
                    .param("lastAutosavedAt", toTimestamp(item.lastAutosavedAt()))
                    .param("scratchpadRequired", item.scratchpadRequired())
                    .param("createdAt", Timestamp.from(item.createdAt()))
                    .param("updatedAt", Timestamp.from(item.updatedAt()))
                    .update();
        }

        return findById(session.id()).orElse(session);
    }

    @Override
    @Transactional
    public ExamSession updateSessionHeader(ExamSession session) {
        jdbcClient.sql("""
                UPDATE exam_sessions
                SET status = :status,
                    last_heartbeat_at = :lastHeartbeatAt,
                    offline_detected_at = :offlineDetectedAt,
                    submitted_at = :submittedAt,
                    submission_reason = :submissionReason,
                    submit_idempotency_key = :submitIdempotencyKey,
                    answered_item_count = :answeredItemCount,
                    flagged_item_count = :flaggedItemCount,
                    omitted_item_count = :omittedItemCount,
                    scratchpad_attachment_count = :scratchpadAttachmentCount,
                    client_clock_skew_ms = :clientClockSkewMs,
                    f28_verification_batch_manifest = CAST(:f28Manifest AS JSONB),
                    updated_at = :updatedAt
                WHERE id = :id
                """)
                .param("id", session.id())
                .param("status", session.status().name())
                .param("lastHeartbeatAt", Timestamp.from(session.lastHeartbeatAt()))
                .param("offlineDetectedAt", toTimestamp(session.offlineDetectedAt()))
                .param("submittedAt", toTimestamp(session.submittedAt()))
                .param("submissionReason", session.submissionReason())
                .param("submitIdempotencyKey", session.submitIdempotencyKey())
                .param("answeredItemCount", session.answeredItemCount())
                .param("flaggedItemCount", session.flaggedItemCount())
                .param("omittedItemCount", session.omittedItemCount())
                .param("scratchpadAttachmentCount", session.scratchpadAttachmentCount())
                .param("clientClockSkewMs", session.clientClockSkewMs())
                .param("f28Manifest", session.f28VerificationBatchManifest() == null
                        ? null
                        : writeJson(session.f28VerificationBatchManifest()))
                .param("updatedAt", Timestamp.from(session.updatedAt()))
                .update();

        return findById(session.id()).orElse(session);
    }

    @Override
    @Transactional
    public ExamSession updateSessionAndItem(ExamSession session, ExamSessionItem updatedItem, int expectedAutosaveVersion) {
        int updatedRows = jdbcClient.sql("""
                UPDATE exam_session_items
                SET response_state = :responseState,
                    selected_option_key = :selectedOptionKey,
                    numeric_response_value = :numericResponseValue,
                    flagged_for_review = :flaggedForReview,
                    eliminated_option_keys = CAST(:eliminatedOptionKeys AS JSONB),
                    time_spent_seconds = :timeSpentSeconds,
                    autosave_version = :autosaveVersion,
                    client_autosave_seq = :clientAutosaveSeq,
                    last_autosaved_at = :lastAutosavedAt,
                    updated_at = :updatedAt
                WHERE id = :id
                  AND autosave_version = :expectedVersion
                """)
                .param("id", updatedItem.id())
                .param("responseState", updatedItem.responseState().name())
                .param("selectedOptionKey", updatedItem.selectedOptionKey())
                .param("numericResponseValue", updatedItem.numericResponseValue())
                .param("flaggedForReview", updatedItem.flaggedForReview())
                .param("eliminatedOptionKeys", writeJson(updatedItem.eliminatedOptionKeys()))
                .param("timeSpentSeconds", updatedItem.timeSpentSeconds())
                .param("autosaveVersion", updatedItem.autosaveVersion())
                .param("clientAutosaveSeq", updatedItem.clientAutosaveSeq())
                .param("lastAutosavedAt", toTimestamp(updatedItem.lastAutosavedAt()))
                .param("updatedAt", Timestamp.from(updatedItem.updatedAt()))
                .param("expectedVersion", expectedAutosaveVersion)
                .update();

        if (updatedRows == 0) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_ITEM_STALE_VERSION,
                    "Concurrent modification detected on item " + updatedItem.itemPosition(),
                    true,
                    "Refresh the session state and retry autosave."
            );
        }

        return updateSessionHeader(session);
    }

    @Override
    @Transactional
    public ExamSession saveScratchpadAsset(ExamSession session, ExamSessionItem updatedItem, ExamScratchpadAsset asset) {
        jdbcClient.sql("""
                INSERT INTO exam_session_scratchpad_assets (
                    id, session_id, session_item_id, user_id, item_position, page_number,
                    storage_key, mime_type, byte_size, sha256_checksum, width_px, height_px,
                    capture_source, booklet_publisher, f28_verification_status,
                    training_eligible, captured_at, created_at
                ) VALUES (
                    :id, :sessionId, :sessionItemId, :userId, :itemPosition, :pageNumber,
                    :storageKey, :mimeType, :byteSize, :sha256Checksum, :widthPx, :heightPx,
                    :captureSource, :bookletPublisher, :f28VerificationStatus,
                    FALSE, :capturedAt, :createdAt
                )
                """)
                .param("id", asset.id())
                .param("sessionId", asset.sessionId())
                .param("sessionItemId", asset.sessionItemId())
                .param("userId", asset.userId())
                .param("itemPosition", asset.itemPosition())
                .param("pageNumber", asset.pageNumber())
                .param("storageKey", asset.storageKey())
                .param("mimeType", asset.mimeType())
                .param("byteSize", asset.byteSize())
                .param("sha256Checksum", asset.sha256Checksum())
                .param("widthPx", asset.widthPx())
                .param("heightPx", asset.heightPx())
                .param("captureSource", asset.captureSource())
                .param("bookletPublisher", asset.bookletPublisher())
                .param("f28VerificationStatus", asset.f28VerificationStatus())
                .param("capturedAt", Timestamp.from(asset.capturedAt()))
                .param("createdAt", Timestamp.from(asset.createdAt()))
                .update();

        jdbcClient.sql("""
                UPDATE exam_session_items
                SET updated_at = :updatedAt
                WHERE id = :itemId
                """)
                .param("updatedAt", Timestamp.from(updatedItem.updatedAt()))
                .param("itemId", updatedItem.id())
                .update();

        return updateSessionHeader(session);
    }

    @Override
    public Optional<IdempotencyRecord> findIdempotencyRecord(UUID sessionId, String operationType, String idempotencyKey) {
        return jdbcClient.sql("""
                SELECT id, session_id, user_id, operation_type, idempotency_key,
                       request_payload_hash, response_status_code,
                       response_snapshot::text AS response_snapshot_text,
                       training_eligible, created_at
                FROM exam_session_idempotency_records
                WHERE session_id = :sessionId
                  AND operation_type = :operationType
                  AND idempotency_key = :idempotencyKey
                """)
                .param("sessionId", sessionId)
                .param("operationType", operationType)
                .param("idempotencyKey", idempotencyKey)
                .query((rs, rowNum) -> new IdempotencyRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("session_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("operation_type"),
                        rs.getString("idempotency_key"),
                        rs.getString("request_payload_hash").trim(),
                        rs.getInt("response_status_code"),
                        rs.getString("response_snapshot_text"),
                        rs.getBoolean("training_eligible"),
                        toInstant(rs.getTimestamp("created_at"))
                ))
                .optional();
    }

    @Override
    @Transactional
    public void saveIdempotencyRecord(IdempotencyRecord record) {
        jdbcClient.sql("""
                INSERT INTO exam_session_idempotency_records (
                    id, session_id, user_id, operation_type, idempotency_key,
                    request_payload_hash, response_status_code, response_snapshot,
                    training_eligible, created_at
                ) VALUES (
                    :id, :sessionId, :userId, :operationType, :idempotencyKey,
                    :requestPayloadHash, :responseStatusCode, CAST(:responseSnapshot AS JSONB),
                    FALSE, :createdAt
                )
                ON CONFLICT (session_id, operation_type, idempotency_key) DO NOTHING
                """)
                .param("id", record.id())
                .param("sessionId", record.sessionId())
                .param("userId", record.userId())
                .param("operationType", record.operationType())
                .param("idempotencyKey", record.idempotencyKey())
                .param("requestPayloadHash", record.requestPayloadHash())
                .param("responseStatusCode", record.responseStatusCode())
                .param("responseSnapshot", record.responseSnapshotJson())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .update();
    }

    @Override
    @Transactional
    public int deleteByUserId(UUID userId) {
        // Strict child-first purge order for V057 tables before V056/V055/V054 deletion.
        int deletedAssets = jdbcClient.sql("""
                DELETE FROM exam_session_scratchpad_assets
                WHERE user_id = :userId
                   OR session_id IN (SELECT id FROM exam_sessions WHERE user_id = :userId)
                """)
                .param("userId", userId)
                .update();

        int deletedIdempotency = jdbcClient.sql("""
                DELETE FROM exam_session_idempotency_records
                WHERE user_id = :userId
                   OR session_id IN (SELECT id FROM exam_sessions WHERE user_id = :userId)
                """)
                .param("userId", userId)
                .update();

        int deletedItems = jdbcClient.sql("""
                DELETE FROM exam_session_items
                WHERE session_id IN (SELECT id FROM exam_sessions WHERE user_id = :userId)
                """)
                .param("userId", userId)
                .update();

        int deletedSessions = jdbcClient.sql("""
                DELETE FROM exam_sessions
                WHERE user_id = :userId
                """)
                .param("userId", userId)
                .update();

        return deletedAssets + deletedIdempotency + deletedItems + deletedSessions;
    }

    private ExamSession mapSessionRow(ResultSet rs, int rowNum) throws SQLException {
        UUID sessionId = rs.getObject("id", UUID.class);
        Map<UUID, List<ExamScratchpadAsset>> assetsByItemId = loadAssetsByItemId(sessionId);
        List<ExamSessionItem> items = loadItems(sessionId, assetsByItemId);

        AppliedAccommodationSnapshot acc = readJson(
                rs.getString("accommodations_snapshot_text"),
                ACCOMMODATIONS_TYPE,
                AppliedAccommodationSnapshot.standardDefaults()
        );
        String manifestText = rs.getString("f28_manifest_text");
        F28VerificationBatchManifest manifest = manifestText == null || manifestText.isBlank()
                ? null
                : readJson(manifestText, MANIFEST_TYPE, null);

        return new ExamSession(
                sessionId,
                rs.getObject("user_id", UUID.class),
                rs.getObject("assembly_id", UUID.class),
                rs.getObject("blueprint_id", UUID.class),
                rs.getString("exam_code"),
                rs.getInt("exam_version_number"),
                ExamSessionStatus.valueOf(rs.getString("status")),
                rs.getString("navigation_policy"),
                rs.getBoolean("no_hint_policy_enforced"),
                acc,
                rs.getInt("base_duration_seconds"),
                rs.getBigDecimal("extra_time_multiplier"),
                rs.getInt("effective_duration_seconds"),
                rs.getInt("offline_grace_buffer_seconds"),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("server_deadline_at")),
                toInstant(rs.getTimestamp("grace_deadline_at")),
                toInstant(rs.getTimestamp("last_heartbeat_at")),
                toInstant(rs.getTimestamp("offline_detected_at")),
                toInstant(rs.getTimestamp("submitted_at")),
                rs.getString("submission_reason"),
                rs.getString("submit_idempotency_key"),
                rs.getInt("total_item_count"),
                rs.getInt("answered_item_count"),
                rs.getInt("flagged_item_count"),
                rs.getInt("omitted_item_count"),
                rs.getInt("scratchpad_attachment_count"),
                rs.getLong("client_clock_skew_ms"),
                manifest,
                rs.getBoolean("training_eligible"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                items
        );
    }

    private List<ExamSessionItem> loadItems(UUID sessionId, Map<UUID, List<ExamScratchpadAsset>> assetsByItemId) {
        return jdbcClient.sql("""
                SELECT id, session_id, item_position, question_item_id, question_code,
                       section_code, section_order, topic_code, item_format, difficulty_band,
                       stem_markdown, options_snapshot::text AS options_snapshot_text,
                       figure_alt_text, max_points, negative_marking_weight,
                       response_state, selected_option_key, numeric_response_value,
                       flagged_for_review, eliminated_option_keys::text AS eliminated_option_keys_text,
                       time_spent_seconds, autosave_version, client_autosave_seq,
                       last_autosaved_at, scratchpad_required, training_eligible,
                       created_at, updated_at
                FROM exam_session_items
                WHERE session_id = :sessionId
                ORDER BY item_position ASC
                """)
                .param("sessionId", sessionId)
                .query((rs, rowNum) -> {
                    UUID itemId = rs.getObject("id", UUID.class);
                    return new ExamSessionItem(
                            itemId,
                            rs.getObject("session_id", UUID.class),
                            rs.getInt("item_position"),
                            rs.getObject("question_item_id", UUID.class),
                            rs.getString("question_code"),
                            rs.getString("section_code"),
                            rs.getInt("section_order"),
                            rs.getString("topic_code"),
                            rs.getString("item_format"),
                            QuestionDifficultyBand.valueOf(rs.getString("difficulty_band")),
                            rs.getString("stem_markdown"),
                            readJson(rs.getString("options_snapshot_text"), OPTIONS_TYPE, List.of()),
                            rs.getString("figure_alt_text"),
                            rs.getBigDecimal("max_points"),
                            rs.getBigDecimal("negative_marking_weight"),
                            ExamItemResponseState.valueOf(rs.getString("response_state")),
                            rs.getString("selected_option_key"),
                            rs.getBigDecimal("numeric_response_value"),
                            rs.getBoolean("flagged_for_review"),
                            readJson(rs.getString("eliminated_option_keys_text"), STRING_LIST_TYPE, List.of()),
                            rs.getInt("time_spent_seconds"),
                            rs.getInt("autosave_version"),
                            rs.getLong("client_autosave_seq"),
                            toInstant(rs.getTimestamp("last_autosaved_at")),
                            rs.getBoolean("scratchpad_required"),
                            rs.getBoolean("training_eligible"),
                            toInstant(rs.getTimestamp("created_at")),
                            toInstant(rs.getTimestamp("updated_at")),
                            assetsByItemId.getOrDefault(itemId, List.of())
                    );
                })
                .list();
    }

    private Map<UUID, List<ExamScratchpadAsset>> loadAssetsByItemId(UUID sessionId) {
        List<ExamScratchpadAsset> allAssets = jdbcClient.sql("""
                SELECT id, session_id, session_item_id, user_id, item_position, page_number,
                       storage_key, mime_type, byte_size, sha256_checksum, width_px, height_px,
                       capture_source, booklet_publisher, f28_verification_status,
                       training_eligible, captured_at, created_at
                FROM exam_session_scratchpad_assets
                WHERE session_id = :sessionId
                ORDER BY item_position ASC, page_number ASC
                """)
                .param("sessionId", sessionId)
                .query((rs, rowNum) -> new ExamScratchpadAsset(
                        rs.getObject("id", UUID.class),
                        rs.getObject("session_id", UUID.class),
                        rs.getObject("session_item_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getInt("item_position"),
                        rs.getInt("page_number"),
                        rs.getString("storage_key"),
                        rs.getString("mime_type"),
                        rs.getLong("byte_size"),
                        rs.getString("sha256_checksum").trim(),
                        rs.getObject("width_px", Integer.class),
                        rs.getObject("height_px", Integer.class),
                        rs.getString("capture_source"),
                        rs.getString("booklet_publisher"),
                        rs.getString("f28_verification_status"),
                        rs.getBoolean("training_eligible"),
                        toInstant(rs.getTimestamp("captured_at")),
                        toInstant(rs.getTimestamp("created_at"))
                ))
                .list();

        return allAssets.stream().collect(Collectors.groupingBy(ExamScratchpadAsset::sessionItemId));
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize JSON column in JdbcExamSessionRepository", ex);
        }
    }

    private <T> T readJson(String raw, TypeReference<T> typeRef, T fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return jsonMapper.readValue(raw, typeRef);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to deserialize JSON column in JdbcExamSessionRepository", ex);
        }
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
