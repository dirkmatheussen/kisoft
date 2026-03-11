package com.knapp.kisoft.mock.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .servers(List.of(new Server().url("https://wispelberg.eu/kisoft").description("KNAPP KiSoft Mock Server")))
                .info(new Info()
                        .title("KNAPP KiSoft Mock API")
                        .version("1.0.2")
                        .description("Mock server simulating KNAPP KiSoft One API. Bearer token required (OAuth2/Entra ID). "
                                + "When knapp.mock.reply-callback-url is set, the mock sends outgoing webhooks (see Webhooks section)."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("OAuth2 Bearer token (Entra ID). Any token accepted.")));
    }

    @Bean
    public OpenApiCustomizer webhooksCustomizer() {
        return openApi -> {
            PathItem inboundDeliveryReply = new PathItem().post(
                    new io.swagger.v3.oas.models.Operation()
                            .summary("InboundDeliveryReply")
                            .description("The mock POSTs this payload to {reply-callback-url}/inboundDeliveryReply when an inbound delivery is created (processingStatus NEW).")
                            .requestBody(new RequestBody()
                                    .content(new Content()
                                            .addMediaType("application/json",
                                                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/InboundDeliveryReply")))))
                            .responses(new ApiResponses())
            );
            PathItem storageOrderReply = new PathItem().post(
                    new io.swagger.v3.oas.models.Operation()
                            .summary("StorageOrderReply")
                            .description("The mock POSTs this payload to {reply-callback-url}/storageOrderReply when a storage order is created (NEW) and when status advances to STARTED and FINISHED (random delay 10–60s between transitions).")
                            .requestBody(new RequestBody()
                                    .content(new Content()
                                            .addMediaType("application/json",
                                                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/StorageOrderReply")))))
                            .responses(new ApiResponses())
            );
            openApi.setWebhooks(Map.of(
                    "inboundDeliveryReply", inboundDeliveryReply,
                    "storageOrderReply", storageOrderReply
            ));
        };
    }
}
