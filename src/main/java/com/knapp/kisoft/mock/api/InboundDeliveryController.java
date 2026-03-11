package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.InboundDelivery;
import com.knapp.kisoft.mock.api.dto.InboundDeliveryLine;
import com.knapp.kisoft.mock.api.dto.InboundDeliveryRef;
import com.knapp.kisoft.mock.api.dto.InboundDeliveryReply;
import com.knapp.kisoft.mock.api.dto.UpdateInboundDelivery;
import com.knapp.kisoft.mock.service.MockDataStore;
import com.knapp.kisoft.mock.service.ReplyCallbackService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock KiSoft InboundDelivery API - Goods-In.
 * POST /inboundDelivery, PATCH /inboundDelivery, DELETE /inboundDelivery
 */
@Tag(name = "Goods-In", description = "Inbound delivery (POST, PATCH, DELETE)")
@RestController
@RequestMapping("/oneapi/v1")
public class InboundDeliveryController {

    private final MockDataStore dataStore;
    private final ReplyCallbackService replyCallbackService;

    public InboundDeliveryController(MockDataStore dataStore, ReplyCallbackService replyCallbackService) {
        this.dataStore = dataStore;
        this.replyCallbackService = replyCallbackService;
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inbound delivery created successfully"),
            @ApiResponse(responseCode = "400", description = "Errors detected. lineCode: E-AKO-MOVM-0002 (Order already active), E-AKO-MAST-0001 (Unknown article), E-AKO-GENR-0001, E-AKO-GENR-0002")
    })
    @PostMapping("/inboundDelivery")
    public ResponseEntity<?> postInboundDelivery(
            @RequestBody @Valid InboundDelivery request) {

        if (dataStore.hasInboundDelivery(request.clientNumber(), request.inboundDeliveryNumber())) {
            return ResponseEntity.status(400).body(Map.of(
                    "clientNumber", request.clientNumber(),
                    "inboundDeliveryNumber", request.inboundDeliveryNumber(),
                    "codes", List.of("E-AKO-MOVM-0002")
            ));
        }
        if (request.inboundDeliveryLines() != null) {
            for (InboundDeliveryLine line : request.inboundDeliveryLines()) {
                if (!dataStore.hasPackUnit(request.clientNumber(), line.articleNumber(), line.packSize())) {
                    return ResponseEntity.status(400).body(Map.of(
                            "error", "Bad Request",
                            "message", "Unknown article/packSize: " + line.articleNumber() + "/" + line.packSize(),
                            "codes", List.of("E-AKO-MAST-0001")
                    ));
                }
            }
        }
        dataStore.putInboundDelivery(request, "NEW");

        InboundDeliveryReply reply = new InboundDeliveryReply(
                request.clientNumber(),
                request.inboundDeliveryNumber(),
                request.businessCase() != null ? request.businessCase() : "GOODS_IN",
                "HOST",
                "NEW",
                Instant.now().toString(),
                request.inboundDeliveryLines()
        );
        replyCallbackService.sendInboundDeliveryReply(reply);

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
        response.put("lineCount", request.inboundDeliveryLines() != null ? request.inboundDeliveryLines().size() : 0);
        response.put("message", "Inbound delivery created successfully");
        return ResponseEntity.ok(response);
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inbound delivery updated"),
            @ApiResponse(responseCode = "400", description = "Errors detected. lineCode: E-AKO-MOVM-0003 (Order not found), E-AKO-MOVM-0005 (Wrong order process status)")
    })
    @PatchMapping("/inboundDelivery")
    public ResponseEntity<?> patchInboundDelivery(@RequestBody @Valid UpdateInboundDelivery request) {
        MockDataStore.InboundDeliveryWithStatus existing = dataStore.getInboundDelivery(request.clientNumber(), request.inboundDeliveryNumber());
        if (existing == null) {
            return ResponseEntity.status(400).body(Map.of(
                    "clientNumber", request.clientNumber(),
                    "inboundDeliveryNumber", request.inboundDeliveryNumber(),
                    "codes", List.of("E-AKO-MOVM-0003")
            ));
        }
        String status = existing.getProcessingStatus();
        // NEW: priority, vasTasks, additionalProperties, inboundDeliveryLines (add/delete)
        // STARTED / PROCESSED: only priority
        if ("STARTED".equals(status) || "PROCESSED".equals(status) || "FINISHED".equals(status) || "CANCELLED".equals(status)) {
            if (request.vasTasks() != null || request.additionalProperties() != null
                    || request.addInboundDeliveryLines() != null || request.deleteLinesByReference() != null) {
                return ResponseEntity.status(400).body(Map.of(
                        "clientNumber", request.clientNumber(),
                        "inboundDeliveryNumber", request.inboundDeliveryNumber(),
                        "codes", List.of("E-AKO-MOVM-0005")
                ));
            }
        }
        InboundDelivery current = existing.getDelivery();
        Integer priority = request.priority() != null ? request.priority() : (current.priority() != null ? current.priority() : 1);
        List<InboundDeliveryLine> lines = new ArrayList<>(current.inboundDeliveryLines() != null ? current.inboundDeliveryLines() : List.of());
        if (request.addInboundDeliveryLines() != null) {
            lines.addAll(request.addInboundDeliveryLines());
        }
        if (request.deleteLinesByReference() != null) {
            lines.removeIf(l -> request.deleteLinesByReference().contains(l.lineReference()));
        }
        InboundDelivery updated = new InboundDelivery(
                current.clientNumber(),
                current.inboundDeliveryNumber(),
                current.supplierNumber(),
                priority,
                current.businessCase(),
                request.vasTasks() != null ? request.vasTasks() : current.vasTasks(),
                request.additionalProperties() != null ? request.additionalProperties() : current.additionalProperties(),
                lines.isEmpty() ? null : lines
        );
        existing.setDelivery(updated);
        return ResponseEntity.ok(Map.of(
                "http", 200,
                "status", "OK",
                "message", "Inbound delivery updated"
        ));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inbound delivery deleted"),
            @ApiResponse(responseCode = "400", description = "Errors detected. lineCode: E-AKO-MOVM-0003 (Order not found), E-AKO-MOVM-0005 (Wrong order process status – delete only when status is NEW)")
    })
    @DeleteMapping("/inboundDelivery")
    public ResponseEntity<?> deleteInboundDelivery(@RequestBody @Valid InboundDeliveryRef request) {
        MockDataStore.InboundDeliveryWithStatus existing = dataStore.getInboundDelivery(request.clientNumber(), request.inboundDeliveryNumber());
        if (existing == null) {
            return ResponseEntity.status(400).body(Map.of(
                    "clientNumber", request.clientNumber(),
                    "inboundDeliveryNumber", request.inboundDeliveryNumber(),
                    "codes", List.of("E-AKO-MOVM-0003")
            ));
        }
        if (!"NEW".equals(existing.getProcessingStatus())) {
            return ResponseEntity.status(400).body(Map.of(
                    "clientNumber", request.clientNumber(),
                    "inboundDeliveryNumber", request.inboundDeliveryNumber(),
                    "codes", List.of("E-AKO-MOVM-0005")
            ));
        }
        dataStore.removeInboundDelivery(request.clientNumber(), request.inboundDeliveryNumber());
        return ResponseEntity.ok(Map.of(
                "http", 200,
                "status", "OK",
                "message", "Inbound delivery deleted"
        ));
    }
}
