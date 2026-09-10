package com.knapp.kisoft.mock.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationCodesTest {

    @Test
    void normalize_mapsNullAndBlankToEmptyString() {
        assertThat(ReservationCodes.normalize(null)).isEqualTo("");
        assertThat(ReservationCodes.normalize("")).isEqualTo("");
        assertThat(ReservationCodes.normalize("  ")).isEqualTo("");
        assertThat(ReservationCodes.normalize(" PL ")).isEqualTo("PL");
        assertThat(ReservationCodes.normalize("BE")).isEqualTo("BE");
    }
}
