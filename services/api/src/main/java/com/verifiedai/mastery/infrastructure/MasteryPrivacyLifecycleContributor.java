package com.verifiedai.mastery.infrastructure;

import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MasteryPrivacyLifecycleContributor implements AccountDataLifecycleContributor {
    private final MasteryRepository store;
    public MasteryPrivacyLifecycleContributor(MasteryRepository store) { this.store=store; }
    public String category() { return "mastery"; }
    public Map<String,Object> exportUserData(UUID owner) { return store.available()?store.export(owner):Map.of(); }
    public void deleteUserData(UUID owner,Instant now) { if(store.available()) store.delete(owner); }
}
