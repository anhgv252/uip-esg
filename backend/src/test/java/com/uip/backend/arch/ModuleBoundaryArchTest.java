package com.uip.backend.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Module Boundary Architecture Tests (MVP5 Sprint M5-1 Task T13)
 *
 * Mỗi bounded context phải độc lập:
 *   - KHÔNG access domain/service/repository internals từ module khác
 *   - Giao tiếp cross-module ONLY qua: Kafka event (DTO/value object), Port Interface,
 *     hoặc gRPC stub
 *
 * Quy ước rule (rất quan trọng — tránh false positive):
 *   - Dùng {@code accessClassesThat().resideInAPackage("..module..")} — đây là
 *     bytecode access thật, không phải import text. Event DTO hợp lệ được phép.
 *   - Chỉ cấm toàn bộ package {@code ..module..} khi module đó thực sự isolated
 *     (không cần consume event/DTO của module kia).
 *   - Cấm {@code ..module.domain..} / {@code ..module.repository..} /
 *     {@code ..module.service..} khi module chỉ được giao tiếp qua event DTO —
 *     đây mới là coupling cấm.
 *
 * Khi test fail → có coupling ngầm. CÁCH XỬ LÝ (theo ADR-052, ưu tiên từ trên xuống):
 *
 *   1. [ƯU TIÊN] Extract Hexagonal Port (ADR-052): tạo Port interface ở
 *      {@code common.spi.<Port>}, Adapter implement ở provider module
 *      ({@code <provider>.adapter.<Adapter>}), consumer inject Port.
 *      Đây là cách fix CHUẨN — KHÔNG relax rule.
 *   2. Nếu là documented exception (ADR cũ) → thêm exception package vào rule.
 *   3. Nếu là coupling ngầm thật chưa fix được → defer rule, ghi tech-debt register
 *      cho SA follow-up (pattern D1/D2/D3 trong project_mvp5_archtest_deferred_coupling).
 *
 *   ⚠️ KHÔNG BAO GIỜ relax rule (thêm @ArchIgnore) chỉ để "fix nhanh" một vi phạm —
 *   rule là contract kiến trúc. Relax = chấp nhận debt vĩnh viễn (bài học BUG-M5-009).
 *
 * Tham khảo:
 *   - **ADR-052** (docs/mvp5/adr/): Hexagonal Port cho cross-module dependency — QUY CHUẨN
 *   - docs/architecture/modular-architecture-evaluation.md
 *   - docs/mvp5/reports/mvp5-sprint1-archtest-coverage.md
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
    @DisplayName("alert repository must only be accessed within alert module (exception: ai feedback loop)")
    void alertRepository_mustOnlyBeAccessedWithin_alertModule() {
        // ADR-046: Incident Feedback Loop (ai.feedback) reads 30-day alert feedback data
        // to generate trigger suggestions. This is a documented cross-module read port —
        // ai.feedback reads alert feedback state but never writes alert domain objects.
        // Same exception pattern as forecast.. → esg.repository (ADR-032 D6).
        noClasses().that()
                .resideOutsideOfPackage(BASE + ".alert..")
                .and().resideOutsideOfPackage(BASE + ".ai.feedback..")  // ADR-046 feedback loop
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
                .and().resideOutsideOfPackage(BASE + ".forecast..")  // ADR-032 D6: NaiveForecastAdapter
                .should().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .check(classes);
    }

    @Test
    @DisplayName("traffic repository must only be accessed within traffic module")
    void trafficRepository_mustOnlyBeAccessedWithin_trafficModule() {
        noClasses().that().resideOutsideOfPackage(BASE + ".traffic..")
                .should().accessClassesThat().resideInAPackage(BASE + ".traffic.repository..")
                .check(classes);
    }

    @Test
    @DisplayName("aiworkflow repository must only be accessed within aiworkflow module")
    void aiworkflowRepository_mustOnlyBeAccessedWithin_aiworkflowModule() {
        noClasses().that().resideOutsideOfPackage(BASE + ".aiworkflow..")
                .should().accessClassesThat().resideInAPackage(BASE + ".aiworkflow.repository..")
                .check(classes);
    }

    @Test
    @DisplayName("admin repository must only be accessed within admin module")
    void adminRepository_mustOnlyBeAccessedWithin_adminModule() {
        noClasses().that().resideOutsideOfPackage(BASE + ".admin..")
                .should().accessClassesThat().resideInAPackage(BASE + ".admin.repository..")
                .check(classes);
    }

    // NOTE: auth.repository (AppUserRepository) isolation rule DEFERRED —
    // AppUserRepository is intentionally shared identity infrastructure accessed by
    // admin (AdminController user management), citizen (CitizenController.register),
    // notification (PushSubscriptionController user lookup), and tenant
    // (InviteService, TenantAdminService). See audit report
    // docs/mvp5/reports/mvp5-sprint1-archtest-coverage.md — SA follow-up needed to
    // decide whether to extract a UserPort/UserIdentityPort interface.

    @Test
    @DisplayName("bms repository must only be accessed within bms module")
    void bmsRepository_mustOnlyBeAccessedWithin_bmsModule() {
        noClasses().that().resideOutsideOfPackage(BASE + ".bms..")
                .should().accessClassesThat().resideInAPackage(BASE + ".bms.repository..")
                .check(classes);
    }

    @Test
    @DisplayName("building repository must only be accessed within building module")
    void buildingRepository_mustOnlyBeAccessedWithin_buildingModule() {
        noClasses().that().resideOutsideOfPackage(BASE + ".building..")
                .should().accessClassesThat().resideInAPackage(BASE + ".building.repository..")
                .check(classes);
    }

    @Test
    @DisplayName("citizen repository must only be accessed within citizen module")
    void citizenRepository_mustOnlyBeAccessedWithin_citizenModule() {
        noClasses().that().resideOutsideOfPackage(BASE + ".citizen..")
                .should().accessClassesThat().resideInAPackage(BASE + ".citizen.repository..")
                .check(classes);
    }

    @Test
    @DisplayName("correlation repository must only be accessed within correlation module")
    void correlationRepository_mustOnlyBeAccessedWithin_correlationModule() {
        noClasses().that().resideOutsideOfPackage(BASE + ".correlation..")
                .should().accessClassesThat().resideInAPackage(BASE + ".correlation.repository..")
                .check(classes);
    }

    // NOTE: tenant.repository isolation rule DEFERRED —
    // TenantConfigRepository and TenantRepository are intentionally shared tenant
    // configuration infrastructure accessed by:
    //   - auth.config.DynamicCorsConfigurationSource (reload CORS per tenant)
    //   - common.ratelimit.TenantRateLimiter (reload tenant RPM limits)
    //   - esg.service.EsgCacheWarmupService (warmup cache across tenants)
    //   - esg.service.EsgService.calculateWaterIntensity (per-tenant config lookup)
    // See audit report docs/mvp5/reports/mvp5-sprint1-archtest-coverage.md — SA follow-up
    // needed to decide whether to extract a TenantConfigPort interface.

    // ─────────────────────────────────────────────────────────────────────────
    // Domain/service isolation giữa các business module độc lập
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("bms must not depend on environment internals (domain/service/repository)")
    void bms_mustNotDependOn_environment_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".bms..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("building must not depend on environment internals (domain/service/repository)")
    void building_mustNotDependOn_environment_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".building..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("citizen must not depend on environment internals (domain/service/repository)")
    void citizen_mustNotDependOn_environment_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".citizen..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("citizen must not depend on esg internals (domain/service/repository)")
    void citizen_mustNotDependOn_esg_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".citizen..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("dashboard must not depend on environment internals (domain/service/repository)")
    void dashboard_mustNotDependOn_environment_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".dashboard..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("dashboard must not depend on esg internals (domain/service/repository)")
    void dashboard_mustNotDependOn_esg_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".dashboard..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("dashboard must not depend on alert internals (domain/service/repository)")
    void dashboard_mustNotDependOn_alert_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".dashboard..")
                .should().accessClassesThat().resideInAPackage(BASE + ".alert.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".alert.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".alert.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("partner must not depend on environment internals (domain/service/repository)")
    void partner_mustNotDependOn_environment_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".partner..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("partner must not depend on esg internals (domain/service/repository)")
    void partner_mustNotDependOn_esg_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".partner..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("safety must not depend on environment internals (domain/service/repository)")
    void safety_mustNotDependOn_environment_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".safety..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("safety must not depend on esg internals (domain/service/repository)")
    void safety_mustNotDependOn_esg_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".safety..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("workflow must not depend on environment internals (domain/service/repository)")
    void workflow_mustNotDependOn_environment_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".workflow..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("forecast must not depend on environment internals (domain/service/repository)")
    void forecast_mustNotDependOn_environment_internals() {
        // forecast được đọc esg.repository (ADR-032 D6, exception đã có ở rule khác),
        // nhưng KHÔNG được access domain/service/repository của environment.
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".forecast..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("forecast must not depend on alert internals (domain/service/repository)")
    void forecast_mustNotDependOn_alert_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".forecast..")
                .should().accessClassesThat().resideInAPackage(BASE + ".alert.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".alert.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".alert.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("forecast must not depend on notification")
    void forecast_mustNotDependOn_notification() {
        noClasses().that().resideInAPackage(BASE + ".forecast..")
                .should().accessClassesThat().resideInAPackage(BASE + ".notification..")
                .check(classes);
    }

    @Test
    @DisplayName("forecast must not depend on traffic")
    void forecast_mustNotDependOn_traffic() {
        noClasses().that().resideInAPackage(BASE + ".forecast..")
                .should().accessClassesThat().resideInAPackage(BASE + ".traffic..")
                .check(classes);
    }

    @Test
    @DisplayName("ai must not depend on environment internals (domain/service/repository)")
    void ai_mustNotDependOn_environment_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".ai..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("ai must not depend on esg internals (domain/service/repository)")
    void ai_mustNotDependOn_esg_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".ai..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("ai must not depend on notification")
    void ai_mustNotDependOn_notification() {
        noClasses().that().resideInAPackage(BASE + ".ai..")
                .should().accessClassesThat().resideInAPackage(BASE + ".notification..")
                .check(classes);
    }

    @Test
    @DisplayName("ai must not depend on traffic")
    void ai_mustNotDependOn_traffic() {
        noClasses().that().resideInAPackage(BASE + ".ai..")
                .should().accessClassesThat().resideInAPackage(BASE + ".traffic..")
                .check(classes);
    }

    @Test
    @DisplayName("correlation must not depend on environment internals (domain/service/repository)")
    void correlation_mustNotDependOn_environment_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".correlation..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("correlation must not depend on esg internals (domain/service/repository)")
    void correlation_mustNotDependOn_esg_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".correlation..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("correlation must not depend on notification")
    void correlation_mustNotDependOn_notification() {
        noClasses().that().resideInAPackage(BASE + ".correlation..")
                .should().accessClassesThat().resideInAPackage(BASE + ".notification..")
                .check(classes);
    }

    @Test
    @DisplayName("correlation must not depend on traffic")
    void correlation_mustNotDependOn_traffic() {
        noClasses().that().resideInAPackage(BASE + ".correlation..")
                .should().accessClassesThat().resideInAPackage(BASE + ".traffic..")
                .check(classes);
    }

    @Test
    @DisplayName("aiworkflow must not depend on environment internals (domain/service/repository)")
    void aiworkflow_mustNotDependOn_environment_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".aiworkflow..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("aiworkflow must not depend on esg internals (domain/service/repository)")
    void aiworkflow_mustNotDependOn_esg_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".aiworkflow..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("aiworkflow must not depend on traffic internals (domain/service/repository)")
    void aiworkflow_mustNotDependOn_traffic_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".aiworkflow..")
                .should().accessClassesThat().resideInAPackage(BASE + ".traffic.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".traffic.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".traffic.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("aiworkflow must not depend on notification")
    void aiworkflow_mustNotDependOn_notification() {
        noClasses().that().resideInAPackage(BASE + ".aiworkflow..")
                .should().accessClassesThat().resideInAPackage(BASE + ".notification..")
                .check(classes);
    }

    // NOTE: scheduler → environment.service isolation rule DEFERRED —
    // EnvironmentBroadcastScheduler.broadcastSensorUpdates() calls
    // EnvironmentService.getCurrentAqi() directly instead of via a Port interface
    // or scheduled event pull. This is pre-existing MVP4 coupling. See audit report
    // docs/mvp5/reports/mvp5-sprint1-archtest-coverage.md — SA follow-up to introduce
    // an EnvironmentBroadcastPort.

    @Test
    @DisplayName("scheduler must not depend on esg internals (domain/service/repository)")
    void scheduler_mustNotDependOn_esg_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".scheduler..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("scheduler must not depend on alert internals (domain/service/repository)")
    void scheduler_mustNotDependOn_alert_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".scheduler..")
                .should().accessClassesThat().resideInAPackage(BASE + ".alert.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".alert.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".alert.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("scheduler must not depend on traffic internals (domain/service/repository)")
    void scheduler_mustNotDependOn_traffic_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".scheduler..")
                .should().accessClassesThat().resideInAPackage(BASE + ".traffic.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".traffic.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".traffic.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("monitoring must not depend on business domain internals (environment/esg/alert/traffic)")
    void monitoring_mustNotDependOn_businessDomainInternals() {
        // monitoring là observability cross-cutting; nó exposes metrics về các module,
        // nhưng KHÔNG được access domain/repository/service internals của business module.
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".monitoring..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".alert.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".alert.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".traffic.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".traffic.repository..");
        rule.check(classes);
    }

    @Test
    @DisplayName("bms must not depend on esg internals (domain/service/repository)")
    void bms_mustNotDependOn_esg_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".bms..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("building must not depend on esg internals (domain/service/repository)")
    void building_mustNotDependOn_esg_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".building..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("bms must not depend on traffic internals (domain/service/repository)")
    void bms_mustNotDependOn_traffic_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".bms..")
                .should().accessClassesThat().resideInAPackage(BASE + ".traffic.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".traffic.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".traffic.service..");
        rule.check(classes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Infrastructure / cross-cutting module boundaries
    //   auth, tenant, common, kafka là infrastructure; chúng KHÔNG được access
    //   business domain internals của các module nghiệp vụ.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("auth must not depend on environment internals (domain/service/repository)")
    void auth_mustNotDependOn_environment_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".auth..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("auth must not depend on esg internals (domain/service/repository)")
    void auth_mustNotDependOn_esg_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".auth..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("tenant must not depend on environment internals (domain/service/repository)")
    void tenant_mustNotDependOn_environment_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".tenant..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("tenant must not depend on esg internals (domain/service/repository)")
    void tenant_mustNotDependOn_esg_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".tenant..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("common must not depend on business domain internals (environment/esg/alert/traffic)")
    void common_mustNotDependOn_businessDomainInternals() {
        // common là cross-cutting infrastructure (ratelimit, filter, logging, exception).
        // Nó KHÔNG được access domain/repository/service internals của business module —
        // chỉ được giao tiếp qua shared DTO/event hoặc Spring Security context.
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".common..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".alert.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".alert.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".traffic.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".traffic.repository..");
        rule.check(classes);
    }

    @Test
    @DisplayName("kafka must not depend on business domain internals (environment/esg/alert/traffic)")
    void kafka_mustNotDependOn_businessDomainInternals() {
        // kafka module chỉ chứa KafkaTemplate config, producer helper, topic constants.
        // Nó KHÔNG được access domain/repository/service internals của business module —
        // kafka config phải generic, không phụ thuộc schema của module nghiệp vụ.
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".kafka..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".environment.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".alert.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".alert.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".traffic.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".traffic.repository..");
        rule.check(classes);
    }

    @Test
    @DisplayName("admin must not depend on esg internals (domain/service/repository)")
    void admin_mustNotDependOn_esg_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".admin..")
                .should().accessClassesThat().resideInAPackage(BASE + ".esg.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".esg.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("admin must not depend on alert internals (domain/service/repository)")
    void admin_mustNotDependOn_alert_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".admin..")
                .should().accessClassesThat().resideInAPackage(BASE + ".alert.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".alert.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".alert.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("admin must not depend on traffic internals (domain/service/repository)")
    void admin_mustNotDependOn_traffic_internals() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".admin..")
                .should().accessClassesThat().resideInAPackage(BASE + ".traffic.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".traffic.repository..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".traffic.service..");
        rule.check(classes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADR-052 migration P1 — close ArchTest coverage gap (Nhóm C coupling).
    // These rules lock the boundaries that were migrated to common.spi Ports in
    // the post-BUG-M5-009 cleanup, so the coupling cannot silently regress.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("safety must not depend on alert/notification service internals (use AlertPort / NotificationPort)")
    void safety_mustNotDependOn_alertOrNotification_service() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".safety..")
                .should().accessClassesThat().resideInAPackage(BASE + ".alert.service..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".alert.domain..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".notification.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("billing must not depend on audit.service (use @EventListener, not direct inject)")
    void billing_mustNotDependOn_audit_service() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".billing..")
                .should().accessClassesThat().resideInAPackage(BASE + ".audit.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("scheduler must not depend on environment/notification service (use broadcast Ports)")
    void scheduler_mustNotDependOn_environmentOrNotification_service() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".scheduler..")
                .should().accessClassesThat().resideInAPackage(BASE + ".environment.service..")
                .orShould().accessClassesThat().resideInAPackage(BASE + ".notification.service..");
        rule.check(classes);
    }

    @Test
    @DisplayName("bms must not depend on notification.service (use SseBroadcastPort; @KafkaListener is allowed)")
    void bms_mustNotDependOn_notification_service() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE + ".bms..")
                .should().accessClassesThat().resideInAPackage(BASE + ".notification.service..");
        rule.check(classes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Caching rule
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("controllers must not use @Cacheable")
    void controllers_mustNotUse_cacheable() {
        ArchRule rule = methods()
                .that().areDeclaredInClassesThat().areAnnotatedWith(Controller.class)
                .or().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .should().notBeAnnotatedWith(Cacheable.class);

        rule.check(classes);
    }
}
