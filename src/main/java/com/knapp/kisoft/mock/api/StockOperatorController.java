package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.OneApiErrorResponse;
import com.knapp.kisoft.mock.api.dto.OneApiOkResponse;
import com.knapp.kisoft.mock.api.dto.PackUnitKeyRef;
import com.knapp.kisoft.mock.api.dto.SpontaneousStockCorrection;
import com.knapp.kisoft.mock.api.dto.StockCorrected;
import com.knapp.kisoft.mock.api.dto.StockEntry;
import com.knapp.kisoft.mock.api.dto.StockLockChanged;
import com.knapp.kisoft.mock.api.dto.StockLockOperatorRequest;
import com.knapp.kisoft.mock.persistence.AsrsStockEntity;
import com.knapp.kisoft.mock.service.AsrsStockService;
import com.knapp.kisoft.mock.service.AsrsStockService.LockAction;
import com.knapp.kisoft.mock.service.AsrsStockService.LockChange;
import com.knapp.kisoft.mock.service.ReplyCallbackService;
import com.knapp.kisoft.mock.service.StockLockReasons;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.knapp.kisoft.mock.service.PackSizeKeys.toKey;

/**
 * Mock-only operator endpoint for spontaneous stock corrections (spec 007 / IN-02).
 */
@Tag(name = "Stock Operator (mock)", description = "Mock-only spontaneous stock correction (PostStockCorrected without prior "
        + "inventory request) and operator stock lock/unlock (PostStockLockChanged).")
@RestController
@RequestMapping("/oneapi/v1/stock/operator")
public class StockOperatorController {

    private final AsrsStockService asrsStock;
    private final ReplyCallbackService callback;

    public StockOperatorController(AsrsStockService asrsStock, ReplyCallbackService callback) {
        this.asrsStock = asrsStock;
        this.callback = callback;
    }

