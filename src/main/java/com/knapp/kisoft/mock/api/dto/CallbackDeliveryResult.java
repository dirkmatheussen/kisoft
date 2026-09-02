package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;

@Schema(description = "Result of the outbound POST to the configured IBM APIC webhook URL")
public record CallbackDeliveryResult(
        @Schema(description = "Full callback URL that was called")
        String targetUrl,
        @Schema(description = "True when APIC returned a 2xx status")
        boolean delivered,
        @Schema(description = "HTTP status returned by APIC, or null when no response was received")
        Integer callbackHttpStatus,
        @Schema(description = "Response body from APIC (truncated for display)")
        String callbackResponseBody,
        @Schema(description = "APIC httpMessage when present in the response body")
        String callbackHttpMessage,
        @Schema(description = "APIC moreInformation when present in the response body")
        String moreInformation,
        @Schema(description = "Full error summary (same text as the server log line)")
        String errorMessage
) {
    private static final int MAX_BODY_LENGTH = 2000;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static CallbackDeliveryResult success(String url, ResponseEntity<String> response) {
        return new CallbackDeliveryResult(
                url,
                true,
                response.getStatusCode().value(),
                truncate(response.getBody()),
                null,
                null,
                null);
    }

    public static CallbackDeliveryResult failure(String url, HttpStatusCodeException exception) {
        String body = exception.getResponseBodyAsString();
        Integer status = exception.getStatusCode().value();
        String httpMessage = exception.getStatusText();
        String moreInformation = null;

        if (body != null && !body.isBlank()) {
            try {
                JsonNode node = MAPPER.readTree(body);
                if (node.hasNonNull("httpCode")) {
                    status = node.get("httpCode").asInt();
                }
                if (node.hasNonNull("httpMessage")) {
                    httpMessage = node.get("httpMessage").asText();
                }
                if (node.hasNonNull("moreInformation")) {
                    moreInformation = node.get("moreInformation").asText();
                }
            } catch (Exception ignored) {
                // keep raw body and Spring status text
            }
        }

        return new CallbackDeliveryResult(
                url,
                false,
                status,
                truncate(body),
                httpMessage,
                moreInformation,
                formatErrorMessage(status, httpMessage, moreInformation, exception.getMessage()));
    }

    public static CallbackDeliveryResult failure(String url, String errorMessage) {
        return new CallbackDeliveryResult(url, false, null, null, null, null, errorMessage);
    }

    /** One-line summary matching the server log format. */
    public String logLine(String messageName) {
        if (delivered) {
            return "Sent " + messageName + " to " + targetUrl;
        }
        return "Failed to send " + messageName + " to " + targetUrl + ": " + errorMessage;
    }

    private static String formatErrorMessage(
            Integer status, String httpMessage, String moreInformation, String fallback) {
        if (status == null && httpMessage == null && moreInformation == null) {
            return fallback;
        }
        StringBuilder sb = new StringBuilder();
        if (status != null) {
            sb.append(status);
        }
        if (httpMessage != null && !httpMessage.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(httpMessage);
        }
        if (moreInformation != null && !moreInformation.isBlank()) {
            sb.append(":\"").append(moreInformation).append('"');
        }
        if (sb.isEmpty()) {
            return fallback;
        }
        return sb.toString();
    }

    private static String truncate(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        if (body.length() <= MAX_BODY_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_BODY_LENGTH) + "…";
    }
}
