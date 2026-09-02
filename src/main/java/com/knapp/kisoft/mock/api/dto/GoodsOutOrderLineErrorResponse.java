package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Goods-out order rejection with order- and line-level error codes (Product One API MsgGoodsOutOrderLineErrorResponse)")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoodsOutOrderLineErrorResponse(
        String clientNumber,
        String orderNumber,
        Integer sheetNumber,
        List<String> codes,
        List<LineCodeError> lineCodes
) {}
