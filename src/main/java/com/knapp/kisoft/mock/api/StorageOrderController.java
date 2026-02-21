package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.StorageOrder;
import com.knapp.kisoft.mock.service.MockDataStore;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock KiSoft StorageOrder API - Storage/Goods-In.
 * POST /storageOrder, GET /storageOrder, DELETE /storageOrder
 */
@RestController
@RequestMapping("/oneapi/v1")
public class StorageOrderController {

    private final MockDataStore dataStore;

    public StorageOrderController(MockDataStore dataStore) {
        this.dataStore = dataStore;
    }

    @PostMapping("/storageOrder")
    public ResponseEntity<Map<String, Object>> postStorageOrder(
            @RequestBody @Valid StorageOrder request) {

        dataStore.addStorageOrder(request);

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

    @GetMapping("/storageOrder")
    public ResponseEntity<List<StorageOrder>> getStorageOrders() {
        return ResponseEntity.ok(dataStore.getAllStorageOrders());
    }

    @DeleteMapping("/storageOrder")
    public ResponseEntity<Map<String, Object>> deleteStorageOrders() {
        dataStore.clearStorageOrders();
        return ResponseEntity.ok(Map.of(
                "http", 200,
                "status", "OK",
                "message", "All storage orders deleted"
        ));
    }
}
