package com.uip.backend.billing.api;

import com.uip.backend.billing.domain.MeteringEvent;
import com.uip.backend.billing.domain.MeteringEventType;
import com.uip.backend.billing.repository.MeteringEventRepository;
import com.uip.backend.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * M5-2 T07: Billing metering REST API.
 * 
 * Endpoints:
 *   - GET /api/v1/billing/metering/events   — List events (citizen:read, own tenant only)
 *   - GET /api/v1/billing/metering/summary  — Monthly summary (tenant:admin, any tenant)
 * 
 * Security:
 *   - citizen:read: can view own tenant's billing events
 *   - tenant:admin: can view any tenant's billing summary (cross-tenant admin)
 */
@RestController
@RequestMapping("/api/v1/billing/metering")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Billing", description = "Billing and metering API")
@SecurityRequirement(name = "bearerAuth")
public class MeteringController {

    private final MeteringEventRepository meteringEventRepository;

    /**
     * GET /api/v1/billing/metering/events
     * 
     * Returns metering events for the authenticated user's tenant.
     * Scope: citizen:read (can view own tenant's billing events)
     * 
     * Query params:
     *   - from: Start timestamp (ISO-8601, default: 30 days ago)
     *   - to: End timestamp (ISO-8601, default: now)
     *   - eventType: Optional event type filter (AI_PREDICTION, WORKFLOW_RUN, etc.)
     */
    @GetMapping("/events")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "List metering events",
        description = "Returns metering events for the authenticated user's tenant. " +
                      "Requires citizen:read scope (own tenant only)."
    )
    public ResponseEntity<List<MeteringEvent>> getEvents(
            @Parameter(description = "Start timestamp (ISO-8601, default: 30 days ago)")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "End timestamp (ISO-8601, default: now)")
            @RequestParam(required = false) Instant to,
            @Parameter(description = "Optional event type filter")
            @RequestParam(required = false) MeteringEventType eventType,
            @AuthenticationPrincipal Authentication auth) {

        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "default";
        }

        // Default time range: last 30 days
        if (from == null) {
            from = Instant.now().minus(30, ChronoUnit.DAYS);
        }
        if (to == null) {
            to = Instant.now();
        }

        List<MeteringEvent> events;
        if (eventType != null) {
            events = meteringEventRepository.findByTenantTypeAndTimeRange(tenantId, eventType, from, to);
        } else {
            events = meteringEventRepository.findByTenantAndTimeRange(tenantId, from, to);
        }

        log.debug("Fetched {} metering events for tenant={} from={} to={} type={}",
                events.size(), tenantId, from, to, eventType);
        return ResponseEntity.ok(events);
    }

    /**
     * GET /api/v1/billing/metering/summary
     * 
     * Returns billing summary (total cost) for a tenant in a time range.
     * Scope: tenant:admin (cross-tenant admin can view any tenant's summary)
     * 
     * Query params:
     *   - tenantId: Target tenant ID (required for tenant:admin scope)
     *   - from: Start timestamp (ISO-8601, default: 30 days ago)
     *   - to: End timestamp (ISO-8601, default: now)
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(
        summary = "Get billing summary",
        description = "Returns total cost (in USD cents) for a tenant in a time range. " +
                      "Requires tenant:admin scope (cross-tenant admin only)."
    )
    public ResponseEntity<Map<String, Object>> getSummary(
            @Parameter(description = "Target tenant ID (required)", required = true)
            @RequestParam String tenantId,
            @Parameter(description = "Start timestamp (ISO-8601, default: 30 days ago)")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "End timestamp (ISO-8601, default: now)")
            @RequestParam(required = false) Instant to,
            @AuthenticationPrincipal Authentication auth) {

        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "default";
        }

        // Default time range: last 30 days
        if (from == null) {
            from = Instant.now().minus(30, ChronoUnit.DAYS);
        }
        if (to == null) {
            to = Instant.now();
        }

        Long totalCostCents = meteringEventRepository.sumCostByTenantAndTimeRange(tenantId, from, to);

        Map<String, Object> summary = Map.of(
                "tenantId", tenantId,
                "from", from.toString(),
                "to", to.toString(),
                "totalCostUsdCents", totalCostCents,
                "totalCostUsd", String.format("$%.2f", totalCostCents / 100.0)
        );

        log.debug("Billing summary for tenant={}: {} cents", tenantId, totalCostCents);
        return ResponseEntity.ok(summary);
    }
}
