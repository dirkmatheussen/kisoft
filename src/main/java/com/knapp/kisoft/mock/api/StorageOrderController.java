package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.OrderKey;
import com.knapp.kisoft.mock.api.dto.StorageOrder;
import com.knapp.kisoft.mock.api.dto.StorageOrderReply;
import com.knapp.kisoft.mock.api.dto.UpdateStorageOrder;
import com.knapp.kisoft.mock.service.MockDataStore;
import com.knapp.kisoft.mock.service.ReplyCallbackService;
import com.knapp.kisoft.mock.service.StorageOrderStatusAdvancer;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock KiSoft StorageOrder API - Storage/Goods-In.
 * POST /storageOrder, GET /storageOrder, PATCH /storageOrder, DELETE /storageOrder
 */
@Tag(name = "Goods-In", description = "Storage order (POST, GET, PATCH, DELETE); status auto-advances NEW→STARTED→FINISHED with webhooks")
@RestController
@RequestMapping("/oneapi/v1")
public class StorageOrderController {

    private final MockDataStore dataStore;
    private final ReplyCallbackService replyCallbackService;
    private final StorageOrderStatusAdvancer statusAdvancer;

    public StorageOrderController(MockDataStore dataStore, ReplyCallbackService replyCallbackService,
                                  StorageOrderStatusAdvancer statusAdvancer) {
        this.dataStore = dataStore;
        this.replyCallbackService = replyCallbackService;
        this.statusAdvancer = statusAdvancer;
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Storage order created successfully"),
            @ApiResponse(responseCode = "400", description = "Errors detected. lineCode: E-AKO-MOVM-0002 (Order already active), E-AKO-GENR-0001, E-AKO-GENR-0002 (e.g. max records)")
    })
    @PostMapping("/storageOrder")
    public ResponseEntity<?> postStorageOrder(
            @RequestBody @Valid StorageOrder request) {

        if (dataStore.hasStorageOrder(request.clientNumber(), request.orderNumber())) {
            return ResponseEntity.status(400).body(Map.of(
                    "clientNumber", request.clientNumber(),
                    "orderNumber", request.orderNumber(),
                    "codes", List.of("E-AKO-MOVM-0002")
            ));
        }
        if (dataStore.getStorageOrderCount() >= dataStore.getMaxRecords()) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", "Bad Request",
                    "message", "No more then " + dataStore.getMaxRecords() + " records allowed"
            ));
        }
        dataStore.putStorageOrder(request, "NEW");

        StorageOrderReply reply = new StorageOrderReply(
                request.clientNumber(),
                request.orderNumber(),
                request.businessCase() != null ? request.businessCase() : "GOODS_IN",
                "HOST",
                "NEW",
                Instant.now().toString(),
                request.loadUnitCode(),
                request.loadCarrier(),
                request.contentType()
        );
        replyCallbackService.sendStorageOrderReply(reply);
        statusAdvancer.scheduleTransitions(request.clientNumber(), request.orderNumber());

        var response = new HashMap<String, Object>();
        response.put("http", 200);
        response.put("clientNumber", request.clientNumber());
        response.put("orderNumber", request.orderNumber());
        response.put("loadUnitCode", request.loadUnitCode());
        response.put("loadCarrier", request.loadCarrier());
        response.put("contentType", request.contentType());
        response.put("businessCase", request.businessCase() != null ? request.businessCase() : "GOODS_IN");
        response.put("processingStatus", "NEW");
        response.put("createdBy", "HOST");
        response.put("statusEventTime", Instant.now().toString());
        response.put("message", "Storage order created successfully");
        return ResponseEntity.ok(response);
    }

    @ApiResponse(responseCode = "200", description = "List of all storage orders")
    @GetMapping("/storageOrder")
    public ResponseEntity<List<StorageOrder>> getStorageOrders() {
        return ResponseEntity.ok(dataStore.getAllStorageOrders());
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Storage order updated"),
            @ApiResponse(responseCode = "400", description = "Errors detected. lineCode: E-AKO-MOVM-0003 (Order not found), E-AKO-MOVM-0005 (Wrong order process status)")
    })
    @PatchMapping("/storageOrder")
    public ResponseEntity<?> patchStorageOrder(@RequestBody @Valid UpdateStorageOrder request) {
        MockDataStore.StorageOrderWithStatus existing = dataStore.getStorageOrder(request.clientNumber(), request.orderNumber());
        if (existing == null) {
            return ResponseEntity.status(400).body(Map.of(
                    "clientNumber", request.clientNumber(),
                    "orderNumber", request.orderNumber(),
                    "codes", List.of("E-AKO-MOVM-0003")
            ));
        }
        String status = existing.getProcessingStatus();
        if ("FINISHED".equals(status) || "CANCELLED".equals(status)) {
            return ResponseEntity.status(400).body(Map.of(
                    "clientNumber", request.clientNumber(),
                    "orderNumber", request.orderNumber(),
                    "codes", List.of("E-AKO-MOVM-0005")
            ));
        }
        if ("STARTED".equals(status) && request.handoverPosition() != null) {
            return ResponseEntity.status(400).body(Map.of(
                    "clientNumber", request.clientNumber(),
                    "orderNumber", request.orderNumber(),
                    "codes", List.of("E-AKO-MOVM-0005")
            ));
        }
        StorageOrder current = existing.getOrder();
        StorageOrder updated = new StorageOrder(
                current.clientNumber(),
                current.orderNumber(),
                current.loadUnitCode(),
                current.loadCarrier(),
                request.targetPosition() != null ? request.targetPosition() : current.targetPosition(),
                current.contentType(),
                current.businessCase(),
                request.handoverPosition() != null ? request.handoverPosition() : current.handoverPosition(),
                request.possibleFinalTarget() != null ? request.possibleFinalTarget() : current.possibleFinalTarget(),
                request.controlFlags() != null ? request.controlFlags() : current.controlFlags(),
                request.additionalProperties() != null ? request.additionalProperties() : current.additionalProperties(),
                current.loadUnitStock()
        );
        existing.setOrder(updated);
        return ResponseEntity.ok(Map.of(
                "http", 200,
                "status", "OK",
                "message", "Storage order updated"
        ));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Storage order deleted (or all if no body)"),
            @ApiResponse(responseCode = "400", description = "Errors detected. lineCode: E-AKO-MOVM-0003 (Order not found), E-AKO-MOVM-0005 (Wrong order process status – delete only when status is NEW)")
    })
    @DeleteMapping("/storageOrder")
    public ResponseEntity<?> deleteStorageOrder(@RequestBody(required = false) OrderKey body) {
        if (body == null) {
            dataStore.clearStorageOrders();
            return ResponseEntity.ok(Map.of(
                    "http", 200,
                    "status", "OK",
                    "message", "All storage orders deleted"
            ));
        }
        MockDataStore.StorageOrderWithStatus existing = dataStore.getStorageOrder(body.clientNumber(), body.orderNumber());
        if (existing == null) {
            return ResponseEntity.status(400).body(Map.of(
                    "clientNumber", body.clientNumber(),
                    "orderNumber", body.orderNumber(),
                    "codes", List.of("E-AKO-MOVM-0003")
            ));
        }
        if (!"NEW".equals(existing.getProcessingStatus())) {
            return ResponseEntity.status(400).body(Map.of(
                    "clientNumber", body.clientNumber(),
                    "orderNumber", body.orderNumber(),
                    "codes", List.of("E-AKO-MOVM-0005")
            ));
        }
        dataStore.removeStorageOrder(body.clientNumber(), body.orderNumber());
        return ResponseEntity.ok(Map.of(
                "http", 200,
                "status", "OK",
                "message", "Storage order deleted"
        ));
    }
}
