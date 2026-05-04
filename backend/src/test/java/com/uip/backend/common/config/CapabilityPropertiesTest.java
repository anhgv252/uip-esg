package com.uip.backend.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CapabilityProperties")
class CapabilityPropertiesTest {

    @Test
    @DisplayName("default values are all false except what's configured")
    void defaultsAreFalse() {
        CapabilityProperties props = new CapabilityProperties();

        assertThat(props.isMultiTenancy()).isFalse();
        assertThat(props.isRedisCache()).isFalse();
        assertThat(props.isClickhouse()).isFalse();
        assertThat(props.isKongGateway()).isFalse();
        assertThat(props.isKeycloak()).isFalse();
        assertThat(props.isEdgeComputing()).isFalse();
        assertThat(props.isMultiRegion()).isFalse();
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
