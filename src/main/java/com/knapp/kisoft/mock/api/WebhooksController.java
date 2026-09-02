package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.CallbackDeliveryResult;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderReply;
import com.knapp.kisoft.mock.api.dto.InboundDeliveryReply;
import com.knapp.kisoft.mock.api.dto.InventoryReport;
import com.knapp.kisoft.mock.api.dto.InventoryRequestReply;
import com.knapp.kisoft.mock.api.dto.LoadUnitMoved;
import com.knapp.kisoft.mock.api.dto.OneApiOkResponse;
import com.knapp.kisoft.mock.api.dto.StockCorrected;
import com.knapp.kisoft.mock.api.dto.StockLockChanged;
import com.knapp.kisoft.mock.api.dto.StockReceived;
import com.knapp.kisoft.mock.api.dto.StorageCapacityReport;
import com.knapp.kisoft.mock.api.dto.StorageOrderReply;
import com.knapp.kisoft.mock.config.KnappMockProperties;
import com.knapp.kisoft.mock.service.ReplyCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Swagger-accessible triggers for KiSoft One → HOST callbacks. Each POST accepts the reply payload
 * and forwards it to IBM APIC at {@code {reply-callback-url}/{messageName}} via
 * {@link ReplyCallbackService}. Use {@code wait=true} (default) to include the APIC response in
 * the HTTP reply for Swagger testing.
 */
@Tag(name = "Webhooks (outgoing)",
        description = "KiSoft One → HOST callbacks. Try it out uses the current app host "
                + "(e.g. http://localhost:8084/kisoft). Use wait=true (default) to see the IBM APIC response.")
@SecurityRequirements
@RestController
@RequestMapping("/oneapi/v1/_webhooks")
public class WebhooksController {

    private final KnappMockProperties properties;
    private final ReplyCallbackService callbacks;

    public WebhooksController(KnappMockProperties properties, ReplyCallbackService callbacks) {
        this.properties = properties;
        this.callbacks = callbacks;
    }

    private ResponseEntity<OneApiOkResponse> dispatch(
            String pathKey,
            Object payload,
            String messageName,
            boolean wait,
            Runnable asyncSend) {
        if (!properties.areCallbacksEnabled()) {
            return ResponseEntity.status(503).body(new OneApiOkResponse(
                    503,
                    "UNAVAILABLE",
                    "Outgoing callbacks disabled — set knapp.mock.reply-callback-enabled=true "
                            + "and knapp.mock.reply-callback-url"));
        }
        String target = properties.webhookTargetUrl(pathKey);
        if (wait) {
            CallbackDeliveryResult callback = callbacks.deliverSync(pathKey, payload, messageName)
                    .orElse(null);
            return WebhookWaitResponse.of("Callback delivered to POST " + target, messageName, callback);
        }
        asyncSend.run();
        return ResponseEntity.accepted().body(new OneApiOkResponse(
                202,
                "ACCEPTED",
                "Callback dispatched to POST " + target));
    }

