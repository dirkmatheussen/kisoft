package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Mock-only request body for a spontaneous stock correction (spec 007 / IN-02).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SpontaneousStockCorrection(
        @NotBlank String clientNumber,
        @NotBlank String articleNumber,
        @NotNull Integer packSize,
        @NotNull @Min(0) Integer countedQuantity,
        String reason,
        String stationName
) {}
