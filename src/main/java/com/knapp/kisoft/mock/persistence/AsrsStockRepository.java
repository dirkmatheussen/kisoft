package com.knapp.kisoft.mock.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface AsrsStockRepository extends JpaRepository<AsrsStockEntity, Long>,
        JpaSpecificationExecutor<AsrsStockEntity> {
    Optional<AsrsStockEntity> findByClientNumberAndArticleNumberAndPackSize(String clientNumber, String articleNumber, String packSize);
    Optional<AsrsStockEntity> findByClientNumberAndArticleNumberAndPackSizeAndReservationCode(
            String clientNumber, String articleNumber, String packSize, String reservationCode);
    boolean existsByClientNumberAndArticleNumberAndPackSizeAndQuantityGreaterThan(String clientNumber, String articleNumber, String packSize, int qty);
    boolean existsByClientNumberAndArticleNumberAndPackSizeAndReservationCodeAndQuantityGreaterThan(
            String clientNumber, String articleNumber, String packSize, String reservationCode, int qty);
    List<AsrsStockEntity> findByClientNumberAndArticleNumber(String clientNumber, String articleNumber);
}
