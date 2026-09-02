package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Line of a Goods-Out Order Processing Result (HIS Appendix §7.4.2, goodsOutOrderLines).
 * {@code uuid} is mock-only; {@code prjContainerID} is the VOLVO Tacoma customer container id.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoodsOutOrderReplyLine(
        String uuid,
        String prjContainerID,
        String lineReference,
        Integer processedQuantity,
        String processingResult,
        String processingError,
        List<PickedStock> pickedStock
) {}
