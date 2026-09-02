package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * One picked line in a mock goods-out picking confirmation (GS §5.2.3).
 * {@code pickedQuantity} below the requested quantity is a short pick; {@code damaged}
 * marks the source slot as damaged (locked + marked for inventory).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoodsOutPickLine(
        @NotBlank String lineReference,
        Integer pickedQuantity,
        String sourceLoadUnitCode,
        Integer slot,
        Boolean damaged
) {}
