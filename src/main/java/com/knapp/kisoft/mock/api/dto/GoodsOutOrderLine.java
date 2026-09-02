package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Line of a Create Goods-Out Order message (HIS Appendix §7.4.1, goodsOutOrderLines).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoodsOutOrderLine(
        @NotBlank String lineReference,
        @NotNull Integer requestedQuantity,
        @NotBlank String articleNumber,
        Integer packSize,
        String stockType,
        String lotNumber,
        String dateMark,
        @NotBlank String reservationCode,
        String stationName,
        String locationNumber,
        String noteOnProcessing,
        AdditionalProperty[] additionalProperties,
        List<PrintDocument> printDocuments,
        String selectionStrategy
) {}
