package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.GoodsOutOrderRef;
import com.knapp.kisoft.mock.api.dto.GoodsOutPickConfirmation;
import com.knapp.kisoft.mock.api.dto.OneApiErrorResponse;
import com.knapp.kisoft.mock.api.dto.OneApiOkResponse;
import com.knapp.kisoft.mock.service.GoodsOutOrderLifecycleService;
import com.knapp.kisoft.mock.service.GoodsOutOrderLifecycleService.Code;
import com.knapp.kisoft.mock.service.GoodsOutOrderLifecycleService.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Mock-only endpoints that simulate the KiSoft workstation goods-out flow (GS §5.2.3–§5.2.5).
 * They are not part of the formal KiSoft OneAPI; they let host integrators drive the goods-out
 * order through its statuses and trigger the corresponding PostGoodsOutOrderReply, PostStockCorrected
 * and PostStockLockChanged webhooks.
 */
@Tag(name = "Goods-Out Operator (mock)",
        description = "Mock endpoints simulating the GS §5.2.3–§5.2.5 picking/check flow. "
                + "Drives STARTED → PROCESSED → FINISHED and triggers PostGoodsOutOrderReply / PostStockCorrected / PostStockLockChanged.")
@RestController
@RequestMapping("/oneapi/v1/goodsOutOrder/operator")
public class GoodsOutOperatorController {

    private final GoodsOutOrderLifecycleService lifecycle;

    public GoodsOutOperatorController(GoodsOutOrderLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Operation(operationId = "OperatorStartGoodsOutOrder",
            summary = "Start registration of the goods-out order (GS §5.2.1)",
            description = "Transitions the goods-out order NEW → STARTED and emits PostGoodsOutOrderReply(STARTED).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order started"),
            @ApiResponse(responseCode = "400", description = "lineCode: E-AKO-MOVM-0003 (not found), E-AKO-MOVM-0004 (wrong status)")
    })
    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody @Valid GoodsOutOrderRef ref) {
        return toResponse(lifecycle.startProcessing(ref.clientNumber(), ref.orderNumber(), ref.sheetNumber()),
                ref.clientNumber(), ref.orderNumber());
    }

    @Operation(operationId = "OperatorPickGoodsOutOrder",
            summary = "Confirm picking at the AeroBot workstation (GS §5.2.3)",
            description = "Confirms the picks for the order, decrements ASRS stock and transitions STARTED → PROCESSED. "
                    + "A line picked below its requested quantity yields processingResult QUANTITY_ERROR and a "
                    + "PostStockCorrected; a line flagged damaged emits PostStockLockChanged. Emits PostGoodsOutOrderReply(PROCESSED).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Picking confirmed"),
            @ApiResponse(responseCode = "400", description = "lineCode: E-AKO-MOVM-0003 (not found), E-AKO-MOVM-0004 (wrong status)")
    })
    @PostMapping("/pick")
    public ResponseEntity<?> pick(@RequestBody @Valid GoodsOutPickConfirmation confirmation) {
        return toResponse(lifecycle.confirmPicking(confirmation),
                confirmation.clientNumber(), confirmation.orderNumber());
    }

    @Operation(operationId = "OperatorFinalCheckGoodsOutOrder",
            summary = "Final check / dispatch staging (GS §5.2.4–§5.2.5)",
            description = "Passes the shipping load unit through the final check, transitions PROCESSED → FINISHED and "
                    + "emits PostGoodsOutOrderReply(FINISHED). No confirmation by the customer is required to close the order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Final check passed"),
            @ApiResponse(responseCode = "400", description = "lineCode: E-AKO-MOVM-0003 (not found), E-AKO-MOVM-0004 (wrong status)")
    })
    @PostMapping("/finalCheck")
    public ResponseEntity<?> finalCheck(@RequestBody @Valid GoodsOutOrderRef ref) {
        return toResponse(lifecycle.finalCheck(ref.clientNumber(), ref.orderNumber(), ref.sheetNumber()),
                ref.clientNumber(), ref.orderNumber());
    }

    private ResponseEntity<?> toResponse(Result res, String clientNumber, String orderNumber) {
        if (res.code() == Code.OK) {
            return ResponseEntity.ok(new OneApiOkResponse(200, "OK", res.message()));
        }
        String code = res.code() == Code.NOT_FOUND ? "E-AKO-MOVM-0003" : "E-AKO-MOVM-0004";
        return ResponseEntity.status(400).body(
                new OneApiErrorResponse(clientNumber, null, orderNumber, null, null, null, res.message(), List.of(code)));
    }
}
