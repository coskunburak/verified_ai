# Verified AI API

Spring Boot modular monolith for the Verified AI Learning Platform.

## Requirements

- Java 21
- Maven 3.9+
- PostgreSQL for local runtime
- Docker for Testcontainers integration tests

## Local Commands

```sh
mvn test
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The API is the product authority for identity, entitlement, learning state, verification policy, billing, and durable orchestration. It does not expose AI provider secrets or the internal math verifier to the mobile client.

