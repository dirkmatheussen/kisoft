package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * inventoryRequestLine entry of a Create Inventory Request (HIS Appendix §7.2.2).
 * {@code slot} is mandatory if {@code loadUnitCode} is transmitted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventoryRequestLine(
        @NotBlank String lineReference,
        String articleNumber,
        Integer packSize,
        String stockType,
        String lotNumber,
        String dateMark,
        String reservationCode,
        String storageArea,
        String locationNumber,
        String loadUnitCode,
        String slot,
        String noteOnProcessing,
        AdditionalProperty[] additionalProperties
) {}
