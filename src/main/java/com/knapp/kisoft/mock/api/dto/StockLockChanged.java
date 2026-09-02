package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Stock Lock Changed (KiSoft One to HOST) — operationId {@code PostStockLockChanged},
 * HIS Appendix §8.1.3. POSTed by the mock to {reply-callback-url}/stockLockChanged every
 * time a lock code is set or removed for a stock object.
 */
@Schema(description = "Payload POSTed by the mock to the callback URL when a stock lock is added/removed (PostStockLockChanged).")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StockLockChanged(
        String eventId,
        StockLockRequestReference stockLockRequestReference,
        Integer processedQuantity,
        List<String> addedStockLocks,
        List<String> removedStockLocks,
        String stationName,
        String reason,
        String userCode,
        String eventTime,
        StockEntry processedStock
) {}
