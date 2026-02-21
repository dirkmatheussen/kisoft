package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * MsgInboundDelivery - Create inbound delivery (goods-in).
 * Extends InboundDeliveryRef with supplier and lines.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InboundDelivery(
        @NotBlank String clientNumber,
        @NotBlank String inboundDeliveryNumber,
        @NotBlank String supplierNumber,
        Integer priority,
        String businessCase,
        VasTask[] vasTasks,
        AdditionalProperty[] additionalProperties,
        List<@Valid InboundDeliveryLine> inboundDeliveryLines
) {}
