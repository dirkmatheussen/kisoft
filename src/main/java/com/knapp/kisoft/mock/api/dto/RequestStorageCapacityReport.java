package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * Request a Storage Capacity Report (HOST to KiSoft One) — operationId
 * {@code PostRequestStorageCapacityReport}, HIS Appendix §8.3.2.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestStorageCapacityReport(
        @NotBlank String requestNumber,
        String storageArea
) {}
