package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * Request an Inventory Report (HOST to KiSoft One) — operationId
 * {@code PostRequestInventoryReport}, HIS Appendix §8.2.2.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestInventoryReport(
        @NotBlank String requestNumber,
        String clientNumber,
        String articleNumber,
        Integer packSize,
        String stockType,
        String lotNumber,
        String dateMark,
        String serialNumber,
        String reservationCode,
        String storageArea
) {}
