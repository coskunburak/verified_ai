package com.verifiedai.exam.infrastructure.persistence;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import com.verifiedai.exam.application.ExamPostAssessmentAttributionService.Attribution;
import com.verifiedai.exam.application.ExamPostAssessmentAttributionService.Category;
import com.verifiedai.exam.domain.ExamAttributionModels.StoredAttribution;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ExamAttributionRepository {
    private final JdbcClient jdbc;
    private final JsonMapper mapper;
    public ExamAttributionRepository(JdbcClient jdbc, JsonMapper mapper) {
        this.jdbc=jdbc; this.mapper=mapper;
    }
    public void insert(StoredAttribution row) {
        Attribution a=row.attribution();
        jdbc.sql("""
                INSERT INTO exam_session_item_attributions
                (attribution_id,session_id,item_evaluation_id,user_id,section_code,topic_id,subtopic_id,
                 skill_id,item_outcome,attribution_category,matched_mistake_code,evidence_source,
                 time_spent_seconds,expected_solve_seconds,mastery_snapshot,estimated_net_impact,
                 recommended_action_type,recommended_target_ref,rationale_tr,rationale_en,
                 attribution_metadata,llm_tokens_used,training_eligible,created_at)
                VALUES (:id,:session,:evaluation,:user,:section,:topic,:subtopic,:skill,:outcome,:category,
                 :mistake,:evidence,:elapsed,:expected,:mastery,:impact,:action,:target,:tr,:en,
                 CAST(:metadata AS jsonb),0,FALSE,:created)
                ON CONFLICT (item_evaluation_id) DO NOTHING
                """).param("id",row.attributionId()).param("session",row.sessionId())
                .param("evaluation",row.itemEvaluationId()).param("user",row.userId())
                .param("section",row.sectionCode()).param("topic",row.topicId()).param("subtopic",row.subtopicId())
                .param("skill",a.skillId()).param("outcome",a.itemOutcome()).param("category",a.category().name())
                .param("mistake",a.matchedMistakeCode()).param("evidence",a.evidenceSource())
                .param("elapsed",a.timeSpentSeconds()).param("expected",a.expectedSolveSeconds())
                .param("mastery",a.masterySnapshot()).param("impact",a.estimatedNetImpact())
                .param("action",a.recommendedActionType()).param("target",a.recommendedTargetRef())
                .param("tr",a.rationaleTr()).param("en",a.rationaleEn())
                .param("metadata",json(a.metadata())).param("created",java.sql.Timestamp.from(row.createdAt())).update();
    }
    public List<StoredAttribution> find(UUID userId,UUID sessionId) {
        return jdbc.sql("""
                SELECT a.*, e.position_index FROM exam_session_item_attributions a
                JOIN exam_session_item_evaluations e ON e.evaluation_id=a.item_evaluation_id
                WHERE a.user_id=:user AND a.session_id=:session ORDER BY e.position_index
                """).param("user",userId).param("session",sessionId).query((rs,n)->read(rs)).list();
    }
    private StoredAttribution read(ResultSet rs) throws SQLException {
        Map<String,String> metadata;
        try { metadata=mapper.readValue(rs.getString("attribution_metadata"),new TypeReference<>(){}); }
        catch (Exception ex) { throw new SQLException("Invalid persisted attribution metadata",ex); }
        return new StoredAttribution(rs.getObject("attribution_id",UUID.class),rs.getObject("session_id",UUID.class),
                rs.getObject("item_evaluation_id",UUID.class),rs.getObject("user_id",UUID.class),
                rs.getInt("position_index"),rs.getString("section_code"),rs.getString("topic_id"),rs.getString("subtopic_id"),
                new Attribution(rs.getString("skill_id"),rs.getString("item_outcome"),
                        Category.valueOf(rs.getString("attribution_category")),rs.getString("matched_mistake_code"),
                        rs.getString("evidence_source"),rs.getInt("time_spent_seconds"),rs.getInt("expected_solve_seconds"),
                        rs.getBigDecimal("mastery_snapshot"),rs.getBigDecimal("estimated_net_impact"),
                        rs.getString("recommended_action_type"),rs.getString("recommended_target_ref"),
                        rs.getString("rationale_tr"),rs.getString("rationale_en"),metadata,
                        rs.getInt("llm_tokens_used"),rs.getBoolean("training_eligible")),rs.getTimestamp("created_at").toInstant());
    }
    public List<Map<String,Object>> exportItemAttributions(UUID userId) {
        return jdbc.sql("SELECT * FROM exam_session_item_attributions WHERE user_id=:user ORDER BY created_at,attribution_id")
                .param("user",userId).query().listOfRows();
    }
    public List<Map<String,Object>> exportReallocationBatches(UUID userId) {
        return jdbc.sql("SELECT * FROM exam_study_reallocation_batches WHERE user_id=:user ORDER BY created_at,batch_id")
                .param("user",userId).query().listOfRows();
    }
    public int deleteByUserId(UUID userId) {
        int items=jdbc.sql("DELETE FROM exam_session_item_attributions WHERE user_id=:user").param("user",userId).update();
        return items+jdbc.sql("DELETE FROM exam_study_reallocation_batches WHERE user_id=:user").param("user",userId).update();
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch(Exception ex) { throw new IllegalStateException("Cannot serialize deterministic attribution",ex); }
    }
}
