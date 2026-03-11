package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * MsgStorageOrderReply - Reply sent by KiSoft to host (outgoing webhook).
 * OrderKey + createdBy, processingStatus, statusEventTime + loadUnitCode, loadCarrier, contentType.
 */
@Schema(description = "Payload POSTed by the mock to the callback URL when a storage order is created or status changes (NEW → STARTED → FINISHED)")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StorageOrderReply(
        String clientNumber,
        String orderNumber,
        String businessCase,
        String createdBy,
        String processingStatus,
        String statusEventTime,
        String loadUnitCode,
        String loadCarrier,
        String contentType
) {}
