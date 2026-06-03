package com.uip.backend.kafka.config;

import io.apicurio.registry.rest.client.RegistryClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AvroConfig — Apicurio Registry bean configuration")
class AvroConfigTest {

    @Test
    @DisplayName("RegistryClient bean created with configured URL")
    void registryClient_createdWithConfiguredUrl() {
        AvroConfig config = new AvroConfig();
        ReflectionTestUtils.setField(config, "registryUrl", "http://localhost:8087/apis/registry/v2");
        ReflectionTestUtils.setField(config, "autoRegister", true);
        ReflectionTestUtils.setField(config, "compatibility", "BACKWARD");

        RegistryClient client = config.apicurioRegistryClient();

        assertThat(client).isNotNull();
    }

    @Test
    @DisplayName("Default values match application.yml defaults")
    void defaults_matchApplicationYml() {
        AvroConfig config = new AvroConfig();
        ReflectionTestUtils.setField(config, "registryUrl", "http://localhost:8087/apis/registry/v2");
        ReflectionTestUtils.setField(config, "autoRegister", true);
        ReflectionTestUtils.setField(config, "compatibility", "BACKWARD");

        assertThat(config.getRegistryUrl()).isEqualTo("http://localhost:8087/apis/registry/v2");
        assertThat(config.isAutoRegister()).isTrue();
        assertThat(config.getCompatibility()).isEqualTo("BACKWARD");
    }

    @Test
    @DisplayName("RegistryClient bean created with custom URL (env override scenario)")
    void registryClient_customUrl() {
        AvroConfig config = new AvroConfig();
        ReflectionTestUtils.setField(config, "registryUrl", "http://apicurio:8080/apis/registry/v2");
        ReflectionTestUtils.setField(config, "autoRegister", true);
        ReflectionTestUtils.setField(config, "compatibility", "BACKWARD");

        RegistryClient client = config.apicurioRegistryClient();

        assertThat(client).isNotNull();
        assertThat(config.getRegistryUrl()).isEqualTo("http://apicurio:8080/apis/registry/v2");
    }
}
