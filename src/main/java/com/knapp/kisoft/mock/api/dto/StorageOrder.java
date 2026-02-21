package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * MsgStorageOrder - Create storage order for load unit.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StorageOrder(
        @NotBlank String clientNumber,
        @NotBlank String orderNumber,
        @NotBlank String loadUnitCode,
        @NotBlank String loadCarrier,
        @NotNull @Valid StoragePosition targetPosition,
        @NotBlank String contentType,
        String businessCase,
        Position handoverPosition,
        StoragePosition possibleFinalTarget,
        List<String> controlFlags,
        AdditionalProperty[] additionalProperties,
        List<@Valid LoadUnitStock> loadUnitStock
) {}
