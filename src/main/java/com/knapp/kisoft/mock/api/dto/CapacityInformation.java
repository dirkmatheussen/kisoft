package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Capacity info for pack unit in load carrier.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CapacityInformation(
        String loadCarrier,
        Integer maxStoredQuantity
) {}
