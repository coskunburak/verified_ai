FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY services/api/pom.xml services/api/pom.xml
COPY packages/schemas packages/schemas
WORKDIR /workspace/services/api
RUN mvn -q -DskipTests dependency:go-offline
COPY services/api/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && addgroup --system verifiedai \
    && adduser --system --ingroup verifiedai verifiedai
COPY --from=build /workspace/services/api/target/verified-ai-api-*.jar /app/verified-ai-api.jar
USER verifiedai
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/verified-ai-api.jar"]
