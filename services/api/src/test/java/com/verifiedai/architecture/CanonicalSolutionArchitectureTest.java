package com.verifiedai.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class CanonicalSolutionArchitectureTest {
    @Test void canonicalizationHasNoInferenceOrPresentationDependency() {
        var classes=new ClassFileImporter().importPackages("com.verifiedai");
        noClasses().that().resideInAnyPackage("..solving.application.canonical..","..solving.domain.model.canonical..")
            .should().dependOnClassesThat().resideInAnyPackage("..ai..","..solving.api..","..verification..")
            .check(classes);
        noClasses().that().resideInAPackage("..solving.domain.model.canonical..")
            .should().dependOnClassesThat().resideInAnyPackage("com.fasterxml.jackson..","tools.jackson..","org.springframework.jdbc..","..infrastructure..")
            .check(classes);
        noClasses().that().resideInAPackage("..solving.application.canonical..")
            .should().dependOnClassesThat().resideInAPackage("..problem.infrastructure..")
            .check(classes);
    }
}
