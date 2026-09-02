package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.InventoryCountConfirmation;
import com.knapp.kisoft.mock.api.dto.OneApiErrorResponse;
import com.knapp.kisoft.mock.api.dto.OneApiOkResponse;
import com.knapp.kisoft.mock.service.InventoryRequestLifecycleService;
import com.knapp.kisoft.mock.service.InventoryRequestLifecycleService.Code;
import com.knapp.kisoft.mock.service.InventoryRequestLifecycleService.Result;
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
 * Mock-only endpoint that simulates the KiSoft inventory count at the AeroBot workstation (GS §5.3.5).
 * Records the counted quantities, corrects the booked ASRS stock on deviation and closes the request.
 */
@Tag(name = "Inventory Operator (mock)",
        description = "Mock endpoint simulating the GS §5.3.5 inventory count. Triggers PostStockCorrected (on deviation), "
                + "PostInventoryRequestReply(FINISHED) and PostStockLockChanged (unlock).")
@RestController
@RequestMapping("/oneapi/v1/inventoryRequest/operator")
public class InventoryRequestOperatorController {

    private final InventoryRequestLifecycleService lifecycle;

    public InventoryRequestOperatorController(InventoryRequestLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Operation(operationId = "OperatorCountInventory",
            summary = "Carry out the inventory count (GS §5.3.5)",
            description = "Records the counted quantity per line. A deviation from the booked ASRS stock adjusts the "
                    + "stock and emits PostStockCorrected. Closes the request with PostInventoryRequestReply(FINISHED) "
                    + "and unlocks the counted stock via PostStockLockChanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventory counted"),
            @ApiResponse(responseCode = "400", description = "lineCode: E-AKO-MOVM-0003 (not found), E-AKO-MOVM-0004 (wrong status)")
    })
    @PostMapping("/count")
    public ResponseEntity<?> count(@RequestBody @Valid InventoryCountConfirmation confirmation) {
        Result res = lifecycle.count(confirmation);
        if (res.code() == Code.OK) {
            return ResponseEntity.ok(new OneApiOkResponse(200, "OK", res.message()));
        }
        String code = res.code() == Code.NOT_FOUND ? "E-AKO-MOVM-0003" : "E-AKO-MOVM-0004";
        return ResponseEntity.status(400).body(new OneApiErrorResponse(
                confirmation.clientNumber(), null, null, null, null, confirmation.requestNumber(),
                res.message(), List.of(code)));
    }
}
