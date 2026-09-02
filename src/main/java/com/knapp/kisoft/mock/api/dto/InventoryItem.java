package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Mock OData read of one ASRS inventory row (not part of the KiSoft One Product API).
 * Flat shape aligned with {@code inventoryRequestLine} / goods-out keys:
 * {@code clientNumber}, {@code articleNumber}, {@code packSize}, plus {@code quantity}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "ASRS inventory item (mock OData read — NOT KiSoft API)")
public record InventoryItem(
        String clientNumber,
        String articleNumber,
        Integer packSize,
        Integer quantity
) {}
