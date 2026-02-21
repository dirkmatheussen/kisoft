package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * InboundDeliveryRef - Reference to inbound delivery by client and number.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InboundDeliveryRef(
        @NotBlank String clientNumber,
        @NotBlank String inboundDeliveryNumber
) {}
