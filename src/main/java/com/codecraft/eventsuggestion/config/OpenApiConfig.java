package com.codecraft.eventsuggestion.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LiveLife API")
                        .version("1.0.0")
                        .description("""
                                Personalized life enrichment platform.

                                Registered customers receive AI-generated suggestions — daily events,
                                weekend getaways, and monthly travel ideas — tailored to their profile,
                                interests, and past behaviour. The system learns from each accepted or
                                rejected suggestion to continuously improve future recommendations.

                                **Authentication:** Register at `/api/auth/register`, then pass the
                                returned JWT in the `Authorization: Bearer <token>` header for all
                                other requests. Use the **Authorize** button above to set your token.
                                """)
                        .contact(new Contact().name("CodeCraft").email("hello@codecraft.com")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the JWT token obtained from /api/auth/register or /api/auth/login")));
    }
}