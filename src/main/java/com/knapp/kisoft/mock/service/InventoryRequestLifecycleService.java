package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.api.dto.InventoryCountConfirmation;
import com.knapp.kisoft.mock.api.dto.InventoryCountLine;
import com.knapp.kisoft.mock.api.dto.InventoryRequest;
import com.knapp.kisoft.mock.api.dto.InventoryRequestReference;
import com.knapp.kisoft.mock.api.dto.InventoryRequestReply;
import com.knapp.kisoft.mock.api.dto.InventoryRequestReplyLine;
import com.knapp.kisoft.mock.api.dto.PackUnitKeyRef;
import com.knapp.kisoft.mock.api.dto.StockCorrected;
import com.knapp.kisoft.mock.api.dto.StockEntry;
import com.knapp.kisoft.mock.api.dto.StockLockChanged;
import com.knapp.kisoft.mock.api.dto.StockLockRequestReference;
import com.knapp.kisoft.mock.persistence.InventoryRequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.knapp.kisoft.mock.service.PackSizeKeys.isPresent;

/**
 * KiSoft-side inventory logic (GS §5.3.5).
 */
@Service
public class InventoryRequestLifecycleService {

    public enum Code { OK, NOT_FOUND, WRONG_STATUS }

    public record Result(Code code, String message) {
        public static Result ok(String msg) { return new Result(Code.OK, msg); }
    }

    private final InventoryRequestStoreService store;
    private final AsrsStockService asrsStock;
    private final ReplyCallbackService callback;

    public InventoryRequestLifecycleService(InventoryRequestStoreService store,
                                            AsrsStockService asrsStock,
                                            ReplyCallbackService callback) {
        this.store = store;
        this.asrsStock = asrsStock;
        this.callback = callback;
    }

    /** Build the intake (NEW) reply echoing the request line with processedQuantity 0. */
    public InventoryRequestReply buildIntakeReply(InventoryRequest request, String createdBy) {
        List<InventoryRequestReplyLine> lines = new ArrayList<>();
        if (request.inventoryRequestLine() != null) {
            lines.add(new InventoryRequestReplyLine(request.inventoryRequestLine().lineReference(), 0));
        }
        return new InventoryRequestReply(
                request.clientNumber(), request.requestNumber(), lines.isEmpty() ? null : lines,
                createdBy, "NEW", Instant.now().toString(),
                request.businessCase() != null ? request.businessCase() : "INVENTORY",
                request.additionalProperties());
    }

    @Transactional
    public Result count(InventoryCountConfirmation c) {
        InventoryRequestEntity entity = store.find(c.clientNumber(), c.requestNumber()).orElse(null);
        if (entity == null) return new Result(Code.NOT_FOUND, "Inventory request not found");
        if ("FINISHED".equals(entity.getProcessingStatus()) || "CANCELLED".equals(entity.getProcessingStatus())) {
            return new Result(Code.WRONG_STATUS, "Inventory request already " + entity.getProcessingStatus());
        }

        List<InventoryRequestReplyLine> replyLines = new ArrayList<>();
        StockEntry countedEntry = null;
        int netDelta = 0;
        if (c.lines() != null) {
            for (InventoryCountLine line : c.lines()) {
                int counted = line.countedQuantity() != null ? line.countedQuantity() : 0;
                replyLines.add(new InventoryRequestReplyLine(line.lineReference(), counted));

                if (line.articleNumber() != null && isPresent(line.packSize())) {
                    int delta = asrsStock.setQuantity(
                            c.clientNumber(), line.articleNumber(), PackSizeKeys.toKey(line.packSize()), counted);
                    countedEntry = new StockEntry(line.loadUnitCode(), line.slot(),
                            new PackUnitKeyRef(c.clientNumber(), line.articleNumber(), line.packSize()),
                            counted, null, null, null, null, null, null, null, null,
                            Instant.now().toString());
                    netDelta += delta;
                }
            }
        }

        store.updateStatus(c.clientNumber(), c.requestNumber(), "FINISHED");

        InventoryRequest request = store.readPayload(entity);
        callback.sendInventoryRequestReply(new InventoryRequestReply(
                c.clientNumber(), c.requestNumber(), replyLines.isEmpty() ? null : replyLines,
                "SYSTEM", "FINISHED", Instant.now().toString(),
                request.businessCase() != null ? request.businessCase() : "INVENTORY",
                request.additionalProperties()));

        if (netDelta != 0 && countedEntry != null) {
            String lineRef = c.lines() != null && !c.lines().isEmpty() ? c.lines().get(0).lineReference() : null;
            callback.sendStockCorrected(new StockCorrected(
                    UUID.randomUUID().toString(),
                    new InventoryRequestReference(c.clientNumber(), c.requestNumber(), lineRef),
                    null, netDelta, null, "INVENTORY", null, null, Instant.now().toString(),
                    countedEntry));
        }

        if (countedEntry != null) {
            callback.sendStockLockChanged(new StockLockChanged(
                    UUID.randomUUID().toString(),
                    new StockLockRequestReference(c.clientNumber(), c.requestNumber()),
                    null, null, List.of("LOCKED_FOR_VISION_CHECK"), null, "INVENTORY_DONE", null,
                    Instant.now().toString(), countedEntry));
        }
        return Result.ok("Inventory counted, PostInventoryRequestReply(FINISHED) sent");
    }
}
