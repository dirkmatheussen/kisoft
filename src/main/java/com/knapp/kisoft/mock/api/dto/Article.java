package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * Article - Master data spanning across all pack units for an article.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Article(
        @NotBlank String clientNumber,
        @NotBlank String articleNumber,
        @NotBlank String articleName,
        Boolean isLotRequired,
        Boolean isDateMarkRequired,
        Boolean isSerialNumberRequired,
        String sizeRange,
        Integer sizePosition,
        String sizeName,
        String colorName,
        String colorNumber,
        Double salesPrice,
        String currencyUnit,
        AdditionalProperty[] additionalProperties
) {}
