package com.uip.backend.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Tenant Isolation Architecture Tests (ADR-047 §1.3 — Flink tenant enforcement).
 *
 * <p>Asserts that any Flink keyed operator in the backend must delegate through
 * {@code com.uip.backend.tenant.flink.TenantKeyedProcessFunctionDelegate} — never
 * process records under an unbound tenant context. A bare {@code KeyedProcessFunction}
 * subclass that bypasses the delegate is a cross-tenant leak waiting to happen.</p>
 *
 * <p><b>Current state.</b> This repo contains no in-tree {@code KeyedProcessFunction}
 * subclasses — Flink jobs run in the external submitter image
 * ({@code Dockerfile.flink-submitter}). These rules are a forward guard: when MVP6
 * brings Flink jobs back in-tree, any new operator that forgets the delegate will
 * fail the build here.</p>
 */
@DisplayName("Tenant Isolation Rules (ADR-047)")
class TenantIsolationArchTest {

    private static final String BASE = "com.uip.backend";
    private static final String FLINK_TENANT_DELEGATE =
        "com.uip.backend.tenant.flink.TenantKeyedProcessFunctionDelegate";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    /**
     * Rule 1 — No class may extend Flink's {@code KeyedProcessFunction} directly.
     * Concrete operators must be composed with {@link
     * com.uip.backend.tenant.flink.TenantKeyedProcessFunctionDelegate} instead, so the
     * tenant ThreadLocal is always set/cleared.
     *
     * <p>KeyedProcessFunction is not on the backend classpath today (no Flink dep),
     * so this rule is vacuous until MVP6 — but it stays armed so a future dependency
     * addition cannot silently introduce a non-tenant-aware operator.</p>
     */
    @Test
    void noBareKeyedProcessFunctionSubclasses() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + "..")
                .and().areAssignableTo("org.apache.flink.streaming.api.functions.KeyedProcessFunction")
                .should().resideInAnyPackage(BASE + "..")
                .because("Flink keyed operators must delegate tenant handling to "
                    + FLINK_TENANT_DELEGATE + " (ADR-047 §1.3). A bare KeyedProcessFunction "
                    + "subclass bypasses the tenant ThreadLocal guard.");

        // Forward guard: no in-tree KeyedProcessFunction today (Flink runs external).
        // allowEmptyShould so the rule stays armed but passes until MVP6 lands jobs.
        rule.allowEmptyShould(true).check(classes);
    }

    /**
     * Rule 2 — Any class named {@code *Job} in a {@code flink} package must reference
     * the tenant delegate. Catches the common case where a job is written without
     * tenant handling even before Flink types resolve.
     */
    @Test
    void flinkJobsMustReferenceTenantDelegate() {
        ArchRule rule = classes()
                .that().resideInAPackage(BASE + "..flink..")
                .and().haveSimpleNameEndingWith("Job")
                .should().dependOnClassesThat()
                    .haveFullyQualifiedName(FLINK_TENANT_DELEGATE)
                .orShould().haveFullyQualifiedName(FLINK_TENANT_DELEGATE)
                .because("Every Flink job must route processElement through the tenant "
                    + "delegate so TenantContext is bound and cleared per record (ADR-047 §1.3).");

        // Forward guard — no in-tree Flink jobs yet. allowEmptyShould keeps it armed.
        rule.allowEmptyShould(true).check(classes);
    }

    /**
     * Rule 3 — The tenant delegate itself must live in the tenant package (not
     * scattered). Keeps the isolation contract discoverable.
     */
    @Test
    void tenantDelegateLivesInTenantPackage() {
        ArchRule rule = classes()
                .that().haveSimpleName("TenantKeyedProcessFunctionDelegate")
                .should().resideInAPackage("com.uip.backend.tenant.flink")
                .because("The tenant-isolation delegate is the single source of the "
                    + "set/clear lifecycle; co-locate it with TenantContext (ADR-047 §1.3).");

        rule.check(classes);
    }
}
