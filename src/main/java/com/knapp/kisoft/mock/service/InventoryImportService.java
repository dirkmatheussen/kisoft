package com.knapp.kisoft.mock.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knapp.kisoft.mock.api.dto.PackUnitKeyRef;
import com.knapp.kisoft.mock.api.dto.StockInventory;
import com.knapp.kisoft.mock.persistence.AsrsStockEntity;
import com.knapp.kisoft.mock.persistence.AsrsStockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Bulk-load ASRS stock from an InventoryReport {@code stockInventory} array (mock load-test helper).
 */
@Service
public class InventoryImportService {

    private static final Logger log = LoggerFactory.getLogger(InventoryImportService.class);
    private static final int BATCH_SIZE = 500;

    private final AsrsStockRepository repo;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public InventoryImportService(AsrsStockRepository repo, ObjectMapper objectMapper, TransactionTemplate tx) {
        this.repo = repo;
        this.objectMapper = objectMapper;
        this.tx = tx;
    }

    public record ImportResult(int rowsRead, int rowsWritten, boolean uniquifyArticles, boolean replaceAll) {}

    public ImportResult importItems(List<StockInventory> items, boolean uniquifyArticles, boolean replaceAll) {
        if (items == null || items.isEmpty()) {
            return new ImportResult(0, 0, uniquifyArticles, replaceAll);
        }
        if (replaceAll) {
            tx.executeWithoutResult(status -> repo.deleteAllInBatch());
        }
        int written = persistBatches(items, uniquifyArticles);
        return new ImportResult(items.size(), written, uniquifyArticles, replaceAll);
    }

    /**
     * Stream-parse an InventoryReport JSON file and import {@code stockInventory}.
     */
    public ImportResult importFromFile(Path path, boolean uniquifyArticles, boolean replaceAll) throws Exception {
        if (replaceAll) {
            tx.executeWithoutResult(status -> repo.deleteAllInBatch());
        }
        int read = 0;
        int written = 0;
        List<StockInventory> batch = new ArrayList<>(BATCH_SIZE);
        JsonFactory factory = objectMapper.getFactory();
        try (InputStream in = Files.newInputStream(path);
             JsonParser parser = factory.createParser(in)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("Expected InventoryReport object at root");
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName();
                parser.nextToken();
                if (!"stockInventory".equals(field)) {
                    parser.skipChildren();
                    continue;
                }
                if (parser.currentToken() != JsonToken.START_ARRAY) {
                    throw new IllegalArgumentException("stockInventory must be an array");
                }
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    StockInventory item = objectMapper.readValue(parser, StockInventory.class);
                    batch.add(item);
                    read++;
                    if (batch.size() >= BATCH_SIZE) {
                        written += persistBatch(batch, uniquifyArticles, read - batch.size());
                        batch.clear();
                        if (read % 10000 == 0) {
                            log.info("Inventory import progress: {} rows read", read);
                        }
                    }
                }
            }
        }
        if (!batch.isEmpty()) {
            written += persistBatch(batch, uniquifyArticles, read - batch.size());
        }
        log.info("Inventory import done: read={}, written={}, uniquify={}, replaceAll={}",
                read, written, uniquifyArticles, replaceAll);
        return new ImportResult(read, written, uniquifyArticles, replaceAll);
    }

    private int persistBatches(List<StockInventory> items, boolean uniquifyArticles) {
        int written = 0;
        for (int i = 0; i < items.size(); i += BATCH_SIZE) {
            List<StockInventory> slice = items.subList(i, Math.min(i + BATCH_SIZE, items.size()));
            written += persistBatch(slice, uniquifyArticles, i);
        }
        return written;
    }

    private int persistBatch(List<StockInventory> slice, boolean uniquifyArticles, int startIndex) {
        Integer written = tx.execute(status -> {
            List<AsrsStockEntity> entities = new ArrayList<>(slice.size());
            for (int i = 0; i < slice.size(); i++) {
                StockInventory item = slice.get(i);
                if (item == null || item.packUnit() == null || item.packUnit().articleNumber() == null) {
                    continue;
                }
                PackUnitKeyRef pu = item.packUnit();
                int seq = startIndex + i + 1;
                String article = uniquifyArticles
                        ? pu.articleNumber() + " - " + seq
                        : pu.articleNumber();
                String packSize = pu.packSize() != null ? String.valueOf(pu.packSize()) : "1";
                AsrsStockEntity entity = new AsrsStockEntity(
                        pu.clientNumber(),
                        article,
                        packSize,
                        ReservationCodes.normalize(item.reservationCode()),
                        item.quantity() != null ? item.quantity() : 0);
                entity.setStockType(item.stockType());
                entity.setLotNumber(item.lotNumber());
                entity.setDateMark(item.dateMark());
                entity.setSerialNumber(item.serialNumber());
                if (item.stockLockReasons() != null && !item.stockLockReasons().isEmpty()) {
                    try {
                        entity.setStockLockReasonsJson(objectMapper.writeValueAsString(item.stockLockReasons()));
                    } catch (Exception ignored) {
                        entity.setStockLockReasonsJson(null);
                    }
                }
                entities.add(entity);
            }
            repo.saveAll(entities);
            repo.flush();
            return entities.size();
        });
        return written != null ? written : 0;
    }
}