    @Operation(operationId = "PostInboundDeliveryReply",
            summary = "InboundDeliveryReply (PostInboundDeliveryReply)",
            description = "Forwards the payload to IBM APIC. Set wait=true (default) to return the APIC HTTP status and body.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Callback delivered (wait=true); callback field contains APIC response"),
            @ApiResponse(responseCode = "202", description = "Callback queued (wait=false)"),
            @ApiResponse(responseCode = "503", description = "Callbacks disabled (reply-callback-url not set)")
    })
    @PostMapping(value = "/inboundDeliveryReply", consumes = "application/json")
    public ResponseEntity<OneApiOkResponse> sendInboundDeliveryReply(
            @RequestBody @Schema(implementation = InboundDeliveryReply.class) InboundDeliveryReply body,
            @Parameter(description = "Wait for IBM APIC response and include it in the reply (recommended for Swagger)")
            @RequestParam(defaultValue = "true") boolean wait) {
        return dispatch("inboundDeliveryReply", body, "InboundDeliveryReply", wait,
                () -> callbacks.sendInboundDeliveryReply(body));
    }

    @Operation(operationId = "PostGoodsOutOrderReply", summary = "GoodsOutOrderReply (PostGoodsOutOrderReply)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Callback delivered (wait=true)"),
            @ApiResponse(responseCode = "202", description = "Callback queued (wait=false)"),
            @ApiResponse(responseCode = "503", description = "Callbacks disabled")
    })
    @PostMapping(value = "/goodsOutOrderReply", consumes = "application/json")
    public ResponseEntity<OneApiOkResponse> sendGoodsOutOrderReply(
            @RequestBody @Schema(implementation = GoodsOutOrderReply.class) GoodsOutOrderReply body,
            @RequestParam(defaultValue = "true") boolean wait) {
        return dispatch("goodsOutOrderReply", body, "GoodsOutOrderReply", wait,
                () -> callbacks.sendGoodsOutOrderReply(body));
    }

    @Operation(operationId = "PostInventoryRequestReply", summary = "InventoryRequestReply (PostInventoryRequestReply)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Callback delivered (wait=true)"),
            @ApiResponse(responseCode = "202", description = "Callback queued (wait=false)"),
            @ApiResponse(responseCode = "503", description = "Callbacks disabled")
    })
    @PostMapping(value = "/inventoryRequestReply", consumes = "application/json")
    public ResponseEntity<OneApiOkResponse> sendInventoryRequestReply(
            @RequestBody @Schema(implementation = InventoryRequestReply.class) InventoryRequestReply body,
            @RequestParam(defaultValue = "true") boolean wait) {
        return dispatch("inventoryRequestReply", body, "InventoryRequestReply", wait,
                () -> callbacks.sendInventoryRequestReply(body));
    }

    @Operation(operationId = "PostLoadUnitMoved", summary = "LoadUnitMoved (PostLoadUnitMoved)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Callback delivered (wait=true)"),
            @ApiResponse(responseCode = "202", description = "Callback queued (wait=false)"),
            @ApiResponse(responseCode = "503", description = "Callbacks disabled")
    })
    @PostMapping(value = "/loadUnitMoved", consumes = "application/json")
    public ResponseEntity<OneApiOkResponse> sendLoadUnitMoved(
            @RequestBody @Schema(implementation = LoadUnitMoved.class) LoadUnitMoved body,
            @RequestParam(defaultValue = "true") boolean wait) {
        return dispatch("loadUnitMoved", body, "LoadUnitMoved", wait,
                () -> callbacks.sendLoadUnitMoved(body));
    }

    @Operation(operationId = "PostStockReceived", summary = "StockReceived (PostStockReceived)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Callback delivered (wait=true)"),
            @ApiResponse(responseCode = "202", description = "Callback queued (wait=false)"),
            @ApiResponse(responseCode = "503", description = "Callbacks disabled")
    })
    @PostMapping(value = "/stockReceived", consumes = "application/json")
    public ResponseEntity<OneApiOkResponse> sendStockReceived(
            @RequestBody @Schema(implementation = StockReceived.class) StockReceived body,
            @RequestParam(defaultValue = "true") boolean wait) {
        return dispatch("stockReceived", body, "StockReceived", wait,
                () -> callbacks.sendStockReceived(body));
    }

    @Operation(operationId = "PostStockCorrected", summary = "StockCorrected (PostStockCorrected)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Callback delivered (wait=true)"),
            @ApiResponse(responseCode = "202", description = "Callback queued (wait=false)"),
            @ApiResponse(responseCode = "503", description = "Callbacks disabled")
    })
    @PostMapping(value = "/stockCorrected", consumes = "application/json")
    public ResponseEntity<OneApiOkResponse> sendStockCorrected(
            @RequestBody @Schema(implementation = StockCorrected.class) StockCorrected body,
            @RequestParam(defaultValue = "true") boolean wait) {
        return dispatch("stockCorrected", body, "StockCorrected", wait,
                () -> callbacks.sendStockCorrected(body));
    }

    @Operation(operationId = "PostStockLockChanged", summary = "StockLockChanged (PostStockLockChanged)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Callback delivered (wait=true)"),
            @ApiResponse(responseCode = "202", description = "Callback queued (wait=false)"),
            @ApiResponse(responseCode = "503", description = "Callbacks disabled")
    })
    @PostMapping(value = "/stockLockChanged", consumes = "application/json")
    public ResponseEntity<OneApiOkResponse> sendStockLockChanged(
            @RequestBody @Schema(implementation = StockLockChanged.class) StockLockChanged body,
            @RequestParam(defaultValue = "true") boolean wait) {
        return dispatch("stockLockChanged", body, "StockLockChanged", wait,
                () -> callbacks.sendStockLockChanged(body));
    }

    @Operation(operationId = "PostInventoryReport", summary = "InventoryReport (PostInventoryReport)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Callback delivered (wait=true)"),
            @ApiResponse(responseCode = "202", description = "Callback queued (wait=false)"),
            @ApiResponse(responseCode = "503", description = "Callbacks disabled")
    })
    @PostMapping(value = "/inventoryReport", consumes = "application/json")
    public ResponseEntity<OneApiOkResponse> sendInventoryReport(
            @RequestBody @Schema(implementation = InventoryReport.class) InventoryReport body,
            @RequestParam(defaultValue = "true") boolean wait) {
        return dispatch("inventoryReport", body, "InventoryReport", wait,
                () -> callbacks.sendInventoryReport(body));
    }

    @Operation(operationId = "PostStorageOrderReply", summary = "StorageOrderReply (PostStorageOrderReply)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Callback delivered (wait=true)"),
            @ApiResponse(responseCode = "202", description = "Callback queued (wait=false)"),
            @ApiResponse(responseCode = "503", description = "Callbacks disabled")
    })
    @PostMapping(value = "/storageOrderReply", consumes = "application/json")
    public ResponseEntity<OneApiOkResponse> sendStorageOrderReply(
            @RequestBody @Schema(implementation = StorageOrderReply.class) StorageOrderReply body,
            @RequestParam(defaultValue = "true") boolean wait) {
        return dispatch("storageOrderReply", body, "StorageOrderReply", wait,
                () -> callbacks.sendStorageOrderReply(body));
    }

    @Operation(operationId = "PostStorageCapacityReport", summary = "StorageCapacityReport (PostStorageCapacityReport)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Callback delivered (wait=true)"),
            @ApiResponse(responseCode = "202", description = "Callback queued (wait=false)"),
            @ApiResponse(responseCode = "503", description = "Callbacks disabled")
    })
    @PostMapping(value = "/storageCapacityReport", consumes = "application/json")
    public ResponseEntity<OneApiOkResponse> sendStorageCapacityReport(
            @RequestBody @Schema(implementation = StorageCapacityReport.class) StorageCapacityReport body,
            @RequestParam(defaultValue = "true") boolean wait) {
        return dispatch("storageCapacityReport", body, "StorageCapacityReport", wait,
                () -> callbacks.sendStorageCapacityReport(body));
    }
}
