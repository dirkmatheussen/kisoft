package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.api.dto.InboundDelivery;
import com.knapp.kisoft.mock.api.dto.PackUnitFull;
import com.knapp.kisoft.mock.api.dto.PackUnitKeyRef;
import com.knapp.kisoft.mock.api.dto.StorageOrder;
import com.knapp.kisoft.mock.config.KnappMockProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory store for pack units and storage orders (mock check endpoints).
 * Max records configurable via knapp.mock.max-records (default 1000).
 * PackUnit update session: SET opens session per client; CLEANUP removes pack units not re-sent since last SET.
 */
@Component
public class MockDataStore {

    private final KnappMockProperties properties;
    private final List<PackUnitFull> packUnits = new CopyOnWriteArrayList<>();
    /** Per clientNumber: set of (clientNumber, articleNumber, packSize) keys PUT since last SET. */
    private final Map<String, java.util.Set<String>> packUnitSessionDeltaByClient = new ConcurrentHashMap<>();
    /** Pack unit keys (clientNumber|articleNumber|packSize) that have stock - delete rejected with E-AKO-STOC-0002. */
    private final java.util.Set<String> packUnitKeysWithStock = ConcurrentHashMap.newKeySet();
    /** Inbound deliveries by key clientNumber|inboundDeliveryNumber. */
    private final Map<String, InboundDeliveryWithStatus> inboundDeliveries = new ConcurrentHashMap<>();

    public MockDataStore(KnappMockProperties properties) {
        this.properties = properties;
    }

    public int getMaxRecords() {
        return properties.getMaxRecords();
    }

    public List<PackUnitFull> getAllPackUnits() {
        return Collections.unmodifiableList(new ArrayList<>(packUnits));
    }

    public int getPackUnitCount() {
        return packUnits.size();
    }

    /** Key string for a pack unit: clientNumber|articleNumber|packSize */
    public static String packUnitKey(String clientNumber, String articleNumber, String packSize) {
        return clientNumber + "|" + articleNumber + "|" + packSize;
    }

    public static String packUnitKey(PackUnitFull u) {
        return packUnitKey(u.article().clientNumber(), u.article().articleNumber(), u.packSize());
    }

    /** Open update session for client (SET). Resets delta for this client. */
    public void packUnitSessionSet(String clientNumber) {
        packUnitSessionDeltaByClient.put(clientNumber, ConcurrentHashMap.newKeySet());
    }

    /** Merge pack units into store and add their keys to session delta for each client. */
    public void addOrUpdatePackUnits(List<PackUnitFull> units) {
        for (PackUnitFull u : units) {
            String client = u.article() != null ? u.article().clientNumber() : "DEFAULT";
            String key = packUnitKey(u);
            packUnits.removeIf(p -> packUnitKey(p).equals(key));
            packUnits.add(u);
            packUnitSessionDeltaByClient.computeIfAbsent(client, c -> ConcurrentHashMap.newKeySet()).add(key);
        }
    }

    /** Close session for client (CLEANUP): remove pack units not in delta since last SET. */
    public void packUnitSessionCleanup(String clientNumber) {
        java.util.Set<String> delta = packUnitSessionDeltaByClient.get(clientNumber);
        if (delta != null) {
            packUnits.removeIf(p -> {
                String c = p.article() != null ? p.article().clientNumber() : "DEFAULT";
                return c.equals(clientNumber) && !delta.contains(packUnitKey(p));
            });
            packUnitSessionDeltaByClient.remove(clientNumber);
        }
    }

    public void addPackUnits(List<PackUnitFull> units) {
        packUnits.addAll(units);
    }

    public void clearPackUnits() {
        packUnits.clear();
        packUnitSessionDeltaByClient.clear();
    }

    /** Find pack unit by key ref. */
    public PackUnitFull findPackUnit(PackUnitKeyRef ref) {
        String key = packUnitKey(ref.clientNumber(), ref.articleNumber(), ref.packSize());
        return packUnits.stream()
                .filter(p -> packUnitKey(p).equals(key))
                .findFirst()
                .orElse(null);
    }

    /** Check if any pack unit exists for article+packSize (any client or given client). */
    public boolean hasPackUnit(String clientNumber, String articleNumber, String packSize) {
        return packUnits.stream().anyMatch(p ->
                p.article().articleNumber().equals(articleNumber) && p.packSize().equals(packSize)
                        && (clientNumber == null || p.article().clientNumber().equals(clientNumber)));
    }

    /** Remove pack units by key refs. Returns list of refs that were actually removed. */
    public List<PackUnitKeyRef> removePackUnitsByRefs(List<PackUnitKeyRef> refs) {
        List<PackUnitKeyRef> removed = new ArrayList<>();
        for (PackUnitKeyRef ref : refs) {
            String key = packUnitKey(ref.clientNumber(), ref.articleNumber(), ref.packSize());
            boolean removedAny = packUnits.removeIf(p -> packUnitKey(p).equals(key));
            if (removedAny) removed.add(ref);
        }
        return removed;
    }

