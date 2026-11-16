package com.verifiedai.exam.infrastructure.persistence;

import com.verifiedai.exam.domain.ExamScoreRegradeAudit;
import com.verifiedai.exam.domain.ExamSessionItemEvaluation;
import com.verifiedai.exam.domain.ExamSessionScoreSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamScoringRepository {

    ExamSessionScoreSnapshot saveInitialScoreSnapshot(ExamSessionScoreSnapshot snapshot);

    ExamSessionScoreSnapshot updateRegradedScoreSnapshot(
            ExamSessionScoreSnapshot updatedSnapshot,
            ExamScoreRegradeAudit audit
    );

    Optional<ExamSessionScoreSnapshot> findScoreSnapshotBySessionId(UUID sessionId);

    List<ExamSessionScoreSnapshot> findLatestScoresByUserId(UUID userId, int limit);

    List<ExamSessionItemEvaluation> findItemEvaluationsBySessionId(UUID sessionId);

    List<ExamScoreRegradeAudit> findRegradeAuditsBySessionId(UUID sessionId);

    List<ExamScoreRegradeAudit> findRegradeAuditsByUserId(UUID userId);

    int deleteByUserId(UUID userId);
}
