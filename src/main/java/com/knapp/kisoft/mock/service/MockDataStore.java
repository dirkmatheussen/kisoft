package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.api.dto.PackUnitFull;
import com.knapp.kisoft.mock.api.dto.StorageOrder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory store for pack units and storage orders (mock check endpoints).
 */
@Component
public class MockDataStore {

    private final List<PackUnitFull> packUnits = new CopyOnWriteArrayList<>();
    private final List<StorageOrder> storageOrders = new CopyOnWriteArrayList<>();

    public List<PackUnitFull> getAllPackUnits() {
        return Collections.unmodifiableList(new ArrayList<>(packUnits));
    }

    public void addPackUnits(List<PackUnitFull> units) {
        packUnits.addAll(units);
    }

    public void clearPackUnits() {
        packUnits.clear();
    }

    public List<StorageOrder> getAllStorageOrders() {
        return Collections.unmodifiableList(new ArrayList<>(storageOrders));
    }

    public void addStorageOrder(StorageOrder order) {
        storageOrders.add(order);
    }

    public void clearStorageOrders() {
        storageOrders.clear();
    }
}
