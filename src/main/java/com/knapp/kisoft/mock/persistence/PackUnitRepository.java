package com.knapp.kisoft.mock.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PackUnitRepository extends JpaRepository<PackUnitEntity, Long> {
    Optional<PackUnitEntity> findByClientNumberAndArticleNumberAndPackSize(String clientNumber, String articleNumber, String packSize);
    boolean existsByClientNumberAndArticleNumberAndPackSize(String clientNumber, String articleNumber, String packSize);
    boolean existsByClientNumberAndArticleNumber(String clientNumber, String articleNumber);
    List<PackUnitEntity> findByClientNumberAndArticleNumber(String clientNumber, String articleNumber);
    List<PackUnitEntity> findByClientNumber(String clientNumber);
    void deleteByClientNumber(String clientNumber);
}

