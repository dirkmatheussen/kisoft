package com.knapp.kisoft.mock.api;

import java.util.List;
import java.util.Map;

/** Shared v4 OpenAPI test fixtures (packSize integer, required capacityInformation). */
final class TestFixtures {

    private TestFixtures() {}

    static final int PACK_SIZE = 1;
    static final String PACK_SIZE_KEY = "1";
    static final String RESERVATION_CODE = "BE";

    static List<Map<String, Object>> defaultCapacity() {
        return List.of(Map.of("loadCarrier", "FULL", "maximumStoredQuantity", 100));
    }

    static Map<String, Object> packUnit(Map<String, Object> article) {
        return Map.of("article", article, "packSize", PACK_SIZE, "capacityInformation", defaultCapacity());
    }

    static Map<String, Object> packUnit(Map<String, Object> article, List<String> articleFeatures) {
        return Map.of(
                "article", article,
                "packSize", PACK_SIZE,
                "capacityInformation", defaultCapacity(),
                "articleFeatures", articleFeatures);
    }
}
