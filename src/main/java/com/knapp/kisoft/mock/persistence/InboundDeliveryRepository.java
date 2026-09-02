package com.knapp.kisoft.mock.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InboundDeliveryRepository extends JpaRepository<InboundDeliveryEntity, Long> {
    Optional<InboundDeliveryEntity> findByClientNumberAndInboundDeliveryNumber(String clientNumber, String inboundDeliveryNumber);
    boolean existsByClientNumberAndInboundDeliveryNumber(String clientNumber, String inboundDeliveryNumber);
    void deleteByClientNumberAndInboundDeliveryNumber(String clientNumber, String inboundDeliveryNumber);
}

