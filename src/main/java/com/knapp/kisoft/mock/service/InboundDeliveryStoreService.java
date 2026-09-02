package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.api.dto.InboundDelivery;
import com.knapp.kisoft.mock.persistence.InboundDeliveryEntity;
import com.knapp.kisoft.mock.persistence.InboundDeliveryProgressRepository;
import com.knapp.kisoft.mock.persistence.InboundDeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class InboundDeliveryStoreService {

    private final InboundDeliveryRepository repo;
    private final InboundDeliveryProgressRepository progressRepo;
    private final JsonPayloadMapper json;

    public InboundDeliveryStoreService(InboundDeliveryRepository repo,
                                       InboundDeliveryProgressRepository progressRepo,
                                       JsonPayloadMapper json) {
        this.repo = repo;
        this.progressRepo = progressRepo;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public boolean exists(String clientNumber, String inboundDeliveryNumber) {
        return repo.existsByClientNumberAndInboundDeliveryNumber(clientNumber, inboundDeliveryNumber);
    }

    @Transactional
    public void createNew(InboundDelivery delivery) {
        repo.save(new InboundDeliveryEntity(
                delivery.clientNumber(),
                delivery.inboundDeliveryNumber(),
                "NEW",
                json.toJson(delivery)
        ));
    }

    @Transactional(readOnly = true)
    public Optional<InboundDeliveryEntity> find(String clientNumber, String inboundDeliveryNumber) {
        return repo.findByClientNumberAndInboundDeliveryNumber(clientNumber, inboundDeliveryNumber);
    }

    @Transactional
    public void update(InboundDeliveryEntity entity, InboundDelivery updated) {
        entity.setPayloadJson(json.toJson(updated));
        repo.save(entity);
    }

    @Transactional
    public void updateStatus(InboundDeliveryEntity entity, String status) {
        entity.setProcessingStatus(status);
        repo.save(entity);
    }

    @Transactional(readOnly = true)
    public InboundDelivery readPayload(InboundDeliveryEntity entity) {
        return json.fromJson(entity.getPayloadJson(), InboundDelivery.class);
    }

    @Transactional(readOnly = true)
    public List<InboundDeliveryEntity> listAllEntities() {
        return repo.findAll();
    }

    @Transactional
    public void delete(String clientNumber, String inboundDeliveryNumber) {
        progressRepo.deleteByClientNumberAndInboundDeliveryNumber(clientNumber, inboundDeliveryNumber);
        repo.deleteByClientNumberAndInboundDeliveryNumber(clientNumber, inboundDeliveryNumber);
    }
}

