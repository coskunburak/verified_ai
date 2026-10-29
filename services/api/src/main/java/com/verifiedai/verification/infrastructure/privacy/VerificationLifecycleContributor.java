package com.verifiedai.verification.infrastructure.privacy;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import com.verifiedai.verification.domain.repository.VerificationRunRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class VerificationLifecycleContributor implements AccountDataLifecycleContributor {
    private static final ObjectMapper EXPORT_MAPPER = exportMapper();
    private static final TypeReference<List<Map<String, Object>>> EXPORTED_RUNS = new TypeReference<>() { };
    private final VerificationRunRepository repository;
    public VerificationLifecycleContributor(VerificationRunRepository repository) { this.repository = repository; }
    public String category() { return "verificationEvidence"; }
    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(UUID user) {
        // The shared account exporter accepts JSON values; keep time handling inside this module's boundary.
        return Map.of("runs", EXPORT_MAPPER.convertValue(repository.export(user), EXPORTED_RUNS));
    }
    private static ObjectMapper exportMapper() {
        var time = new SimpleModule();
        time.addSerializer(Instant.class, new JsonSerializer<Instant>() {
            public void serialize(Instant value, JsonGenerator generator, SerializerProvider provider) throws IOException {
                generator.writeString(value.toString());
            }
        });
        return new ObjectMapper().registerModule(time);
    }
    public void deleteUserData(UUID user, Instant now) {
        // Owned canonical solution/job/account deletion cascades through the composite FK.
        // Direct deletion while a source exists is forbidden by the immutable-evidence trigger.
    }
}
