package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.MasterDataUpdateSession;
import com.knapp.kisoft.mock.api.dto.PackUnitFull;
import com.knapp.kisoft.mock.api.dto.PackUnitKeyRef;
import com.knapp.kisoft.mock.service.MockDataStore;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mock KiSoft PackUnit API - Masterdata Article.
 * POST /packUnit/updateSession, PUT /packUnit, GET /packUnit, DELETE /packUnit
 */
@Tag(name = "MasterData-Article", description = "Pack unit master data (update session, PUT, GET, DELETE)")
@RestController
@RequestMapping("/oneapi/v1")
public class PackUnitController {

    private final MockDataStore dataStore;

    public PackUnitController(MockDataStore dataStore) {
        this.dataStore = dataStore;
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Update session opened (SET) or closed (CLEANUP) successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request. lineCode: E-AKO-GENR-0001 (General error), E-AKO-GENR-0002 (Format error)")
    })
    @PostMapping("/packUnit/updateSession")
    public ResponseEntity<Map<String, Object>> postPackUnitUpdateSession(
            @RequestBody @Valid MasterDataUpdateSession request) {

        String transmissionTag = request.transmissionTag();
        String clientNumber = request.clientNumber() != null ? request.clientNumber() : "DEFAULT";

        if ("SET".equals(transmissionTag)) {
            dataStore.packUnitSessionSet(clientNumber);
        } else if ("CLEANUP".equals(transmissionTag)) {
            dataStore.packUnitSessionCleanup(clientNumber);
        }

        var response = Map.<String, Object>of(
                "http", 200,
                "clientNumber", clientNumber,
                "transmissionTag", transmissionTag,
                "status", "OK",
                "message", "Update session " + ("SET".equals(transmissionTag) ? "opened" : "closed") + " successfully"
        );
        return ResponseEntity.ok(response);
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pack units created/updated successfully"),
            @ApiResponse(responseCode = "207", description = "Some operations failed. lineCode: E-AKO-MAST-0006 (Wrong pack size / duplicate article+packSize), E-AKO-MAST-0012, E-AKO-MAST-0013"),
            @ApiResponse(responseCode = "400", description = "Bad request (e.g. max records exceeded). lineCode: E-AKO-GENR-0001, E-AKO-GENR-0002")
    })
    @PutMapping("/packUnit")
    public ResponseEntity<?> putPackUnits(@RequestBody List<PackUnitFull> packUnits) {
        if (dataStore.getPackUnitCount() + packUnits.size() > dataStore.getMaxRecords()) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", "Bad Request",
                    "message", "No more then " + dataStore.getMaxRecords() + " records allowed"
            ));
        }
        // Uniqueness: one pack unit per (clientNumber, articleNumber, packSize). Reject duplicates in request.
        Set<String> seen = new java.util.HashSet<>();
        List<PackUnitFull> valid = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        for (PackUnitFull u : packUnits) {
            String client = u.article() != null ? u.article().clientNumber() : "DEFAULT";
            String key = MockDataStore.packUnitKey(u);
            if (!seen.add(key)) {
                errors.add(perItemError(client, u.article().articleNumber(), u.packSize(), "E-AKO-MAST-0006"));
            } else {
                valid.add(u);
            }
        }
        if (!errors.isEmpty()) {
            dataStore.addOrUpdatePackUnits(valid);
            return ResponseEntity.status(207).body(errors);
        }
        dataStore.addOrUpdatePackUnits(packUnits);
        var response = Map.<String, Object>of(
                "http", 200,
                "status", "OK",
                "message", "Pack units created/updated successfully",
                "processedCount", packUnits.size()
        );
        return ResponseEntity.ok(response);
    }

    private static Map<String, Object> perItemError(String clientNumber, String articleNumber, String packSize, String code) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clientNumber", clientNumber);
        m.put("articleNumber", articleNumber);
        m.put("packSize", packSize);
        m.put("codes", List.of(code));
        return m;
    }

    @ApiResponse(responseCode = "200", description = "List of all pack units")
    @GetMapping("/packUnit")
    public ResponseEntity<List<PackUnitFull>> getPackUnits() {
        return ResponseEntity.ok(dataStore.getAllPackUnits());
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pack units deleted (all if no body, or listed refs without stock)"),
            @ApiResponse(responseCode = "207", description = "Some operations failed. lineCode: E-AKO-STOC-0002 (Active stock exists, not allowed to delete)"),
            @ApiResponse(responseCode = "400", description = "Bad request. lineCode: E-AKO-GENR-0001, E-AKO-GENR-0002")
    })
    @DeleteMapping("/packUnit")
    public ResponseEntity<?> deletePackUnits(@RequestBody(required = false) List<PackUnitKeyRef> refs) {
        if (refs == null || refs.isEmpty()) {
            dataStore.clearPackUnits();
            return ResponseEntity.ok(Map.of(
                    "http", 200,
                    "status", "OK",
                    "message", "All pack units deleted"
            ));
        }
        List<Map<String, Object>> errors = new ArrayList<>();
        List<PackUnitKeyRef> toDelete = new ArrayList<>();
        for (PackUnitKeyRef ref : refs) {
            if (dataStore.packUnitHasStock(ref.clientNumber(), ref.articleNumber(), ref.packSize())) {
                errors.add(perItemError(ref.clientNumber(), ref.articleNumber(), ref.packSize(), "E-AKO-STOC-0002"));
            } else {
                toDelete.add(ref);
            }
        }
        for (PackUnitKeyRef ref : toDelete) {
            dataStore.removePackUnit(ref.clientNumber(), ref.articleNumber(), ref.packSize());
        }
        if (!errors.isEmpty()) {
            return ResponseEntity.status(207).body(errors);
        }
        return ResponseEntity.ok(Map.of(
                "http", 200,
                "status", "OK",
                "message", "Pack units deleted",
                "processedCount", toDelete.size()
        ));
    }
}
