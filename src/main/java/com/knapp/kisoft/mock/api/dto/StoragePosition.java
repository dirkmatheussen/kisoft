package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * StoragePosition - Destination for storing load unit (storageArea required).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StoragePosition(
        @NotBlank String storageArea,
        String locationNumber
) {}
