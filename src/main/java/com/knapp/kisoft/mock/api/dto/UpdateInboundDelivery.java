package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * MsgUpdateInboundDelivery - PATCH body for inbound delivery.
 * InboundDeliveryRef + optional priority, vasTasks, additionalProperties, addInboundDeliveryLines, deleteLinesByReference.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateInboundDelivery(
        @NotBlank String clientNumber,
        @NotBlank String inboundDeliveryNumber,
        Integer priority,
        VasTask[] vasTasks,
        AdditionalProperty[] additionalProperties,
        List<InboundDeliveryLine> addInboundDeliveryLines,
        List<String> deleteLinesByReference
) {}
