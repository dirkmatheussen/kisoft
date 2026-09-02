package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * InboundDeliveryLine - Line of an inbound delivery (article + quantity).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InboundDeliveryLine(
        @NotBlank String lineReference,
        @NotBlank String articleNumber,
        @NotNull Integer packSize,
        @NotNull Integer expectedQuantity,
        String loadUnitCode,
        String loadCarrier,
        String stockType,
        String lotNumber,
        String dateMark,
        String serialNumber,
        /** Aligns with {@code inventoryRequestLine.reservationCode} / goods-out (e.g. Country of Origin). */
        String reservationCode,
        List<String> stockLockReasons,
        String noteOnProcessing,
        String stockQuality,
        AdditionalProperty[] additionalProperties
) {}
