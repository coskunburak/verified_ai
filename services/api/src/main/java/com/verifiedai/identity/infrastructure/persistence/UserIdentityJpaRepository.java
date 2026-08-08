package com.verifiedai.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityJpaRepository extends JpaRepository<UserIdentityJpaEntity, UUID> {
    Optional<UserIdentityJpaEntity> findByProviderAndProviderSubject(String provider, String providerSubject);
}
