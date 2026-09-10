package com.knapp.kisoft.mock.service;

import java.util.List;

/**
 * stockLockReasons values configured for VOLVO TRUCKS Tacoma (HIS Appendix §4.2.3,
 * OpenAPI schema {@code StockLockReason}).
 */
public final class StockLockReasons {

    public static final List<String> ALL = List.of(
            "DEFAULT",
            "EXPIRED",
            "HOST",
            "LOCATION_LOCKED",
            "LOCKED_FOR_VISION_CHECK",
            "LOST",
            "QS_REQ",
            "SRS_SYSTEM_BROKEN",
            "SUBSYSTEM_LOCKED",
            "TIME_TO_EXPIRE");

    private StockLockReasons() {}

    /** Entries of {@code reasons} that are not valid Tacoma lock reasons (empty when all valid or input null). */
    public static List<String> invalid(List<String> reasons) {
        if (reasons == null) return List.of();
        return reasons.stream().filter(r -> r == null || !ALL.contains(r)).toList();
    }
}
