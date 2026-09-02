package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * MsgStockReceived - Outgoing webhook payload sent to the host when a goods-in
 * load unit is finished and its stock is pickable in ASRS (IB-02 step 7,
 * Page7 §4 PostStockReceived).
 */
@Schema(description = "Payload POSTed by the mock to the callback URL when a goods-in load unit is stored and pickable.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StockReceived(
        String eventId,
        InboundDeliveryReference inboundDeliveryReference,
        Integer processedQuantity,
        String stationName,
        String reason,
        String userCode,
        String eventTime,
        StockEntry processedStock
) {}
