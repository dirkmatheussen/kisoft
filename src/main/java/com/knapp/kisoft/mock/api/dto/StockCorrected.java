package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Stock Corrected (KiSoft One to HOST) — operationId {@code PostStockCorrected},
 * HIS Appendix §8.1.2. POSTed by the mock to {reply-callback-url}/stockCorrected when a
 * stock correction occurs (e.g. as the result of an inventory request).
 */
@Schema(description = "Payload POSTed by the mock to the callback URL when stock is corrected (PostStockCorrected).")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StockCorrected(
        String eventId,
        InventoryRequestReference inventoryRequestReference,
        GoodsOutOrderReference goodsOutOrderReference,
        Integer deltaQuantity,
        String stationName,
        String reason,
        String extendedReason,
        String userCode,
        String eventTime,
        StockEntry processedStock
) {}
