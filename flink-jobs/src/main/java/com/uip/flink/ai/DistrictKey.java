package com.uip.flink.ai;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;

/**
 * M4-AI-01: Composite key for the district aggregation job —
 * {@code (tenantId, districtCode, sensorType)}. Used as the {@code keyBy}
 * selector so readings from the same district and sensor type land in the
 * same tumbling window.
 */
@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class DistrictKey implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String tenantId;
    private final String districtCode;
    private final String sensorType;
}
