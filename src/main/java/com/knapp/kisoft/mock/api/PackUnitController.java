package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.config.OpenApiConfig;
import com.knapp.kisoft.mock.api.dto.MasterDataUpdateSession;
import com.knapp.kisoft.mock.api.dto.ODataCollectionResponse;
import com.knapp.kisoft.mock.api.dto.OneApiErrorResponse;
import com.knapp.kisoft.mock.api.dto.OneApiOkResponse;
import com.knapp.kisoft.mock.api.dto.PackUnitFull;
import com.knapp.kisoft.mock.api.dto.PackUnitKeyRef;
import com.knapp.kisoft.mock.service.AsrsStockService;
import com.knapp.kisoft.mock.service.ODataQuerySupport;
import com.knapp.kisoft.mock.service.PackUnitStoreService;

import static com.knapp.kisoft.mock.service.PackSizeKeys.toKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Mock KiSoft PackUnit API - Masterdata Article (HIS Appendix §6.1).
 * POST /packUnit/updateSession, PUT /packUnit, DELETE /packUnit, GET /packUnit (mock OData read).
 * Note: the KiSoft One Product API provides no GET requests (HIS Appendix §2.3.1); GET is mock-only.
 */
@Tag(name = "MasterData-Article", description = "Pack unit master data (update session, PUT, DELETE, GET mock read)")
@RestController
@RequestMapping("/oneapi/v1")
public class PackUnitController {

    /** MA-01 batch size limit (asrs-specs spec.md FR-003: e.g. 10,000 records per batch). */
    static final int MAX_BATCH_SIZE = 10_000;

