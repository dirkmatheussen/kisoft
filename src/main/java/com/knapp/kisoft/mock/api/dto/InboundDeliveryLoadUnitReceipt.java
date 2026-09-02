package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Mock-only request body for the operator-driven goods-in load unit
 * confirmation (IB-02 MF-4 / MF-5). Triggers KiSoft to update progress and
 * send PostStockReceived to the host.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InboundDeliveryLoadUnitReceipt(
        @NotBlank String clientNumber,
        @NotBlank String inboundDeliveryNumber,
        @NotBlank String lineReference,
        @NotBlank String loadUnitCode,
        @NotBlank String compartment,
        @NotNull @Min(1) Integer quantity,
        String lotNumber,
        String dateMark,
        String serialNumber,
        String stockType,
        String stockQuality,
        String reservationCode
) {}
