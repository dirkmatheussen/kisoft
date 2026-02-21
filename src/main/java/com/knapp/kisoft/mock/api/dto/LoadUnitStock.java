package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * LoadUnitStock - Stock info for a load unit slot.
 * packUnit can be PackUnitKeyRef or PackUnitFull (when creating new articles).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoadUnitStock(
        @NotNull Integer slot,
        @NotNull Object packUnit,  // PackUnitKeyRef or PackUnitFull
        @Min(0) Integer quantity,
        String lotNumber,
        String dateMark,
        String serialNumber,
        String stockType,
        String reservationCode,
        String stockQuality
) {}
