package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Position - Workstation and/or location in warehouse.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Position(
        String stationName,
        String locationNumber
) {}