    @Operation(operationId = "OperatorSpontaneousStockCorrection",
            summary = "Spontaneous stock correction (IN-02)",
            description = "Sets ASRS stock to the absolute counted quantity and emits PostStockCorrected "
                    + "without an inventoryRequestReference. Use wait=true (default) to include the IBM APIC response.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Correction applied; callback field shows APIC result when wait=true"),
    })
    @PostMapping("/correct")
    public ResponseEntity<OneApiOkResponse> correct(
            @RequestBody @Valid SpontaneousStockCorrection request,
            @Parameter(description = "Wait for IBM APIC response and include it in the reply (recommended for Swagger)")
            @RequestParam(defaultValue = "true") boolean wait) {
        int delta = asrsStock.setQuantity(
                request.clientNumber(), request.articleNumber(), toKey(request.packSize()), request.countedQuantity());
        StockEntry entry = new StockEntry(
                null, null,
                new PackUnitKeyRef(request.clientNumber(), request.articleNumber(), request.packSize()),
                request.countedQuantity(), null, null, null, null, null, null, null, null,
                Instant.now().toString());
        StockCorrected event = new StockCorrected(
                UUID.randomUUID().toString(),
                null, null, delta,
                request.stationName(),
                request.reason() != null ? request.reason() : "SPONTANEOUS_CORRECTION",
                null, null, Instant.now().toString(),
                entry);
        if (wait) {
            var delivery = callback.deliverSync("stockCorrected", event, "StockCorrected").orElse(null);
            return WebhookWaitResponse.of("Spontaneous stock correction applied", "StockCorrected", delivery);
        }
        callback.sendStockCorrected(event);
        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", "Spontaneous stock correction applied"));
    }

    static final String CODE_STOCK_NOT_FOUND = "E-AKO-STOC-0003";
    static final String CODE_FORMAT_ERROR = "E-AKO-GENR-0002";

    @Operation(operationId = "OperatorStockLockChange",
            summary = "Operator stock lock / unlock (IN-05)",
            description = "Adds (LOCK) or removes (UNLOCK) stock lock reasons on the ASRS stock row matched by "
                    + "clientNumber + articleNumber + packSize + reservationCode (Country of Origin), then emits "
                    + "PostStockLockChanged with addedStockLocks / removedStockLocks and the resulting processedStock. "
                    + "UNLOCK with an empty list removes all locks. Quantity is unchanged. "
                    + "Valid reasons: " + "DEFAULT, EXPIRED, HOST, LOCATION_LOCKED, LOCKED_FOR_VISION_CHECK, LOST, "
                    + "QS_REQ, SRS_SYSTEM_BROKEN, SUBSYSTEM_LOCKED, TIME_TO_EXPIRE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lock change applied; callback field shows APIC result when wait=true"),
            @ApiResponse(responseCode = "400", description = "codes: E-AKO-GENR-0002 (unknown stockLockReasons, or LOCK without reasons)"),
            @ApiResponse(responseCode = "404", description = "codes: E-AKO-STOC-0003 (no ASRS stock for key + reservationCode)")
    })
    @PostMapping("/lock")
    public ResponseEntity<?> lock(
            @RequestBody @Valid StockLockOperatorRequest request,
            @Parameter(description = "Wait for IBM APIC response and include it in the reply (recommended for Swagger)")
            @RequestParam(defaultValue = "true") boolean wait) {
        List<String> reasons = request.stockLockReasons() == null ? List.of() : request.stockLockReasons();
        List<String> invalid = StockLockReasons.invalid(reasons);
        if (!invalid.isEmpty()) {
            return error(400, request, CODE_FORMAT_ERROR,
                    "Unknown stockLockReasons " + invalid + "; allowed: " + StockLockReasons.ALL);
        }
        if (request.action() == LockAction.LOCK && reasons.isEmpty()) {
            return error(400, request, CODE_FORMAT_ERROR, "LOCK requires at least one stockLockReasons entry");
        }
        String packSizeKey = toKey(request.packSize());
        LockChange change = asrsStock.changeLocks(
                request.clientNumber(), request.articleNumber(), packSizeKey,
                request.reservationCode(), request.action(), reasons).orElse(null);
        if (change == null) {
            return error(404, request, CODE_STOCK_NOT_FOUND,
                    "No ASRS stock for " + request.articleNumber() + " / packSize " + request.packSize()
                            + " with reservationCode " + request.reservationCode());
        }

        AsrsStockEntity row = change.entity();
        boolean isLock = request.action() == LockAction.LOCK;
        StockEntry processedStock = new StockEntry(
                null, null,
                new PackUnitKeyRef(row.getClientNumber(), row.getArticleNumber(), request.packSize()),
                row.getQuantity(), row.getStockType(), row.getLotNumber(), row.getDateMark(), row.getSerialNumber(),
                row.getReservationCode(), change.resultingReasons(), null, null, null);
        StockLockChanged event = new StockLockChanged(
                UUID.randomUUID().toString(),
                null,
                row.getQuantity(),
                isLock ? change.added() : null,
                isLock ? null : change.removed(),
                request.stationName(),
                request.reason() != null ? request.reason() : (isLock ? "OPERATOR_LOCK" : "OPERATOR_UNLOCK"),
                null,
                Instant.now().toString(),
                processedStock);
        String message = (isLock ? "Stock lock added " + change.added() : "Stock lock removed " + change.removed())
                + "; current locks " + (change.resultingReasons() == null ? "[]" : change.resultingReasons());
        if (wait) {
            var delivery = callback.deliverSync("stockLockChanged", event, "StockLockChanged").orElse(null);
            return WebhookWaitResponse.of(message, "StockLockChanged", delivery);
        }
        callback.sendStockLockChanged(event);
        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", message));
    }

    private static ResponseEntity<OneApiErrorResponse> error(int status, StockLockOperatorRequest r,
                                                             String code, String message) {
        return ResponseEntity.status(status).body(new OneApiErrorResponse(
                r.clientNumber(), null, null, r.articleNumber(),
                r.packSize() == null ? null : String.valueOf(r.packSize()),
                null, message, List.of(code)));
    }
}
