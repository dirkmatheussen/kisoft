package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.api.dto.GoodsOutOrder;
import com.knapp.kisoft.mock.persistence.GoodsOutOrderEntity;
import com.knapp.kisoft.mock.persistence.GoodsOutOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.knapp.kisoft.mock.service.SheetNumbers.toKey;

@Service
public class GoodsOutOrderStoreService {

    private final GoodsOutOrderRepository repo;
    private final JsonPayloadMapper json;

    public GoodsOutOrderStoreService(GoodsOutOrderRepository repo, JsonPayloadMapper json) {
        this.repo = repo;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public boolean exists(String clientNumber, String orderNumber, Integer sheetNumber) {
        return repo.existsByClientNumberAndOrderNumberAndSheetNumber(clientNumber, orderNumber, toKey(sheetNumber));
    }

    @Transactional
    public void createNew(GoodsOutOrder order) {
        repo.save(new GoodsOutOrderEntity(
                order.clientNumber(),
                order.orderNumber(),
                toKey(order.sheetNumber()),
                "NEW",
                json.toJson(order)
        ));
    }

    @Transactional(readOnly = true)
    public Optional<GoodsOutOrderEntity> find(String clientNumber, String orderNumber, Integer sheetNumber) {
        return repo.findByClientNumberAndOrderNumberAndSheetNumber(clientNumber, orderNumber, toKey(sheetNumber));
    }

    @Transactional(readOnly = true)
    public GoodsOutOrder readPayload(GoodsOutOrderEntity entity) {
        return json.fromJson(entity.getPayloadJson(), GoodsOutOrder.class);
    }

    @Transactional
    public void update(GoodsOutOrderEntity entity, GoodsOutOrder updated) {
        entity.setPayloadJson(json.toJson(updated));
        repo.save(entity);
    }

    @Transactional
    public void updateStatus(String clientNumber, String orderNumber, Integer sheetNumber, String status) {
        repo.findByClientNumberAndOrderNumberAndSheetNumber(clientNumber, orderNumber, toKey(sheetNumber))
                .ifPresent(e -> { e.setProcessingStatus(status); repo.save(e); });
    }

    @Transactional(readOnly = true)
    public String getStatus(String clientNumber, String orderNumber, Integer sheetNumber) {
        return repo.findByClientNumberAndOrderNumberAndSheetNumber(clientNumber, orderNumber, toKey(sheetNumber))
                .map(GoodsOutOrderEntity::getProcessingStatus)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<GoodsOutOrderEntity> listAllEntities() {
        return repo.findAll();
    }

    @Transactional
    public boolean delete(String clientNumber, String orderNumber, Integer sheetNumber) {
        if (!repo.existsByClientNumberAndOrderNumberAndSheetNumber(clientNumber, orderNumber, toKey(sheetNumber))) {
            return false;
        }
        repo.deleteByClientNumberAndOrderNumberAndSheetNumber(clientNumber, orderNumber, toKey(sheetNumber));
        return true;
    }
}
