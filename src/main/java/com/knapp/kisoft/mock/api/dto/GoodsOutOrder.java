package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Create Goods-Out Order (HOST to KiSoft One) — operationId {@code PostGoodsOutOrder}.
 * Mirrors HIS Appendix §7.4.1. Customer scope: Goods-Out (Outbound Delivery is NOT in scope).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoodsOutOrder(
        @NotBlank String clientNumber,
        @NotBlank String orderNumber,
        @NotNull Integer sheetNumber,
        @NotBlank String loadCarrier,
        Integer priority,
        String businessCase,
        String startStationName,
        String loadUnitCode,
        String departureTime,
        String departureDate,
        String customerNumber,
        String routeNumber,
        List<AreaWeight> areaWeights,
        List<Integer> dispatchRampNumbers,
        VasTask[] vasTasks,
        AdditionalProperty[] additionalProperties,
        List<String> controlFlags,
        List<String> transportTargets,
        List<PrintDocument> printDocuments,
        List<@Valid GoodsOutOrderLine> goodsOutOrderLines
) {}
