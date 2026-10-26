package com.verifiedai.solving.infrastructure.privacy;

import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import com.verifiedai.solving.infrastructure.persistence.SolveStore;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SolvingLifecycleContributor implements AccountDataLifecycleContributor {
    private final SolveStore store;
    public SolvingLifecycleContributor(SolveStore store) { this.store=store; }
    public String category() { return "solutionCandidates"; }
    public Map<String,Object> exportUserData(UUID user) { return Map.of("jobs",store.export(user)); }
    public void deleteUserData(UUID user,Instant now) { store.delete(user); }
}
