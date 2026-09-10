package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.api.dto.PackUnitKeyRef;
import com.knapp.kisoft.mock.api.dto.StockInventory;
import com.knapp.kisoft.mock.persistence.AsrsStockEntity;
import com.knapp.kisoft.mock.persistence.AsrsStockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventoryImportServiceTest {

    @Autowired InventoryImportService importService;
    @Autowired AsrsStockService asrsStock;
    @Autowired AsrsStockRepository repo;

    @Test
    void importItems_uniquifiesIdenticalArticleNumbers() {
        StockInventory same = new StockInventory(
                new PackUnitKeyRef("VPNA-TAC", "VO 25133699", 1),
                6, null, null, null, null, "PL", null);
        var result = importService.importItems(List.of(same, same, same), true, true);

        assertThat(result.rowsRead()).isEqualTo(3);
        assertThat(result.rowsWritten()).isEqualTo(3);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "VO 25133699 - 1", "1")).isEqualTo(6);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "VO 25133699 - 2", "1")).isEqualTo(6);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "VO 25133699 - 3", "1")).isEqualTo(6);
        assertThat(repo.findByClientNumberAndArticleNumberAndPackSize("VPNA-TAC", "VO 25133699 - 2", "1"))
                .get()
                .extracting(AsrsStockEntity::getReservationCode)
                .isEqualTo("PL");
    }

    @Test
    void importFromFile_streamsStockInventory(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("report.json");
        Files.writeString(file, """
                {"requestNumber":"R1","stockInventory":[
                  {"quantity":6,"packUnit":{"clientNumber":"C1","articleNumber":"ART","packSize":1},"reservationCode":"PL"},
                  {"quantity":6,"packUnit":{"clientNumber":"C1","articleNumber":"ART","packSize":1},"reservationCode":"PL"}
                ]}
                """);

        var result = importService.importFromFile(file, true, true);

        assertThat(result.rowsWritten()).isEqualTo(2);
        assertThat(asrsStock.getQuantity("C1", "ART - 1", "1")).isEqualTo(6);
        assertThat(asrsStock.getQuantity("C1", "ART - 2", "1")).isEqualTo(6);
    }
}
