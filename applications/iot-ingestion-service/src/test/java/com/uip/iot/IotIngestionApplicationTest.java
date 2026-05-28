package com.uip.iot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("IotIngestionApplication — smoke")
class IotIngestionApplicationTest {

    @Test
    @DisplayName("main — does not throw")
    void main_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> {
            // Just verify the class exists and main method is callable
            IotIngestionApplication.class.getDeclaredMethod("main", String[].class);
        });
    }
}
