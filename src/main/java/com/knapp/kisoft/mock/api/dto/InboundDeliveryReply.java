package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * MsgInboundDeliveryReply - Reply sent by KiSoft to host (outgoing webhook).
 * InboundDeliveryRef + createdBy, processingStatus, statusEventTime, optional businessCase, inboundDeliveryLines.
 */
@Schema(description = "Payload POSTed by the mock to the callback URL when an inbound delivery is created or status changes")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InboundDeliveryReply(
        String clientNumber,
        String inboundDeliveryNumber,
        String businessCase,
        String createdBy,
        String processingStatus,
        String statusEventTime,
        List<InboundDeliveryLine> inboundDeliveryLines
) {}
