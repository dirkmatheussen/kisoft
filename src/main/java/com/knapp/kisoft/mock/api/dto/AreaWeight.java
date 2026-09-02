package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * areaWeights entry of a Goods-Out Order (HIS Appendix §7.4.1). Holds either an
 * expected weight or a min/max weight range for a scale station.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AreaWeight(
        String scaleStationName,
        Double expectedWeight,
        Double minimumWeight,
        Double maximumWeight
) {}
