package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.InventoryItem;
import com.knapp.kisoft.mock.api.dto.InventoryReport;
import com.knapp.kisoft.mock.api.dto.ODataCollectionResponse;
import com.knapp.kisoft.mock.api.dto.OneApiOkResponse;
import com.knapp.kisoft.mock.api.dto.PackUnitKeyRef;
import com.knapp.kisoft.mock.api.dto.RequestInventoryReport;
import com.knapp.kisoft.mock.api.dto.RequestStorageCapacityReport;
import com.knapp.kisoft.mock.api.dto.StockInventory;
import com.knapp.kisoft.mock.api.dto.StorageCapacityDetail;
import com.knapp.kisoft.mock.api.dto.StorageCapacityReport;
import com.knapp.kisoft.mock.config.OpenApiConfig;
import com.knapp.kisoft.mock.persistence.AsrsStockEntity;
import com.knapp.kisoft.mock.service.AsrsStockService;
import com.knapp.kisoft.mock.service.ODataQuerySupport;
import com.knapp.kisoft.mock.service.ReplyCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

import static com.knapp.kisoft.mock.service.PackSizeKeys.toKey;

/**
 * Mock KiSoft Stock Report API — Stock Management (HIS Appendix §8.2, §8.3),
 * plus mock-only OData GET for ASRS inventory items.
 */
@Tag(name = "Stock Reports", description = "Request an inventory report or storage capacity report; the report is POSTed back as a webhook")
@RestController
@RequestMapping("/oneapi/v1")
public class StockReportController {

    private final AsrsStockService asrsStock;
    private final ReplyCallbackService replyCallbackService;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    public StockReportController(AsrsStockService asrsStock, ReplyCallbackService replyCallbackService) {
        this.asrsStock = asrsStock;
        this.replyCallbackService = replyCallbackService;
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OData v4 collection (mock-only; not KiSoft API)")
    })
    @Operation(
            operationId = "GetInventoryItems",
            summary = "[MOCK ONLY — not KiSoft API] List ASRS inventory items (OData read)",
            description = "**Not part of the KiSoft One Product API** (HIS Appendix §2.3.1: KiSoft exposes no GET requests). "
                    + "Mock-only inspection of booked ASRS stock (same articleNumber/packSize keys as "
                    + "`inventoryRequestLine` / goods-out). Supports `$filter`, `$top`, `$skip`, `$count`. "
                    + "Example filter: `clientNumber eq 'OB' and articleNumber eq 'ART-001'`.",
            tags = { OpenApiConfig.MOCK_ODATA_READ_TAG })
    @GetMapping("/inventoryItem")
    public ResponseEntity<ODataCollectionResponse<InventoryItem>> getInventoryItems(
            @Parameter(description = "OData filter, e.g. clientNumber eq 'OB' and packSize eq '1'")
            @RequestParam(value = "$filter", required = false) String filter,
            @Parameter(description = "Maximum records to return (default 100, max 1000)")
            @RequestParam(value = "$top", required = false) String top,
            @Parameter(description = "Number of records to skip")
            @RequestParam(value = "$skip", required = false) String skip,
            @Parameter(description = "Include @odata.count when true")
            @RequestParam(value = "$count", required = false) String count) {
        List<InventoryItem> all = asrsStock.listAll().stream()
                .map(StockReportController::toInventoryItem)
                .toList();
        ODataCollectionResponse<InventoryItem> response = ODataQuerySupport.buildPage(
                ODataQuerySupport.metadataContext(contextPath, "InventoryItems"),
                all,
                ODataQuerySupport.parseFilter(filter),
                item -> ODataQuerySupport.fields(
                        "clientNumber", ODataQuerySupport.str(item.clientNumber()),
                        "articleNumber", ODataQuerySupport.str(item.articleNumber()),
                        "packSize", ODataQuerySupport.str(item.packSize()),
                        "quantity", ODataQuerySupport.str(item.quantity())),
                ODataQuerySupport.parseTop(top),
                ODataQuerySupport.parseSkip(skip),
                ODataQuerySupport.parseCount(count));
        return ResponseEntity.ok(response);
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request accepted; PostInventoryReport will be POSTed to the callback URL"),
            @ApiResponse(responseCode = "400", description = "Bad request. lineCode: E-AKO-GENR-0002 (Format error)")
    })
    @Operation(operationId = "PostRequestInventoryReport")
    @PostMapping("/requestInventoryReport")
    public ResponseEntity<?> requestInventoryReport(@RequestBody @Valid RequestInventoryReport request) {
        List<StockInventory> stockInventory = asrsStock.listAll().stream()
                .filter(s -> match(request.clientNumber(), s.getClientNumber()))
                .filter(s -> match(request.articleNumber(), s.getArticleNumber()))
                .filter(s -> matchPackSize(request.packSize(), s.getPackSize()))
                .map(StockReportController::toStockInventory)
                .toList();
        InventoryReport report = new InventoryReport(
                request.requestNumber(),
                stockInventory.isEmpty() ? null : stockInventory);
        replyCallbackService.sendInventoryReport(report);
        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", "Inventory report requested; PostInventoryReport will follow"));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request accepted; PostStorageCapacityReport will be POSTed to the callback URL"),
            @ApiResponse(responseCode = "400", description = "Bad request. lineCode: E-AKO-GENR-0002 (Format error)")
    })
    @Operation(operationId = "PostRequestStorageCapacityReport")
    @PostMapping("/requestStorageCapacityReport")
    public ResponseEntity<?> requestStorageCapacityReport(@RequestBody @Valid RequestStorageCapacityReport request) {
        String area = request.storageArea() != null ? request.storageArea() : "DEFAULT";
        StorageCapacityDetail detail = new StorageCapacityDetail(
                area, 1000, 250, 750, 250, null, null, null, null, null);
        StorageCapacityReport report = new StorageCapacityReport(
                request.requestNumber(),
                Instant.now().toString(),
                List.of(detail));
        replyCallbackService.sendStorageCapacityReport(report);
        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", "Storage capacity report requested; PostStorageCapacityReport will follow"));
    }

    private static boolean match(String filter, String value) {
        return filter == null || filter.isBlank() || filter.equals(value);
    }

    private static boolean matchPackSize(Integer filter, String stored) {
        return filter == null || toKey(filter).equals(stored);
    }

    private static InventoryItem toInventoryItem(AsrsStockEntity s) {
        Integer packSize = s.getPackSize() != null ? Integer.valueOf(s.getPackSize()) : null;
        return new InventoryItem(s.getClientNumber(), s.getArticleNumber(), packSize, s.getQuantity());
    }

    private static StockInventory toStockInventory(AsrsStockEntity s) {
        Integer packSize = s.getPackSize() != null ? Integer.valueOf(s.getPackSize()) : null;
        return new StockInventory(
                new PackUnitKeyRef(s.getClientNumber(), s.getArticleNumber(), packSize),
                s.getQuantity(),
                null, null, null, null, null, null);
    }
}
