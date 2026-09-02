package com.knapp.kisoft.mock.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InboundDeliveryProgressRepository extends JpaRepository<InboundDeliveryProgressEntity, Long> {
    Optional<InboundDeliveryProgressEntity> findByClientNumberAndInboundDeliveryNumberAndLineReference(
            String clientNumber, String inboundDeliveryNumber, String lineReference);

    List<InboundDeliveryProgressEntity> findByClientNumberAndInboundDeliveryNumber(
            String clientNumber, String inboundDeliveryNumber);

    void deleteByClientNumberAndInboundDeliveryNumber(String clientNumber, String inboundDeliveryNumber);
}
