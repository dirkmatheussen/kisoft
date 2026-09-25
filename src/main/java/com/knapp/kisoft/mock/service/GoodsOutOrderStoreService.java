package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.api.dto.GoodsOutOrder;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderReplyLine;
import com.knapp.kisoft.mock.persistence.GoodsOutOrderEntity;
import com.knapp.kisoft.mock.persistence.GoodsOutOrderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
    public void savePickResult(GoodsOutOrderEntity entity, List<GoodsOutOrderReplyLine> lines) {
        entity.setPickResultJson(lines == null ? null : json.toJson(lines));
        repo.save(entity);
    }

    @Transactional(readOnly = true)
    public List<GoodsOutOrderReplyLine> readPickResult(GoodsOutOrderEntity entity) {
        String raw = entity.getPickResultJson();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<GoodsOutOrderReplyLine> lines = json.fromJson(raw, new TypeReference<>() {});
        return lines != null ? lines : List.of();
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

    /** Count of goods-out orders matching a specification (database COUNT; used for OData {@code $count}). */
    @Transactional(readOnly = true)
    public long count(Specification<GoodsOutOrderEntity> spec) {
        return repo.count(spec);
    }

    /** A single page of goods-out orders matching a specification (database OFFSET/LIMIT; used for OData reads). */
    @Transactional(readOnly = true)
    public List<GoodsOutOrderEntity> page(Specification<GoodsOutOrderEntity> spec, Pageable pageable) {
        return repo.findAll(spec, pageable).getContent();
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
