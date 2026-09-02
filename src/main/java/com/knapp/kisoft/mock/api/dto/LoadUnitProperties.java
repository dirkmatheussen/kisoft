package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * loadUnitProperties of a Load Unit Moved message (HIS Appendix §7.3.1).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoadUnitProperties(
        Boolean isTouched,
        Double loadUnitHeight,
        Double loadUnitWidth,
        Double loadUnitLength,
        Double loadUnitWeight
) {}
