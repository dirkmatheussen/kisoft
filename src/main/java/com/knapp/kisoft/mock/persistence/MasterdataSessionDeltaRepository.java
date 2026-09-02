package com.knapp.kisoft.mock.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MasterdataSessionDeltaRepository extends JpaRepository<MasterdataSessionDeltaEntity, Long> {
    void deleteByDomainAndClientNumber(String domain, String clientNumber);
    boolean existsByDomainAndClientNumberAndKeyValue(String domain, String clientNumber, String keyValue);
    List<MasterdataSessionDeltaEntity> findByDomainAndClientNumber(String domain, String clientNumber);
}

