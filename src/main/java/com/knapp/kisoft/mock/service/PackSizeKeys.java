package com.knapp.kisoft.mock.service;

/**
 * Normalizes API {@code packSize} integers to the string keys used in persistence.
 */
public final class PackSizeKeys {

    private PackSizeKeys() {}

    public static String toKey(Integer packSize) {
        return packSize == null ? null : String.valueOf(packSize);
    }

    public static boolean isPresent(Integer packSize) {
        return packSize != null;
    }
}
