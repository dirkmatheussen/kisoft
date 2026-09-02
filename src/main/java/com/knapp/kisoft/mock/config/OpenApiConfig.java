package com.knapp.kisoft.mock.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    /** Swagger tag for OData GET endpoints that are mock-only (not in KiSoft One API). */
    public static final String MOCK_ODATA_READ_TAG = "Mock OData Read (NOT KiSoft API)";

    private final KnappMockProperties mockProperties;

    public OpenApiConfig(KnappMockProperties mockProperties) {
        this.mockProperties = mockProperties;
    }

    @Bean
    public OpenAPI openAPI() {
        String apicExample = mockProperties.areCallbacksEnabled()
                ? mockProperties.webhookTargetUrl("stockReceived")
                : "{reply-callback-url}/oneapi/v1/_webhooks/stockReceived";
        String webhookNote = mockProperties.areCallbacksEnabled()
                ? "Lifecycle events POST callbacks to IBM APIC server-side (e.g. **" + apicExample + "**). "
                + "Use **Webhooks (outgoing)** with wait=true to see the APIC response in the reply."
                : "Set knapp.mock.reply-callback-url to enable outgoing IBM APIC callbacks.";

        // No hardcoded servers: springdoc fills servers[0].url from the current request
        // (e.g. http://localhost:8084/kisoft or https://wispelberg.eu/kisoft), including context-path.
        return new OpenAPI()
                .info(new Info()
                        .title("KNAPP KiSoft Mock API")
                        .version("4.0.3")
                        .description("Mock server simulating the KNAPP KiSoft One API (OpenAPI 4.0.0 / KiSoft 2.12.2), limited to the message subset in scope for "
                                + "VOLVO TRUCKS Tacoma per the HOST Interface Specification One API Appendix (P000-013061). "
                                + "Only the in-scope HOST → KiSoft One calls are exposed. Bearer token required (OAuth2/Entra ID). "
                                + "**Operations under tag \"" + MOCK_ODATA_READ_TAG + "\" are mock-only inspection GETs — they are "
                                + "not part of the KiSoft One Product API (HIS Appendix §2.3.1: KiSoft exposes no GET endpoints).** "
                                + "Swagger Try it out uses the host you opened the docs on. "
                                + webhookNote))
                .tags(List.of(new Tag()
                        .name(MOCK_ODATA_READ_TAG)
                        .description("OData-style GET endpoints added by this mock for debugging and test verification. "
                                + "**Not defined in the KiSoft One Product API or HIS Appendix.** "
                                + "A real KiSoft One installation does not expose these URLs; do not rely on them in production WMS integration.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("OAuth2 Bearer token for inbound /oneapi calls (any token accepted on the mock).")));
    }
}
