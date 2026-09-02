package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Inbound delivery payload plus KiSoft {@code processingStatus} (mock GET / OData read).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Inbound delivery with processing status (mock OData read — NOT KiSoft API)")
public record InboundDeliveryRead(
        String processingStatus,
        InboundDelivery inboundDelivery
) {}
