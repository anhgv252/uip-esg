package com.uip.backend.partner;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "uip.partner")
public class PartnerConfig {

    private boolean enabled = false;
    private String activeProfile;
    private List<String> basePackages = List.of();
}
