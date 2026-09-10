package com.knapp.kisoft.mock.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knapp.kisoft.mock.persistence.AsrsStockEntity;
import com.knapp.kisoft.mock.persistence.AsrsStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Tracks aggregated ASRS inventory per (clientNumber, articleNumber, packSize).
 * Backs the MA-01 E1 guard ("no part delete while ASRS inventory exists"), is increased on
 * inbound ({@link #addStock} creates or increments), and decreased when a goods-out order
 * reaches PROCESSED. Optional stock attributes follow inventory-report / StockInventory shape.
 */
@Service
public class AsrsStockService {

    private static final ObjectMapper LOCK_REASONS_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final AsrsStockRepository repo;

    public AsrsStockService(AsrsStockRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void addStock(String clientNumber, String articleNumber, String packSize, int delta) {
        addStock(clientNumber, articleNumber, packSize, delta, null);
    }

    /**
     * Add quantity. When {@code attributes} is non-null, metadata is overwritten (last write wins).
     */
    @Transactional
    public void addStock(String clientNumber, String articleNumber, String packSize, int delta,
                         AsrsStockAttributes attributes) {
        if (delta <= 0) return;
        AsrsStockEntity entity = repo
                .findByClientNumberAndArticleNumberAndPackSize(clientNumber, articleNumber, packSize)
                .orElseGet(() -> new AsrsStockEntity(clientNumber, articleNumber, packSize, 0));
        entity.setQuantity(entity.getQuantity() + delta);
        if (attributes != null) {
            applyAttributes(entity, attributes);
        }
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

    public enum LockAction { LOCK, UNLOCK }

    /**
     * Outcome of {@link #changeLocks}. {@code resultingReasons} is null when the row has no locks left;
     * {@code added} / {@code removed} list the reasons that effectively changed (never null).
     */
    public record LockChange(AsrsStockEntity entity, List<String> resultingReasons,
                             List<String> added, List<String> removed) {}

    /**
     * Add or remove lock reasons on the ASRS row matching (client, article, packSize) whose stored
     * reservationCode (Country of Origin) equals {@code reservationCode} (blank == null).
     * UNLOCK with a null/empty list removes all locks. Quantity is untouched.
     *
     * @return empty when no row matches the key or the reservation code differs
     */
    @Transactional
    public Optional<LockChange> changeLocks(String clientNumber, String articleNumber, String packSizeKey,
                                            String reservationCode, LockAction action, List<String> reasons) {
        AsrsStockEntity entity = repo
                .findByClientNumberAndArticleNumberAndPackSize(clientNumber, articleNumber, packSizeKey)
                .orElse(null);
        if (entity == null || !Objects.equals(normalize(entity.getReservationCode()), normalize(reservationCode))) {
            return Optional.empty();
        }
        List<String> current = readLockReasons(entity);
        List<String> result = current == null ? new ArrayList<>() : new ArrayList<>(current);
        List<String> requested = reasons == null ? List.of() : reasons;
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        if (action == LockAction.LOCK) {
            for (String r : requested) {
                if (!result.contains(r)) {
                    result.add(r);
                    added.add(r);
                }
            }
        } else if (requested.isEmpty()) {
            removed.addAll(result);
            result.clear();
        } else {
            for (String r : requested) {
                if (result.remove(r)) {
                    removed.add(r);
                }
            }
        }
        writeLockReasons(entity, result);
        repo.save(entity);
        return Optional.of(new LockChange(entity, result.isEmpty() ? null : List.copyOf(result),
                List.copyOf(added), List.copyOf(removed)));
    }

    private static String normalize(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void writeLockReasons(AsrsStockEntity entity, List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            entity.setStockLockReasonsJson(null);
            return;
        }
        try {
            entity.setStockLockReasonsJson(LOCK_REASONS_MAPPER.writeValueAsString(reasons));
        } catch (Exception e) {
            entity.setStockLockReasonsJson(null);
        }
    }

    public static List<String> readLockReasons(AsrsStockEntity entity) {
        String json = entity.getStockLockReasonsJson();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            List<String> list = LOCK_REASONS_MAPPER.readValue(json, STRING_LIST);
            return list == null || list.isEmpty() ? null : list;
        } catch (Exception e) {
            return null;
        }
    }

    private static void applyAttributes(AsrsStockEntity entity, AsrsStockAttributes attributes) {
        entity.setStockType(attributes.stockType());
        entity.setLotNumber(attributes.lotNumber());
        entity.setDateMark(attributes.dateMark());
        entity.setSerialNumber(attributes.serialNumber());
        entity.setReservationCode(attributes.reservationCode());
        writeLockReasons(entity, attributes.stockLockReasons());
    }
}
