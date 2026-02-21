package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * PackUnitFull - Pack unit defines article quantity held as a unit.
 * Required: article, packSize.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PackUnitFull(
        @NotNull @Valid Article article,
        @NotBlank String packSize,
        Integer length,
        Integer width,
        Integer height,
        Integer pocketedWidth,
        Double weight,
        List<String> opticalCodes,
        List<String> articleFeatures,
        String loadCarrier,
        String articleImageLink,
        CapacityInformation capacityInformation
) {}
