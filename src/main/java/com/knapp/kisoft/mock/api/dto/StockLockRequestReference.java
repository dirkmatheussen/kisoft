package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * stockLockRequestReference of a Stock Lock Changed message (HIS Appendix §8.1.3).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StockLockRequestReference(
        String clientNumber,
        String requestNumber
) {}
