package com.uip.flink.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TenantKeyedProcessFunctionDelegate} — the ADR-047 §1.3 tenant guard.
 * Ported from {@code com.uip.backend.tenant.flink.TenantKeyedProcessFunctionDelegateTest} so the
 * in-tree contract has its own coverage independent of the backend forward-guard copy.
 *
 * <p>Covers: TenantContext fail-closed set, delegate extract→bind→process→clear lifecycle,
 * drop-on-missing-tenant, and ThreadLocal cleanup on processor throw.</p>
 */
@DisplayName("TenantKeyedProcessFunctionDelegate — tenant guard lifecycle (ADR-047 §1.3)")
class TenantKeyedProcessFunctionDelegateTest {

    @AfterEach
    void clearThread() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("binds tenant during processing then clears the ThreadLocal")
    void bindsTenantDuringProcessing_thenClears() {
        TenantKeyedProcessFunctionDelegate<String, String> guard =
                TenantKeyedProcessFunctionDelegate.forFn(record -> record.split(":")[0]);

        List<String> emitted = new ArrayList<>();
        List<String> seenTenant = new ArrayList<>();

        guard.run("tenant_A:payload",
                (record, emit) -> {
                    seenTenant.add(TenantContext.get()); // bound inside processor
                    emit.accept("out-" + record);
                },
                emitted::add);

        assertEquals("tenant_A", seenTenant.get(0));
        assertEquals(1, emitted.size());
        assertEquals("out-tenant_A:payload", emitted.get(0));
        // After run, ThreadLocal MUST be cleared (no bleed to next record on same thread).
        assertEquals(null, TenantContext.get());
    }

    @Test
    @DisplayName("drops record + increments counter when tenant is blank")
    void dropsRecord_whenTenantBlank() {
        TenantKeyedProcessFunctionDelegate<String, String> guard =
                TenantKeyedProcessFunctionDelegate.forFn(record -> record.split(":")[0]);

        List<String> emitted = new ArrayList<>();
        guard.run(":payload", (record, emit) -> emit.accept("should-not-emit"), emitted::add);

        assertTrue(emitted.isEmpty(), "Record with blank tenant must be dropped (fail-closed)");
        assertEquals(1L, guard.droppedNoTenant(), "Drop counter must increment");
        assertEquals(null, TenantContext.get(), "No tenant leaked into ThreadLocal");
    }

    @Test
    @DisplayName("drops record when extractor throws (treat as no-tenant)")
    void dropsRecord_whenExtractorThrows() {
        TenantKeyedProcessFunctionDelegate<String, String> guard =
                TenantKeyedProcessFunctionDelegate.forFn(record -> {
                    throw new RuntimeException("malformed record");
                });

        List<String> emitted = new ArrayList<>();
        guard.run("tenant_A:payload", (record, emit) -> emit.accept("x"), emitted::add);

        assertTrue(emitted.isEmpty(), "Extractor throw → fail-closed drop");
        assertEquals(1L, guard.droppedNoTenant());
    }

    @Test
    @DisplayName("clears ThreadLocal even when the processor throws")
    void clearsThreadLocal_evenWhenProcessorThrows() {
        TenantKeyedProcessFunctionDelegate<String, String> guard =
                TenantKeyedProcessFunctionDelegate.forFn(record -> record.split(":")[0]);

        assertThrows(RuntimeException.class,
                () -> guard.run("tenant_A:payload",
                        (record, emit) -> { throw new RuntimeException("processor blew up"); },
                        ignored -> {}));

        // CRITICAL: ThreadLocal must not survive a processor failure.
        assertEquals(null, TenantContext.get(),
                "TenantContext must clear even when processor throws (no bleed to next record)");
    }

    @Test
    @DisplayName("consecutive records on the same thread isolate tenants")
    void consecutiveRecords_isolateTenants() {
        TenantKeyedProcessFunctionDelegate<String, String> guard =
                TenantKeyedProcessFunctionDelegate.forFn(record -> record.split(":")[0]);

        List<String> seen = new ArrayList<>();
        guard.run("tenant_A:x", (r, emit) -> seen.add(TenantContext.get()), ignored -> {});
        guard.run("tenant_B:y", (r, emit) -> seen.add(TenantContext.get()), ignored -> {});

        assertEquals(2, seen.size());
        assertEquals("tenant_A", seen.get(0));
        assertEquals("tenant_B", seen.get(1));
    }

    @Test
    @DisplayName("forFn(null) throws — extractor is mandatory")
    void forFn_rejectsNullExtractor() {
        assertThrows(IllegalArgumentException.class,
                () -> TenantKeyedProcessFunctionDelegate.forFn(null));
    }
}
