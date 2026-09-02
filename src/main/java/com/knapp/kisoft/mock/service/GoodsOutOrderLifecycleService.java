package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.api.dto.GoodsOutOrder;
import com.knapp.kisoft.mock.api.dto.LineCodeError;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderLine;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderReference;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderReply;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderReplyLine;
import com.knapp.kisoft.mock.api.dto.GoodsOutPickConfirmation;
import com.knapp.kisoft.mock.api.dto.GoodsOutPickLine;
import com.knapp.kisoft.mock.api.dto.PackUnitFull;
import com.knapp.kisoft.mock.api.dto.PackUnitKeyRef;
import com.knapp.kisoft.mock.api.dto.PickedStock;
import com.knapp.kisoft.mock.api.dto.StockCorrected;
import com.knapp.kisoft.mock.api.dto.StockEntry;
import com.knapp.kisoft.mock.api.dto.StockLockChanged;
import com.knapp.kisoft.mock.api.dto.StockLockRequestReference;
import com.knapp.kisoft.mock.persistence.GoodsOutOrderEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.knapp.kisoft.mock.service.PackSizeKeys.isPresent;
import static com.knapp.kisoft.mock.service.PackSizeKeys.toKey;
import static com.knapp.kisoft.mock.service.PrjContainerIds.forLine;

/**
 * KiSoft-side goods-out order logic (GS §5.2.1, §5.2.3–§5.2.5), excluding multiphase picking (§5.2.2).
 *
 * <ul>
 *   <li>Intake validation ({@link #validateIntakeLines}) rejects invalid lines synchronously with Product One
 *       API error codes on PostGoodsOutOrder / PatchGoodsOutOrder (add lines).</li>
 *   <li>Accepted orders emit PostGoodsOutOrderReply(NEW) with all lines {@code UNTOUCHED}.</li>
 *   <li>{@link #startProcessing} → STARTED, {@link #confirmPicking} → PROCESSED, {@link #finalCheck} → FINISHED.</li>
 *   <li>Picking (PROCESSED) decrements ASRS stock by the picked quantity (defaults to
 *       {@code requestedQuantity}); a short pick emits PostStockCorrected (QUANTITY_ERROR) and a
 *       damaged source slot emits PostStockLockChanged. FINISHED does not re-check or re-deduct stock.</li>
 * </ul>
 */
@Service
public class GoodsOutOrderLifecycleService {

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_STARTED = "STARTED";
    public static final String STATUS_PROCESSED = "PROCESSED";
    public static final String STATUS_FINISHED = "FINISHED";

    /** Article master-data markers (carried as articleFeatures) the mock honours for goods-out. */
    private static final String FEATURE_LOCKED = "ARTICLE_IS_LOCKED";
    private static final String FEATURE_TAKEN_OFF_SALE = "TAKEN_OFF_SALE";

    /** Product One API line codes returned synchronously on PostGoodsOutOrder / PatchGoodsOutOrder. */
    public static final String CODE_FORMAT_ERROR = "E-AKO-GENR-0002";
    public static final String CODE_GENERAL_ERROR = "E-AKO-GENR-0001";
    public static final String CODE_UNKNOWN_ARTICLE = "E-AKO-MAST-0001";
    public static final String CODE_NOT_ENOUGH_STOCK = "E-AKO-STOC-0001";

    public enum Code { OK, NOT_FOUND, WRONG_STATUS }

    public record Result(Code code, String message) {
        public static Result ok(String msg) { return new Result(Code.OK, msg); }
    }

    private final GoodsOutOrderStoreService store;
    private final PackUnitStoreService packUnitStore;
    private final AsrsStockService asrsStock;
    private final ReplyCallbackService callback;

    public GoodsOutOrderLifecycleService(GoodsOutOrderStoreService store,
                                         PackUnitStoreService packUnitStore,
                                         AsrsStockService asrsStock,
                                         ReplyCallbackService callback) {
        this.store = store;
        this.packUnitStore = packUnitStore;
        this.asrsStock = asrsStock;
        this.callback = callback;
    }

    // ---- Intake (PostGoodsOutOrder, GS §5.2.1) -------------------------------------------------

