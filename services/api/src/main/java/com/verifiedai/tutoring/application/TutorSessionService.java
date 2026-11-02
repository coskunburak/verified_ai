package com.verifiedai.tutoring.application;

import com.verifiedai.problem.application.tutoring.TutorSourceAccess;
import com.verifiedai.profile.application.tutoring.TutorLearnerContext;
import com.verifiedai.sharedkernel.error.*;
import com.verifiedai.sharedkernel.ratelimit.*;
import com.verifiedai.tutoring.domain.*;
import com.verifiedai.tutoring.infrastructure.persistence.TutorStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TutorSessionService {
    private final TutorSourceAccess sources;
    private final TutorLearnerContext contexts;
    private final TutorStore store;
    private final Clock clock;
    private final RateLimiter limiter;
    private final MeterRegistry metrics;
    private final int sessionLimit, messageLimit;
    @Value("${verifiedai.tutoring.socratic-enabled:false}")
    private boolean socraticEnabled;
    @Value("${verifiedai.tutoring.hints-enabled:false}")
    private boolean hintsEnabled;

    public TutorSessionService(TutorSourceAccess sources, TutorLearnerContext contexts, TutorStore store, Clock clock,
        RateLimiter limiter, MeterRegistry metrics, @Value("${verifiedai.tutoring.max-sessions:30}") int sessionLimit,
        @Value("${verifiedai.tutoring.max-messages:100}") int messageLimit) {
        this.sources=sources; this.contexts=contexts; this.store=store; this.clock=clock; this.limiter=limiter; this.metrics=metrics;
        if(sessionLimit<1 || sessionLimit>TutorPolicy.MAX_SESSIONS || messageLimit<1 || messageLimit>TutorPolicy.MAX_MESSAGES)
            throw new IllegalArgumentException("Tutor capacity exceeds reviewed bounds");
        this.sessionLimit=sessionLimit; this.messageLimit=messageLimit;
    }
    public TutorSession create(UUID owner, UUID problem, String key) {
        return operation("create",owner,problem,()-> {
            TutorPolicy.key(key);
            var replay=store.creation(owner,problem,key);
            if(replay.isPresent()) { count("replay"); return current(owner,replay.get()); }
            if(store.count(owner)>=sessionLimit) throw TutorPolicy.failure(ApiErrorCode.TUTOR_CAPACITY_EXCEEDED,HttpStatus.CONFLICT);
            TutorSourceAccess.Snapshot source;
            try { source=sources.current(owner,problem); }
            catch(ApiProblemException ex) {
                if(ex.code()==ApiErrorCode.SOLVE_SOURCE_NOT_READY) throw TutorPolicy.failure(ApiErrorCode.TUTOR_SOURCE_NOT_READY,HttpStatus.CONFLICT);
                throw ex;
            }
            var context=contexts.capture(owner); var now=clock.instant();
            var session=new TutorSession(TutorPolicy.SCHEMA,UUID.randomUUID(),problem,source.canonicalId(),source.canonicalRevision(),
                source.parseId(),source.parseRevision(),source.primarySkillId(),context.schemaVersion(),context.profileVersion(),
                context.explanationDepth(),context.language(),context.mastery(),TutorPolicy.SAFETY,TutorPolicy.Status.ACTIVE,
                true,0,0,now,now,false);
            store.insert(owner,key,session); count("created"); return session;
        });
    }
    public List<TutorSession> list(UUID owner, UUID problem, int offset, int limit) {
        return operation("list",owner,problem,()-> {
            TutorPolicy.page(offset,limit);
            if(offset>TutorPolicy.MAX_SESSIONS) throw TutorPolicy.failure(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.BAD_REQUEST);
            return store.list(owner,problem,offset,limit).stream().map(s->current(owner,s)).toList();
        });
    }
    public TutorSession read(UUID owner, UUID problem, UUID id) {
        return operation("read",owner,problem,()-> { var session=current(owner,owned(owner,problem,id)); count("resumed"); return session; });
    }
    public List<TutorMessage> messages(UUID owner, UUID problem, UUID id, long after, int limit) {
        return operation("history",owner,problem,()-> { owned(owner,problem,id); TutorPolicy.page(after,limit); return store.messages(owner,id,after,limit); });
    }
    public TutorMessage append(UUID owner, UUID problem, UUID id, String key, String content) {
        return operation("append",owner,problem,()-> {
            var session=owned(owner,problem,id); TutorPolicy.key(key); TutorPolicy.content(content);
            rejectHintKey(owner,id,key);
            var replay=store.replay(owner,id,key);
            if(replay.isPresent()) {
                if(store.turnForMessage(owner,id,replay.get().id()).isPresent() || !replay.get().content().equals(content)) throw TutorPolicy.failure(ApiErrorCode.IDEMPOTENCY_KEY_REUSED,HttpStatus.CONFLICT);
                count("replay"); return replay.get();
            }
            TutorPolicy.append(session.status(),current(owner,session).sourceCurrent(),session.messageCount()+store.replyCount(owner,id),messageLimit);
            var message=store.append(owner,session,key,content,clock.instant()); count("message_accepted"); return message;
        });
    }
    @Transactional(timeout=5)
    public List<TutorTurn> turns(UUID owner, UUID problem, UUID id, long after, int limit) {
        return operation("turn_history",owner,problem,()-> {
            owned(owner,problem,id); TutorPolicy.page(after,limit);
            return store.turns(owner,id,after,limit);
        });
    }
    @Transactional(timeout=5)
    public TutorTurn submitTurn(UUID owner, UUID problem, UUID id, String key, String content, SocraticPolicy.Intent requestedIntent) {
        return operation("turn",owner,problem,()-> {
            var session=owned(owner,problem,id); TutorPolicy.key(key); TutorPolicy.content(content);
            var intent=requestedIntent == null || requestedIntent == SocraticPolicy.Intent.UNKNOWN
                ? SocraticPolicy.classify(content) : requestedIntent;
            rejectHintKey(owner,id,key);
            var replay=store.replay(owner,id,key);
            if(replay.isPresent()) {
                var existing=store.turnForMessage(owner,id,replay.get().id());
                if(!replay.get().content().equals(content) || existing.isEmpty() || existing.get().intent()!=intent)
                    throw TutorPolicy.failure(ApiErrorCode.IDEMPOTENCY_KEY_REUSED,HttpStatus.CONFLICT);
                count("turn_replay"); return existing.get();
            }
            if(!socraticEnabled) {
                count("provider_disabled");
                throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE,ApiErrorCode.TUTOR_CAPABILITY_UNAVAILABLE,
                    "Socratic guidance is unavailable",true,"RETRY");
            }
            // Two free transcript slots are required; messageCount itself keeps its v1 learner-only meaning.
            TutorPolicy.append(session.status(),current(owner,session).sourceCurrent(),
                session.messageCount()+store.replyCount(owner,id)+1,messageLimit);
            var previous=store.progress(owner,id);
            // No trusted step-evidence contract exists: never infer correctness from a reference or learner text.
            var decision=SocraticPolicy.decide(previous,intent,SocraticPolicy.Evidence.UNKNOWN);
            String language="tr".equals(session.language()) ? "tr" : "en";
            var now=clock.instant();
            var learner=store.append(owner,session,key,content,now);
            var turn=TutorTurn.create(learner,previous,intent,decision,language,now);
            store.insertTurn(owner,turn);
            metrics.counter("tutor.transition","phase",turn.progress().phase().name(),"action",turn.action().name(),
                "outcome",turn.outcome().name()).increment();
            count("deterministic_reply"); return turn;
        });
    }
    @Transactional(timeout=5)
    public TutorHint.History hints(UUID owner, UUID problem, UUID id) {
        return operation("hint_history",owner,problem,()-> {
            var session=current(owner,owned(owner,problem,id));
            var history=store.hints(owner,id); var progress=store.progress(owner,id);
            var level=history.isEmpty() ? null : history.getLast().level();
            boolean supported=session.sourceCurrent() && sources.hintFamily(owner,problem)
                == com.verifiedai.problem.application.tutoring.TutorHintSource.Family.TWO_STEP_LINEAR;
            var availability=HintPolicy.availability(session,progress,level,socraticEnabled && hintsEnabled,supported);
            if(availability==HintPolicy.Availability.AVAILABLE && session.messageCount()+store.replyCount(owner,id)>=messageLimit)
                availability=HintPolicy.Availability.STOPPED;
            return new TutorHint.History("tutor-hint-history-v1",id,history,level,
                availability==HintPolicy.Availability.AVAILABLE ? HintPolicy.next(level) : null,
                availability,progress,"UNAVAILABLE");
        });
    }
    @Transactional(timeout=5)
    public TutorHint requestHint(UUID owner, UUID problem, UUID id, String key, HintPolicy.Level level) {
        return operation("hint",owner,problem,()-> {
            var session=owned(owner,problem,id); TutorPolicy.key(key); count("hint_requested");
            var replay=store.hintReplay(owner,id,key);
            if(replay.isPresent()) {
                if(replay.get().level()!=level) throw TutorPolicy.failure(ApiErrorCode.IDEMPOTENCY_KEY_REUSED,HttpStatus.CONFLICT);
                count("hint_replay"); return replay.get();
            }
            if(store.replay(owner,id,key).isPresent()) throw TutorPolicy.failure(ApiErrorCode.IDEMPOTENCY_KEY_REUSED,HttpStatus.CONFLICT);
            TutorPolicy.append(session.status(),current(owner,session).sourceCurrent(),session.messageCount()+store.replyCount(owner,id),messageLimit);
            var history=store.hints(owner,id); var previous=store.progress(owner,id);
            var next=HintPolicy.advance(previous,history.isEmpty() ? null : history.getLast().level(),level);
            if(!socraticEnabled || !hintsEnabled || sources.hintFamily(owner,problem)
                != com.verifiedai.problem.application.tutoring.TutorHintSource.Family.TWO_STEP_LINEAR)
                throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE,ApiErrorCode.TUTOR_CAPABILITY_UNAVAILABLE,
                    "Hints unavailable for this source",true,"REVIEW");
            // PostgreSQL TIMESTAMPTZ retains microseconds: first response and durable replay must match exactly.
            var hint=TutorHint.create(id,level,"tr".equals(session.language()) ? "tr" : "en",previous,next,
                clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            store.insertHint(owner,key,hint);
            metrics.counter("tutor.hint.delivered","level",level.name()).increment();
            count("hint_delivered"); return hint;
        });
    }
    @Transactional(timeout=5)
    public void reveal(UUID owner, UUID problem, UUID id, String key, boolean confirmed) {
        operation("reveal",owner,problem,()-> {
            var session=owned(owner,problem,id); TutorPolicy.key(key); count("reveal_requested");
            rejectHintKey(owner,id,key);
            if(store.replay(owner,id,key).isPresent()) throw TutorPolicy.failure(ApiErrorCode.IDEMPOTENCY_KEY_REUSED,HttpStatus.CONFLICT);
            TutorPolicy.append(session.status(),current(owner,session).sourceCurrent(),session.messageCount()+store.replyCount(owner,id),messageLimit);
            if(store.progress(owner,id).phase().terminal()) throw TutorPolicy.failure(ApiErrorCode.TUTOR_PEDAGOGY_STOPPED,HttpStatus.CONFLICT);
            HintPolicy.requireReveal(confirmed);
            return null;
        });
    }
    private void rejectHintKey(UUID owner,UUID id,String key) {
        if(store.hintReplay(owner,id,key).isPresent()) throw TutorPolicy.failure(ApiErrorCode.IDEMPOTENCY_KEY_REUSED,HttpStatus.CONFLICT);
    }
    public TutorSession close(UUID owner, UUID problem, UUID id) {
        return operation("close",owner,problem,()-> {
            var session=owned(owner,problem,id);
            if(session.status()==TutorPolicy.Status.ACTIVE) { store.close(owner,id,clock.instant()); count("closed"); }
            else { count("replay"); }
            return current(owner,owned(owner,problem,id));
        });
    }
    private TutorSession owned(UUID owner, UUID problem, UUID id) {
        return store.get(owner,problem,id).orElseThrow(()->TutorPolicy.failure(ApiErrorCode.TUTOR_NOT_FOUND,HttpStatus.NOT_FOUND));
    }
    private TutorSession current(UUID owner, TutorSession s) {
        return s.withCurrent(sources.isCurrent(owner,s.problemSessionId(),new TutorSourceAccess.Snapshot(
            s.canonicalId(),s.canonicalRevision(),s.parseId(),s.parseRevision(),s.primarySkillId())));
    }
    private <T> T operation(String operation, UUID owner, UUID problem, Supplier<T> body) {
        long start=System.nanoTime();
        try {
            if(!limiter.check(new RateLimitPolicy("tutor-requests",120,Duration.ofMinutes(1),true),owner.toString()).allowed())
                throw TutorPolicy.failure(ApiErrorCode.RATE_LIMIT_EXCEEDED,HttpStatus.TOO_MANY_REQUESTS);
            sources.lockOwner(owner,problem);
            return body.get();
        } catch(ApiProblemException ex) {
            metrics.counter("tutor.outcome","operation",operation,"outcome",ex.code().name()).increment(); throw ex;
        } catch(org.springframework.dao.DataAccessException ex) {
            count("database_failure");
            // Do not propagate SQL parameter/content details into exception logging.
            throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE,ApiErrorCode.TEMPORARY_UNAVAILABLE,"Tutor storage unavailable",true,"RETRY");
        } finally { metrics.timer("tutor.request","operation",operation).record(System.nanoTime()-start,TimeUnit.NANOSECONDS); }
    }
    private void count(String outcome) { metrics.counter("tutor.lifecycle","outcome",outcome).increment(); }
}
