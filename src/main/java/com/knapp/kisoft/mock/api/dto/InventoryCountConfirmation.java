package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Mock operator inventory count confirmation (GS §5.3.5). Records the counted quantities for an
 * inventory request; the request is then closed (PostInventoryRequestReply FINISHED).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventoryCountConfirmation(
        @NotBlank String clientNumber,
        @NotBlank String requestNumber,
        List<@Valid InventoryCountLine> lines
) {}