    /**
     * Build the intake (NEW) reply: all accepted lines are {@code UNTOUCHED} (planning errors are
     * rejected synchronously before the order is stored).
     */
    public GoodsOutOrderReply buildIntakeReply(GoodsOutOrder order, String createdBy) {
        List<GoodsOutOrderReplyLine> lines = new ArrayList<>();
        if (order.goodsOutOrderLines() != null) {
            for (GoodsOutOrderLine line : order.goodsOutOrderLines()) {
                lines.add(replyLine(order, line, UUID.randomUUID().toString(), 0,
                        "UNTOUCHED", null, null));
            }
        }
        return reply(order, createdBy, STATUS_NEW, lines.isEmpty() ? null : lines);
    }

    /**
     * Validates order lines for PostGoodsOutOrder / PatchGoodsOutOrder (add lines). Each invalid line
     * maps to a Product One API {@code lineCode} (HTTP 400).
     */
    public List<LineCodeError> validateIntakeLines(String clientNumber, List<GoodsOutOrderLine> lines) {
        if (lines == null) {
            return List.of();
        }
        List<LineCodeError> errors = new ArrayList<>();
        for (GoodsOutOrderLine line : lines) {
            intakeLineCode(clientNumber, line)
                    .ifPresent(code -> errors.add(new LineCodeError(line.lineReference(), code)));
        }
        return errors;
    }

    private Optional<String> intakeLineCode(String clientNumber, GoodsOutOrderLine line) {
        if (line.requestedQuantity() == null || line.requestedQuantity() <= 0) {
            return Optional.of(CODE_FORMAT_ERROR);
        }
        if (!masterDataExists(clientNumber, line)) {
            return Optional.of(CODE_UNKNOWN_ARTICLE);
        }
        Optional<PackUnitFull> master = packUnitStore.findAnyByArticle(clientNumber, line.articleNumber());
        if (master.isPresent() && master.get().articleFeatures() != null) {
            List<String> features = master.get().articleFeatures();
            if (features.contains(FEATURE_LOCKED) || features.contains(FEATURE_TAKEN_OFF_SALE)) {
                return Optional.of(CODE_GENERAL_ERROR);
            }
        }
        int available = availableStock(clientNumber, line);
        if (available < line.requestedQuantity()) {
            return Optional.of(CODE_NOT_ENOUGH_STOCK);
        }
        return Optional.empty();
    }

    private int availableStock(String clientNumber, GoodsOutOrderLine line) {
        if (isPresent(line.packSize())) {
            return asrsStock.getQuantity(clientNumber, line.articleNumber(), toKey(line.packSize()));
        }
        return asrsStock.availableForArticle(clientNumber, line.articleNumber());
    }

    /**
     * Runtime pick validation: returns a {@code processingResult} when the line cannot be picked
     * (e.g. stock depleted after intake), or {@code null} when pickable.
     */
    public String validateLine(String clientNumber, GoodsOutOrderLine line) {
        if (line.requestedQuantity() == null || line.requestedQuantity() <= 0) {
            return "ZERO_REQUESTED";
        }
        Optional<PackUnitFull> master = packUnitStore.findAnyByArticle(clientNumber, line.articleNumber());
        if (master.isPresent() && master.get().articleFeatures() != null) {
            List<String> features = master.get().articleFeatures();
            if (features.contains(FEATURE_LOCKED)) return "ARTICLE_IS_BLOCKED";
            if (features.contains(FEATURE_TAKEN_OFF_SALE)) return "TAKEN_OFF_SALE";
        }
        if (availableStock(clientNumber, line) < line.requestedQuantity()) {
            return "OUT_OF_STOCK";
        }
        return null;
    }

    /** True when the article (and pack size when provided) exists in pack-unit master data. */
    public boolean masterDataExists(String clientNumber, GoodsOutOrderLine line) {
        if (isPresent(line.packSize())) {
            return packUnitStore.exists(clientNumber, line.articleNumber(), line.packSize());
        }
        return packUnitStore.existsArticle(clientNumber, line.articleNumber());
    }

    // ---- Lifecycle -----------------------------------------------------------------------------

    @Transactional
    public Result startProcessing(String clientNumber, String orderNumber, Integer sheetNumber) {
        GoodsOutOrderEntity entity = store.find(clientNumber, orderNumber, sheetNumber).orElse(null);
        if (entity == null) return new Result(Code.NOT_FOUND, "Goods-out order not found");
        if (!STATUS_NEW.equals(entity.getProcessingStatus())) {
            return new Result(Code.WRONG_STATUS, "Order is not NEW (current: " + entity.getProcessingStatus() + ")");
        }
        GoodsOutOrder order = store.readPayload(entity);
        store.updateStatus(clientNumber, orderNumber, sheetNumber, STATUS_STARTED);
        callback.sendGoodsOutOrderReply(reply(order, "SYSTEM", STATUS_STARTED, untouchedLines(order)));
        return Result.ok("Order started, PostGoodsOutOrderReply(STARTED) sent");
    }

