package com.verifiedai.studyplan.rebalance;

import com.verifiedai.exam.events.*;
import com.verifiedai.exam.events.ExamAttributionGeneratedEvent.HighRoiTopicDirective;
import com.verifiedai.studyplan.coach.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExamAttributionStudyPlanListenerTest {
    private final UUID user=UUID.randomUUID();
    private final Instant now=Instant.parse("2026-11-17T10:00:00Z");
    private final Map<UUID,DailyCoachSessionEntity> sessionRows=new LinkedHashMap<>();
    private final Map<UUID,DailyCoachTaskEntity> taskRows=new LinkedHashMap<>();
    @SuppressWarnings("unchecked")
    private <T> T repository(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(proxy,method,args)-> {
            switch(method.getName()) {
                case "save", "saveAndFlush":
                    if(args[0] instanceof DailyCoachSessionEntity s) {
                        assertTrue(s.getTotalTasksCount()>0, "Parent must have a positive task count at every save/flush");
                        sessionRows.put(s.getId(),s);
                    }
                    else {var t=(DailyCoachTaskEntity)args[0];
                        assertTrue(sessionRows.containsKey(t.getSessionId()), "Parent must be persisted before task rows");
                        taskRows.put(t.getId(),t);}return args[0];
                case "findByUserIdAndLocalDate":return sessionRows.values().stream().filter(s->s.getUserId().equals(args[0])&&s.getLocalDate().equals(args[1])).findFirst();
                case "findAllBySessionIdOrderByTaskOrderAsc":return taskRows.values().stream().filter(t->t.getSessionId().equals(args[0])).sorted(Comparator.comparingInt(DailyCoachTaskEntity::getTaskOrder)).toList();
                case "findById":return Optional.ofNullable(taskRows.get(args[0]));
                case "flush":return null;
                default:throw new UnsupportedOperationException(method.getName());
            }
        });
    }
    private ExamStudyRebalanceRequestedEvent request(int minutes) {
        var directives=new ArrayList<HighRoiTopicDirective>();
        for(int rank=1;rank<=3;rank++) directives.add(new HighRoiTopicDirective(rank,"topic","skill"+rank,"CONCEPTUAL_GAP",null,new BigDecimal("0.5"),new BigDecimal("3.00"),"MICRO_LESSON","microlesson:skill"+rank));
        return new ExamStudyRebalanceRequestedEvent(UUID.randomUUID(),user,UUID.randomUUID(),"TYT","synthetic",directives,now,7,minutes);
    }
    private ExamStudyRebalancePort.Result apply(int minutes) {
        return new ExamAttributionStudyPlanListener(repository(DailyCoachSessionRepository.class),repository(DailyCoachTaskRepository.class)).apply(request(minutes));
    }
    @Test void sevenDaysRespectDailyCapAndReachAllTopThreeSkills() {
        var result=apply(45);
        assertNotNull(result.planId());assertEquals(7,sessionRows.size());assertEquals(Set.of("skill1","skill2","skill3"),result.scheduledMinutes().keySet());
        assertTrue(result.scheduledMinutes().values().stream().mapToInt(Integer::intValue).sum()<=175);
        for(var session:sessionRows.values()) {
            assertTrue(session.getBudgetMinutes()<=25);
            assertTrue(taskRows.values().stream().filter(t->t.getSessionId().equals(session.getId())).mapToInt(DailyCoachTaskEntity::getEstimatedMinutes).sum()<=25);
        }
    }
    @Test void fiveMinutePreferenceStillReachesThreeSkillsAcrossWeek() {
        var result=apply(5);
        assertEquals(35,result.scheduledMinutes().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(Set.of("skill1","skill2","skill3"),result.scheduledMinutes().keySet());
    }
    @Test void completedTasksAreNotRewrittenAndRoutineSlotIsReplaced() {
        UUID id=UUID.randomUUID();
        var session=new DailyCoachSessionEntity(id,user,LocalDate.of(2026,11,17),15,DailyCoachSessionStatus.IN_PROGRESS,false,false,1,2,"synthetic",DailyCoachMode.SOCRATIC_COACH_MODE,now);
        sessionRows.put(id,session);
        var complete=new DailyCoachTaskEntity(UUID.randomUUID(),id,1,DailyCoachTaskType.EXAM_SYNTHESIS,"old-skill",null,"old-target",5,"old", "",DailyCoachTaskStatus.COMPLETED);
        var pending=new DailyCoachTaskEntity(UUID.randomUUID(),id,2,DailyCoachTaskType.EXAM_SYNTHESIS,"old-skill",null,"old-target",5,"old", "",DailyCoachTaskStatus.PENDING);
        taskRows.put(complete.getId(),complete);taskRows.put(pending.getId(),pending);
        apply(45);
        assertSame(complete,taskRows.get(complete.getId()));assertEquals("old-target",complete.getAssignedTwinId());
        assertTrue(taskRows.get(pending.getId()).getAssignedTwinId().startsWith("microlesson:"));
        assertTrue(taskRows.values().stream().filter(t->t.getSessionId().equals(id)).mapToInt(DailyCoachTaskEntity::getEstimatedMinutes).sum()<=15);
    }
    @Test void completedDailySessionsArePreserved() {
        UUID id=UUID.randomUUID();
        var completed=new DailyCoachSessionEntity(id,user,LocalDate.of(2026,11,17),15,DailyCoachSessionStatus.COMPLETED,false,false,1,1,"completed",DailyCoachMode.SOCRATIC_COACH_MODE,now);
        sessionRows.put(id,completed);apply(45);
        assertEquals("completed",sessionRows.get(id).getBriefingText());assertEquals(0,taskRows.values().stream().filter(t->t.getSessionId().equals(id)).count());
    }
}
