package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.OneApiOkResponse;
import com.knapp.kisoft.mock.api.dto.PackUnitKeyRef;
import com.knapp.kisoft.mock.api.dto.SpontaneousStockCorrection;
import com.knapp.kisoft.mock.api.dto.StockCorrected;
import com.knapp.kisoft.mock.api.dto.StockEntry;
import com.knapp.kisoft.mock.service.AsrsStockService;
import com.knapp.kisoft.mock.service.ReplyCallbackService;
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
import java.util.UUID;

import static com.knapp.kisoft.mock.service.PackSizeKeys.toKey;

/**
 * Mock-only operator endpoint for spontaneous stock corrections (spec 007 / IN-02).
 */
@Tag(name = "Stock Operator (mock)", description = "Mock-only spontaneous stock correction (PostStockCorrected without prior inventory request).")
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
}