    @Transactional
    public Result confirmPicking(GoodsOutPickConfirmation c) {
        GoodsOutOrderEntity entity = store.find(c.clientNumber(), c.orderNumber(), c.sheetNumber()).orElse(null);
        if (entity == null) return new Result(Code.NOT_FOUND, "Goods-out order not found");
        if (!STATUS_STARTED.equals(entity.getProcessingStatus())) {
            return new Result(Code.WRONG_STATUS, "Order is not STARTED (current: " + entity.getProcessingStatus() + ")");
        }
        GoodsOutOrder order = store.readPayload(entity);
        List<GoodsOutOrderReplyLine> replyLines = new ArrayList<>();
        if (order.goodsOutOrderLines() != null) {
            for (GoodsOutOrderLine line : order.goodsOutOrderLines()) {
                replyLines.add(pickLine(order, line, findPick(c, line.lineReference())));
            }
        }
        store.updateStatus(c.clientNumber(), c.orderNumber(), c.sheetNumber(), STATUS_PROCESSED);
        callback.sendGoodsOutOrderReply(reply(order, "SYSTEM", STATUS_PROCESSED, replyLines.isEmpty() ? null : replyLines));
        return Result.ok("Picking confirmed, PostGoodsOutOrderReply(PROCESSED) sent");
    }

    @Transactional
    public Result finalCheck(String clientNumber, String orderNumber, Integer sheetNumber) {
        GoodsOutOrderEntity entity = store.find(clientNumber, orderNumber, sheetNumber).orElse(null);
        if (entity == null) return new Result(Code.NOT_FOUND, "Goods-out order not found");
        if (!STATUS_PROCESSED.equals(entity.getProcessingStatus())) {
            return new Result(Code.WRONG_STATUS, "Order is not PROCESSED (current: " + entity.getProcessingStatus() + ")");
        }
        GoodsOutOrder order = store.readPayload(entity);
        store.updateStatus(clientNumber, orderNumber, sheetNumber, STATUS_FINISHED);
        callback.sendGoodsOutOrderReply(reply(order, "SYSTEM", STATUS_FINISHED, finishedLines(order)));
        return Result.ok("Final check passed, PostGoodsOutOrderReply(FINISHED) sent");
    }

    // ---- Picking of a single line --------------------------------------------------------------

    private GoodsOutOrderReplyLine pickLine(GoodsOutOrder order, GoodsOutOrderLine line, GoodsOutPickLine pick) {
        String uuid = UUID.randomUUID().toString();
        String validation = validateLine(order.clientNumber(), line);
        if (validation != null) {
            // Rejected at intake — not pickable.
            return replyLine(order, line, uuid, 0, validation, null, null);
        }
        int requested = line.requestedQuantity();
        int picked = pick != null && pick.pickedQuantity() != null ? Math.max(0, pick.pickedQuantity()) : requested;
        boolean damaged = pick != null && Boolean.TRUE.equals(pick.damaged());
        String sourceLoadUnitCode = pick != null ? pick.sourceLoadUnitCode() : null;
        Integer slot = pick != null ? pick.slot() : null;

        if (isPresent(line.packSize())) {
            asrsStock.removeStock(order.clientNumber(), line.articleNumber(), toKey(line.packSize()), picked);
        }

        String result = "PROCESSED";
        if (picked < requested) {
            result = "QUANTITY_ERROR";
            // GS §5.2.3: short pick at source LU -> stock correction (deviation for expected stock).
            callback.sendStockCorrected(shortPickCorrection(order, line, picked - requested,
                    sourceLoadUnitCode, slot, picked));
        }
        if (damaged) {
            // GS §5.2.3: damaged articles -> source slot is locked and marked for inventory.
            callback.sendStockLockChanged(damageLock(order, line, sourceLoadUnitCode, slot, picked));
        }

        List<PickedStock> pickedStock = List.of(new PickedStock(
                picked, line.articleNumber(), line.packSize(), line.stockType(),
                line.lotNumber(), line.dateMark(), line.reservationCode(), null,
                damaged ? List.of("LOCKED_FOR_VISION_CHECK") : null));
        return replyLine(order, line, uuid, picked, result, null, pickedStock);
    }

