package com.uip.backend.bms.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ModbusTcpAdapter — unit")
class ModbusTcpAdapterTest {

    private ModbusTcpAdapter adapter;

    @BeforeEach
    void setUp() {
        Map<String, String> registerMap = Map.of(
                "temperature", "0:1:C",
                "humidity", "1:1:%"
        );
        adapter = new ModbusTcpAdapter(registerMap);
    }

    @Test
    @DisplayName("getProtocol — returns MODBUS_TCP")
    void getProtocol() {
        assertThat(adapter.getProtocol()).isEqualTo("MODBUS_TCP");
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

    @Test
    @DisplayName("sendCommand — throws when not connected")
    void sendCommand_throwsWhenNotConnected() {
        assertThatThrownBy(() -> adapter.sendCommand(
                new com.uip.backend.bms.api.dto.BmsCommand("SET_POINT", Map.of("value", 22, "register", 0))))
                .isInstanceOf(BmsAdapterException.class);
    }
}
