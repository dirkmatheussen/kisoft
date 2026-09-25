package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.api.dto.GoodsOutOrder;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderLine;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderReply;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderReplyLine;
import com.knapp.kisoft.mock.api.dto.GoodsOutPickConfirmation;
import com.knapp.kisoft.mock.api.dto.GoodsOutPickLine;
import com.knapp.kisoft.mock.api.dto.PickedStock;
import com.knapp.kisoft.mock.persistence.GoodsOutOrderEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stock accounting: intake checks quantity; PROCESSED deducts; inbound creates/increments elsewhere.
 */
@ExtendWith(MockitoExtension.class)
class GoodsOutStockAccountingTest {

    @Mock GoodsOutOrderStoreService store;
    @Mock PackUnitStoreService packUnitStore;
    @Mock AsrsStockService asrsStock;
    @Mock ReplyCallbackService callback;

    GoodsOutOrderLifecycleService lifecycle;

    @BeforeEach
    void setUp() {
        lifecycle = new GoodsOutOrderLifecycleService(store, packUnitStore, asrsStock, callback);
    }

    @Test
    void confirmPicking_removesRequestedQuantityFromAsrsStock() {
        GoodsOutOrder order = order(5);
        GoodsOutOrderEntity entity = entity("STARTED");
        when(store.find("OB", "GO-1", 1)).thenReturn(Optional.of(entity));
        when(store.readPayload(entity)).thenReturn(order);
        when(asrsStock.getQuantity("OB", "ART-1", "1", "BE")).thenReturn(5);
        when(packUnitStore.findAnyByArticle("OB", "ART-1")).thenReturn(Optional.empty());

        lifecycle.confirmPicking(new GoodsOutPickConfirmation(
                "OB", "GO-1", 1,
                List.of(new GoodsOutPickLine("GL1", 5, null, null, null))));

        verify(asrsStock).removeStock("OB", "ART-1", "1", "BE", 5);
        verify(store).updateStatus("OB", "GO-1", 1, GoodsOutOrderLifecycleService.STATUS_PROCESSED);
        verify(store).savePickResult(eq(entity), any());
    }

    @Test
    void confirmPicking_pickedStockEchoesOrderLineAndInventoryAttributes() {
        GoodsOutOrder order = order(5);
        GoodsOutOrderEntity entity = entity("STARTED");
        when(store.find("OB", "GO-1", 1)).thenReturn(Optional.of(entity));
        when(store.readPayload(entity)).thenReturn(order);
        when(asrsStock.getQuantity("OB", "ART-1", "1", "BE")).thenReturn(5);
        when(packUnitStore.findAnyByArticle("OB", "ART-1")).thenReturn(Optional.empty());
        var stock = new com.knapp.kisoft.mock.persistence.AsrsStockEntity("OB", "ART-1", "1", "BE", 5);
        stock.setStockType("NORMAL");
        stock.setLotNumber("LOT-9");
        stock.setDateMark("2026-01-15");
        stock.setSerialNumber("SN-1");
        stock.setStockLockReasonsJson("[\"QS_REQ\"]");
        when(asrsStock.find("OB", "ART-1", "1", "BE")).thenReturn(Optional.of(stock));

        lifecycle.confirmPicking(new GoodsOutPickConfirmation(
                "OB", "GO-1", 1,
                List.of(new GoodsOutPickLine("GL1", 5, null, null, null))));

        ArgumentCaptor<GoodsOutOrderReply> captor = ArgumentCaptor.forClass(GoodsOutOrderReply.class);
        verify(callback).sendGoodsOutOrderReply(captor.capture());
        var picked = captor.getValue().goodsOutOrderLines().get(0).pickedStock();
        assertThat(picked).hasSize(1);
        assertThat(picked.get(0).articleNumber()).isEqualTo("ART-1");
        assertThat(picked.get(0).packSize()).isEqualTo(1);
        assertThat(picked.get(0).reservationCode()).isEqualTo("BE");
        assertThat(picked.get(0).processedQuantity()).isEqualTo(5);
        assertThat(picked.get(0).stockType()).isEqualTo("NORMAL");
        assertThat(picked.get(0).lotNumber()).isEqualTo("LOT-9");
        assertThat(picked.get(0).dateMark()).isEqualTo("2026-01-15");
        assertThat(picked.get(0).serialNumber()).isEqualTo("SN-1");
        assertThat(picked.get(0).stockLockReasons()).containsExactly("QS_REQ");
    }

