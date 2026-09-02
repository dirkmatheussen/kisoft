package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.api.dto.InboundDelivery;
import com.knapp.kisoft.mock.api.dto.InboundDeliveryLine;
import com.knapp.kisoft.mock.api.dto.InboundDeliveryLoadUnitReceipt;
import com.knapp.kisoft.mock.api.dto.InboundDeliveryReference;
import com.knapp.kisoft.mock.api.dto.InboundDeliveryReply;
import com.knapp.kisoft.mock.api.dto.PackUnitKeyRef;
import com.knapp.kisoft.mock.api.dto.StockEntry;
import com.knapp.kisoft.mock.api.dto.StockReceived;
import com.knapp.kisoft.mock.api.dto.StorageOrderReply;
import com.knapp.kisoft.mock.persistence.InboundDeliveryEntity;
import com.knapp.kisoft.mock.persistence.InboundDeliveryProgressEntity;
import com.knapp.kisoft.mock.persistence.InboundDeliveryProgressRepository;
import com.knapp.kisoft.mock.persistence.ToteCompartmentEntity;
import com.knapp.kisoft.mock.persistence.ToteCompartmentRepository;
import com.knapp.kisoft.mock.config.KnappMockProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.knapp.kisoft.mock.service.PackSizeKeys.toKey;

/**
 * Encapsulates the IB-02 KiSoft-side execution lifecycle: NEW → STARTED → FINISHED.
 *
 * <ul>
 *   <li>{@link #startProcessing(String, String)} - operator activates the inbound (MF-3).</li>
 *   <li>{@link #recordLoadUnit(InboundDeliveryLoadUnitReceipt)} - operator confirms a goods-in
 *       load unit (MF-4..MF-6) with EF-04/06/14 guards.</li>
 *   <li>{@link #finishProcessing(String, String)} - close out the delivery (MF-7).</li>
 * </ul>
 */
@Service
public class InboundDeliveryLifecycleService {

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_STARTED = "STARTED";
    public static final String STATUS_FINISHED = "FINISHED";

    public enum Code {
        OK,
        E_AKO_MOVM_0003_NOT_FOUND,
        E_AKO_MOVM_0004_WRONG_STATUS,
        E_AKO_MOVM_0006_QTY_EXCEEDS_OPEN,
        E_AKO_MOVM_0007_LINE_NOT_FOUND,
        E_AKO_MOVM_0008_COMPARTMENT_NOT_EMPTY,
        E_AKO_MOVM_0009_WRONG_ARTICLE_IN_COMPARTMENT
    }

    public record LifecycleResult(Code code, String message, StockReceived stockReceived,
                                  InboundDeliveryReply reply, boolean finished) {
        public static LifecycleResult ok() { return new LifecycleResult(Code.OK, null, null, null, false); }
    }

    private final InboundDeliveryStoreService inboundStore;
    private final InboundDeliveryProgressRepository progressRepo;
    private final ToteCompartmentRepository toteRepo;
    private final AsrsStockService asrsStock;
    private final ReplyCallbackService callback;
    private final KnappMockProperties properties;

    public InboundDeliveryLifecycleService(InboundDeliveryStoreService inboundStore,
                                           InboundDeliveryProgressRepository progressRepo,
                                           ToteCompartmentRepository toteRepo,
                                           AsrsStockService asrsStock,
                                           ReplyCallbackService callback,
                                           KnappMockProperties properties) {
        this.inboundStore = inboundStore;
        this.progressRepo = progressRepo;
        this.toteRepo = toteRepo;
        this.asrsStock = asrsStock;
        this.callback = callback;
        this.properties = properties;
    }

