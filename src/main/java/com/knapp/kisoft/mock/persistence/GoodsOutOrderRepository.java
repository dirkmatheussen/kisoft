package com.knapp.kisoft.mock.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoodsOutOrderRepository extends JpaRepository<GoodsOutOrderEntity, Long> {
    Optional<GoodsOutOrderEntity> findByClientNumberAndOrderNumberAndSheetNumber(String clientNumber, String orderNumber, String sheetNumber);
    boolean existsByClientNumberAndOrderNumberAndSheetNumber(String clientNumber, String orderNumber, String sheetNumber);
    void deleteByClientNumberAndOrderNumberAndSheetNumber(String clientNumber, String orderNumber, String sheetNumber);
}