    @Test
    void finalCheck_afterStockFullyDeducted_replyLinesAreProcessedNotOutOfStock() {
        GoodsOutOrder order = order(5);
        GoodsOutOrderEntity entity = entity("PROCESSED");
        when(store.find("OB", "GO-1", 1)).thenReturn(Optional.of(entity));
        when(store.readPayload(entity)).thenReturn(order);

        lifecycle.finalCheck("OB", "GO-1", 1);

        ArgumentCaptor<GoodsOutOrderReply> captor = ArgumentCaptor.forClass(GoodsOutOrderReply.class);
        verify(callback).sendGoodsOutOrderReply(captor.capture());
        GoodsOutOrderReply reply = captor.getValue();
        assertThat(reply.processingStatus()).isEqualTo("FINISHED");
        assertThat(reply.goodsOutOrderLines()).hasSize(1);
        assertThat(reply.goodsOutOrderLines().get(0).processingResult()).isEqualTo("PROCESSED");
        assertThat(reply.goodsOutOrderLines().get(0).processedQuantity()).isEqualTo(5);
        verify(asrsStock, never()).removeStock(anyString(), anyString(), anyString(), anyString(), anyInt());
        verify(asrsStock, never()).getQuantity(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void finalCheck_copiesShortPickQuantityAndQuantityErrorFromPick() {
        GoodsOutOrder order = order(2);
        GoodsOutOrderEntity entity = entity("PROCESSED");
        when(store.find("OB", "GO-1", 1)).thenReturn(Optional.of(entity));
        when(store.readPayload(entity)).thenReturn(order);
        when(store.readPickResult(entity)).thenReturn(List.of(new GoodsOutOrderReplyLine(
                "pick-uuid", "00194690524", "GL1", 1, "QUANTITY_ERROR", null,
                List.of(new PickedStock(1, "ART-1", 1, null, null, null, "BE", null, null)))));

        lifecycle.finalCheck("OB", "GO-1", 1);

        ArgumentCaptor<GoodsOutOrderReply> captor = ArgumentCaptor.forClass(GoodsOutOrderReply.class);
        verify(callback).sendGoodsOutOrderReply(captor.capture());
        GoodsOutOrderReply reply = captor.getValue();
        assertThat(reply.processingStatus()).isEqualTo("FINISHED");
        var line = reply.goodsOutOrderLines().get(0);
        assertThat(line.lineReference()).isEqualTo("GL1");
        assertThat(line.processedQuantity()).isEqualTo(1);
        assertThat(line.processingResult()).isEqualTo("QUANTITY_ERROR");
        assertThat(line.pickedStock()).hasSize(1);
        assertThat(line.pickedStock().get(0).processedQuantity()).isEqualTo(1);
        verify(asrsStock, never()).removeStock(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void validateIntake_rejectsWhenRequestedExceedsAvailable() {
        when(packUnitStore.exists("OB", "ART-1", 1)).thenReturn(true);
        when(packUnitStore.findAnyByArticle("OB", "ART-1")).thenReturn(Optional.empty());
        when(asrsStock.getQuantity("OB", "ART-1", "1", "BE")).thenReturn(3);

        var errors = lifecycle.validateIntakeLines("OB", List.of(line(5)));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).lineCode()).isEqualTo(GoodsOutOrderLifecycleService.CODE_NOT_ENOUGH_STOCK);
    }

    private static GoodsOutOrder order(int qty) {
        return new GoodsOutOrder(
                "OB", "GO-1", 1, "FULL",
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                List.of(line(qty)));
    }

    private static GoodsOutOrderLine line(int qty) {
        return new GoodsOutOrderLine("GL1", qty, "ART-1", 1,
                null, null, null, "BE",
                null, null, null, null, null, null);
    }

    private static GoodsOutOrderEntity entity(String status) {
        return new GoodsOutOrderEntity("OB", "GO-1", "1", status, "{}");
    }
}
