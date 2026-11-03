package com.verifiedai.attempt.infrastructure.privacy;
import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import com.verifiedai.attempt.infrastructure.persistence.StudentAttemptRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
/** Account privacy holds the same account lock used for prepare and publish. No assets are owned here. */
@Component
public class StudentAttemptPrivacyLifecycleContributor implements AccountDataLifecycleContributor {
    private final StudentAttemptRepository store;
    public StudentAttemptPrivacyLifecycleContributor(StudentAttemptRepository store) { this.store=store; }
    public String category() { return "student-attempts"; }
    public Map<String,Object> exportUserData(UUID owner) {
        return Map.of("schemaVersion","attempt-export-v1","trainingEligible",false,"attempts",store.available()?store.export(owner):List.of(),"learning",store.available()?store.learningExport(owner):Map.of());
    }
    public void deleteUserData(UUID owner,Instant now) { if(store.available()) store.delete(owner); }
}
