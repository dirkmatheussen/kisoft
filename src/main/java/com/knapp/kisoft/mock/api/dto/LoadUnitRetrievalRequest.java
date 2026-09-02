package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * Mock request to retrieve a source load unit from the AeroBot system (GS §5.3.3).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoadUnitRetrievalRequest(
        @NotBlank String clientNumber,
        @NotBlank String loadUnitCode,
        String loadCarrier,
        String stationName,
        String locationNumber,
        String articleNumber,
        Integer packSize,
        Integer quantity,
        Integer slot,
        String stockType,
        Boolean toConventional
) {}
