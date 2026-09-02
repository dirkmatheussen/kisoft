package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * goodsOutOrderReference of a Stock Corrected message (HIS Appendix §8.1.2).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoodsOutOrderReference(
        String clientNumber,
        String orderNumber,
        Integer sheetNumber,
        String lineReference
) {}
