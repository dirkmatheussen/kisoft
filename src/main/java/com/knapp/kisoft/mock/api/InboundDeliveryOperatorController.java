package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.InboundDeliveryLoadUnitReceipt;
import com.knapp.kisoft.mock.api.dto.InboundDeliveryRef;
import com.knapp.kisoft.mock.api.dto.OneApiErrorResponse;
import com.knapp.kisoft.mock.api.dto.OneApiOkResponse;
import com.knapp.kisoft.mock.service.InboundDeliveryLifecycleService;
import com.knapp.kisoft.mock.service.InboundDeliveryLifecycleService.Code;
import com.knapp.kisoft.mock.service.InboundDeliveryLifecycleService.LifecycleResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Mock-only endpoints that simulate the operator-driven decanting flow at the
 * KiSoft / WCS station (IB-02 MF-3..MF-7). They are not part of the formal
 * KiSoft OneAPI; they let host integrators trigger the corresponding outgoing
 * webhooks (PostInboundDeliveryReply STARTED/FINISHED, PostStockReceived) via
 * Swagger UI.
 */
@Tag(name = "Goods-In Operator (mock)",
        description = "Mock endpoints that simulate the operator-driven IB-02 decanting flow. " +
                "Triggers PostInboundDeliveryReply STARTED/FINISHED and PostStockReceived webhooks.")
@RestController
@RequestMapping("/oneapi/v1/inboundDelivery/operator")
public class InboundDeliveryOperatorController {

    private final InboundDeliveryLifecycleService lifecycle;

    public InboundDeliveryOperatorController(InboundDeliveryLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Operation(operationId = "OperatorStartInboundDelivery",
            summary = "Operator starts processing the inbound delivery",
            description = "Transitions inbound delivery NEW → STARTED and emits PostInboundDeliveryReply(STARTED). " +
                    "Mirrors IB-02 MF-3 / Page7 §3 in the asrs-specs use case.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Processing started"),
            @ApiResponse(responseCode = "400", description = "Errors detected. lineCode: E-AKO-MOVM-0003 (not found), E-AKO-MOVM-0004 (wrong status, e.g. already STARTED/FINISHED)")
    })
    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody @Valid InboundDeliveryRef ref) {
        LifecycleResult res = lifecycle.startProcessing(ref.clientNumber(), ref.inboundDeliveryNumber());
        return toResponse(res, ref.clientNumber(), ref.inboundDeliveryNumber(),
                "Processing started, PostInboundDeliveryReply(STARTED) sent");
    }

    @Operation(operationId = "OperatorReceiveLoadUnit",
            summary = "Operator confirms a goods-in load unit (decant + inject)",
            description = "Updates per-line received quantity, stores tote-compartment occupancy, increments ASRS stock " +
                    "and emits PostStockReceived. Enforces IB-02 EF-04 (qty > open), EF-06 (no topping-up) and EF-14 (mixed SKU). " +
                    "When all lines are fully received, KiSoft auto-finishes and emits PostInboundDeliveryReply(FINISHED).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Load unit recorded"),
            @ApiResponse(responseCode = "400", description = "Errors detected. lineCode: " +
                    "E-AKO-MOVM-0003 (not found), E-AKO-MOVM-0004 (wrong status), " +
                    "E-AKO-MOVM-0006 (qty exceeds open), E-AKO-MOVM-0007 (line not found), " +
                    "E-AKO-MOVM-0008 (compartment not empty), E-AKO-MOVM-0009 (mixed SKU in compartment)")
    })
    @PostMapping("/loadUnit")
    public ResponseEntity<?> loadUnit(@RequestBody @Valid InboundDeliveryLoadUnitReceipt body) {
        LifecycleResult res = lifecycle.recordLoadUnit(body);
        if (res.code() != Code.OK) {
            return toResponse(res, body.clientNumber(), body.inboundDeliveryNumber(), null);
        }
        String msg = res.finished()
                ? "Load unit stored, PostStockReceived sent, delivery FINISHED"
                : "Load unit stored, PostStockReceived sent";
        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", msg));
    }

    @Operation(operationId = "OperatorFinishInboundDelivery",
            summary = "Operator forces processing to FINISHED",
            description = "Transitions inbound delivery STARTED → FINISHED and emits PostInboundDeliveryReply(FINISHED). " +
                    "Use to close out short receipts (IB-02 EF-05).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Processing finished"),
            @ApiResponse(responseCode = "400", description = "Errors detected. lineCode: E-AKO-MOVM-0003 (not found), E-AKO-MOVM-0004 (wrong status)")
    })
    @PostMapping("/finish")
    public ResponseEntity<?> finish(@RequestBody @Valid InboundDeliveryRef ref) {
        LifecycleResult res = lifecycle.finishProcessing(ref.clientNumber(), ref.inboundDeliveryNumber());
        return toResponse(res, ref.clientNumber(), ref.inboundDeliveryNumber(),
                "Processing finished, PostInboundDeliveryReply(FINISHED) sent");
    }

    private ResponseEntity<?> toResponse(LifecycleResult res, String client, String idn, String successMsg) {
        if (res.code() == Code.OK) {
            return ResponseEntity.ok(new OneApiOkResponse(200, "OK", successMsg));
        }
        return ResponseEntity.status(400).body(new OneApiErrorResponse(
                client,
                idn,
                null,
                null,
                null,
                null,
                res.message(),
                List.of(toCode(res.code()))
        ));
    }

    private String toCode(Code c) {
        return switch (c) {
            case OK -> "";
            case E_AKO_MOVM_0003_NOT_FOUND -> "E-AKO-MOVM-0003";
            case E_AKO_MOVM_0004_WRONG_STATUS -> "E-AKO-MOVM-0004";
            case E_AKO_MOVM_0006_QTY_EXCEEDS_OPEN -> "E-AKO-MOVM-0006";
            case E_AKO_MOVM_0007_LINE_NOT_FOUND -> "E-AKO-MOVM-0007";
            case E_AKO_MOVM_0008_COMPARTMENT_NOT_EMPTY -> "E-AKO-MOVM-0008";
            case E_AKO_MOVM_0009_WRONG_ARTICLE_IN_COMPARTMENT -> "E-AKO-MOVM-0009";
        };
    }
}
