package com.verifiedai.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.verifiedai.VerifiedAiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {

    @Test
    void verifiesSpringModulithBoundaries() {
        ApplicationModules.of(VerifiedAiApplication.class).verify();
    }

    @Test
    void downstreamModulesOnlyUseBillingApplicationPublicSpi() {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.verifiedai");

        noClasses()
            .that()
            .resideInAnyPackage(
                "com.verifiedai.attempt..",
                "com.verifiedai.mistake..",
                "com.verifiedai.mastery..",
                "com.verifiedai.studyplan..",
                "com.verifiedai.exam..",
                "com.verifiedai.tutoring.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.verifiedai.billing.infrastructure..",
                "com.verifiedai.billing.domain..",
                "com.verifiedai.billing.api.."
            )
            .check(classes);

        noClasses()
            .that()
            .resideInAnyPackage("com.verifiedai.billing..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.verifiedai.attempt..",
                "com.verifiedai.mistake..",
                "com.verifiedai.mastery..",
                "com.verifiedai.studyplan..",
                "com.verifiedai.exam..",
                "com.verifiedai.tutoring.."
            )
            .check(classes);
    }

    @Test
    void downstreamModulesOnlyUseNotificationApplicationPublicSpi() {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.verifiedai");

        noClasses()
            .that()
            .resideInAnyPackage(
                "com.verifiedai.billing..",
                "com.verifiedai.attempt..",
                "com.verifiedai.mistake..",
                "com.verifiedai.mastery..",
                "com.verifiedai.studyplan..",
                "com.verifiedai.exam..",
                "com.verifiedai.tutoring..",
                "com.verifiedai.identity.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.verifiedai.notification.infrastructure..",
                "com.verifiedai.notification.domain..",
                "com.verifiedai.notification.api.."
            )
            .check(classes);

        noClasses()
            .that()
            .resideInAnyPackage("com.verifiedai.notification..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.verifiedai.billing..",
                "com.verifiedai.attempt..",
                "com.verifiedai.mistake..",
                "com.verifiedai.mastery..",
                "com.verifiedai.studyplan..",
                "com.verifiedai.exam..",
                "com.verifiedai.tutoring..",
                "com.verifiedai.identity.."
            )
            .check(classes);
    }

    @Test
    void downstreamModulesOnlyUseAnalyticsApplicationPublicSpi() {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.verifiedai");

        noClasses()
            .that()
            .resideInAnyPackage(
                "com.verifiedai.billing..",
                "com.verifiedai.attempt..",
                "com.verifiedai.mistake..",
                "com.verifiedai.mastery..",
                "com.verifiedai.studyplan..",
                "com.verifiedai.exam..",
                "com.verifiedai.tutoring..",
                "com.verifiedai.notification..",
                "com.verifiedai.identity.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.verifiedai.analytics.domain..",
                "com.verifiedai.analytics.infrastructure..",
                "com.verifiedai.analytics.api.."
            )
            .check(classes);

        noClasses()
            .that()
            .resideInAnyPackage("com.verifiedai.analytics..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.verifiedai.billing..",
                "com.verifiedai.attempt..",
                "com.verifiedai.mistake..",
                "com.verifiedai.mastery..",
                "com.verifiedai.studyplan..",
                "com.verifiedai.exam..",
                "com.verifiedai.tutoring..",
                "com.verifiedai.notification..",
                "com.verifiedai.identity.."
            )
            .check(classes);
    }

    @Test
    void downstreamModulesOnlyUseFeatureFlagsApplicationPublicSpi() {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.verifiedai");

        noClasses()
            .that()
            .resideInAnyPackage(
                "com.verifiedai.billing..",
                "com.verifiedai.attempt..",
                "com.verifiedai.mistake..",
                "com.verifiedai.mastery..",
                "com.verifiedai.studyplan..",
                "com.verifiedai.exam..",
                "com.verifiedai.tutoring..",
                "com.verifiedai.notification..",
                "com.verifiedai.identity..",
                "com.verifiedai.analytics..",
                "com.verifiedai.ai..",
                "com.verifiedai.solving..",
                "com.verifiedai.verification..",
                "com.verifiedai.problem.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.verifiedai.featureflags.domain..",
                "com.verifiedai.featureflags.infrastructure..",
                "com.verifiedai.featureflags.api.."
            )
            .check(classes);

        noClasses()
            .that()
            .resideInAnyPackage("com.verifiedai.featureflags..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.verifiedai.billing..",
                "com.verifiedai.attempt..",
                "com.verifiedai.mistake..",
                "com.verifiedai.mastery..",
                "com.verifiedai.studyplan..",
                "com.verifiedai.exam..",
                "com.verifiedai.tutoring..",
                "com.verifiedai.notification..",
                "com.verifiedai.identity..",
                "com.verifiedai.analytics..",
                "com.verifiedai.ai..",
                "com.verifiedai.solving..",
                "com.verifiedai.verification..",
                "com.verifiedai.problem.."
            )
            .check(classes);
    }

    @Test
    void adminModuleMaintainsAcyclicPublicSpiBoundaries() {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.verifiedai");

        noClasses()
            .that()
            .resideInAnyPackage("com.verifiedai.admin..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.verifiedai.billing.domain..",
                "com.verifiedai.billing.infrastructure..",
                "com.verifiedai.billing.api..",
                "com.verifiedai.featureflags.domain..",
                "com.verifiedai.featureflags.infrastructure..",
                "com.verifiedai.featureflags.api..",
                "com.verifiedai.attempt..",
                "com.verifiedai.mistake..",
                "com.verifiedai.mastery..",
                "com.verifiedai.studyplan..",
                "com.verifiedai.exam..",
                "com.verifiedai.tutoring..",
                "com.verifiedai.notification..",
                "com.verifiedai.identity..",
                "com.verifiedai.analytics..",
                "com.verifiedai.ai..",
                "com.verifiedai.solving..",
                "com.verifiedai.verification..",
                "com.verifiedai.problem.."
            )
            .check(classes);

        noClasses()
            .that()
            .resideInAnyPackage(
                "com.verifiedai.billing..",
                "com.verifiedai.featureflags..",
                "com.verifiedai.attempt..",
                "com.verifiedai.mistake..",
                "com.verifiedai.mastery..",
                "com.verifiedai.studyplan..",
                "com.verifiedai.exam..",
                "com.verifiedai.tutoring..",
                "com.verifiedai.notification..",
                "com.verifiedai.identity..",
                "com.verifiedai.analytics..",
                "com.verifiedai.ai..",
                "com.verifiedai.solving..",
                "com.verifiedai.verification..",
                "com.verifiedai.problem.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.verifiedai.admin..")
            .check(classes);
    }

    @Test
    void growthModuleMaintainsAcyclicPublicSpiBoundaries() {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.verifiedai");

        noClasses()
            .that()
            .resideInAnyPackage("com.verifiedai.growth..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.verifiedai.billing.domain..",
                "com.verifiedai.billing.infrastructure..",
                "com.verifiedai.billing.api..",
                "com.verifiedai.featureflags.domain..",
                "com.verifiedai.featureflags.infrastructure..",
                "com.verifiedai.featureflags.api..",
                "com.verifiedai.attempt..",
                "com.verifiedai.mistake..",
                "com.verifiedai.mastery..",
                "com.verifiedai.studyplan..",
                "com.verifiedai.exam..",
                "com.verifiedai.tutoring..",
                "com.verifiedai.notification..",
                "com.verifiedai.identity..",
                "com.verifiedai.analytics..",
                "com.verifiedai.admin..",
                "com.verifiedai.ai..",
                "com.verifiedai.solving..",
                "com.verifiedai.verification..",
                "com.verifiedai.problem.."
            )
            .check(classes);

        noClasses()
            .that()
            .resideInAnyPackage(
                "com.verifiedai.billing..",
                "com.verifiedai.featureflags..",
                "com.verifiedai.attempt..",
                "com.verifiedai.mistake..",
                "com.verifiedai.mastery..",
                "com.verifiedai.studyplan..",
                "com.verifiedai.exam..",
                "com.verifiedai.tutoring..",
                "com.verifiedai.notification..",
                "com.verifiedai.identity..",
                "com.verifiedai.analytics..",
                "com.verifiedai.admin..",
                "com.verifiedai.ai..",
                "com.verifiedai.solving..",
                "com.verifiedai.verification..",
                "com.verifiedai.problem.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.verifiedai.growth.domain..",
                "com.verifiedai.growth.infrastructure..",
                "com.verifiedai.growth.api.."
            )
            .check(classes);
    }
}
