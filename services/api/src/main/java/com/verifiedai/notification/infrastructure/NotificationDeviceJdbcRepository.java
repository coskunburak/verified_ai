package com.verifiedai.notification.infrastructure;

import com.verifiedai.notification.domain.DevicePlatform;
import com.verifiedai.notification.domain.OsNotificationPermissionStatus;
import com.verifiedai.notification.domain.PushEnvironment;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationDeviceJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public NotificationDeviceJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lockToken(String tokenSha256) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtextextended(?, 1043))",
            preparedStatement -> preparedStatement.setString(1, tokenSha256),
            resultSet -> {
            }
        );
    }

    public void lockUser(UUID userId) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtextextended(?, 1042))",
            preparedStatement -> preparedStatement.setString(1, userId.toString()),
            resultSet -> {
            }
        );
    }

    public int revokeActiveTokenOwnedByOtherUsers(String tokenSha256, UUID currentUserId, Instant now) {
        return jdbcTemplate.update(
            """
            update notification_devices
               set active = false,
                   revoked_at = ?,
                   version = version + 1,
                   updated_at = ?
             where token_sha256 = ?
               and user_id <> ?
               and active = true
            """,
            Timestamp.from(now),
            Timestamp.from(now),
            tokenSha256,
            currentUserId
        );
    }

    public Optional<DeviceSummaryRow> findByUserAndTokenSha256(UUID userId, String tokenSha256) {
        List<DeviceSummaryRow> rows = jdbcTemplate.query(
            """
            select id, user_id, platform, environment, token_last4, os_permission_status,
                   app_version, locale, active, version, last_seen_at, revoked_at, created_at, updated_at
            from notification_devices
            where user_id = ? and token_sha256 = ?
            """,
            (rs, rowNum) -> mapSummary(rs),
            userId,
            tokenSha256
        );
        return rows.stream().findFirst();
    }

    public int countActiveByUserId(UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from notification_devices where user_id = ? and active = true",
            Integer.class,
            userId
        );
        return count == null ? 0 : count;
    }

    public DeviceSummaryRow insert(
        UUID id,
        UUID userId,
        DevicePlatform platform,
        PushEnvironment environment,
        String deviceToken,
        String tokenSha256,
        String tokenLast4,
        OsNotificationPermissionStatus osPermissionStatus,
        String appVersion,
        String locale,
        Instant now
    ) {
        jdbcTemplate.update(
            """
            insert into notification_devices (
                id, user_id, platform, environment, device_token, token_sha256, token_last4,
                os_permission_status, app_version, locale, active, version, training_eligible,
                last_seen_at, revoked_at, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, 0, false, ?, null, ?, ?)
            """,
            id,
            userId,
            platform.name(),
            environment.name(),
            deviceToken,
            tokenSha256,
            tokenLast4,
            osPermissionStatus.name(),
            appVersion,
            locale,
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now)
        );
        return new DeviceSummaryRow(
            id,
            userId,
            platform,
            environment,
            tokenLast4,
            osPermissionStatus,
            appVersion,
            locale,
            true,
            0L,
            now,
            null,
            now,
            now
        );
    }

    public DeviceSummaryRow reactivateOrUpdate(
        UUID deviceId,
        UUID userId,
        DevicePlatform platform,
        PushEnvironment environment,
        String deviceToken,
        String tokenLast4,
        OsNotificationPermissionStatus osPermissionStatus,
        String appVersion,
        String locale,
        Instant now
    ) {
        jdbcTemplate.update(
            """
            update notification_devices
               set platform = ?,
                   environment = ?,
                   device_token = ?,
                   token_last4 = ?,
                   os_permission_status = ?,
                   app_version = ?,
                   locale = ?,
                   active = true,
                   revoked_at = null,
                   last_seen_at = ?,
                   version = version + 1,
                   updated_at = ?
             where id = ? and user_id = ?
            """,
            platform.name(),
            environment.name(),
            deviceToken,
            tokenLast4,
            osPermissionStatus.name(),
            appVersion,
            locale,
            Timestamp.from(now),
            Timestamp.from(now),
            deviceId,
            userId
        );
        return findByIdAndUserId(deviceId, userId).orElseThrow();
    }

    public List<DeviceSummaryRow> findActiveByUserId(UUID userId) {
        return jdbcTemplate.query(
            """
            select id, user_id, platform, environment, token_last4, os_permission_status,
                   app_version, locale, active, version, last_seen_at, revoked_at, created_at, updated_at
            from notification_devices
            where user_id = ? and active = true
            order by updated_at desc, id desc
            """,
            (rs, rowNum) -> mapSummary(rs),
            userId
        );
    }

    public List<DispatchDeviceTarget> findDeliverableActiveByUserId(UUID userId) {
        return jdbcTemplate.query(
            """
            select id, user_id, platform, environment, device_token, token_last4
            from notification_devices
            where user_id = ?
              and active = true
              and os_permission_status in ('AUTHORIZED', 'PROVISIONAL', 'EPHEMERAL')
            order by updated_at desc, id desc
            """,
            (rs, rowNum) -> new DispatchDeviceTarget(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                DevicePlatform.valueOf(rs.getString("platform")),
                PushEnvironment.valueOf(rs.getString("environment")),
                rs.getString("device_token"),
                rs.getString("token_last4")
            ),
            userId
        );
    }

    public Optional<DeviceSummaryRow> findByIdAndUserId(UUID deviceId, UUID userId) {
        List<DeviceSummaryRow> rows = jdbcTemplate.query(
            """
            select id, user_id, platform, environment, token_last4, os_permission_status,
                   app_version, locale, active, version, last_seen_at, revoked_at, created_at, updated_at
            from notification_devices
            where id = ? and user_id = ?
            """,
            (rs, rowNum) -> mapSummary(rs),
            deviceId,
            userId
        );
        return rows.stream().findFirst();
    }

    public int revokeByIdAndUserId(UUID deviceId, UUID userId, Instant now) {
        return jdbcTemplate.update(
            """
            update notification_devices
               set active = false,
                   revoked_at = ?,
                   version = version + 1,
                   updated_at = ?
             where id = ? and user_id = ? and active = true
            """,
            Timestamp.from(now),
            Timestamp.from(now),
            deviceId,
            userId
        );
    }

    public List<Map<String, Object>> findAllByUserIdForExport(UUID userId) {
        return jdbcTemplate.query(
            """
            select id, platform, environment, token_last4, os_permission_status,
                   app_version, locale, active, last_seen_at, revoked_at, created_at, updated_at
            from notification_devices
            where user_id = ?
            order by created_at desc, id desc
            """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("deviceId", rs.getObject("id", UUID.class).toString());
                row.put("platform", rs.getString("platform"));
                row.put("environment", rs.getString("environment"));
                row.put("tokenLast4", rs.getString("token_last4"));
                row.put("osPermissionStatus", rs.getString("os_permission_status"));
                row.put("appVersion", rs.getString("app_version"));
                row.put("locale", rs.getString("locale"));
                row.put("active", rs.getBoolean("active"));
                row.put("lastSeenAt", instantString(rs.getTimestamp("last_seen_at")));
                row.put("revokedAt", instantString(rs.getTimestamp("revoked_at")));
                row.put("createdAt", instantString(rs.getTimestamp("created_at")));
                row.put("updatedAt", instantString(rs.getTimestamp("updated_at")));
                return row;
            },
            userId
        );
    }

    public int deleteByUserId(UUID userId) {
        return jdbcTemplate.update("delete from notification_devices where user_id = ?", userId);
    }

    private static DeviceSummaryRow mapSummary(ResultSet rs) throws SQLException {
        Timestamp revokedAt = rs.getTimestamp("revoked_at");
        return new DeviceSummaryRow(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            DevicePlatform.valueOf(rs.getString("platform")),
            PushEnvironment.valueOf(rs.getString("environment")),
            rs.getString("token_last4"),
            OsNotificationPermissionStatus.valueOf(rs.getString("os_permission_status")),
            rs.getString("app_version"),
            rs.getString("locale"),
            rs.getBoolean("active"),
            rs.getLong("version"),
            rs.getTimestamp("last_seen_at").toInstant(),
            revokedAt == null ? null : revokedAt.toInstant(),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private static String instantString(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    public record DeviceSummaryRow(
        UUID deviceId,
        UUID userId,
        DevicePlatform platform,
        PushEnvironment environment,
        String tokenLast4,
        OsNotificationPermissionStatus osPermissionStatus,
        String appVersion,
        String locale,
        boolean active,
        long version,
        Instant lastSeenAt,
        Instant revokedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record DispatchDeviceTarget(
        UUID deviceId,
        UUID userId,
        DevicePlatform platform,
        PushEnvironment environment,
        String deviceToken,
        String tokenLast4
    ) {
        @Override
        public String toString() {
            return "DispatchDeviceTarget[deviceId=" + deviceId + ", tokenLast4=" + tokenLast4 + "]";
        }
    }
}
