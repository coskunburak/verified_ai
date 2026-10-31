package com.verifiedai.architecture;

import com.tngtech.archunit.core.importer.*;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class VerificationArbitrationArchitectureTest {
    @Test void policyAndReadProjectionCannotDispatchInferenceReserveUsageOrReachVerifier() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("com.verifiedai");
        noClasses().that().haveSimpleName("VerificationArbitrationService").or().haveSimpleName("VerificationPolicy")
            .or().haveSimpleName("CalculusCompositePolicy").or().haveSimpleName("CompositeEvidencePolicy")
            .or().haveSimpleName("VerificationDecisionController").or().haveSimpleName("SecondaryCorroborationQuery")
            .should().dependOnClassesThat().resideInAnyPackage("..ai..", "..billing..", "..verification.infrastructure.http..")
            .check(classes);
        noClasses().that().resideInAPackage("..verification.api..")
            .should().dependOnClassesThat().resideInAnyPackage("..verification.domain.policy..", "..verification.infrastructure..")
            .check(classes);
        noClasses().that().resideInAPackage("..solving..")
            .should().dependOnClassesThat().resideInAPackage("..verification..").check(classes);
    }
}
