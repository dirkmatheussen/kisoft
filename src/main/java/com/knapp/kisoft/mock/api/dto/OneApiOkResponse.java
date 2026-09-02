package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Generic OK response used by the mock when the OneAPI endpoint returns a payload.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OneApiOkResponse(
        Integer http,
        String status,
        String message,
        @Schema(description = "Present when wait=true: synchronous result of the outbound APIC POST")
        CallbackDeliveryResult callback
) {
    public OneApiOkResponse(Integer http, String status, String message) {
        this(http, status, message, null);
    }
}

