package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * newPosition of a Load Unit Moved message (HIS Appendix §7.3.1).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NewPosition(
        String stationName,
        String locationNumber,
        String locationZPosition
) {}
