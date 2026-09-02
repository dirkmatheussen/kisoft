package com.knapp.kisoft.mock.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ToteCompartmentRepository extends JpaRepository<ToteCompartmentEntity, Long> {
    Optional<ToteCompartmentEntity> findByClientNumberAndLoadUnitCodeAndCompartment(
            String clientNumber, String loadUnitCode, String compartment);
}
