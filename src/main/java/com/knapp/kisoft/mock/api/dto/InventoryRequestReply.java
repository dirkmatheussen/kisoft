package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Inventory Processing Results (KiSoft One to HOST) — operationId
 * {@code PostInventoryRequestReply}, HIS Appendix §7.2.1. POSTed by the mock to
 * {reply-callback-url}/inventoryRequestReply on processingStatus change.
 */
@Schema(description = "Payload POSTed by the mock to the callback URL on inventory request status change (PostInventoryRequestReply).")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventoryRequestReply(
        String clientNumber,
        String requestNumber,
        List<InventoryRequestReplyLine> inventoryRequestLine,
        String createdBy,
        String processingStatus,
        String statusEventTime,
        String businessCase,
        AdditionalProperty[] additionalProperties
) {}
