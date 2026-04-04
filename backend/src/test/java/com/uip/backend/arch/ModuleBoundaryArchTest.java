package com.uip.backend.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Module Boundary Architecture Tests
 *
 * Mỗi module (environment, esg, alert, notification, traffic) phải độc lập:
 *   - KHÔNG import domain/service/repository từ module khác
 *   - Giao tiếp cross-module ONLY qua: Kafka event, Port Interface, hoặc gRPC
 *
 * Khi test fail → có coupling ngầm cần fix trước khi merge.
 * Tham khảo: docs/architecture/modular-architecture-evaluation.md
 */
@DisplayName("Module Boundary Rules")
class ModuleBoundaryArchTest {

    private static final String BASE = "com.uip.backend";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // environment module
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("environment must not depend on esg")
    void environment_mustNotDependOn_esg() {
        noClasses().that().resideInAPackage(BASE + ".environment..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg..")
                .check(classes);
    }

    @Test
    @DisplayName("environment must not depend on alert")
    void environment_mustNotDependOn_alert() {
        noClasses().that().resideInAPackage(BASE + ".environment..")
                .should().accessClassesThat().resideInAPackage(BASE + ".alert..")
                .check(classes);
    }

    @Test
    @DisplayName("environment must not depend on notification")
    void environment_mustNotDependOn_notification() {
        noClasses().that().resideInAPackage(BASE + ".environment..")
                .should().accessClassesThat().resideInAPackage(BASE + ".notification..")
                .check(classes);
    }

    @Test
    @DisplayName("environment must not depend on traffic")
    void environment_mustNotDependOn_traffic() {
        noClasses().that().resideInAPackage(BASE + ".environment..")
                .should().accessClassesThat().resideInAPackage(BASE + ".traffic..")
                .check(classes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // esg module
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("esg must not depend on environment")
    void esg_mustNotDependOn_environment() {
        noClasses().that().resideInAPackage(BASE + ".esg..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment..")
                .check(classes);
    }

    @Test
    @DisplayName("esg must not depend on alert")
    void esg_mustNotDependOn_alert() {
        noClasses().that().resideInAPackage(BASE + ".esg..")
                .should().accessClassesThat().resideInAPackage(BASE + ".alert..")
                .check(classes);
    }

    @Test
    @DisplayName("esg must not depend on notification")
    void esg_mustNotDependOn_notification() {
        noClasses().that().resideInAPackage(BASE + ".esg..")
                .should().accessClassesThat().resideInAPackage(BASE + ".notification..")
                .check(classes);
    }

    @Test
    @DisplayName("esg must not depend on traffic")
    void esg_mustNotDependOn_traffic() {
        noClasses().that().resideInAPackage(BASE + ".esg..")
                .should().accessClassesThat().resideInAPackage(BASE + ".traffic..")
                .check(classes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // alert module
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("alert must not depend on environment internals (domain/service/repository)")
    void alert_mustNotDependOn_environment_internals() {
        // alert có thể nhận value primitives từ environment qua service call,
        // nhưng KHÔNG được import domain/repository của environment
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".alert..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("alert must not depend on esg")
    void alert_mustNotDependOn_esg() {
        noClasses().that().resideInAPackage(BASE + ".alert..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg..")
                .check(classes);
    }

    @Test
    @DisplayName("alert must not depend on notification internals")
    void alert_mustNotDependOn_notification() {
        noClasses().that().resideInAPackage(BASE + ".alert..")
                .should().accessClassesThat().resideInAPackage(BASE + ".notification..")
                .check(classes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // notification module
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("notification must not depend on alert domain or repository")
    void notification_mustNotDependOn_alert_internals() {
        // notification chỉ được biết alert event schema qua Kafka payload (Map<String,Object>)
        // KHÔNG được import alert.domain.* hoặc alert.repository.*
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".notification..")
                .should().accessClassesThat().resideInAPackage(BASE + ".alert.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".alert.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".alert.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("notification must not depend on environment")
    void notification_mustNotDependOn_environment() {
        noClasses().that().resideInAPackage(BASE + ".notification..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment..")
                .check(classes);
    }

    @Test
    @DisplayName("notification must not depend on esg")
    void notification_mustNotDependOn_esg() {
        noClasses().that().resideInAPackage(BASE + ".notification..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg..")
                .check(classes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // traffic module
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("traffic must not depend on environment")
    void traffic_mustNotDependOn_environment() {
        noClasses().that().resideInAPackage(BASE + ".traffic..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment..")
                .check(classes);
    }

    @Test
    @DisplayName("traffic must not depend on esg")
    void traffic_mustNotDependOn_esg() {
        noClasses().that().resideInAPackage(BASE + ".traffic..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg..")
                .check(classes);
    }

    @Test
    @DisplayName("traffic must not depend on alert")
    void traffic_mustNotDependOn_alert() {
        noClasses().that().resideInAPackage(BASE + ".traffic..")
                .should().accessClassesThat().resideInAPackage(BASE + ".alert..")
                .check(classes);
    }

    @Test
    @DisplayName("traffic must not depend on notification")
    void traffic_mustNotDependOn_notification() {
        noClasses().that().resideInAPackage(BASE + ".traffic..")
                .should().accessClassesThat().resideInAPackage(BASE + ".notification..")
                .check(classes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Repository isolation — repository chỉ được inject trong cùng module
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("alert repository must only be accessed within alert module")
    void alertRepository_mustOnlyBeAccessedWithin_alertModule() {
        noClasses().that().resideOutsideOfPackage(BASE + ".alert..")
                .should().accessClassesThat().resideInAPackage(BASE + ".alert.repository..")
                .check(classes);
    }

    @Test
    @DisplayName("environment repository must only be accessed within environment module")
    void environmentRepository_mustOnlyBeAccessedWithin_environmentModule() {
        noClasses().that().resideOutsideOfPackage(BASE + ".environment..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .check(classes);
    }

    @Test
    @DisplayName("esg repository must only be accessed within esg module")
    void esgRepository_mustOnlyBeAccessedWithin_esgModule() {
        noClasses().that().resideOutsideOfPackage(BASE + ".esg..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .check(classes);
    }
}
