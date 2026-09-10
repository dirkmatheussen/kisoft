package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.persistence.AsrsStockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AsrsStockReservationKeyTest {

    @Autowired AsrsStockService asrsStock;
    @Autowired AsrsStockRepository repo;

    @Test
    void sameArticlePackDifferentCoo_createsTwoRows() {
        repo.deleteAll();
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "PL", 6, null);
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "SE", 4, null);

        assertThat(repo.findAll()).hasSize(2);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "PL")).isEqualTo(6);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "SE")).isEqualTo(4);
    }

    @Test
    void sameArticlePackSameCoo_incrementsQuantity() {
        repo.deleteAll();
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "PL", 6,
                new AsrsStockAttributes(null, null, null, null, "PL", null));
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "PL", 3,
                new AsrsStockAttributes(null, null, null, null, "PL", null));

        assertThat(repo.findAll()).hasSize(1);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "PL")).isEqualTo(9);
    }

    @Test
    void blankAndNullReservationCode_shareTheEmptyKey() {
        repo.deleteAll();
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", 5, null);
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "  ", 2, null);
        assertThat(repo.findAll()).hasSize(1);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "")).isEqualTo(7);
    }

    @Test
    void removeStock_onlyAffectsMatchingCoo() {
        repo.deleteAll();
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "PL", 6, null);
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "SE", 6, null);
        assertThat(asrsStock.removeStock("VPNA-TAC", "ART-1", "1", "PL", 4)).isEqualTo(4);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "PL")).isEqualTo(2);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "SE")).isEqualTo(6);
    }

    @Test
    void setQuantity_withoutCoo_updatesTheOnlyMatchingPackSizeRow() {
        repo.deleteAll();
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "PL", 6, null);

        int delta = asrsStock.setQuantity("VPNA-TAC", "ART-1", "1", "", 4);

        assertThat(delta).isEqualTo(-2);
        assertThat(repo.findAll()).hasSize(1);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "PL")).isEqualTo(4);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "")).isEqualTo(0);
    }

    @Test
    void setQuantity_withoutCoo_createsEmptyKeyWhenMultipleCoosExist() {
        repo.deleteAll();
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "PL", 6, null);
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "SE", 4, null);

        asrsStock.setQuantity("VPNA-TAC", "ART-1", "1", "", 1);

        assertThat(repo.findAll()).hasSize(3);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "PL")).isEqualTo(6);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "SE")).isEqualTo(4);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "")).isEqualTo(1);
    }

    @Test
    void removeStock_withoutCoo_deductsTheOnlyMatchingPackSizeRow() {
        repo.deleteAll();
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "PL", 6, null);

        assertThat(asrsStock.removeStock("VPNA-TAC", "ART-1", "1", "", 2)).isEqualTo(2);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "PL")).isEqualTo(4);
    }
}