    /**
     * Mock convenience: book each inbound line's {@code expectedQuantity} into ASRS using the same
     * (clientNumber, articleNumber, packSize) keys as goods-out and {@code inventoryRequestLine}.
     * Always adds: increments an existing inventory item, or creates one when missing (opt-in via
     * {@code inbound-auto-stock}). Emits {@code PostStockReceived} per line.
     */
    @Transactional
    public void bookExpectedStock(InboundDelivery delivery) {
        if (!properties.isInboundAutoStock() || delivery.inboundDeliveryLines() == null) {
            return;
        }
        for (InboundDeliveryLine line : delivery.inboundDeliveryLines()) {
            if (line.articleNumber() == null || line.packSize() == null
                    || line.expectedQuantity() == null || line.expectedQuantity() <= 0) {
                continue;
            }
            String packSizeKey = toKey(line.packSize());
            asrsStock.addStock(delivery.clientNumber(), line.articleNumber(), packSizeKey, line.expectedQuantity());
            StockEntry processedStock = new StockEntry(
                    line.loadUnitCode(),
                    null,
                    new PackUnitKeyRef(delivery.clientNumber(), line.articleNumber(), line.packSize()),
                    line.expectedQuantity(),
                    line.stockType(),
                    line.lotNumber(),
                    line.dateMark(),
                    line.serialNumber(),
                    line.reservationCode(),
                    line.stockLockReasons(),
                    line.stockQuality(),
                    null,
                    null
            );
            callback.sendStockReceived(new StockReceived(
                    UUID.randomUUID().toString(),
                    new InboundDeliveryReference(
                            delivery.clientNumber(), delivery.inboundDeliveryNumber(), line.lineReference()),
                    line.expectedQuantity(),
                    null,
                    "INBOUND_AUTO_STOCK",
                    null,
                    Instant.now().toString(),
                    processedStock
            ));
        }
    }

    /**
     * Reverse {@link #bookExpectedStock} when an inbound delivery is cancelled while still NEW.
     */
    @Transactional
    public void releaseExpectedStock(InboundDelivery delivery) {
        if (!properties.isInboundAutoStock() || delivery.inboundDeliveryLines() == null) {
            return;
        }
        for (InboundDeliveryLine line : delivery.inboundDeliveryLines()) {
            if (line.articleNumber() == null || line.packSize() == null
                    || line.expectedQuantity() == null || line.expectedQuantity() <= 0) {
                continue;
            }
            asrsStock.removeStock(
                    delivery.clientNumber(), line.articleNumber(), toKey(line.packSize()), line.expectedQuantity());
        }
    }

    @Transactional
    public LifecycleResult startProcessing(String clientNumber, String inboundDeliveryNumber) {
        Optional<InboundDeliveryEntity> existing = inboundStore.find(clientNumber, inboundDeliveryNumber);
        if (existing.isEmpty()) {
            return new LifecycleResult(Code.E_AKO_MOVM_0003_NOT_FOUND,
                    "Inbound delivery not found", null, null, false);
        }
        InboundDeliveryEntity entity = existing.get();
        if (!STATUS_NEW.equals(entity.getProcessingStatus())) {
            return new LifecycleResult(Code.E_AKO_MOVM_0004_WRONG_STATUS,
                    "Cannot start processing in status " + entity.getProcessingStatus(), null, null, false);
        }

        InboundDelivery delivery = inboundStore.readPayload(entity);
        if (delivery.inboundDeliveryLines() != null) {
            for (InboundDeliveryLine line : delivery.inboundDeliveryLines()) {
                progressRepo.findByClientNumberAndInboundDeliveryNumberAndLineReference(
                                clientNumber, inboundDeliveryNumber, line.lineReference())
                        .orElseGet(() -> progressRepo.save(new InboundDeliveryProgressEntity(
                                clientNumber,
                                inboundDeliveryNumber,
                                line.lineReference(),
                                line.articleNumber(),
                                line.packSize() != null ? toKey(line.packSize()) : null,
                                line.expectedQuantity() != null ? line.expectedQuantity() : 0,
                                0
                        )));
            }
        }
        inboundStore.updateStatus(entity, STATUS_STARTED);

        InboundDeliveryReply reply = new InboundDeliveryReply(
                delivery.clientNumber(),
                delivery.inboundDeliveryNumber(),
                delivery.businessCase() != null ? delivery.businessCase() : "GOODS_IN",
                "KISOFT",
                STATUS_STARTED,
                Instant.now().toString(),
                delivery.inboundDeliveryLines()
        );
        callback.sendInboundDeliveryReply(reply);
        return new LifecycleResult(Code.OK, "Processing started", null, reply, false);
    }

