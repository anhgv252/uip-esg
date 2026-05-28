package com.uip.iot.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IotIngestionProperties — unit")
class ConditionalModeTest {

    @Test
    @DisplayName("default mode is DISABLED")
    void defaultMode_isDisabled() {
        IotIngestionProperties props = new IotIngestionProperties();
        assertThat(props.getMode()).isEqualTo(IotIngestionProperties.Mode.DISABLED);
    }

    @Test
    @DisplayName("mode enum has 3 values")
    void modeEnum_hasThreeValues() {
        assertThat(IotIngestionProperties.Mode.values()).hasSize(3);
        assertThat(IotIngestionProperties.Mode.valueOf("SHADOW")).isNotNull();
        assertThat(IotIngestionProperties.Mode.valueOf("PRIMARY")).isNotNull();
        assertThat(IotIngestionProperties.Mode.valueOf("DISABLED")).isNotNull();
    }
}
