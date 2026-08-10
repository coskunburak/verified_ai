package com.verifiedai.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPasswordCredentialJpaRepository extends JpaRepository<UserPasswordCredentialJpaEntity, UUID> {
    Optional<UserPasswordCredentialJpaEntity> findByEmailNormalized(String emailNormalized);
}
