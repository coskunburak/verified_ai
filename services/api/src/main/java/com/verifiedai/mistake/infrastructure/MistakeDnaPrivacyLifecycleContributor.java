package com.verifiedai.mistake.infrastructure;

import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

/** Operations follow the immutable work's deletion cascade, projections are independently purgeable. */
@Component
public class MistakeDnaPrivacyLifecycleContributor implements AccountDataLifecycleContributor {
    private final MistakeDnaRepository store;
    public MistakeDnaPrivacyLifecycleContributor(MistakeDnaRepository store) { this.store=store; }
    public String category() { return "mistake-dna"; }
    public Map<String,Object> exportUserData(UUID owner) { return store.available()?store.export(owner):Map.of(); }
    public void deleteUserData(UUID owner,Instant now) { if(store.available()) store.deleteProfiles(owner); }
}
