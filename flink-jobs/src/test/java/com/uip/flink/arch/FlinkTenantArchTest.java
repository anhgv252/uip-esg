package com.uip.flink.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.base.DescribedPredicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ADR-047 §1.3 — Flink tenant-isolation architecture rules (in-tree enforcement).
 *
 * <p>Unlike the parallel {@code TenantIsolationArchTest} in the backend module (which is a
 * forward guard because that module has zero Flink jobs), this test lives inside the
 * {@code flink-jobs} module where the real jobs are. Every {@code ProcessFunction} /
 * {@code KeyedProcessFunction} / {@code PatternProcessFunction} subclass MUST depend on the
 * tenant delegate, the tenant context, or the {@link com.uip.flink.common.tenant.TenantBindingProcessFunction}
 * wrapper — a bare operator bypasses the tenant ThreadLocal guard and is a cross-tenant leak
 * waiting to happen.</p>
 *
 * <p>These rules fail the build if a future operator forgets the delegate. They are NOT
 * {@code allowEmptyShould}: there are in-tree operators today, so the rule has real teeth.</p>
 */
@DisplayName("Flink Tenant Isolation Rules (ADR-047, flink-jobs module)")
class FlinkTenantArchTest {

    private static final String BASE = "com.uip.flink";

    private static final DescribedPredicate<JavaClass> IS_TENANT_AWARE =
            new DescribedPredicate<JavaClass>(
                    "a tenant-aware class (TenantKeyedProcessFunctionDelegate / TenantContext / "
                            + "TenantBindingProcessFunction)") {
                @Override
                public boolean test(JavaClass jc) {
                    String n = jc.getName();
                    return "com.uip.flink.common.tenant.TenantKeyedProcessFunctionDelegate".equals(n)
                            || "com.uip.flink.common.tenant.TenantContext".equals(n)
                            || "com.uip.flink.common.tenant.TenantKeyedProcessFunction".equals(n)
                            || "com.uip.flink.common.tenant.TenantBindingProcessFunction".equals(n);
                }
            };

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    /**
     * Rule 1 — Every Flink record-processing operator ({@code ProcessFunction},
     * {@code KeyedProcessFunction}, {@code PatternProcessFunction}) in
     * {@code com.uip.flink..} MUST depend on a tenant-aware class.
     *
     * <p>Concrete operators may satisfy the rule by holding a delegate field (keyed operators,
     * pattern functions) or by being the {@code TenantBindingProcessFunction} wrapper itself
     * (window pipelines that have no keyed operator of their own — the wrapper references the
     * delegate).</p>
     */
    @Test
    void everyFlinkOperatorReferencesTenantDelegate() {
        ArchRule rule = classes()
                .that().resideInAPackage(BASE + "..")
                .and().areAssignableTo("org.apache.flink.streaming.api.functions.ProcessFunction")
                .should().dependOnClassesThat(IS_TENANT_AWARE)
                .because("Every Flink ProcessFunction (covers KeyedProcessFunction subclass too) must "
                        + "route record processing through the tenant delegate or bind TenantContext, "
                        + "so the per-record tenant ThreadLocal is always set and cleared "
                        + "(ADR-047 §1.3). A bare operator bypasses the guard.");

        rule.check(classes);
    }

    /**
     * Rule 1b — {@code PatternProcessFunction} is NOT a {@code ProcessFunction} subclass
     * in the Flink type hierarchy, so it needs its own rule. Same contract.
     */
    @Test
    void everyFlinkPatternOperatorReferencesTenantDelegate() {
        ArchRule rule = classes()
                .that().resideInAPackage(BASE + "..")
                .and().areAssignableTo("org.apache.flink.cep.functions.PatternProcessFunction")
                .should().dependOnClassesThat(IS_TENANT_AWARE)
                .because("Every CEP PatternProcessFunction must bind the tenant from its matched "
                        + "events via the delegate before emitting downstream (ADR-047 §1.3).");

        rule.check(classes);
    }

    /**
     * Rule 2 — Tenant base classes live only in the canonical tenant package.
     * Keeps the isolation contract discoverable and prevents shadow copies.
     */
    @Test
    void tenantBaseClassesLiveInCanonicalPackage() {
        ArchRule rule = classes()
                .that().haveSimpleName("TenantContext")
                .or().haveSimpleName("TenantKeyedProcessFunction")
                .or().haveSimpleName("TenantKeyedProcessFunctionDelegate")
                .or().haveSimpleName("TenantBindingProcessFunction")
                .should().resideInAPackage("com.uip.flink.common.tenant")
                .because("The tenant-isolation contract (ADR-047 §1.3) is the single source of "
                        + "the set/clear ThreadLocal lifecycle; co-locate it so it is discoverable.");

        rule.check(classes);
    }

    /**
     * Rule 3 — No class outside the tenant package may mutate {@code TenantContext} directly.
     * Only {@code TenantKeyedProcessFunctionDelegate} (and {@code TenantContext} itself) may
     * call {@code set} / {@code clear}. Hand-rolled tenant handling outside the delegate risks
     * forgetting the finally-clear and leaking tenant across records.
     *
     * <p>Two rules (set, clear) rather than one combined predicate, because
     * {@code noClasses().should().callMethod(...)} does not chain with {@code orShould}.</p>
     */
    @Test
    void noClassOutsideTenantPackageCallsTenantContextSet() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + "..")
                .and().resideOutsideOfPackage("com.uip.flink.common.tenant..")
                .should().callMethod(
                        com.uip.flink.common.tenant.TenantContext.class, "set", String.class)
                .because("Only TenantKeyedProcessFunctionDelegate may mutate TenantContext "
                        + "(ADR-047 §1.3). Calling set() by hand risks forgetting the "
                        + "finally-clear and leaking tenant across records.");

        rule.check(classes);
    }

    @Test
    void noClassOutsideTenantPackageCallsTenantContextClear() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + "..")
                .and().resideOutsideOfPackage("com.uip.flink.common.tenant..")
                .should().callMethod(
                        com.uip.flink.common.tenant.TenantContext.class, "clear")
                .because("Only TenantKeyedProcessFunctionDelegate may clear TenantContext "
                        + "(ADR-047 §1.3). Calling clear() by hand bypasses the delegate guard.");

        rule.check(classes);
    }
}
