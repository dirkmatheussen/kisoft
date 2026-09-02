package com.knapp.kisoft.mock.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AsrsStockRepository extends JpaRepository<AsrsStockEntity, Long> {
    Optional<AsrsStockEntity> findByClientNumberAndArticleNumberAndPackSize(String clientNumber, String articleNumber, String packSize);
    boolean existsByClientNumberAndArticleNumberAndPackSizeAndQuantityGreaterThan(String clientNumber, String articleNumber, String packSize, int qty);
    List<AsrsStockEntity> findByClientNumberAndArticleNumber(String clientNumber, String articleNumber);
}
