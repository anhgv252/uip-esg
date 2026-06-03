package com.uip.backend.kafka.config;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Apicurio Schema Registry configuration for Avro serialization.
 *
 * <p>Provides a {@link RegistryClient} bean for interacting with the Apicurio Registry,
 * used by Kafka producers/consumers for Avro schema registration and validation.</p>
 *
 * <p>Configuration properties (in application.yml under {@code apicurio.registry}):</p>
 * <ul>
 *   <li>{@code url} — Apicurio Registry API v2 endpoint</li>
 *   <li>{@code auto-register} — automatically register new schemas (default: true)</li>
 *   <li>{@code compatibility} — schema compatibility level (default: BACKWARD)</li>
 * </ul>
 */
@Configuration
public class AvroConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AvroConfig.class);

    @Value("${apicurio.registry.url:http://localhost:8087/apis/registry/v2}")
    private String registryUrl;

    @Value("${apicurio.registry.auto-register:true}")
    private boolean autoRegister;

    @Value("${apicurio.registry.compatibility:BACKWARD}")
    private String compatibility;

    @Bean
    public RegistryClient apicurioRegistryClient() {
        LOG.info("Initializing Apicurio Registry client: url={}, auto-register={}, compatibility={}",
                registryUrl, autoRegister, compatibility);
        return RegistryClientFactory.create(registryUrl);
    }

    public String getRegistryUrl() { return registryUrl; }
    public boolean isAutoRegister() { return autoRegister; }
    public String getCompatibility() { return compatibility; }
}
