package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Mock OData read of one ASRS inventory row (not part of the KiSoft One Product API).
 * Shape matches {@link StockInventory} / inventory-report stock lines:
 * nested {@code packUnit} plus quantity and stock attributes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "ASRS inventory item (mock OData read — NOT KiSoft API)")
public record InventoryItem(
        PackUnitKeyRef packUnit,
        Integer quantity,
        String stockType,
        String lotNumber,
        String dateMark,
        String serialNumber,
        String reservationCode,
        List<String> stockLockReasons
) {}
