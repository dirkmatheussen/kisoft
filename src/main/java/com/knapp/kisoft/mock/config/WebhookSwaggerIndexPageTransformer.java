package com.knapp.kisoft.mock.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;

import java.io.IOException;
import java.io.InputStream;

/**
 * Adds a Swagger UI {@code requestInterceptor} so inbound {@code /oneapi/} calls automatically
 * include a Bearer token. Outbound webhook credentials stay server-side only.
 */
public class WebhookSwaggerIndexPageTransformer extends SwaggerIndexPageTransformer {

    private static final String PRESETS = "presets: [";

    public WebhookSwaggerIndexPageTransformer(
            SwaggerUiConfigProperties swaggerUiConfig,
            SwaggerUiOAuthProperties swaggerUiOAuthProperties,
            SwaggerUiConfigParameters swaggerUiConfigParameters,
            SwaggerWelcomeCommon swaggerWelcomeCommon,
            ObjectMapperProvider objectMapperProvider) {
        super(swaggerUiConfig, swaggerUiOAuthProperties, swaggerUiConfigParameters, swaggerWelcomeCommon, objectMapperProvider);
    }

    @Override
    protected String defaultTransformations(InputStream inputStream) throws IOException {
        return addMockBearerInterceptor(super.defaultTransformations(inputStream));
    }

    private String addMockBearerInterceptor(String html) {
        StringBuilder script = new StringBuilder();
        script.append("requestInterceptor: (request) => {\n");
        script.append("  const url = String(request.url || '');\n");
        script.append("  if (url.indexOf('/oneapi/') !== -1 && !request.headers['Authorization']) {\n");
        script.append("    request.headers['Authorization'] = ").append(jsString("Bearer swagger-ui")).append(";\n");
        script.append("  }\n");
        script.append("  return request;\n");
        script.append("},\n");
        script.append("\t\t").append(PRESETS);
        return html.replace(PRESETS, script.toString());
    }

    private String jsString(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "\"\"";
        }
    }
}
