package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Goods-Out Order Processing Results (KiSoft One to HOST) — operationId
 * {@code PostGoodsOutOrderReply}, HIS Appendix §7.4.2. POSTed by the mock to
 * {reply-callback-url}/goodsOutOrderReply on every processingStatus change.
 */
@Schema(description = "Payload POSTed by the mock to the callback URL on goods-out order status change (PostGoodsOutOrderReply).")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoodsOutOrderReply(
        String clientNumber,
        String orderNumber,
        Integer sheetNumber,
        String createdBy,
        String processingStatus,
        String businessCase,
        String statusEventTime,
        String loadUnitCode,
        String loadCarrier,
        String customerNumber,
        Double loadUnitGrossWeight,
        List<GoodsOutOrderReplyLine> goodsOutOrderLines
) {}
