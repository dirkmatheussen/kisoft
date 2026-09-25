package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.InventoryReport;
import com.knapp.kisoft.mock.api.dto.OneApiOkResponse;
import com.knapp.kisoft.mock.config.KnappMockProperties;
import com.knapp.kisoft.mock.service.InventoryImportService;
import com.knapp.kisoft.mock.service.InventoryImportService.ImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Mock-only bulk inventory load for APIC load tests (not part of KiSoft One API).
 */
@Tag(name = "Inventory Import (mock)",
        description = "Bulk-load ASRS stock from an InventoryReport JSON (stockInventory). "
                + "Optional uniquifyArticles rewrites each articleNumber to `{original} - {n}` so duplicate "
                + "swagger samples become distinct rows.")
@RestController
@RequestMapping("/oneapi/v1/inventoryItem/operator")
public class InventoryImportController {

    private static final Logger log = LoggerFactory.getLogger(InventoryImportController.class);

    private final InventoryImportService importService;
    private final KnappMockProperties properties;

    public InventoryImportController(InventoryImportService importService, KnappMockProperties properties) {
        this.importService = importService;
        this.properties = properties;
    }

    @Operation(operationId = "OperatorImportInventoryReport",
            summary = "Import InventoryReport.stockInventory into ASRS (mock)",
            description = "Accepts a PostInventoryReport-shaped JSON body. With uniquifyArticles=true (default), "
                    + "each line becomes `articleNumber - <sequence>` (1-based) so 100k identical swagger rows "
                    + "yield 100k DB records.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Import completed"),
            @ApiResponse(responseCode = "400", description = "Invalid body")
    })
    @PostMapping(value = "/import", consumes = "application/json")
    public ResponseEntity<OneApiOkResponse> importReport(
            @RequestBody @Valid InventoryReport report,
            @Parameter(description = "Append sequence to articleNumber: `{article} - {n}`")
            @RequestParam(defaultValue = "true") boolean uniquifyArticles,
            @Parameter(description = "Delete all ASRS stock before import")
            @RequestParam(defaultValue = "false") boolean replaceAll) {
        ImportResult result = importService.importItems(
                report.stockInventory(), uniquifyArticles, replaceAll);
        return ResponseEntity.ok(new OneApiOkResponse(
                200,
                "OK",
                "Imported " + result.rowsWritten() + " of " + result.rowsRead()
                        + " stockInventory rows (uniquifyArticles=" + result.uniquifyArticles()
                        + ", replaceAll=" + result.replaceAll() + ")"));
    }

    @Operation(operationId = "OperatorImportInventoryReportFile",
            summary = "Import InventoryReport JSON from a local file path (mock)",
            description = "Streams the file from the mock server filesystem (useful for ~100k-row load-test files).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Import completed"),
            @ApiResponse(responseCode = "400", description = "Path missing or unreadable")
    })
    @PostMapping(value = "/importFile", consumes = "application/json")
    public ResponseEntity<?> importFile(
            @RequestBody Map<String, String> body,
            @RequestParam(defaultValue = "true") boolean uniquifyArticles,
            @RequestParam(defaultValue = "false") boolean replaceAll) {
        String pathValue = body != null ? body.get("path") : null;
        if (pathValue == null || pathValue.isBlank()) {
            return ResponseEntity.badRequest().body(new OneApiOkResponse(
                    400, "BAD_REQUEST", "Body must include non-blank \"path\""));
        }
        Path path = resolveWithinImportDir(pathValue);
        if (path == null) {
            // Path escaped the allowed import directory — reject without echoing the caller's input.
            return ResponseEntity.badRequest().body(new OneApiOkResponse(
                    400, "BAD_REQUEST", "path must resolve inside the configured import directory"));
        }
        if (!Files.isRegularFile(path)) {
            return ResponseEntity.badRequest().body(new OneApiOkResponse(
                    400, "BAD_REQUEST", "No importable file at the requested path"));
        }
        try {
            ImportResult result = importService.importFromFile(path, uniquifyArticles, replaceAll);
            return ResponseEntity.ok(new OneApiOkResponse(
                    200,
                    "OK",
                    "Imported " + result.rowsWritten() + " of " + result.rowsRead()
                            + " stockInventory rows"
                            + " (uniquifyArticles=" + result.uniquifyArticles()
                            + ", replaceAll=" + result.replaceAll() + ")"));
        } catch (Exception e) {
            // Log the detail server-side; do not leak parser/IO messages (which can echo file contents).
            log.warn("importFile failed for a path under the import directory", e);
            return ResponseEntity.badRequest().body(new OneApiOkResponse(
                    400, "BAD_REQUEST", "Import failed: file is not a valid InventoryReport JSON"));
        }
    }

    /**
     * Resolves a caller-supplied path against the configured import directory and returns it only when it
     * stays inside that directory. Absolute paths under the import directory are allowed; anything that
     * escapes it (via {@code ..} or an unrelated absolute path) yields {@code null}.
     */
    private Path resolveWithinImportDir(String pathValue) {
        Path base = Path.of(properties.getImportDir()).toAbsolutePath().normalize();
        Path resolved = base.resolve(pathValue).normalize();
        return resolved.startsWith(base) ? resolved : null;
    }
}
