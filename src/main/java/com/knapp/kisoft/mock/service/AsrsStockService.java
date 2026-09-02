package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.persistence.AsrsStockEntity;
import com.knapp.kisoft.mock.persistence.AsrsStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tracks aggregated ASRS inventory per (clientNumber, articleNumber, packSize).
 * Backs the MA-01 E1 guard ("no part delete while ASRS inventory exists"), is increased on
 * inbound ({@link #addStock} creates or increments), and decreased when a goods-out order
 * reaches PROCESSED.
 */
@Service
public class AsrsStockService {

    private final AsrsStockRepository repo;

    public AsrsStockService(AsrsStockRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void addStock(String clientNumber, String articleNumber, String packSize, int delta) {
        if (delta <= 0) return;
        // Create inventory item when missing; otherwise increment existing quantity.
        AsrsStockEntity entity = repo
                .findByClientNumberAndArticleNumberAndPackSize(clientNumber, articleNumber, packSize)
                .orElseGet(() -> new AsrsStockEntity(clientNumber, articleNumber, packSize, 0));
        entity.setQuantity(entity.getQuantity() + delta);
        repo.save(entity);
    }

    /**
     * Remove up to {@code qty} from ASRS stock (goods-out picking, retrieval to conventional).
     * Returns the quantity actually removed (capped at the available stock).
     */
    @Transactional
    public int removeStock(String clientNumber, String articleNumber, String packSize, int qty) {
        if (qty <= 0) return 0;
        AsrsStockEntity entity = repo
                .findByClientNumberAndArticleNumberAndPackSize(clientNumber, articleNumber, packSize)
                .orElse(null);
        if (entity == null) return 0;
        int removed = Math.min(qty, entity.getQuantity());
        entity.setQuantity(entity.getQuantity() - removed);
        repo.save(entity);
        return removed;
    }

    /** Set the absolute quantity for a slot/article (inventory count correction). Returns the delta applied. */
    @Transactional
    public int setQuantity(String clientNumber, String articleNumber, String packSize, int counted) {
        if (counted < 0) counted = 0;
        AsrsStockEntity entity = repo
                .findByClientNumberAndArticleNumberAndPackSize(clientNumber, articleNumber, packSize)
                .orElseGet(() -> new AsrsStockEntity(clientNumber, articleNumber, packSize, 0));
        int delta = counted - entity.getQuantity();
        entity.setQuantity(counted);
        repo.save(entity);
        return delta;
    }

    /** Total available quantity for an article across all pack sizes (intake OUT_OF_STOCK check). */
    @Transactional(readOnly = true)
    public int availableForArticle(String clientNumber, String articleNumber) {
        return repo.findByClientNumberAndArticleNumber(clientNumber, articleNumber).stream()
                .mapToInt(AsrsStockEntity::getQuantity).sum();
    }

    @Transactional(readOnly = true)
    public boolean hasStock(String clientNumber, String articleNumber, String packSize) {
        return repo.existsByClientNumberAndArticleNumberAndPackSizeAndQuantityGreaterThan(
                clientNumber, articleNumber, packSize, 0);
    }

    @Transactional(readOnly = true)
    public int getQuantity(String clientNumber, String articleNumber, String packSize) {
        return repo.findByClientNumberAndArticleNumberAndPackSize(clientNumber, articleNumber, packSize)
                .map(AsrsStockEntity::getQuantity)
                .orElse(0);
    }

    /** Snapshot of all tracked ASRS stock (used to build an Inventory Report). */
    @Transactional(readOnly = true)
    public List<AsrsStockEntity> listAll() {
        return repo.findAll();
    }
}