    // ---- Reply / message builders --------------------------------------------------------------

    private static GoodsOutOrderReply reply(GoodsOutOrder order, String createdBy, String status,
                                            List<GoodsOutOrderReplyLine> lines) {
        return new GoodsOutOrderReply(
                order.clientNumber(), order.orderNumber(), order.sheetNumber(), createdBy, status,
                order.businessCase() != null ? order.businessCase() : "GOODS_OUT",
                Instant.now().toString(), order.loadUnitCode(), order.loadCarrier(),
                order.customerNumber(), null, lines);
    }

    private List<GoodsOutOrderReplyLine> untouchedLines(GoodsOutOrder order) {
        if (order.goodsOutOrderLines() == null) return null;
        List<GoodsOutOrderReplyLine> lines = new ArrayList<>();
        for (GoodsOutOrderLine line : order.goodsOutOrderLines()) {
            lines.add(replyLine(order, line, UUID.randomUUID().toString(), 0,
                    "UNTOUCHED", null, null));
        }
        return lines.isEmpty() ? null : lines;
    }

    private List<GoodsOutOrderReplyLine> finishedLines(GoodsOutOrder order) {
        // Stock was already deducted at PROCESSED — do not re-validate ASRS quantity here.
        if (order.goodsOutOrderLines() == null) return null;
        List<GoodsOutOrderReplyLine> lines = new ArrayList<>();
        for (GoodsOutOrderLine line : order.goodsOutOrderLines()) {
            int qty = line.requestedQuantity() != null ? line.requestedQuantity() : 0;
            lines.add(replyLine(order, line, UUID.randomUUID().toString(), qty, "PROCESSED", null, null));
        }
        return lines.isEmpty() ? null : lines;
    }

    private StockCorrected shortPickCorrection(GoodsOutOrder order, GoodsOutOrderLine line, int delta,
                                               String loadUnitCode, Integer slot, int picked) {
        return new StockCorrected(
                UUID.randomUUID().toString(),
                null,
                new GoodsOutOrderReference(order.clientNumber(), order.orderNumber(), order.sheetNumber(), line.lineReference()),
                delta, line.stationName(), "SHORT_PICK", null, null, Instant.now().toString(),
                stockEntry(order.clientNumber(), line, loadUnitCode, slot, picked, null));
    }

    private StockLockChanged damageLock(GoodsOutOrder order, GoodsOutOrderLine line,
                                        String loadUnitCode, Integer slot, int picked) {
        return new StockLockChanged(
                UUID.randomUUID().toString(),
                new StockLockRequestReference(order.clientNumber(), order.orderNumber()),
                picked, List.of("LOCKED_FOR_VISION_CHECK"), null, line.stationName(),
                "DAMAGED_ARTICLE", null, Instant.now().toString(),
                stockEntry(order.clientNumber(), line, loadUnitCode, slot, picked,
                        List.of("LOCKED_FOR_VISION_CHECK")));
    }

    private static StockEntry stockEntry(String clientNumber, GoodsOutOrderLine line, String loadUnitCode,
                                         Integer slot, int qty, List<String> locks) {
        return new StockEntry(loadUnitCode, slot,
                new PackUnitKeyRef(clientNumber, line.articleNumber(), line.packSize()),
                qty, line.stockType(), line.lotNumber(), line.dateMark(), null,
                line.reservationCode(), locks, null, null, null);
    }

    private static GoodsOutPickLine findPick(GoodsOutPickConfirmation c, String lineReference) {
        if (c.lines() == null) return null;
        return c.lines().stream().filter(l -> lineReference.equals(l.lineReference())).findFirst().orElse(null);
    }

    private static GoodsOutOrderReplyLine replyLine(GoodsOutOrder order, GoodsOutOrderLine line, String uuid,
                                                    int processedQuantity, String processingResult,
                                                    String processingError, List<PickedStock> pickedStock) {
        return new GoodsOutOrderReplyLine(
                uuid,
                forLine(order.orderNumber(), line.lineReference()),
                line.lineReference(),
                processedQuantity,
                processingResult,
                processingError,
                pickedStock);
    }
}
