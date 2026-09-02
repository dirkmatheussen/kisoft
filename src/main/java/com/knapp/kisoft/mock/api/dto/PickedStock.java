package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * pickedStock entry of a Goods-Out Order Processing Result (HIS Appendix §7.4.2).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PickedStock(
        Integer processedQuantity,
        String articleNumber,
        Integer packSize,
        String stockType,
        String lotNumber,
        String dateMark,
        String reservationCode,
        String serialNumber,
        List<String> stockLockReasons
) {}
