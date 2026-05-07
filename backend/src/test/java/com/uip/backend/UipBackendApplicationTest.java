package com.uip.backend;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

import static org.mockito.ArgumentMatchers.any;

class UipBackendApplicationTest {

    @Test
    void main_invokesSpringApplicationRun() {
        try (MockedStatic<SpringApplication> mocked = Mockito.mockStatic(SpringApplication.class)) {
            UipBackendApplication.main(new String[]{});
            mocked.verify(() -> SpringApplication.run(eq(UipBackendApplication.class), any(String[].class)));
        }
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
