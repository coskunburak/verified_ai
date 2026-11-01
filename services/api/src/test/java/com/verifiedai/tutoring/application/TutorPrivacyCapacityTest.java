package com.verifiedai.tutoring.application;

import com.verifiedai.tutoring.infrastructure.persistence.TutorStore;
import com.verifiedai.sharedkernel.error.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TutorPrivacyCapacityTest {
    @Test void oversizedExportFailsBeforeLoadingAnyTranscriptAndNeverTruncates() {
        var store=mock(TutorStore.class); var owner=UUID.randomUUID();
        when(store.exportBytes(owner)).thenReturn(8_000_001L);
        assertThatThrownBy(()->new TutorSessionLifecycleContributor(store).exportUserData(owner))
            .isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.TUTOR_EXPORT_CAPACITY_EXCEEDED));
        verify(store,never()).exportSessions(owner);
    }
}
