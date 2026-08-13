package com.verifiedai.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class AiProviderBoundaryTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes =
            new ClassFileImporter()
                .importPackages(
                    "com.verifiedai"
                );
    }

    @Test
    void productModulesMustNotDependOnAiInfrastructure() {
        noClasses()
            .that()
            .resideInAnyPackage(
                "com.verifiedai.problem..",
                "com.verifiedai.solving..",
                "com.verifiedai.verification..",
                "com.verifiedai.tutoring..",
                "com.verifiedai.mistake..",
                "com.verifiedai.mastery..",
                "com.verifiedai.exam.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.verifiedai.ai.infrastructure.."
            )
            .check(classes);
    }

    @Test
    void aiMustNotReachIntoProblemPersistence() {
        noClasses()
            .that()
            .resideInAnyPackage(
                "com.verifiedai.ai.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.verifiedai.problem.infrastructure.persistence.."
            )
            .check(classes);
    }

    @Test
    void providerSdksMustNotLeakOutsideAiInfrastructure() {
        noClasses()
            .that()
            .resideOutsideOfPackage(
                "com.verifiedai.ai.infrastructure.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.openai..",
                "com.google.genai..",
                "com.google.ai..",
                "com.azure.ai.openai..",
                "software.amazon.awssdk.services.bedrockruntime..",
                "dev.langchain4j.model.openai..",
                "dev.langchain4j.model.googleai.."
            )
            .check(classes);
    }
}