    @Transactional
    public LifecycleResult recordLoadUnit(InboundDeliveryLoadUnitReceipt r) {
        Optional<InboundDeliveryEntity> existing = inboundStore.find(r.clientNumber(), r.inboundDeliveryNumber());
        if (existing.isEmpty()) {
            return new LifecycleResult(Code.E_AKO_MOVM_0003_NOT_FOUND,
                    "Inbound delivery not found", null, null, false);
        }
        InboundDeliveryEntity entity = existing.get();
        if (!STATUS_STARTED.equals(entity.getProcessingStatus())) {
            return new LifecycleResult(Code.E_AKO_MOVM_0004_WRONG_STATUS,
                    "Inbound delivery not in STARTED status (current: " + entity.getProcessingStatus() + ")",
                    null, null, false);
        }

        Optional<InboundDeliveryProgressEntity> progressOpt = progressRepo
                .findByClientNumberAndInboundDeliveryNumberAndLineReference(
                        r.clientNumber(), r.inboundDeliveryNumber(), r.lineReference());
        if (progressOpt.isEmpty()) {
            return new LifecycleResult(Code.E_AKO_MOVM_0007_LINE_NOT_FOUND,
                    "Inbound delivery line " + r.lineReference() + " not found", null, null, false);
        }
        InboundDeliveryProgressEntity progress = progressOpt.get();

        if (r.quantity() > progress.getOpenQuantity()) {
            return new LifecycleResult(Code.E_AKO_MOVM_0006_QTY_EXCEEDS_OPEN,
                    "Confirmed quantity " + r.quantity() + " exceeds remaining open quantity "
                            + progress.getOpenQuantity(),
                    null, null, false);
        }

        Optional<ToteCompartmentEntity> compartmentOpt = toteRepo
                .findByClientNumberAndLoadUnitCodeAndCompartment(
                        r.clientNumber(), r.loadUnitCode(), r.compartment());
        if (compartmentOpt.isPresent()) {
            ToteCompartmentEntity tc = compartmentOpt.get();
            if (!tc.getArticleNumber().equals(progress.getArticleNumber())
                    || !tc.getPackSize().equals(progress.getPackSize())) {
                return new LifecycleResult(Code.E_AKO_MOVM_0009_WRONG_ARTICLE_IN_COMPARTMENT,
                        "Compartment already holds article " + tc.getArticleNumber() + "/" + tc.getPackSize(),
                        null, null, false);
            }
            return new LifecycleResult(Code.E_AKO_MOVM_0008_COMPARTMENT_NOT_EMPTY,
                    "Compartment is not empty - topping-up not allowed",
                    null, null, false);
        }

        progress.setReceivedQuantity(progress.getReceivedQuantity() + r.quantity());
        progressRepo.save(progress);

        toteRepo.save(new ToteCompartmentEntity(
                r.clientNumber(), r.loadUnitCode(), r.compartment(),
                progress.getArticleNumber(), progress.getPackSize(), r.quantity()));

        // When inbound-auto-stock already booked expectedQuantity, do not double-book on load unit.
        if (!properties.isInboundAutoStock()) {
            asrsStock.addStock(r.clientNumber(), progress.getArticleNumber(), progress.getPackSize(), r.quantity());
        }

        Integer packSize = progress.getPackSize() != null ? Integer.valueOf(progress.getPackSize()) : null;
        Integer slot = parseSlot(r.compartment());
        StockEntry processedStock = new StockEntry(
                r.loadUnitCode(),
                slot,
                new PackUnitKeyRef(r.clientNumber(), progress.getArticleNumber(), packSize),
                r.quantity(),
                r.stockType(),
                r.lotNumber(),
                r.dateMark(),
                r.serialNumber(),
                r.reservationCode(),
                null,
                r.stockQuality(),
                null,
                null
        );
        StockReceived event = new StockReceived(
                UUID.randomUUID().toString(),
                new InboundDeliveryReference(r.clientNumber(), r.inboundDeliveryNumber(), r.lineReference()),
                r.quantity(),
                null,
                null,
                null,
                Instant.now().toString(),
                processedStock
        );
        callback.sendStockReceived(event);
        if (properties.isStorageOrderReplyEnabled()) {
            sendStorageOrderReply(r.clientNumber(), r.inboundDeliveryNumber(), r.loadUnitCode(), "STARTED");
            sendStorageOrderReply(r.clientNumber(), r.inboundDeliveryNumber(), r.loadUnitCode(), "FINISHED");
        }

        boolean allDone = isAllReceived(r.clientNumber(), r.inboundDeliveryNumber());
        InboundDeliveryReply reply = null;
        if (allDone) {
            reply = autoFinish(entity);
        }
        return new LifecycleResult(Code.OK, "Load unit recorded", event, reply, allDone);
    }

