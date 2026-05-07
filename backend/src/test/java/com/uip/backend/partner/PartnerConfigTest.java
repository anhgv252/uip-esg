package com.uip.backend.partner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PartnerConfigTest {

    @Test
    void defaultValues() {
        PartnerConfig config = new PartnerConfig();
        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getActiveProfile()).isNull();
        assertThat(config.getBasePackages()).isEmpty();
    }

    @Test
    void settersAndGetters() {
        PartnerConfig config = new PartnerConfig();
        config.setEnabled(true);
        config.setActiveProfile("energy-optimizer");
        config.setBasePackages(List.of("com.uip.partner.energy"));

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getActiveProfile()).isEqualTo("energy-optimizer");
        assertThat(config.getBasePackages()).containsExactly("com.uip.partner.energy");
    }
}
