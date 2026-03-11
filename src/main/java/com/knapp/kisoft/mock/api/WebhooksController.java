package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.InboundDeliveryReply;
import com.knapp.kisoft.mock.api.dto.StorageOrderReply;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Documents the outgoing webhook payloads in Swagger UI. The mock POSTs these to your
 * callback URL (knapp.mock.reply-callback-url) when inbound deliveries or storage orders
 * are created or when storage order status advances. These endpoints are for documentation
 * only; calling them does nothing.
 */
@Tag(name = "Webhooks (outgoing)", description = "Payloads the mock POSTs to your callback URL when reply-callback-url is set")
@RestController
@RequestMapping("/oneapi/v1/_webhooks")
public class WebhooksController {

    @Operation(
            summary = "InboundDeliveryReply payload",
            description = "The mock POSTs this JSON to **{reply-callback-url}/inboundDeliveryReply** when an inbound delivery is created (processingStatus NEW)."
    )
    @ApiResponse(responseCode = "200", description = "Documentation only – this endpoint is not called by the mock")
    @PostMapping(value = "/inboundDeliveryReply", consumes = "application/json")
    public ResponseEntity<Map<String, String>> docInboundDeliveryReply(
            @RequestBody(required = false) @Schema(implementation = InboundDeliveryReply.class) InboundDeliveryReply body) {
        return ResponseEntity.ok(Map.of("message", "Documentation only. The mock POSTs InboundDeliveryReply to your callback URL."));
    }

    @Operation(
            summary = "StorageOrderReply payload",
            description = "The mock POSTs this JSON to **{reply-callback-url}/storageOrderReply** when a storage order is created (NEW) and when status advances to STARTED and FINISHED (random delay 10–60s between transitions)."
    )
    @ApiResponse(responseCode = "200", description = "Documentation only – this endpoint is not called by the mock")
    @PostMapping(value = "/storageOrderReply", consumes = "application/json")
    public ResponseEntity<Map<String, String>> docStorageOrderReply(
            @RequestBody(required = false) @Schema(implementation = StorageOrderReply.class) StorageOrderReply body) {
        return ResponseEntity.ok(Map.of("message", "Documentation only. The mock POSTs StorageOrderReply to your callback URL."));
    }
}
