package com.knapp.kisoft.mock.service;

/**
 * Mock generator for VOLVO TRUCKS Tacoma {@code prjContainerID} on goods-out reply lines
 * (HIS Appendix §7.4.2): 3-digit site prefix {@code 001} + 8 digits.
 */
public final class PrjContainerIds {

    /** Tacoma site prefix per HIS Appendix §7.4.2. */
    public static final String SITE_PREFIX = "001";

    private PrjContainerIds() {}

    /** Deterministic mock id for a goods-out order line (stable across reply statuses). */
    public static String forLine(String orderNumber, String lineReference) {
        String key = (orderNumber != null ? orderNumber : "") + "|" + (lineReference != null ? lineReference : "");
        int suffix = Math.floorMod(key.hashCode(), 100_000_000);
        return SITE_PREFIX + String.format("%08d", suffix);
    }
}
