package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Reference to the inbound delivery that caused a stock movement (HIS Appendix §8.1.1).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InboundDeliveryReference(
        String clientNumber,
        String inboundDeliveryNumber,
        String lineReference
) {}
