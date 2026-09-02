package com.knapp.kisoft.mock.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryRequestRepository extends JpaRepository<InventoryRequestEntity, Long> {
    Optional<InventoryRequestEntity> findByClientNumberAndRequestNumber(String clientNumber, String requestNumber);
    boolean existsByClientNumberAndRequestNumber(String clientNumber, String requestNumber);
    void deleteByClientNumberAndRequestNumber(String clientNumber, String requestNumber);
    List<InventoryRequestEntity> findByClientNumberAndProcessingStatus(String clientNumber, String processingStatus);
}
