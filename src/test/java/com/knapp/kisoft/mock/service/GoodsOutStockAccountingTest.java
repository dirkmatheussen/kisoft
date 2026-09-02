package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.api.dto.GoodsOutOrder;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderLine;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderReply;
import com.knapp.kisoft.mock.api.dto.GoodsOutPickConfirmation;
import com.knapp.kisoft.mock.api.dto.GoodsOutPickLine;
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
        when(asrsStock.getQuantity("OB", "ART-1", "1")).thenReturn(5);
        when(packUnitStore.findAnyByArticle("OB", "ART-1")).thenReturn(Optional.empty());

        lifecycle.confirmPicking(new GoodsOutPickConfirmation(
                "OB", "GO-1", 1,
                List.of(new GoodsOutPickLine("GL1", 5, null, null, null))));

        verify(asrsStock).removeStock("OB", "ART-1", "1", 5);
        verify(store).updateStatus("OB", "GO-1", 1, GoodsOutOrderLifecycleService.STATUS_PROCESSED);
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
        verify(asrsStock, never()).removeStock(anyString(), anyString(), anyString(), anyInt());
        verify(asrsStock, never()).getQuantity(anyString(), anyString(), anyString());
    }

    @Test
    void validateIntake_rejectsWhenRequestedExceedsAvailable() {
        when(packUnitStore.exists("OB", "ART-1", 1)).thenReturn(true);
        when(packUnitStore.findAnyByArticle("OB", "ART-1")).thenReturn(Optional.empty());
        when(asrsStock.getQuantity("OB", "ART-1", "1")).thenReturn(3);

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
