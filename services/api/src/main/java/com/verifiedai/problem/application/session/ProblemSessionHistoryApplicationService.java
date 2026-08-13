package com.verifiedai.problem.application.session;

import com.verifiedai.problem.infrastructure.persistence.entity.ProblemSessionJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.repository.ProblemSessionJpaRepository;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemSessionHistoryApplicationService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final ProblemSessionJpaRepository sessionRepository;
    private final ProblemSessionProjectionService projectionService;
    private final ProblemSessionCursorCodec cursorCodec;
    private final JdbcTemplate jdbcTemplate;
    private final ProblemSessionMetrics metrics;

    ProblemSessionHistoryApplicationService(
        ProblemSessionJpaRepository sessionRepository,
        ProblemSessionProjectionService projectionService,
        ProblemSessionCursorCodec cursorCodec,
        JdbcTemplate jdbcTemplate,
        ProblemSessionMetrics metrics
    ) {
        this.sessionRepository = sessionRepository;
        this.projectionService = projectionService;
        this.cursorCodec = cursorCodec;
        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public ProblemSessionHistoryPage history(UUID userId, Integer requestedLimit, String cursor) {
        long started = System.nanoTime();
        try {
            requireActiveAccount(userId);
            int limit = normalizeLimit(requestedLimit);
            ProblemSessionCursor decodedCursor = cursorCodec.decode(cursor);
            List<ProblemSessionJpaEntity> sessions = decodedCursor == null
                ? sessionRepository.findHistoryFirstPage(userId, PageRequest.of(0, limit + 1))
                : sessionRepository.findHistoryAfter(
                    userId,
                    decodedCursor.updatedAt(),
                    decodedCursor.sessionId(),
                    PageRequest.of(0, limit + 1)
                );

            boolean hasMore = sessions.size() > limit;
            List<ProblemSessionJpaEntity> pageSessions = hasMore ? sessions.subList(0, limit) : sessions;
            List<ProblemSessionSummary> items = projectionService
                .projectAll(userId, pageSessions, false)
                .stream()
                .map(ProblemSessionSummary::from)
                .toList();
            String nextCursor = null;
            if (hasMore && !pageSessions.isEmpty()) {
                ProblemSessionJpaEntity last = pageSessions.get(pageSessions.size() - 1);
                nextCursor = cursorCodec.encode(new ProblemSessionCursor(last.updatedAt(), last.id()));
            }
            metrics.historyLoaded("SUCCESS");
            return new ProblemSessionHistoryPage(items, nextCursor);
        } catch (ApiProblemException exception) {
            metrics.historyLoaded(exception.code().name());
            throw exception;
        } finally {
            metrics.historyLatency(System.nanoTime() - started);
        }
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(MAX_LIMIT, Math.max(1, requestedLimit));
    }

    private void requireActiveAccount(UUID userId) {
        String status = jdbcTemplate.query(
            "select status from users where id = ?",
            preparedStatement -> preparedStatement.setObject(1, userId),
            resultSet -> resultSet.next() ? resultSet.getString("status") : null
        );
        if (!"ACTIVE".equals(status)) {
            throw problem(HttpStatus.FORBIDDEN, ApiErrorCode.ACCOUNT_NOT_ACTIVE, "Account is not active", false, "SIGN_IN");
        }
    }

    private static ApiProblemException problem(
        HttpStatus status,
        ApiErrorCode code,
        String title,
        boolean recoverable,
        String userAction
    ) {
        return new ApiProblemException(status, code, title, recoverable, userAction);
    }
}
