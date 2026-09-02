package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.LoadUnitMoved;
import com.knapp.kisoft.mock.api.dto.LoadUnitRetrievalRequest;
import com.knapp.kisoft.mock.api.dto.NewPosition;
import com.knapp.kisoft.mock.api.dto.OneApiOkResponse;
import com.knapp.kisoft.mock.api.dto.PackUnitKeyRef;
import com.knapp.kisoft.mock.api.dto.RepackRequest;
import com.knapp.kisoft.mock.api.dto.StockCorrected;
import com.knapp.kisoft.mock.api.dto.StockEntry;
import com.knapp.kisoft.mock.service.AsrsStockService;
import com.knapp.kisoft.mock.service.ReplyCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.knapp.kisoft.mock.service.PackSizeKeys.isPresent;
import static com.knapp.kisoft.mock.service.PackSizeKeys.toKey;

/**
 * Mock-only endpoints for the warehouse-internal processes driven by KiSoft One (GS §5.3).
 */
@Tag(name = "Warehouse-Internal (mock)",
        description = "Mock endpoints for GS §5.3 warehouse-internal processes. Triggers PostLoadUnitMoved (targeted retrieval) and PostStockCorrected (retrieval to conventional / repacking).")
@RestController
@RequestMapping("/oneapi/v1/loadUnit")
public class WarehouseInternalController {

    private final ReplyCallbackService callback;
    private final AsrsStockService asrsStock;

    public WarehouseInternalController(ReplyCallbackService callback, AsrsStockService asrsStock) {
        this.callback = callback;
        this.asrsStock = asrsStock;
    }

    @Operation(operationId = "RetrieveLoadUnit",
            summary = "Targeted retrieval of a source load unit (GS §5.3.3)",
            description = "Moves a load unit out of the AeroBot system and emits PostLoadUnitMoved. When toConventional "
                    + "is true the stock leaves the AeroBot system and a PostStockCorrected is also emitted.")
    @ApiResponse(responseCode = "200", description = "Retrieval triggered")
    @PostMapping("/retrieve")
    public ResponseEntity<?> retrieve(@RequestBody @Valid LoadUnitRetrievalRequest r) {
        boolean hasStock = r.articleNumber() != null && isPresent(r.packSize());
        String loadCarrier = r.loadCarrier() != null ? r.loadCarrier() : "FULL";
        List<StockEntry> loadUnitStock = hasStock
                ? List.of(new StockEntry(r.loadUnitCode(), r.slot(),
                        new PackUnitKeyRef(r.clientNumber(), r.articleNumber(), r.packSize()),
                        r.quantity(), r.stockType(), null, null, null, null, null, null, null, null))
                : null;

        callback.sendLoadUnitMoved(new LoadUnitMoved(
                UUID.randomUUID().toString(),
                r.loadUnitCode(), loadCarrier, Instant.now().toString(), "RELOCATION",
                new NewPosition(r.stationName(), r.locationNumber(), null),
                "STOCK", null, null, loadUnitStock));

        if (Boolean.TRUE.equals(r.toConventional()) && hasStock) {
            int qty = r.quantity() != null ? r.quantity() : 0;
            int removed = asrsStock.removeStock(r.clientNumber(), r.articleNumber(), toKey(r.packSize()), qty);
            StockEntry processedStock = loadUnitStock != null && !loadUnitStock.isEmpty()
                    ? loadUnitStock.get(0) : null;
            callback.sendStockCorrected(new StockCorrected(
                    UUID.randomUUID().toString(),
                    null, null, -removed, r.stationName(), "RETRIEVAL_TO_CONVENTIONAL", null, null,
                    Instant.now().toString(), processedStock));
        }
        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", "Targeted retrieval triggered, PostLoadUnitMoved sent"));
    }

    @Operation(operationId = "RepackLoadUnit",
            summary = "Repacking / defragmentation (GS §5.3.4)",
            description = "Repacks stock between load units. A quantity correction (e.g. target slot was not empty) is "
                    + "reported to the Host via PostStockCorrected.")
    @ApiResponse(responseCode = "200", description = "Repacking triggered")
    @PostMapping("/repack")
    public ResponseEntity<?> repack(@RequestBody @Valid RepackRequest r) {
        int delta = r.deltaQuantity() != null ? r.deltaQuantity() : 0;
        if (delta != 0 && isPresent(r.packSize())) {
            if (delta > 0) {
                asrsStock.addStock(r.clientNumber(), r.articleNumber(), toKey(r.packSize()), delta);
            } else {
                asrsStock.removeStock(r.clientNumber(), r.articleNumber(), toKey(r.packSize()), -delta);
            }
        }
        StockEntry entry = new StockEntry(r.targetLoadUnitCode(), r.targetSlot(),
                new PackUnitKeyRef(r.clientNumber(), r.articleNumber(), r.packSize()),
                null, null, null, null, null, null, null, null, null, null);
        callback.sendStockCorrected(new StockCorrected(
                UUID.randomUUID().toString(),
                null, null, delta, r.stationName(),
                r.reason() != null ? r.reason() : "REPACKING", null, null,
                Instant.now().toString(), entry));
        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", "Repacking triggered, PostStockCorrected sent"));
    }
}
