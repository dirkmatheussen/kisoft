package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * One counted line in a mock inventory count confirmation (GS §5.3.5).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventoryCountLine(
        @NotBlank String lineReference,
        String articleNumber,
        Integer packSize,
        Integer countedQuantity,
        String loadUnitCode,
        Integer slot
) {}
