package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Mock operator picking confirmation for a goods-out order (GS §5.2.3).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoodsOutPickConfirmation(
        @NotBlank String clientNumber,
        @NotBlank String orderNumber,
        @NotNull Integer sheetNumber,
        List<@Valid GoodsOutPickLine> lines
) {}
