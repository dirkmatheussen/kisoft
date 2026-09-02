package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Shared stock entry used by the outgoing stock/movement messages as {@code processedStock}
 * (StockReceived/StockCorrected/StockLockChanged §8.1) and {@code loadUnitStock}
 * (LoadUnitMoved §7.3.1). {@code packUnit} reuses {@link PackUnitKeyRef}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StockEntry(
        String loadUnitCode,
        Integer slot,
        PackUnitKeyRef packUnit,
        Integer quantity,
        String stockType,
        String lotNumber,
        String dateMark,
        String serialNumber,
        String reservationCode,
        List<String> stockLockReasons,
        String stockQuality,
        String receiptDate,
        String lastInventoryDate
) {}
