package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.InventoryRequest;
import com.knapp.kisoft.mock.api.dto.InventoryRequestLine;
import com.knapp.kisoft.mock.api.dto.InventoryRequestRef;
import com.knapp.kisoft.mock.api.dto.OneApiErrorResponse;
import com.knapp.kisoft.mock.api.dto.OneApiOkResponse;
import com.knapp.kisoft.mock.service.InventoryRequestLifecycleService;
import com.knapp.kisoft.mock.service.InventoryRequestStoreService;
import com.knapp.kisoft.mock.service.PackUnitStoreService;
import com.knapp.kisoft.mock.service.ReplyCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Mock KiSoft Inventory Request API — Inventory (HIS Appendix §7.2, GS §5.3.5).
 * POST /inventoryRequest ({@code PostInventoryRequest}), DELETE /inventoryRequest ({@code DeleteInventoryRequest}).
 *
 * <p>On create, a PostInventoryRequestReply(NEW) is sent. The inventory count is carried out via the
 * Inventory Operator (mock) endpoint, which compares counted against booked stock and emits
 * PostStockCorrected / PostInventoryRequestReply(FINISHED) / PostStockLockChanged.</p>
 */
@Tag(name = "Inventory", description = "Inventory request (POST, DELETE). The count is carried out via the Inventory Operator (mock) endpoint.")
@RestController
@RequestMapping("/oneapi/v1")
public class InventoryRequestController {

    private final InventoryRequestStoreService store;
    private final InventoryRequestLifecycleService lifecycle;
    private final PackUnitStoreService packUnitStore;
    private final ReplyCallbackService replyCallbackService;

    public InventoryRequestController(InventoryRequestStoreService store,
                                      InventoryRequestLifecycleService lifecycle,
                                      PackUnitStoreService packUnitStore,
                                      ReplyCallbackService replyCallbackService) {
        this.store = store;
        this.lifecycle = lifecycle;
        this.packUnitStore = packUnitStore;
        this.replyCallbackService = replyCallbackService;
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventory request created successfully"),
            @ApiResponse(responseCode = "400", description = "Errors detected. lineCode: "
                    + "E-AKO-MOVM-0002 (Request already active), E-AKO-MAST-0001 (Unknown article), "
                    + "E-AKO-GENR-0001, E-AKO-GENR-0002"),
            @ApiResponse(responseCode = "409", description = "Conflict - duplicate Article + CountryOfOrigin "
                    + "while a prior request is non-terminal. lineCode: E-AKO-MOVM-0005 (spec 006 EF-07)")
    })
    @Operation(operationId = "PostInventoryRequest")
    @PostMapping("/inventoryRequest")
    public ResponseEntity<?> postInventoryRequest(@RequestBody @Valid InventoryRequest request) {
        if (store.exists(request.clientNumber(), request.requestNumber())) {
            return ResponseEntity.status(400).body(error(request.clientNumber(), request.requestNumber(), "E-AKO-MOVM-0002"));
        }
        if (request.inventoryRequestLine() != null) {
            InventoryRequestLine line = request.inventoryRequestLine();
            if (line.articleNumber() != null && !packUnitStore.existsArticle(request.clientNumber(), line.articleNumber())) {
                return ResponseEntity.status(400).body(new OneApiErrorResponse(
                        request.clientNumber(), null, null, line.articleNumber(),
                        line.packSize() != null ? String.valueOf(line.packSize()) : null,
                        request.requestNumber(), "Unknown article: " + line.articleNumber(),
                        List.of("E-AKO-MAST-0001")));
            }
            if (line.articleNumber() != null
                    && store.hasActiveRequestForArticle(request.clientNumber(), line.articleNumber(), line.reservationCode())) {
                return ResponseEntity.status(409).body(new OneApiErrorResponse(
                        request.clientNumber(), null, null, line.articleNumber(),
                        line.packSize() != null ? String.valueOf(line.packSize()) : null,
                        request.requestNumber(),
                        "Active inventory request already exists for article " + line.articleNumber()
                                + " and reservationCode " + line.reservationCode(),
                        List.of("E-AKO-MOVM-0005")));
            }
        }
        store.createNew(request);
        replyCallbackService.sendInventoryRequestReply(lifecycle.buildIntakeReply(request, "HOST"));
        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", "Inventory request created successfully"));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventory request deleted"),
            @ApiResponse(responseCode = "400", description = "Errors detected. lineCode: E-AKO-MOVM-0003 (Request not found)")
    })
    @Operation(operationId = "DeleteInventoryRequest")
    @DeleteMapping("/inventoryRequest")
    public ResponseEntity<?> deleteInventoryRequest(@RequestBody @Valid InventoryRequestRef request) {
        if (!store.delete(request.clientNumber(), request.requestNumber())) {
            return ResponseEntity.status(400).body(error(request.clientNumber(), request.requestNumber(), "E-AKO-MOVM-0003"));
        }
        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", "Inventory request deleted"));
    }

    private static OneApiErrorResponse error(String clientNumber, String requestNumber, String code) {
        return new OneApiErrorResponse(clientNumber, null, null, null, null, requestNumber, null, List.of(code));
    }
}
