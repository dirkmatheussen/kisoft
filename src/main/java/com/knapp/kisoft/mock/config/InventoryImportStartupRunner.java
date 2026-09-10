package com.knapp.kisoft.mock.config;

import com.knapp.kisoft.mock.service.InventoryImportService;
import com.knapp.kisoft.mock.service.InventoryImportService.ImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Optional one-shot seed of ASRS stock from an InventoryReport JSON file at startup.
 */
@Component
public class InventoryImportStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InventoryImportStartupRunner.class);

    private final KnappMockProperties properties;
    private final InventoryImportService importService;

    public InventoryImportStartupRunner(KnappMockProperties properties, InventoryImportService importService) {
        this.properties = properties;
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String pathValue = properties.getImportInventoryReport();
        if (pathValue == null || pathValue.isBlank()) {
            return;
        }
        Path path = Path.of(pathValue);
        if (!Files.isRegularFile(path)) {
            log.error("knapp.mock.import-inventory-report set but file not found: {}", path);
            return;
        }
        try {
            log.info("Starting inventory import from {} (uniquify={}, replaceAll={})",
                    path, properties.isImportUniquifyArticles(), properties.isImportReplaceAll());
            ImportResult result = importService.importFromFile(
                    path, properties.isImportUniquifyArticles(), properties.isImportReplaceAll());
            log.info("Startup inventory import finished: read={}, written={}",
                    result.rowsRead(), result.rowsWritten());
        } catch (Exception e) {
            log.error("Startup inventory import failed: {}", e.getMessage(), e);
        }
    }
}
