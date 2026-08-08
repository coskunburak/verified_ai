package com.verifiedai.identity.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSecurityEventJpaRepository extends JpaRepository<AuthSecurityEventJpaEntity, UUID> {
}
