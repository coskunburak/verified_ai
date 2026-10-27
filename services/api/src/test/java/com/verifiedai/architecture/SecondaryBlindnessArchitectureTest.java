package com.verifiedai.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class SecondaryBlindnessArchitectureTest {
    @Test void blindBuilderCannotReadPrimaryArtifactsOrRepositories() {
        noClasses().that().haveFullyQualifiedName("com.verifiedai.solving.application.secondary.BlindSecondaryRequestBuilder")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure.persistence..","..api..")
            .check(new ClassFileImporter().importPackages("com.verifiedai"));
        noClasses().that().haveFullyQualifiedName("com.verifiedai.solving.application.secondary.BlindSecondaryRequestBuilder")
            .should().dependOnClassesThat().haveSimpleName("SolutionCandidate")
            .check(new ClassFileImporter().importPackages("com.verifiedai"));
    }
}
