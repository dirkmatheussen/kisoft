package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Generic error response used by the mock for rejected OneAPI messages (HTTP 400).")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OneApiErrorResponse(
        String clientNumber,
        String inboundDeliveryNumber,
        String orderNumber,
        String articleNumber,
        String packSize,
        String requestNumber,
        String message,
        List<String> codes
) {}

