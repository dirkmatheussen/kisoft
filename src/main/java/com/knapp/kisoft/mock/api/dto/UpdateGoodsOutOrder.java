package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Update Goods-Out Order (HOST to KiSoft One) — operationId {@code PatchGoodsOutOrder}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateGoodsOutOrder(
        @NotBlank String clientNumber,
        @NotBlank String orderNumber,
        @NotNull Integer sheetNumber,
        Integer priority,
        VasTask[] vasTasks,
        AdditionalProperty[] additionalProperties,
        List<GoodsOutOrderLine> addGoodsOutOrderLines,
        List<String> deleteLinesByReference
) {}
