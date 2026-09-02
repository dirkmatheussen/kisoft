package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * OData v4 JSON collection response ({@code @odata.context} + {@code value} array).
 * Used only by mock inspection GET endpoints — **not part of the KiSoft One Product API**.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "OData v4 collection response (mock-only; not KiSoft API)")
public record ODataCollectionResponse<T>(
        @JsonProperty("@odata.context")
        @Schema(description = "OData metadata context URI", example = "/kisoft/oneapi/v1/$metadata#PackUnits")
        String context,
        @JsonProperty("@odata.count")
        @Schema(description = "Total number of matching records (when $count=true)")
        Integer count,
        @Schema(description = "Entity collection")
        List<T> value
) {}
