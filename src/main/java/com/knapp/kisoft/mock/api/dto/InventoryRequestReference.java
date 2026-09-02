package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * inventoryRequestReference of a Stock Corrected message (HIS Appendix §8.1.2).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventoryRequestReference(
        String clientNumber,
        String requestNumber,
        String lineReference
) {}
