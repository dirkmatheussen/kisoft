package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.InboundDelivery;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Mock KiSoft InboundDelivery API - Goods-In.
 * POST /inboundDelivery
 */
@RestController
@RequestMapping("/oneapi/v1")
public class InboundDeliveryController {

    @PostMapping("/inboundDelivery")
    public ResponseEntity<Map<String, Object>> postInboundDelivery(
            @RequestBody @Valid InboundDelivery request) {

        int lineCount = request.inboundDeliveryLines() != null ? request.inboundDeliveryLines().size() : 0;

        var response = new HashMap<String, Object>();
        response.put("http", 200);
        response.put("clientNumber", request.clientNumber());
        response.put("inboundDeliveryNumber", request.inboundDeliveryNumber());
        response.put("supplierNumber", request.supplierNumber());
        response.put("businessCase", request.businessCase() != null ? request.businessCase() : "GOODS_IN");
        response.put("priority", request.priority() != null ? request.priority() : 1);
        response.put("processingStatus", "NEW");
        response.put("createdBy", "HOST");
        response.put("statusEventTime", Instant.now().toString());
        response.put("lineCount", lineCount);
        response.put("message", "Inbound delivery created successfully");
        return ResponseEntity.ok(response);
    }
}
