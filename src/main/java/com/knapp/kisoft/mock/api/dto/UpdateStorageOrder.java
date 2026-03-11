package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * MsgStorageOrderUpdate - PATCH body for storage order.
 * OrderKey + optional handoverPosition, targetPosition, possibleFinalTarget, controlFlags, additionalProperties, areaWeights.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateStorageOrder(
        @NotBlank String clientNumber,
        @NotBlank String orderNumber,
        @Valid Position handoverPosition,
        @Valid StoragePosition targetPosition,
        StoragePosition possibleFinalTarget,
        List<String> controlFlags,
        AdditionalProperty[] additionalProperties,
        Object areaWeights
) {}
