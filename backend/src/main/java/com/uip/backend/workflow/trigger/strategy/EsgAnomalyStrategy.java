package com.uip.backend.workflow.trigger.strategy;

import com.uip.backend.esg.service.EsgService;
import com.uip.backend.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EsgAnomalyStrategy implements ScheduledQueryStrategy {

    private final EsgService esgService;

    @Override
    public String queryBeanRef() { return "esgService.detectEsgAnomalies"; }

    @Override
    public List<?> execute() {
        String tenantId = TenantContext.getCurrentTenant();
        return esgService.detectEsgAnomalies(tenantId);
    }
}
