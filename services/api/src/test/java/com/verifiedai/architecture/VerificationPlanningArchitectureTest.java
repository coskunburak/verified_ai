package com.verifiedai.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class VerificationPlanningArchitectureTest {
    @Test void planningCannotBypassOwnershipInvokeProvidersOrPersistEvidence() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.verifiedai");
        noClasses().that().resideInAnyPackage("..verification.domain..", "..verification.application..")
            .should().dependOnClassesThat().resideInAnyPackage("..ai..", "..problem.infrastructure..",
                "..solving.infrastructure..", "..solving.domain.repository..", "..solving.api..",
                "org.springframework.jdbc..", "jakarta.persistence..", "java.net..", "java.net.http..", "..verification.infrastructure..")
            .check(classes);
        noClasses().that().resideInAPackage("..verification.infrastructure.http..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "jakarta.persistence..",
                "..verification.infrastructure.persistence..")
            .check(classes);
        noClasses().that().resideInAPackage("..verification.infrastructure.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage("java.net..", "java.net.http..", "..verification.infrastructure.http..")
            .check(classes);
        noClasses().that().resideInAPackage("..verification..")
            .should().dependOnClassesThat().resideInAnyPackage("..ai..", "..problem.infrastructure..",
                "..solving.infrastructure..", "..solving.domain.repository..", "..solving.api..")
            .check(classes);
        noClasses().that().resideInAnyPackage("..verification.domain.model..", "..verification.domain.policy..")
            .should().dependOnClassesThat().resideInAnyPackage("com.fasterxml.jackson..", "tools.jackson..",
                "..verification.application..", "..problem..", "..solving.application..")
            .check(classes);
    }
}
