package com.verifiedai.studyplan.rebalance;

import com.verifiedai.exam.events.ExamStudyRebalancePort;
import com.verifiedai.exam.events.ExamStudyRebalanceRequestedEvent;
import com.verifiedai.studyplan.coach.*;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Replaces only pending routine blocks; completed work and the coach's 25-minute cap are preserved. */
@Service
public class ExamAttributionStudyPlanListener implements ExamStudyRebalancePort {
    private final DailyCoachSessionRepository sessions;
    private final DailyCoachTaskRepository tasks;
    public ExamAttributionStudyPlanListener(DailyCoachSessionRepository sessions, DailyCoachTaskRepository tasks) {
        this.sessions=sessions; this.tasks=tasks;
    }
    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public Result apply(ExamStudyRebalanceRequestedEvent request) {
        Map<String,Integer> remaining=new LinkedHashMap<>(),scheduled=new LinkedHashMap<>();
        for (var d:request.directives()) remaining.put(d.skillId(),d.estimatedRemediationHours()
                .multiply(java.math.BigDecimal.valueOf(60)).setScale(0,RoundingMode.CEILING).intValueExact());
        LocalDate start=request.requestedAt().atZone(ZoneOffset.UTC).toLocalDate();
        UUID anchor=null; int changed=0;
        for(int day=0;day<request.targetHorizonDays() && remaining.values().stream().anyMatch(m->m>0);day++) {
            LocalDate date=start.plusDays(day);
            int budget=Math.min(25,request.preferredDailyMinutes());
            var session=sessions.findByUserIdAndLocalDate(request.userId(),date).orElse(null);
            if(session!=null && session.getStatus()!=DailyCoachSessionStatus.IN_PROGRESS) continue;
            if(session==null) {
                session=new DailyCoachSessionEntity(UUID.randomUUID(),request.userId(),date,budget,
                        DailyCoachSessionStatus.IN_PROGRESS,false,false,0,0,"H-03: Deneme sonrası hedefli çalışma planı",
                        DailyCoachMode.SOCRATIC_COACH_MODE,request.requestedAt());
            } else budget=Math.min(budget,session.getBudgetMinutes());
            var existing=tasks.findAllBySessionIdOrderByTaskOrderAsc(session.getId());
            int occupied=existing.stream().filter(t->t.getStatus()!=DailyCoachTaskStatus.PENDING)
                    .mapToInt(DailyCoachTaskEntity::getEstimatedMinutes).sum();
            int available=Math.max(0,budget-occupied);
            var replacements=new ArrayList<DailyCoachTaskEntity>();
            for(int order=1;order<=3 && available>=5;order++) {
                int slot=order;
                var old=existing.stream().filter(t->t.getTaskOrder()==slot).findFirst().orElse(null);
                if(old!=null && old.getStatus()!=DailyCoachTaskStatus.PENDING) continue;
                var ordered=new ArrayList<>(request.directives());
                Collections.rotate(ordered,-((day+order-1)%ordered.size()));
                var directive=ordered.stream().filter(d->remaining.get(d.skillId())>0).findFirst().orElse(null);
                if(directive==null) break;
                int minutes=Math.min(available,Math.min(15,Math.max(5,remaining.get(directive.skillId()))));
                // Preserve slot identity and the unique (session, order) constraint.
                var replacement=new DailyCoachTaskEntity(old==null?UUID.randomUUID():old.getId(),session.getId(),order,
                        "TWIN_DRILL".equals(directive.recommendedActionType())?DailyCoachTaskType.TRAP_REPAIR:DailyCoachTaskType.EXAM_SYNTHESIS,
                        directive.skillId(),directive.matchedMistakeCode(),directive.recommendedTargetRef(),minutes,
                        "H-03: "+directive.recommendedActionType()+"; "+directive.expectedNetGainPerHour()+" net/saat",
                        "",DailyCoachTaskStatus.PENDING);
                replacements.add(replacement); changed++; available-=minutes;
                remaining.compute(directive.skillId(),(k,v)->Math.max(0,v-minutes));
                scheduled.merge(directive.skillId(),minutes,Integer::sum);
                if(anchor==null) anchor=session.getId();
            }
            // Plan before persisting: the parent CHECK forbids a zero-task session,
            // and task rows require the parent FK to exist before they are flushed.
            var planned=new LinkedHashMap<UUID,DailyCoachTaskEntity>();
            existing.forEach(t->planned.put(t.getId(),t));
            replacements.forEach(t->planned.put(t.getId(),t));
            int plannedCount=(int)planned.values().stream()
                    .filter(t->t.getStatus()!=DailyCoachTaskStatus.SKIPPED).count();
            if(plannedCount==0) continue;
            session.setTotalTasksCount(plannedCount);
            sessions.saveAndFlush(session);
            replacements.forEach(tasks::save);
            // Remaining routine work may use only the residual budget.
            for(var old:existing) {
                if(old.getStatus()==DailyCoachTaskStatus.PENDING && !scheduled.isEmpty()) {
                    var current=tasks.findById(old.getId()).orElse(old);
                    if(!current.getWhyThisTaskRationale().startsWith("H-03:") && current.getEstimatedMinutes()>available) {
                        current.setStatus(DailyCoachTaskStatus.SKIPPED);tasks.save(current);
                    } else if(!current.getWhyThisTaskRationale().startsWith("H-03:")) available-=current.getEstimatedMinutes();
                }
            }
            tasks.flush();
            var finalTasks=tasks.findAllBySessionIdOrderByTaskOrderAsc(session.getId());
            session.setTotalTasksCount((int)finalTasks.stream().filter(t->t.getStatus()!=DailyCoachTaskStatus.SKIPPED).count());
            session.setUpdatedAt(request.requestedAt());sessions.save(session);
        }
        return new Result(anchor,changed,scheduled);
    }
}
