package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * stockInventory entry of an Inventory Report (HIS Appendix §8.2.1). {@code packUnit}
 * reuses {@link PackUnitKeyRef} (clientNumber, articleNumber, packSize).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StockInventory(
        PackUnitKeyRef packUnit,
        Integer quantity,
        String stockType,
        String lotNumber,
        String dateMark,
        String serialNumber,
        String reservationCode,
        List<String> stockLockReasons
) {}
