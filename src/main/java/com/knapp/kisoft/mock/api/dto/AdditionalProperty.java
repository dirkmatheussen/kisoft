package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Key-value pair for additional properties.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdditionalProperty(
        String key,
        String value
) {}
