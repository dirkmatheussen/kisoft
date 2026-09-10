package com.knapp.kisoft.mock.service;

/**
 * Normalizes reservationCode (Country of Origin) for use as part of the ASRS stock key.
 * Null/blank → empty string so the DB unique constraint is stable under H2.
 */
public final class ReservationCodes {

    private ReservationCodes() {}

    public static String normalize(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
