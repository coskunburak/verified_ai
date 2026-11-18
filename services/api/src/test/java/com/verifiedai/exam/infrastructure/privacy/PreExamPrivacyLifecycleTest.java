package com.verifiedai.exam.infrastructure.privacy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.verifiedai.exam.application.ExamRepository;
import com.verifiedai.exam.infrastructure.persistence.PreExamStudySessionRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PreExamPrivacyLifecycleTest {
    @Test void includesSnapshotsAndEventsAndUsesInjectedClockForPurge() {
        UUID user = UUID.fromString("98000000-0000-4000-8000-000000000001");
        Instant now = Instant.parse("2026-11-18T12:00:00Z");
        var exams = mock(ExamRepository.class);
        var snapshots = mock(PreExamStudySessionRepository.class);
        var contributor = new ExamPrivacyLifecycleContributor(exams, null, null, null,
                Clock.fixed(now, ZoneOffset.UTC));
        contributor.setPreExamStudySessionRepository(snapshots);
        when(exams.findTargetsByUserId(user)).thenReturn(List.of());
        var exported = List.<Map<String, Object>>of(Map.of("synthetic", true));
        when(snapshots.exportByUserId(user)).thenReturn(exported);
        when(snapshots.exportEventsByUserId(user)).thenReturn(List.of());
        assertEquals(exported, contributor.exportUserData(user).get("preExamStudySessions"));
        contributor.deleteUserData(user, now);
        var ordered = inOrder(snapshots, exams);
        ordered.verify(snapshots).deleteByUserId(user);
        ordered.verify(exams).deleteTargetsByUserId(user);
        contributor.purgeExpiredPreExamSnapshots();
        verify(snapshots).purgeExpired(now);
    }
}
