package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.api.dto.InventoryRequest;
import com.knapp.kisoft.mock.api.dto.InventoryRequestLine;
import com.knapp.kisoft.mock.persistence.InventoryRequestEntity;
import com.knapp.kisoft.mock.persistence.InventoryRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class InventoryRequestStoreService {

    private final InventoryRequestRepository repo;
    private final JsonPayloadMapper json;

    public InventoryRequestStoreService(InventoryRequestRepository repo, JsonPayloadMapper json) {
        this.repo = repo;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public boolean exists(String clientNumber, String requestNumber) {
        return repo.existsByClientNumberAndRequestNumber(clientNumber, requestNumber);
    }

    @Transactional
    public void createNew(InventoryRequest request) {
        repo.save(new InventoryRequestEntity(
                request.clientNumber(),
                request.requestNumber(),
                "NEW",
                json.toJson(request)
        ));
    }

    @Transactional(readOnly = true)
    public Optional<InventoryRequestEntity> find(String clientNumber, String requestNumber) {
        return repo.findByClientNumberAndRequestNumber(clientNumber, requestNumber);
    }

    @Transactional(readOnly = true)
    public InventoryRequest readPayload(InventoryRequestEntity entity) {
        return json.fromJson(entity.getPayloadJson(), InventoryRequest.class);
    }

    @Transactional
    public void updateStatus(String clientNumber, String requestNumber, String status) {
        repo.findByClientNumberAndRequestNumber(clientNumber, requestNumber)
                .ifPresent(e -> { e.setProcessingStatus(status); repo.save(e); });
    }

    @Transactional(readOnly = true)
    public String getStatus(String clientNumber, String requestNumber) {
        return repo.findByClientNumberAndRequestNumber(clientNumber, requestNumber)
                .map(InventoryRequestEntity::getProcessingStatus)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveRequestForArticle(String clientNumber, String articleNumber, String reservationCode) {
        String coi = reservationCode != null ? reservationCode : "";
        return repo.findByClientNumberAndProcessingStatus(clientNumber, "NEW").stream()
                .anyMatch(entity -> {
                    InventoryRequest request = readPayload(entity);
                    InventoryRequestLine line = request.inventoryRequestLine();
                    if (line == null) return false;
                    return articleNumber.equals(line.articleNumber())
                            && coi.equals(line.reservationCode() != null ? line.reservationCode() : "");
                });
    }

    @Transactional
    public boolean delete(String clientNumber, String requestNumber) {
        if (!repo.existsByClientNumberAndRequestNumber(clientNumber, requestNumber)) {
            return false;
        }
        repo.deleteByClientNumberAndRequestNumber(clientNumber, requestNumber);
        return true;
    }
}
