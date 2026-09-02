package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Storage Order Reply (KiSoft One to HOST) — optional per load unit during IB-02 decanting.
 * POSTed to {reply-callback-url}/storageOrderReply when mock.storage-order-reply-enabled is true.
 */
@Schema(description = "Optional payload POSTed per load unit when storage-order-reply is enabled (PostStorageOrderReply).")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StorageOrderReply(
        String loadUnitCode,
        String clientNumber,
        String inboundDeliveryNumber,
        String processingStatus,
        String statusEventTime
) {}
