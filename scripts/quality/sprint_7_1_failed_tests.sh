#!/usr/bin/env bash
# Owner-only focused repair check; does not constitute full sprint acceptance.
set -Eeuo pipefail
repair_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repair_root"
repair_java="${JAVA21_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
if [[ ! -x "$repair_java/bin/java" ]]; then
    printf 'Java 21 unavailable. Set JAVA21_HOME to the installed JDK 21 directory.\n' >&2
    exit 2
fi
mkdir -p .generated/sprint-7-1-repair
repair_out="$(mktemp -d "$repair_root/.generated/sprint-7-1-repair/$(date +%Y%m%d-%H%M%S)-XXXXXX")"
printf '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>\n' > "$repair_out/maven-settings.xml"
printf 'Running only the two previously failing backend tests. Reports: %s\n' "$repair_out"
repair_code=0
(
    cd services/api
    env -i PATH="$repair_java/bin:$PATH" JAVA_HOME="$repair_java" TMPDIR="${TMPDIR:-/tmp}" \
        APP_ENV=test AI_GEMINI_ENABLED=false SOLVING_PRIMARY_ENABLED=false SOLVING_SECONDARY_ENABLED=false \
        AI_VISION_RECOGNITION_PRIMARY_PROVIDER=LOCAL_FIXTURE AI_PROBLEM_PARSER_PRIMARY_PROVIDER=LOCAL_FIXTURE \
        AI_PROBLEM_CLASSIFIER_PRIMARY_PROVIDER=LOCAL_FIXTURE ATTEMPT_ANALYSIS_ENABLED=false ATTEMPT_VISION_ENABLED=false \
        ATTEMPT_PRODUCTION_AUTHORIZED=false MATH_VERIFIER_ATTEMPT_ENABLED=false \
        MATH_VERIFIER_ATTEMPT_PRODUCTION_AUTHORIZED=false MATH_VERIFIER_VISUALIZATION_ENABLED=false \
        MATH_VERIFIER_VISUALIZATION_PRODUCTION_AUTHORIZED=false MATH_VERIFIER_INTERNAL_TOKEN=local_math_verifier_token_change_me \
        mvn -s "$repair_out/maven-settings.xml" -gs "$repair_out/maven-settings.xml" \
        -Dspring.profiles.active=test -Dsurefire.failIfNoSpecifiedTests=true \
        -Dsurefire.reportsDirectory="$repair_out/reports" \
        '-Dtest=StudentAttemptIntegrationTest#finalAnswerConcurrentReplayStaleAndDisabledHistory+deletedAccountCannotBeRecreatedByLateAssessment' test
) 2>&1 | tee "$repair_out/backend-tests.log" || repair_code=$?
printf 'Maven exit code: %s. Focused repair only; Gates C/E/F and full acceptance were not run.\n' "$repair_code"
exit "$repair_code"
