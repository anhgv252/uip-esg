package com.uip.backend.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CapabilityProperties")
class CapabilityPropertiesTest {

    @Test
    @DisplayName("default values are all false — Tier 1 monolith runs without any flag")
    void defaultsAreFalse() {
        CapabilityProperties props = new CapabilityProperties();

        // Feature flags
        assertThat(props.isMultiTenancy()).isFalse();
        assertThat(props.isRedisCache()).isFalse();
        assertThat(props.isClickhouse()).isFalse();
        assertThat(props.isKongGateway()).isFalse();
        assertThat(props.isKeycloak()).isFalse();
        assertThat(props.isEdgeComputing()).isFalse();
        assertThat(props.isMultiRegion()).isFalse();

        // Extraction flags — all false → monolith handles everything (Tier 1 safe)
        assertThat(props.isIotIngestionExternal()).isFalse();
        assertThat(props.isAlertExternal()).isFalse();
        assertThat(props.isAnalyticsExternal()).isFalse();
        assertThat(props.isAiWorkflowExternal()).isFalse();
        assertThat(props.isCitizenExternal()).isFalse();
    }

    @Test
    @DisplayName("setters work correctly")
    void settersWork() {
        CapabilityProperties props = new CapabilityProperties();
        props.setMultiTenancy(true);
        props.setRedisCache(true);
        props.setClickhouse(true);

        assertThat(props.isMultiTenancy()).isTrue();
        assertThat(props.isRedisCache()).isTrue();
        assertThat(props.isClickhouse()).isTrue();
        assertThat(props.isKeycloak()).isFalse();
    }
}
