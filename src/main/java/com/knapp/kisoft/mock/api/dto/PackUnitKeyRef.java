package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * PackUnitKeyRef - Reference to pack unit by client, article and pack size.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PackUnitKeyRef(
        @NotBlank String clientNumber,
        @NotBlank String articleNumber,
        @NotNull Integer packSize
) {}
