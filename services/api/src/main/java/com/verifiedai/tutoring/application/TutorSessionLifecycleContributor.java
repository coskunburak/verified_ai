package com.verifiedai.tutoring.application;

import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.tutoring.domain.TutorMessage;
import com.verifiedai.tutoring.domain.TutorPolicy;
import com.verifiedai.tutoring.domain.TutorSession;
import com.verifiedai.tutoring.infrastructure.persistence.TutorStore;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TutorSessionLifecycleContributor implements AccountDataLifecycleContributor {
    private final TutorStore store;
    public TutorSessionLifecycleContributor(TutorStore store) { this.store=store; }
    public String category() { return "tutoring"; }
    public Map<String,Object> exportUserData(UUID owner) {
        // AccountPrivacy holds the same account lock as mutations, so export is one consistent snapshot.
        if(store.exportBytes(owner)>8_000_000) throw TutorPolicy.failure(ApiErrorCode.TUTOR_EXPORT_CAPACITY_EXCEEDED,HttpStatus.CONFLICT);
        var sessions=store.exportSessions(owner);
        var transcripts=sessions.stream().map(s->Map.of(
            "session",sessionMap(s),
            "messages",store.messages(owner,s.id(),0,TutorPolicy.MAX_MESSAGES).stream().map(this::messageMap).toList()
        )).toList();
        return Map.of("schemaVersion","tutor-export-v1","trainingEligible",false,"sessions",transcripts);
    }
    public void deleteUserData(UUID owner, Instant now) { store.delete(owner); }

    private Map<String,Object> sessionMap(TutorSession s) {
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("schemaVersion",s.schemaVersion());
        m.put("id",s.id());
        m.put("problemSessionId",s.problemSessionId());
        m.put("canonicalId",s.canonicalId());
        m.put("canonicalRevision",s.canonicalRevision());
        m.put("parseId",s.parseId());
        m.put("parseRevision",s.parseRevision());
        m.put("primarySkillId",s.primarySkillId());
        m.put("contextSchemaVersion",s.contextSchemaVersion());
        m.put("profileVersion",s.profileVersion());
        m.put("explanationDepth",s.explanationDepth());
        m.put("language",s.language());
        m.put("mastery",s.mastery());
        m.put("safetyPolicyVersion",s.safetyPolicyVersion());
        m.put("status",s.status().name());
        m.put("sourceCurrent",s.sourceCurrent());
        m.put("messageCount",s.messageCount());
        m.put("version",s.version());
        m.put("createdAt",s.createdAt()==null?null:s.createdAt().toString());
        m.put("updatedAt",s.updatedAt()==null?null:s.updatedAt().toString());
        m.put("trainingEligible",s.trainingEligible());
        return m;
    }

    private Map<String,Object> messageMap(TutorMessage msg) {
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("schemaVersion",msg.schemaVersion());
        m.put("id",msg.id());
        m.put("tutorSessionId",msg.tutorSessionId());
        m.put("role",msg.role().name());
        m.put("sequenceNumber",msg.sequenceNumber());
        m.put("content",msg.content());
        m.put("createdAt",msg.createdAt()==null?null:msg.createdAt().toString());
        return m;
    }
}