    @Transactional
    public LifecycleResult finishProcessing(String clientNumber, String inboundDeliveryNumber) {
        Optional<InboundDeliveryEntity> existing = inboundStore.find(clientNumber, inboundDeliveryNumber);
        if (existing.isEmpty()) {
            return new LifecycleResult(Code.E_AKO_MOVM_0003_NOT_FOUND,
                    "Inbound delivery not found", null, null, false);
        }
        InboundDeliveryEntity entity = existing.get();
        if (!STATUS_STARTED.equals(entity.getProcessingStatus())) {
            return new LifecycleResult(Code.E_AKO_MOVM_0004_WRONG_STATUS,
                    "Inbound delivery not in STARTED status", null, null, false);
        }
        InboundDeliveryReply reply = autoFinish(entity);
        return new LifecycleResult(Code.OK, "Processing finished", null, reply, true);
    }

    private InboundDeliveryReply autoFinish(InboundDeliveryEntity entity) {
        InboundDelivery delivery = inboundStore.readPayload(entity);
        if (properties.isInboundAutoStock()) {
            correctAutoStockShortfall(delivery);
        }
        inboundStore.updateStatus(entity, STATUS_FINISHED);
        InboundDeliveryReply reply = new InboundDeliveryReply(
                delivery.clientNumber(),
                delivery.inboundDeliveryNumber(),
                delivery.businessCase() != null ? delivery.businessCase() : "GOODS_IN",
                "KISOFT",
                STATUS_FINISHED,
                Instant.now().toString(),
                delivery.inboundDeliveryLines()
        );
        callback.sendInboundDeliveryReply(reply);
        return reply;
    }

    private boolean isAllReceived(String clientNumber, String inboundDeliveryNumber) {
        List<InboundDeliveryProgressEntity> progress = progressRepo
                .findByClientNumberAndInboundDeliveryNumber(clientNumber, inboundDeliveryNumber);
        if (progress.isEmpty()) return false;
        return progress.stream().allMatch(p -> p.getReceivedQuantity() >= p.getExpectedQuantity());
    }

    private void correctAutoStockShortfall(InboundDelivery delivery) {
        List<InboundDeliveryProgressEntity> progressList = progressRepo
                .findByClientNumberAndInboundDeliveryNumber(
                        delivery.clientNumber(), delivery.inboundDeliveryNumber());
        for (InboundDeliveryProgressEntity progress : progressList) {
            int shortfall = progress.getExpectedQuantity() - progress.getReceivedQuantity();
            if (shortfall > 0) {
                asrsStock.removeStock(
                        progress.getClientNumber(),
                        progress.getArticleNumber(),
                        progress.getPackSize(),
                        shortfall);
            }
        }
    }

    private void sendStorageOrderReply(String clientNumber, String inboundDeliveryNumber,
                                       String loadUnitCode, String status) {
        callback.sendStorageOrderReply(new StorageOrderReply(
                loadUnitCode, clientNumber, inboundDeliveryNumber, status, Instant.now().toString()));
    }

    private static Integer parseSlot(String compartment) {
        if (compartment == null || compartment.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(compartment);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
