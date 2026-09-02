package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.api.dto.PackUnitFull;
import com.knapp.kisoft.mock.persistence.PackUnitEntity;
import com.knapp.kisoft.mock.persistence.PackUnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.knapp.kisoft.mock.service.MasterdataSessionService.DOMAIN_PACK_UNIT;
import static com.knapp.kisoft.mock.service.PackSizeKeys.toKey;

@Service
public class PackUnitStoreService {

    private final PackUnitRepository packUnitRepository;
    private final MasterdataSessionService sessionService;
    private final JsonPayloadMapper json;

    public PackUnitStoreService(PackUnitRepository packUnitRepository, MasterdataSessionService sessionService, JsonPayloadMapper json) {
        this.packUnitRepository = packUnitRepository;
        this.sessionService = sessionService;
        this.json = json;
    }

    public static String key(String clientNumber, String articleNumber, Integer packSize) {
        return clientNumber + "|" + articleNumber + "|" + toKey(packSize);
    }

    public static String key(String clientNumber, String articleNumber, String packSize) {
        return clientNumber + "|" + articleNumber + "|" + packSize;
    }

    @Transactional
    public void startUpdateSession(String clientNumber) {
        sessionService.startSession(DOMAIN_PACK_UNIT, clientNumber);
    }

    @Transactional
    public void cleanupUpdateSession(String clientNumber) {
        // Remove pack units not seen since last SET for this client
        List<PackUnitEntity> existing = packUnitRepository.findByClientNumber(clientNumber);
        for (PackUnitEntity e : existing) {
            String k = key(e.getClientNumber(), e.getArticleNumber(), e.getPackSize());
            if (!sessionService.wasSeen(DOMAIN_PACK_UNIT, clientNumber, k)) {
                packUnitRepository.delete(e);
            }
        }
        sessionService.clearSession(DOMAIN_PACK_UNIT, clientNumber);
    }

    @Transactional
    public void upsertAll(List<PackUnitFull> units) {
        for (PackUnitFull u : units) {
            String client = u.article() != null && u.article().clientNumber() != null ? u.article().clientNumber() : "DEFAULT";
            String article = u.article() != null ? u.article().articleNumber() : null;
            String packSizeKey = toKey(u.packSize());
            String payload = json.toJson(u);

            PackUnitEntity entity = packUnitRepository
                    .findByClientNumberAndArticleNumberAndPackSize(client, article, packSizeKey)
                    .orElseGet(() -> new PackUnitEntity(client, article, packSizeKey, payload));
            entity.setPayloadJson(payload);
            packUnitRepository.save(entity);

            sessionService.markSeen(DOMAIN_PACK_UNIT, client, key(client, article, u.packSize()));
        }
    }

    @Transactional(readOnly = true)
    public boolean exists(String clientNumber, String articleNumber, Integer packSize) {
        return packUnitRepository.existsByClientNumberAndArticleNumberAndPackSize(
                clientNumber, articleNumber, toKey(packSize));
    }

    /** Whether any pack unit exists for the article (any pack size) — used for the UNKNOWN_ARTICLE check. */
    @Transactional(readOnly = true)
    public boolean existsArticle(String clientNumber, String articleNumber) {
        return packUnitRepository.existsByClientNumberAndArticleNumber(clientNumber, articleNumber);
    }

    /** First pack unit found for the article (any pack size), used to read article master-data flags. */
    @Transactional(readOnly = true)
    public java.util.Optional<PackUnitFull> findAnyByArticle(String clientNumber, String articleNumber) {
        return packUnitRepository.findByClientNumberAndArticleNumber(clientNumber, articleNumber).stream()
                .findFirst()
                .map(e -> json.fromJson(e.getPayloadJson(), PackUnitFull.class));
    }

    @Transactional(readOnly = true)
    public List<PackUnitFull> listAll() {
        return packUnitRepository.findAll().stream()
                .map(e -> json.fromJson(e.getPayloadJson(), PackUnitFull.class))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PackUnitFull> listByClientNumber(String clientNumber) {
        return packUnitRepository.findByClientNumber(clientNumber).stream()
                .map(e -> json.fromJson(e.getPayloadJson(), PackUnitFull.class))
                .toList();
    }

    @Transactional
    public void deleteAll() {
        packUnitRepository.deleteAll();
    }

    @Transactional
    public boolean deleteOne(String clientNumber, String articleNumber, Integer packSize) {
        var existing = packUnitRepository.findByClientNumberAndArticleNumberAndPackSize(
                clientNumber, articleNumber, toKey(packSize));
        existing.ifPresent(packUnitRepository::delete);
        return existing.isPresent();
    }
}

