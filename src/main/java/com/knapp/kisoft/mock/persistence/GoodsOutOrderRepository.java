package com.knapp.kisoft.mock.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface GoodsOutOrderRepository extends JpaRepository<GoodsOutOrderEntity, Long>,
        JpaSpecificationExecutor<GoodsOutOrderEntity> {
    Optional<GoodsOutOrderEntity> findByClientNumberAndOrderNumberAndSheetNumber(String clientNumber, String orderNumber, String sheetNumber);
    boolean existsByClientNumberAndOrderNumberAndSheetNumber(String clientNumber, String orderNumber, String sheetNumber);
    void deleteByClientNumberAndOrderNumberAndSheetNumber(String clientNumber, String orderNumber, String sheetNumber);
}
