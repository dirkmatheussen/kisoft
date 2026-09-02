package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Goods-out order payload plus KiSoft {@code processingStatus} (mock GET / OData read).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Goods-out order with processing status (mock OData read — NOT KiSoft API)")
public record GoodsOutOrderRead(
        String processingStatus,
        GoodsOutOrder goodsOutOrder
) {}
