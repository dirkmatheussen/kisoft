package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.InventoryReport;
import com.knapp.kisoft.mock.api.dto.OneApiOkResponse;
import com.knapp.kisoft.mock.service.InventoryImportService;
import com.knapp.kisoft.mock.service.InventoryImportService.ImportResult;
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

    private final InventoryImportService importService;

    public InventoryImportController(InventoryImportService importService) {
        this.importService = importService;
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
        Path path = Path.of(pathValue);
        if (!Files.isRegularFile(path)) {
            return ResponseEntity.badRequest().body(new OneApiOkResponse(
                    400, "BAD_REQUEST", "File not found: " + path));
        }
        try {
            ImportResult result = importService.importFromFile(path, uniquifyArticles, replaceAll);
            return ResponseEntity.ok(new OneApiOkResponse(
                    200,
                    "OK",
                    "Imported " + result.rowsWritten() + " of " + result.rowsRead()
                            + " stockInventory rows from " + path
                            + " (uniquifyArticles=" + result.uniquifyArticles()
                            + ", replaceAll=" + result.replaceAll() + ")"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new OneApiOkResponse(
                    400, "BAD_REQUEST", "Import failed: " + e.getMessage()));
        }
    }
}
