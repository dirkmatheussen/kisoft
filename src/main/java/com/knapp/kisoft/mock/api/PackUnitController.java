package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.MasterDataUpdateSession;
import com.knapp.kisoft.mock.api.dto.PackUnitFull;
import com.knapp.kisoft.mock.service.MockDataStore;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Mock KiSoft PackUnit API - Masterdata Article.
 * POST /packUnit/updateSession, PUT /packUnit, GET /packUnit, DELETE /packUnit
 */
@RestController
@RequestMapping("/oneapi/v1")
public class PackUnitController {

    private final MockDataStore dataStore;

    public PackUnitController(MockDataStore dataStore) {
        this.dataStore = dataStore;
    }

    @PostMapping("/packUnit/updateSession")
    public ResponseEntity<Map<String, Object>> postPackUnitUpdateSession(
            @RequestBody @Valid MasterDataUpdateSession request) {

        String transmissionTag = request.transmissionTag();
        String clientNumber = request.clientNumber() != null ? request.clientNumber() : "DEFAULT";

        var response = Map.<String, Object>of(
                "http", 200,
                "clientNumber", clientNumber,
                "transmissionTag", transmissionTag,
                "status", "OK",
                "message", "Update session " + ("SET".equals(transmissionTag) ? "opened" : "closed") + " successfully"
        );
        return ResponseEntity.ok(response);
    }

    @PutMapping("/packUnit")
    public ResponseEntity<Map<String, Object>> putPackUnits(@RequestBody List<PackUnitFull> packUnits) {
        dataStore.addPackUnits(packUnits);
        var response = Map.<String, Object>of(
                "http", 200,
                "status", "OK",
                "message", "Pack units created/updated successfully",
                "processedCount", packUnits.size()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/packUnit")
    public ResponseEntity<List<PackUnitFull>> getPackUnits() {
        return ResponseEntity.ok(dataStore.getAllPackUnits());
    }

    @DeleteMapping("/packUnit")
    public ResponseEntity<Map<String, Object>> deletePackUnits() {
        dataStore.clearPackUnits();
        return ResponseEntity.ok(Map.of(
                "http", 200,
                "status", "OK",
                "message", "All pack units deleted"
        ));
    }
}
