package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.persistence.AsrsStockEntity;
import com.knapp.kisoft.mock.persistence.AsrsStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.knapp.kisoft.mock.service.AsrsStockService.LockAction.LOCK;
import static com.knapp.kisoft.mock.service.AsrsStockService.LockAction.UNLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsrsStockLockTest {

    @Mock AsrsStockRepository repo;
    AsrsStockService service;
    AsrsStockEntity row;

    @BeforeEach
    void setUp() {
        service = new AsrsStockService(repo);
        row = new AsrsStockEntity("VPNA-TAC", "VO 25133699", "1", 6);
        row.setReservationCode("PL");
    }

    @Test
    void invalidReasons_reportsUnknownCodesOnly() {
        assertThat(StockLockReasons.invalid(List.of("QS_REQ", "BOGUS", "HOST"))).containsExactly("BOGUS");
        assertThat(StockLockReasons.invalid(null)).isEmpty();
        assertThat(StockLockReasons.ALL).hasSize(10).contains("LOCKED_FOR_VISION_CHECK", "TIME_TO_EXPIRE");
    }

    @Test
    void lock_addsNewReasonsWithoutDuplicates() {
        row.setStockLockReasonsJson("[\"HOST\"]");
        when(repo.findByClientNumberAndArticleNumberAndPackSize("VPNA-TAC", "VO 25133699", "1"))
                .thenReturn(Optional.of(row));

        var change = service.changeLocks("VPNA-TAC", "VO 25133699", "1", "PL", LOCK, List.of("HOST", "QS_REQ")).orElseThrow();

        assertThat(change.added()).containsExactly("QS_REQ");
        assertThat(change.removed()).isEmpty();
        assertThat(change.resultingReasons()).containsExactly("HOST", "QS_REQ");
        assertThat(row.getStockLockReasonsJson()).isEqualTo("[\"HOST\",\"QS_REQ\"]");
        verify(repo).save(row);
    }

    @Test
    void unlock_removesGivenReasons_andEmptyListRemovesAll() {
        row.setStockLockReasonsJson("[\"HOST\",\"QS_REQ\"]");
        when(repo.findByClientNumberAndArticleNumberAndPackSize("VPNA-TAC", "VO 25133699", "1"))
                .thenReturn(Optional.of(row));

        var first = service.changeLocks("VPNA-TAC", "VO 25133699", "1", "PL", UNLOCK, List.of("QS_REQ", "LOST")).orElseThrow();
        assertThat(first.removed()).containsExactly("QS_REQ");
        assertThat(first.resultingReasons()).containsExactly("HOST");

        var second = service.changeLocks("VPNA-TAC", "VO 25133699", "1", "PL", UNLOCK, null).orElseThrow();
        assertThat(second.removed()).containsExactly("HOST");
        assertThat(second.resultingReasons()).isNull();
        assertThat(row.getStockLockReasonsJson()).isNull();
    }

    @Test
    void changeLocks_isEmptyWhenReservationCodeDiffersOrRowMissing() {
        when(repo.findByClientNumberAndArticleNumberAndPackSize("VPNA-TAC", "VO 25133699", "1"))
                .thenReturn(Optional.of(row));
        assertThat(service.changeLocks("VPNA-TAC", "VO 25133699", "1", "SE", LOCK, List.of("HOST"))).isEmpty();

        when(repo.findByClientNumberAndArticleNumberAndPackSize("VPNA-TAC", "UNKNOWN", "1"))
                .thenReturn(Optional.empty());
        assertThat(service.changeLocks("VPNA-TAC", "UNKNOWN", "1", "PL", LOCK, List.of("HOST"))).isEmpty();
        verify(repo, never()).save(any());
    }

    @Test
    void changeLocks_treatsBlankAndNullReservationCodeAsEqual() {
        row.setReservationCode(null);
        when(repo.findByClientNumberAndArticleNumberAndPackSize("VPNA-TAC", "VO 25133699", "1"))
                .thenReturn(Optional.of(row));
        assertThat(service.changeLocks("VPNA-TAC", "VO 25133699", "1", "  ", LOCK, List.of("HOST"))).isPresent();
    }
}