    private final PackUnitStoreService packUnitStore;
    private final AsrsStockService asrsStock;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    public PackUnitController(PackUnitStoreService packUnitStore, AsrsStockService asrsStock) {
        this.packUnitStore = packUnitStore;
        this.asrsStock = asrsStock;
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OData v4 collection (mock-only; not KiSoft API)")
    })
    @Operation(
            operationId = "GetPackUnits",
            summary = "[MOCK ONLY — not KiSoft API] List pack units (OData read)",
            description = "**Not part of the KiSoft One Product API** (HIS Appendix §2.3.1: KiSoft exposes no GET requests). "
                    + "Mock-only inspection endpoint for stored master data. "
                    + "Supports OData query options `$filter`, `$top`, `$skip`, `$count`. "
                    + "Example filter: `clientNumber eq 'OB' and articleNumber eq 'ART-001'`.",
            tags = { OpenApiConfig.MOCK_ODATA_READ_TAG })
    @GetMapping("/packUnit")
    public ResponseEntity<ODataCollectionResponse<PackUnitFull>> getPackUnits(
            @Parameter(description = "OData filter, e.g. clientNumber eq 'OB' and packSize eq 'EU'")
            @RequestParam(value = "$filter", required = false) String filter,
            @Parameter(description = "Maximum records to return (default 100, max 1000)")
            @RequestParam(value = "$top", required = false) String top,
            @Parameter(description = "Number of records to skip")
            @RequestParam(value = "$skip", required = false) String skip,
            @Parameter(description = "Include @odata.count when true")
            @RequestParam(value = "$count", required = false) String count) {
        ODataCollectionResponse<PackUnitFull> response = ODataQuerySupport.buildPage(
                ODataQuerySupport.metadataContext(contextPath, "PackUnits"),
                packUnitStore.listAll(),
                ODataQuerySupport.parseFilter(filter),
                pu -> ODataQuerySupport.fields(
                        "clientNumber", pu.article() != null ? ODataQuerySupport.str(pu.article().clientNumber()) : "",
                        "articleNumber", pu.article() != null ? ODataQuerySupport.str(pu.article().articleNumber()) : "",
                        "packSize", ODataQuerySupport.str(pu.packSize())),
                ODataQuerySupport.parseTop(top),
                ODataQuerySupport.parseSkip(skip),
                ODataQuerySupport.parseCount(count));
        return ResponseEntity.ok(response);
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Update session opened (SET) or closed (CLEANUP) successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request. lineCode: E-AKO-GENR-0001 (General error), E-AKO-GENR-0002 (Format error)")
    })
    @Operation(operationId = "PostPackUnitUpdateSession")
    @PostMapping("/packUnit/updateSession")
    public ResponseEntity<OneApiOkResponse> postPackUnitUpdateSession(
            @RequestBody @Valid MasterDataUpdateSession request) {

        String transmissionTag = request.transmissionTag();
        String clientNumber = request.clientNumber() != null ? request.clientNumber() : "DEFAULT";

        if ("SET".equals(transmissionTag)) {
            packUnitStore.startUpdateSession(clientNumber);
        } else if ("CLEANUP".equals(transmissionTag)) {
            packUnitStore.cleanupUpdateSession(clientNumber);
        }

        return ResponseEntity.ok(new OneApiOkResponse(
                200,
                "OK",
                "Update session " + ("SET".equals(transmissionTag) ? "opened" : "closed") + " successfully"
        ));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pack units created/updated successfully"),
            @ApiResponse(responseCode = "207", description = "Some operations failed. lineCode: E-AKO-MAST-0006 (Wrong pack size / duplicate article+packSize), E-AKO-MAST-0012, E-AKO-MAST-0013"),
            @ApiResponse(responseCode = "400", description = "Bad request. lineCode: E-AKO-GENR-0001 (General error), E-AKO-GENR-0002 (Format error - e.g. batch exceeds " + MAX_BATCH_SIZE + " records, MA-01 FR-003)")
    })
    @Operation(operationId = "PutPackUnit")
    @PutMapping("/packUnit")
    public ResponseEntity<?> putPackUnits(@RequestBody List<PackUnitFull> packUnits) {
        // MA-01 FR-003: respect maximum batch size; reject oversize submissions
        // explicitly (asrs-specs spec.md User Story 3 + 001 batch limit ~10,000).
        if (packUnits != null && packUnits.size() > MAX_BATCH_SIZE) {
            return ResponseEntity.status(400).body(new OneApiErrorResponse(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "Batch size " + packUnits.size() + " exceeds maximum " + MAX_BATCH_SIZE,
                    List.of("E-AKO-GENR-0002")
            ));
        }
        if (packUnits == null || packUnits.isEmpty()) {
            return ResponseEntity.ok(new OneApiOkResponse(200, "OK", "Empty batch ignored"));
        }
        Set<String> seen = new java.util.HashSet<>();
        List<PackUnitFull> valid = new ArrayList<>();
        List<OneApiErrorResponse> errors = new ArrayList<>();
        for (PackUnitFull u : packUnits) {
            OneApiErrorResponse validationError = validatePackUnit(u);
            if (validationError != null) {
                errors.add(validationError);
                continue;
            }
            String client = u.article().clientNumber() != null ? u.article().clientNumber() : "DEFAULT";
            String key = PackUnitStoreService.key(client, u.article().articleNumber(), u.packSize());
            if (!seen.add(key)) {
                errors.add(new OneApiErrorResponse(
                        client,
                        null,
                        null,
                        u.article().articleNumber(),
                        u.packSize() != null ? String.valueOf(u.packSize()) : null,
                        null,
                        "Duplicate pack unit in same batch",
                        List.of("E-AKO-MAST-0006")
                ));
            } else {
                valid.add(u);
            }
        }
        if (!errors.isEmpty()) {
            if (!valid.isEmpty()) {
                packUnitStore.upsertAll(valid);
                return ResponseEntity.status(207).body(errors);
            }
            return ResponseEntity.status(400).body(errors.size() == 1 ? errors.get(0) : errors);
        }
        packUnitStore.upsertAll(packUnits);
        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", "Pack units created/updated successfully"));
    }

    private static OneApiErrorResponse validatePackUnit(PackUnitFull u) {
        if (u == null) {
            return formatError(null, null, null, "Pack unit entry is missing");
        }
        if (u.article() == null || u.article().articleNumber() == null || u.article().articleNumber().isBlank()) {
            return formatError(u, "article.articleNumber is required");
        }
        if (u.packSize() == null) {
            return formatError(u, "packSize is required");
        }
        if (u.capacityInformation() == null || u.capacityInformation().isEmpty()) {
            return formatError(u, "capacityInformation is required");
        }
        return null;
    }

    private static OneApiErrorResponse formatError(PackUnitFull u, String message) {
        String client = u.article().clientNumber();
        return formatError(client, u.article().articleNumber(), u.packSize() != null ? String.valueOf(u.packSize()) : null, message);
    }

    private static OneApiErrorResponse formatError(
            String clientNumber, String articleNumber, String packSize, String message) {
        return new OneApiErrorResponse(
                clientNumber,
                null,
                null,
                articleNumber,
                packSize,
                null,
                message,
                List.of("E-AKO-GENR-0002")
        );
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pack units deleted (all if no body, or listed refs without stock)"),
            @ApiResponse(responseCode = "207", description = "Some operations failed. lineCode: E-AKO-STOC-0002 (Active ASRS stock exists - MA-01 E1 inventory guard)"),
            @ApiResponse(responseCode = "400", description = "Bad request. lineCode: E-AKO-GENR-0001, E-AKO-GENR-0002")
    })
    @Operation(operationId = "DeletePackUnit")
    @DeleteMapping("/packUnit")
    public ResponseEntity<?> deletePackUnits(@RequestBody(required = false) List<PackUnitKeyRef> refs) {
        if (refs == null || refs.isEmpty()) {
            // Bulk delete - still honour MA-01 E1 inventory guard.
            List<PackUnitFull> all = packUnitStore.listAll();
            List<OneApiErrorResponse> blocked = new ArrayList<>();
            for (PackUnitFull u : all) {
                String client = u.article() != null && u.article().clientNumber() != null
                        ? u.article().clientNumber() : "DEFAULT";
                if (asrsStock.hasStock(client, u.article().articleNumber(), toKey(u.packSize()))) {
                    blocked.add(stockGuardError(client, u.article().articleNumber(), toKey(u.packSize())));
                } else {
                    packUnitStore.deleteOne(client, u.article().articleNumber(), u.packSize());
                }
            }
            if (!blocked.isEmpty()) {
                return ResponseEntity.status(207).body(blocked);
            }
            return ResponseEntity.ok(new OneApiOkResponse(200, "OK", "All pack units deleted"));
        }
        List<OneApiErrorResponse> errors = new ArrayList<>();
        List<PackUnitKeyRef> toDelete = new ArrayList<>();
        for (PackUnitKeyRef ref : refs) {
            String client = ref.clientNumber() != null ? ref.clientNumber() : "DEFAULT";
            // MA-01 E1: KiSoft must reject part delete while ASRS inventory exists.
            if (asrsStock.hasStock(client, ref.articleNumber(), toKey(ref.packSize()))) {
                errors.add(stockGuardError(client, ref.articleNumber(), toKey(ref.packSize())));
            } else {
                toDelete.add(ref);
            }
        }
        for (PackUnitKeyRef ref : toDelete) {
            packUnitStore.deleteOne(ref.clientNumber(), ref.articleNumber(), ref.packSize());
        }
        if (!errors.isEmpty()) {
            return ResponseEntity.status(207).body(errors);
        }
        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", "Pack units deleted"));
    }

    private OneApiErrorResponse stockGuardError(String client, String articleNumber, String packSize) {
        return new OneApiErrorResponse(
                client,
                null,
                null,
                articleNumber,
                packSize,
                null,
                "Cannot delete: ASRS still holds inventory for this part (MA-01 E1)",
                List.of("E-AKO-STOC-0002")
        );
    }
}