    /** Remove a single pack unit by key; returns true if found and removed. */
    public boolean removePackUnit(String clientNumber, String articleNumber, String packSize) {
        String key = packUnitKey(clientNumber, articleNumber, packSize);
        return packUnits.removeIf(p -> packUnitKey(p).equals(key));
    }

    /** Whether this pack unit has stock (delete rejected with E-AKO-STOC-0002). */
    public boolean packUnitHasStock(String clientNumber, String articleNumber, String packSize) {
        return packUnitKeysWithStock.contains(packUnitKey(clientNumber, articleNumber, packSize));
    }

    /** Set whether a pack unit has stock (for mock/testing). */
    public void setPackUnitHasStock(String clientNumber, String articleNumber, String packSize, boolean hasStock) {
        String key = packUnitKey(clientNumber, articleNumber, packSize);
        if (hasStock) packUnitKeysWithStock.add(key);
        else packUnitKeysWithStock.remove(key);
    }

    private static String storageOrderKey(String clientNumber, String orderNumber) {
        return clientNumber + "|" + orderNumber;
    }

    /** Storage order with processing status. */
    public static final class StorageOrderWithStatus {
        private StorageOrder order;
        private String processingStatus;

        public StorageOrderWithStatus(StorageOrder order, String processingStatus) {
            this.order = order;
            this.processingStatus = processingStatus;
        }

        public StorageOrder getOrder() { return order; }
        public void setOrder(StorageOrder order) { this.order = order; }
        public String getProcessingStatus() { return processingStatus; }
        public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
    }

    private final Map<String, StorageOrderWithStatus> storageOrdersByKey = new ConcurrentHashMap<>();

    public List<StorageOrder> getAllStorageOrders() {
        return storageOrdersByKey.values().stream()
                .map(StorageOrderWithStatus::getOrder)
                .toList();
    }

    public int getStorageOrderCount() {
        return storageOrdersByKey.size();
    }

    public StorageOrderWithStatus getStorageOrder(String clientNumber, String orderNumber) {
        return storageOrdersByKey.get(storageOrderKey(clientNumber, orderNumber));
    }

    public void putStorageOrder(StorageOrder order, String processingStatus) {
        storageOrdersByKey.put(storageOrderKey(order.clientNumber(), order.orderNumber()),
                new StorageOrderWithStatus(order, processingStatus));
    }

    public void addStorageOrder(StorageOrder order) {
        putStorageOrder(order, "NEW");
    }

    public boolean removeStorageOrder(String clientNumber, String orderNumber) {
        return storageOrdersByKey.remove(storageOrderKey(clientNumber, orderNumber)) != null;
    }

    public boolean hasStorageOrder(String clientNumber, String orderNumber) {
        return storageOrdersByKey.containsKey(storageOrderKey(clientNumber, orderNumber));
    }

    public void clearStorageOrders() {
        storageOrdersByKey.clear();
    }

    /** Inbound delivery with processing status. */
    public static final class InboundDeliveryWithStatus {
        private InboundDelivery delivery;
        private String processingStatus;

        public InboundDeliveryWithStatus(InboundDelivery delivery, String processingStatus) {
            this.delivery = delivery;
            this.processingStatus = processingStatus;
        }

        public InboundDelivery getDelivery() { return delivery; }
        public void setDelivery(InboundDelivery delivery) { this.delivery = delivery; }
        public String getProcessingStatus() { return processingStatus; }
        public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
    }

    public static String inboundDeliveryKey(String clientNumber, String inboundDeliveryNumber) {
        return clientNumber + "|" + inboundDeliveryNumber;
    }

    public InboundDeliveryWithStatus getInboundDelivery(String clientNumber, String inboundDeliveryNumber) {
        return inboundDeliveries.get(inboundDeliveryKey(clientNumber, inboundDeliveryNumber));
    }

    public void putInboundDelivery(InboundDelivery delivery, String processingStatus) {
        inboundDeliveries.put(inboundDeliveryKey(delivery.clientNumber(), delivery.inboundDeliveryNumber()),
                new InboundDeliveryWithStatus(delivery, processingStatus));
    }

    public boolean removeInboundDelivery(String clientNumber, String inboundDeliveryNumber) {
        return inboundDeliveries.remove(inboundDeliveryKey(clientNumber, inboundDeliveryNumber)) != null;
    }

    public boolean hasInboundDelivery(String clientNumber, String inboundDeliveryNumber) {
        return inboundDeliveries.containsKey(inboundDeliveryKey(clientNumber, inboundDeliveryNumber));
    }
}
