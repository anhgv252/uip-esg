package com.uip.backend.bms.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BacnetIpAdapter — unit")
class BacnetIpAdapterTest {

    private BacnetIpAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new BacnetIpAdapter(Map.of("temperature", "analogInput:0:C"));
    }

    @Test
    @DisplayName("getProtocol — returns BACNET_IP")
    void getProtocol() {
        assertThat(adapter.getProtocol()).isEqualTo("BACNET_IP");
    }

    @Test
    @DisplayName("poll — throws when not connected")
    void poll_throwsWhenNotConnected() {
        assertThatThrownBy(() -> adapter.poll())
                .isInstanceOf(BmsAdapterException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    @DisplayName("isAlive — false when not connected")
    void isAlive_falseWhenNotConnected() {
        assertThat(adapter.isAlive()).isFalse();
    }
}
