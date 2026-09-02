package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * Reference to an inventory request — body of Delete Inventory Request
 * (operationId {@code DeleteInventoryRequest}, HIS Appendix §7.2).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventoryRequestRef(
        @NotBlank String clientNumber,
        @NotBlank String requestNumber
) {}
