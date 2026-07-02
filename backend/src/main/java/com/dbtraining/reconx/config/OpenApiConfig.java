package com.dbtraining.reconx.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI metadata + bearerAuth scheme.
 *
 * Visit http://localhost:8080/api/swagger-ui.html after boot.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI reconxOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ReconX API")
                        .version("v1")
                        .description("Enterprise Trade Reconciliation Platform (Advanced Track)")
                        .contact(new Contact().name("DB TDI Training").email("tdi@db.com")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
