package com.knapp.kisoft.mock.service;

/**
 * Normalizes API {@code sheetNumber} integers to the string keys used in persistence.
 */
public final class SheetNumbers {

    private SheetNumbers() {}

    public static String toKey(Integer sheetNumber) {
        return sheetNumber == null ? null : String.valueOf(sheetNumber);
    }
}
