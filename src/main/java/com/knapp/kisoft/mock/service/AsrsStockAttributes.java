package com.knapp.kisoft.mock.service;

import java.util.List;

/**
 * Optional ASRS stock attributes stored alongside quantity
 * (aligned with StockInventory / inbound delivery lines).
 */
public record AsrsStockAttributes(
        String stockType,
        String lotNumber,
        String dateMark,
        String serialNumber,
        String reservationCode,
        List<String> stockLockReasons
) {
    public static AsrsStockAttributes empty() {
        return new AsrsStockAttributes(null, null, null, null, null, null);
    }
}
