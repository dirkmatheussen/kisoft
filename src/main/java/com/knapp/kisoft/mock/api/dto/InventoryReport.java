package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Inventory Report (KiSoft One to HOST) — operationId {@code PostInventoryReport},
 * HIS Appendix §8.2.1. POSTed by the mock to {reply-callback-url}/inventoryReport in
 * response to a PostRequestInventoryReport.
 */
@Schema(description = "Payload POSTed by the mock to the callback URL in response to a Request Inventory Report (PostInventoryReport).")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventoryReport(
        String requestNumber,
        List<StockInventory> stockInventory
) {}
