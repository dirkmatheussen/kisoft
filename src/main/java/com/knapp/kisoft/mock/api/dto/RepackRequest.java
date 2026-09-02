package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Mock request to repack/defragment stock between load units (GS §5.3.4).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepackRequest(
        @NotBlank String clientNumber,
        String sourceLoadUnitCode,
        Integer sourceSlot,
        String targetLoadUnitCode,
        Integer targetSlot,
        @NotBlank String articleNumber,
        Integer packSize,
        Integer deltaQuantity,
        String stationName,
        String reason
) {}
