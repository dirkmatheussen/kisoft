package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Reference to a goods-out order (clientNumber + orderNumber + sheetNumber). Body of the
 * mock operator start / final-check actions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoodsOutOrderRef(
        @NotBlank String clientNumber,
        @NotBlank String orderNumber,
        @NotNull Integer sheetNumber
) {}
