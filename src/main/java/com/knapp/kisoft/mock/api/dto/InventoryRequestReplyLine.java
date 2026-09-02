package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * inventoryRequestLine entry of an Inventory Processing Result (HIS Appendix §7.2.1).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventoryRequestReplyLine(
        String lineReference,
        Integer processedQuantity
) {}
