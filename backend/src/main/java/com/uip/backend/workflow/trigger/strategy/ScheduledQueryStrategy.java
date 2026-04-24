package com.uip.backend.workflow.trigger.strategy;

import java.util.List;

public interface ScheduledQueryStrategy {
    /** Phải khớp với giá trị `scheduleQueryBean` trong bảng trigger_config. */
    String queryBeanRef();
    List<?> execute();
}
