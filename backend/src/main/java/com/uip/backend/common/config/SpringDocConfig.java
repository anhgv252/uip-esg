package com.uip.backend.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;

@Configuration
public class SpringDocConfig {

    static {
        // Prevent springdoc from introspecting Spring Data's Pageable as a model —
        // avoids circular schema resolution that causes the /v3/api-docs 500.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(Pageable.class);
    }

    @Bean
    public OpenAPI uipOpenAPI() {
        final String bearerScheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("UIP Smart City API")
                        .version("0.1.0")
                        .description("REST API for UIP Smart City Platform — Sprint 1-3"))
                .addSecurityItem(new SecurityRequirement().addList(bearerScheme))
                .components(new Components()
                        .addSecuritySchemes(bearerScheme, new SecurityScheme()
                                .name(bearerScheme)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
