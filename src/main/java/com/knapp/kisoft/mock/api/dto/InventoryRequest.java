package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Create Inventory Request (HOST to KiSoft One) — operationId {@code PostInventoryRequest},
 * HIS Appendix §7.2.2.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventoryRequest(
        @NotBlank String clientNumber,
        @NotBlank String requestNumber,
        @NotNull @Valid InventoryRequestLine inventoryRequestLine,
        Integer priority,
        String businessCase,
        AdditionalProperty[] additionalProperties
) {}
