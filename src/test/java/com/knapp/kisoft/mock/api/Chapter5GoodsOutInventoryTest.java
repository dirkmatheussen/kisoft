package com.knapp.kisoft.mock.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrder;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderLine;
import com.knapp.kisoft.mock.service.AsrsStockService;
import com.knapp.kisoft.mock.service.GoodsOutOrderLifecycleService;
import com.knapp.kisoft.mock.service.PrjContainerIds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for the KiSoft-side Chapter 5 logic (GS §5.2 goods-out, §5.3 inventory & warehouse-internal),
 * excluding multiphase picking (§5.2.2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Chapter5GoodsOutInventoryTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private GoodsOutOrderLifecycleService goodsOutLifecycle;
    @Autowired private AsrsStockService asrsStock;

    private static final String CTX = "/kisoft";
    private static final String API = CTX + "/oneapi/v1";

    private static String json(Object o) throws Exception {
        return new ObjectMapper().writeValueAsString(o);
    }

    private void putPackUnit(String client, String article, List<String> features) throws Exception {
        Map<String, Object> a = Map.of("clientNumber", client, "articleNumber", article, "articleName", article);
        Map<String, Object> pu = features == null
                ? TestFixtures.packUnit(a)
                : TestFixtures.packUnit(a, features);
        mockMvc.perform(put(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(List.of(pu))))
                .andExpect(status().isOk());
    }

    private void postGoodsOut(String order, String article, int qty) throws Exception {
        int available = asrsStock.getQuantity("OB", article, TestFixtures.PACK_SIZE_KEY);
        if (available < qty) {
            asrsStock.addStock("OB", article, TestFixtures.PACK_SIZE_KEY, qty - available);
        }
        Map<String, Object> line = Map.of("lineReference", "GL1", "requestedQuantity", qty,
                "articleNumber", article, "packSize", TestFixtures.PACK_SIZE,
                "reservationCode", TestFixtures.RESERVATION_CODE);
        Map<String, Object> body = Map.of("clientNumber", "OB", "orderNumber", order, "sheetNumber", 1,
                "loadCarrier", "FULL", "goodsOutOrderLines", List.of(line));
        mockMvc.perform(post(API + "/goodsOutOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
    }

    private static Map<String, Object> ref(String order) {
        return Map.of("clientNumber", "OB", "orderNumber", order, "sheetNumber", 1);
    }

    // ---- GS §5.2 goods-out lifecycle ----

    @Test
    void goodsOut_happyPath_started_processed_finished() throws Exception {
        putPackUnit("OB", "ART-OK", null);
        asrsStock.addStock("OB", "ART-OK", "1", 5);
        postGoodsOut("GO-OK", "ART-OK", 5);

        mockMvc.perform(post(API + "/goodsOutOrder/operator/start").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(ref("GO-OK"))))
                .andExpect(status().isOk());

        Map<String, Object> pick = Map.of("clientNumber", "OB", "orderNumber", "GO-OK", "sheetNumber", 1,
                "lines", List.of(Map.of("lineReference", "GL1", "pickedQuantity", 5)));
        mockMvc.perform(post(API + "/goodsOutOrder/operator/pick").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(pick)))
                .andExpect(status().isOk());

        // PROCESSED deducted full requested quantity
        assertEquals(0, asrsStock.getQuantity("OB", "ART-OK", "1"));

        mockMvc.perform(post(API + "/goodsOutOrder/operator/finalCheck").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(ref("GO-OK"))))
                .andExpect(status().isOk());

        // FINISHED must not re-deduct or fail after stock is already zero
        assertEquals(0, asrsStock.getQuantity("OB", "ART-OK", "1"));

        // Final check again -> wrong status (already FINISHED).
        mockMvc.perform(post(API + "/goodsOutOrder/operator/finalCheck").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(ref("GO-OK"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MOVM-0004"));
    }

    @Test
    void inbound_incrementsExistingInventory_thenGoodsOutRejectsOverRequest() throws Exception {
        putPackUnit("OB", "ART-INC", null);
        asrsStock.addStock("OB", "ART-INC", TestFixtures.PACK_SIZE_KEY, 4);

        Map<String, Object> inboundLine = Map.of(
                "lineReference", "IL1",
                "articleNumber", "ART-INC",
                "packSize", TestFixtures.PACK_SIZE,
                "expectedQuantity", 6,
                "reservationCode", TestFixtures.RESERVATION_CODE);
        mockMvc.perform(post(API + "/inboundDelivery").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "OB",
                                "inboundDeliveryNumber", "IB-INC-1",
                                "supplierNumber", "SUP",
                                "inboundDeliveryLines", List.of(inboundLine)))))
                .andExpect(status().isOk());

        assertEquals(10, asrsStock.getQuantity("OB", "ART-INC", TestFixtures.PACK_SIZE_KEY));

        Map<String, Object> goLine = Map.of(
                "lineReference", "GL1",
                "requestedQuantity", 11,
                "articleNumber", "ART-INC",
                "packSize", TestFixtures.PACK_SIZE,
                "reservationCode", TestFixtures.RESERVATION_CODE);
        mockMvc.perform(post(API + "/goodsOutOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "OB",
                                "orderNumber", "GO-OVER",
                                "sheetNumber", 1,
                                "loadCarrier", "FULL",
                                "goodsOutOrderLines", List.of(goLine)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.lineCodes[0].lineCode").value("E-AKO-STOC-0001"));
    }

    @Test
    void goodsOut_succeedsAfterInboundAutoStock_withoutOperatorLoadUnit() throws Exception {
        // PostInboundDelivery books expectedQuantity into ASRS (same articleNumber/packSize as inventoryRequestLine).
        putPackUnit("OB", "ART-IB-GO", null);
        Map<String, Object> inboundLine = Map.of(
                "lineReference", "IL1",
                "articleNumber", "ART-IB-GO",
                "packSize", TestFixtures.PACK_SIZE,
                "expectedQuantity", 8,
                "reservationCode", TestFixtures.RESERVATION_CODE);
        mockMvc.perform(post(API + "/inboundDelivery").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "OB",
                                "inboundDeliveryNumber", "IB-GO-1",
                                "supplierNumber", "SUP",
                                "inboundDeliveryLines", List.of(inboundLine)))))
                .andExpect(status().isOk());

        assertEquals(8, asrsStock.getQuantity("OB", "ART-IB-GO", TestFixtures.PACK_SIZE_KEY));

        Map<String, Object> goLine = Map.of(
                "lineReference", "GL1",
                "requestedQuantity", 5,
                "articleNumber", "ART-IB-GO",
                "packSize", TestFixtures.PACK_SIZE,
                "reservationCode", TestFixtures.RESERVATION_CODE);
        mockMvc.perform(post(API + "/goodsOutOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "OB",
                                "orderNumber", "GO-FROM-IB",
                                "sheetNumber", 1,
                                "loadCarrier", "FULL",
                                "goodsOutOrderLines", List.of(goLine)))))
                .andExpect(status().isOk());
    }

    @Test
    void goodsOut_delete_whenNew_returns200() throws Exception {
        putPackUnit("OB", "ART-DEL", null);
        asrsStock.addStock("OB", "ART-DEL", "1", 5);
        postGoodsOut("GO-DEL", "ART-DEL", 2);

        mockMvc.perform(delete(API + "/goodsOutOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(ref("GO-DEL"))))
                .andExpect(status().isOk());
    }

    @Test
    void goodsOut_delete_whenStarted_returns409() throws Exception {
        putPackUnit("OB", "ART-DEL2", null);
        asrsStock.addStock("OB", "ART-DEL2", "1", 5);
        postGoodsOut("GO-DEL2", "ART-DEL2", 2);

        mockMvc.perform(post(API + "/goodsOutOrder/operator/start").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(ref("GO-DEL2"))))
                .andExpect(status().isOk());

        mockMvc.perform(delete(API + "/goodsOutOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(ref("GO-DEL2"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MOVM-0005"));
    }

    @Test
    void goodsOut_start_unknownOrder_returns400() throws Exception {
        mockMvc.perform(post(API + "/goodsOutOrder/operator/start").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(ref("DOES-NOT-EXIST"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MOVM-0003"));
    }

    @Test
    void goodsOut_pick_beforeStart_returns400() throws Exception {
        putPackUnit("OB", "ART-NS", null);
        asrsStock.addStock("OB", "ART-NS", "1", 3);
        postGoodsOut("GO-NS", "ART-NS", 2);

        Map<String, Object> pick = Map.of("clientNumber", "OB", "orderNumber", "GO-NS", "sheetNumber", 1,
                "lines", List.of(Map.of("lineReference", "GL1", "pickedQuantity", 2)));
        mockMvc.perform(post(API + "/goodsOutOrder/operator/pick").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(pick)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MOVM-0004"));
    }

    // ---- GS §5.2.1 intake validation (Product One API HTTP 400) ----

    @Test
    void goodsOut_post_unknownArticle_returns400WithLineCode() throws Exception {
        Map<String, Object> line = Map.of("lineReference", "GL1", "requestedQuantity", 5,
                "articleNumber", "DOES-NOT-EXIST", "packSize", 1, "reservationCode", TestFixtures.RESERVATION_CODE);
        Map<String, Object> body = Map.of("clientNumber", "OB", "orderNumber", "GO-BAD", "sheetNumber", 1,
                "loadCarrier", "FULL", "goodsOutOrderLines", List.of(line));
        mockMvc.perform(post(API + "/goodsOutOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MAST-0001"))
                .andExpect(jsonPath("$.lineCodes[0].lineReference").value("GL1"))
                .andExpect(jsonPath("$.lineCodes[0].lineCode").value("E-AKO-MAST-0001"));
    }

    @Test
    void goodsOut_post_zeroQuantity_returns400WithLineCode() throws Exception {
        putPackUnit("OB", "ART-ZERO", null);
        asrsStock.addStock("OB", "ART-ZERO", "1", 10);
        Map<String, Object> line = Map.of("lineReference", "GL1", "requestedQuantity", 0,
                "articleNumber", "ART-ZERO", "packSize", 1, "reservationCode", TestFixtures.RESERVATION_CODE);
        Map<String, Object> body = Map.of("clientNumber", "OB", "orderNumber", "GO-ZERO", "sheetNumber", 1,
                "loadCarrier", "FULL", "goodsOutOrderLines", List.of(line));
        mockMvc.perform(post(API + "/goodsOutOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-GENR-0002"))
                .andExpect(jsonPath("$.lineCodes[0].lineCode").value("E-AKO-GENR-0002"));
    }

    @Test
    void goodsOut_post_outOfStock_returns400WithLineCode() throws Exception {
        putPackUnit("OB", "ART-OOS-POST", null);
        Map<String, Object> line = Map.of("lineReference", "GL1", "requestedQuantity", 5,
                "articleNumber", "ART-OOS-POST", "packSize", 1, "reservationCode", TestFixtures.RESERVATION_CODE);
        Map<String, Object> body = Map.of("clientNumber", "OB", "orderNumber", "GO-OOS", "sheetNumber", 1,
                "loadCarrier", "FULL", "goodsOutOrderLines", List.of(line));
        mockMvc.perform(post(API + "/goodsOutOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-STOC-0001"))
                .andExpect(jsonPath("$.lineCodes[0].lineCode").value("E-AKO-STOC-0001"));
    }

    @Test
    void goodsOut_post_blockedArticle_returns400WithLineCode() throws Exception {
        putPackUnit("OB", "ART-LOCK-POST", List.of("ARTICLE_IS_LOCKED"));
        asrsStock.addStock("OB", "ART-LOCK-POST", "1", 10);
        Map<String, Object> line = Map.of("lineReference", "GL1", "requestedQuantity", 5,
                "articleNumber", "ART-LOCK-POST", "packSize", 1, "reservationCode", TestFixtures.RESERVATION_CODE);
        Map<String, Object> body = Map.of("clientNumber", "OB", "orderNumber", "GO-LOCK", "sheetNumber", 1,
                "loadCarrier", "FULL", "goodsOutOrderLines", List.of(line));
        mockMvc.perform(post(API + "/goodsOutOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-GENR-0001"))
                .andExpect(jsonPath("$.lineCodes[0].lineCode").value("E-AKO-GENR-0001"));
    }

    @Test
    void goodsOut_validateIntakeLines_andRuntimeValidateLine() throws Exception {
        putPackUnit("OB", "ART-VAL", null);
        asrsStock.addStock("OB", "ART-VAL", "1", 10);
        putPackUnit("OB", "ART-LOCK", List.of("ARTICLE_IS_LOCKED"));
        putPackUnit("OB", "ART-SALE", List.of("TAKEN_OFF_SALE"));
        putPackUnit("OB", "ART-OOS", null);

        assertEquals(1, goodsOutLifecycle.validateIntakeLines("OB", List.of(line("ART-VAL", 0))).size());
        assertEquals(GoodsOutOrderLifecycleService.CODE_FORMAT_ERROR,
                goodsOutLifecycle.validateIntakeLines("OB", List.of(line("ART-VAL", 0))).get(0).lineCode());
        assertFalse(goodsOutLifecycle.masterDataExists("OB", line("NOPE", 5)));
        assertEquals(GoodsOutOrderLifecycleService.CODE_GENERAL_ERROR,
                goodsOutLifecycle.validateIntakeLines("OB", List.of(line("ART-LOCK", 5))).get(0).lineCode());
        assertEquals(GoodsOutOrderLifecycleService.CODE_GENERAL_ERROR,
                goodsOutLifecycle.validateIntakeLines("OB", List.of(line("ART-SALE", 5))).get(0).lineCode());
        assertEquals(GoodsOutOrderLifecycleService.CODE_NOT_ENOUGH_STOCK,
                goodsOutLifecycle.validateIntakeLines("OB", List.of(line("ART-OOS", 5))).get(0).lineCode());
        assertTrue(goodsOutLifecycle.validateIntakeLines("OB", List.of(line("ART-VAL", 5))).isEmpty());

        assertEquals("OUT_OF_STOCK", goodsOutLifecycle.validateLine("OB", line("ART-OOS", 5)));
        assertNull(goodsOutLifecycle.validateLine("OB", line("ART-VAL", 5)));
    }

    @Test
    void goodsOut_intakeReply_includesPrjContainerID() {
        GoodsOutOrder order = new GoodsOutOrder(
                "OB", "GO-PRJ", 1, "FULL",
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                List.of(line("ART-VAL", 5)));
        var reply = goodsOutLifecycle.buildIntakeReply(order, "HOST");
        assertNotNull(reply.goodsOutOrderLines());
        assertEquals(PrjContainerIds.forLine("GO-PRJ", "GL1"), reply.goodsOutOrderLines().get(0).prjContainerID());
        assertTrue(reply.goodsOutOrderLines().get(0).prjContainerID().matches("001\\d{8}"));
    }

    private static GoodsOutOrderLine line(String article, int qty) {
        // lineReference, requestedQuantity, articleNumber, packSize, then 9 optional fields
        return new GoodsOutOrderLine("GL1", qty, article, TestFixtures.PACK_SIZE,
                null, null, null, TestFixtures.RESERVATION_CODE,
                null, null, null, null, null, null);
    }

    // ---- GS §5.3.5 inventory count ----

    @Test
    void inventory_post_unknownArticle_returns400() throws Exception {
        Map<String, Object> req = Map.of("clientNumber", "OB", "requestNumber", "INV-BAD",
                "inventoryRequestLine", Map.of(
                        "lineReference", "IL1",
                        "articleNumber", "DOES-NOT-EXIST",
                        "reservationCode", "BE"));
        mockMvc.perform(post(API + "/inventoryRequest").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MAST-0001"));
    }

    @Test
    void inventory_post_duplicateArticleAndCoo_returns409() throws Exception {
        putPackUnit("OB", "ART-DUP-INV", null);
        Map<String, Object> line = Map.of("lineReference", "IL1", "articleNumber", "ART-DUP-INV",
                "packSize", 1, "reservationCode", "BE");
        Map<String, Object> req1 = Map.of("clientNumber", "OB", "requestNumber", "INV-DUP-1",
                "inventoryRequestLine", line);
        mockMvc.perform(post(API + "/inventoryRequest").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(req1)))
                .andExpect(status().isOk());

        Map<String, Object> req2 = Map.of("clientNumber", "OB", "requestNumber", "INV-DUP-2",
                "inventoryRequestLine", line);
        mockMvc.perform(post(API + "/inventoryRequest").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MOVM-0005"));
    }

    @Test
    void inventory_count_closesRequestAndCorrectsStock() throws Exception {
        putPackUnit("OB", "ART-INV", null);
        asrsStock.addStock("OB", "ART-INV", "1", 10);

        Map<String, Object> req = Map.of("clientNumber", "OB", "requestNumber", "INV-1",
                "inventoryRequestLine", Map.of("lineReference", "IL1", "articleNumber", "ART-INV", "packSize", 1));
        mockMvc.perform(post(API + "/inventoryRequest").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk());

        // Count 7 vs booked 10 -> stock corrected to 7.
        Map<String, Object> count = Map.of("clientNumber", "OB", "requestNumber", "INV-1",
                "lines", List.of(Map.of("lineReference", "IL1", "articleNumber", "ART-INV", "packSize", 1, "countedQuantity", 7)));
        mockMvc.perform(post(API + "/inventoryRequest/operator/count").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(count)))
                .andExpect(status().isOk());
        assertEquals(7, asrsStock.getQuantity("OB", "ART-INV", "1"));

        // Count again -> already FINISHED.
        mockMvc.perform(post(API + "/inventoryRequest/operator/count").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(count)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MOVM-0004"));
    }

    @Test
    void inventory_count_unknownRequest_returns400() throws Exception {
        Map<String, Object> count = Map.of("clientNumber", "OB", "requestNumber", "NOPE",
                "lines", List.of(Map.of("lineReference", "IL1", "countedQuantity", 1)));
        mockMvc.perform(post(API + "/inventoryRequest/operator/count").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(count)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MOVM-0003"));
    }

    @Test
    void stockOperator_spontaneousCorrection_setsAbsoluteQuantity() throws Exception {
        putPackUnit("OB", "ART-SPON", null);
        asrsStock.addStock("OB", "ART-SPON", "1", 10);

        Map<String, Object> body = Map.of(
                "clientNumber", "OB",
                "articleNumber", "ART-SPON",
                "packSize", 1,
                "countedQuantity", 6,
                "reason", "OPERATOR_CORRECTION");
        mockMvc.perform(post(API + "/stock/operator/correct?wait=false").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
        assertEquals(6, asrsStock.getQuantity("OB", "ART-SPON", "1"));
    }

    // ---- GS §5.3.3 / §5.3.4 warehouse-internal ----

    @Test
    void warehouseInternal_retrieve_and_repack_return200() throws Exception {
        putPackUnit("OB", "ART-WI", null);
        asrsStock.addStock("OB", "ART-WI", "1", 10);

        Map<String, Object> retrieve = Map.of("clientNumber", "OB", "loadUnitCode", "LU-1", "loadCarrier", "FULL",
                "stationName", "GIWS01", "articleNumber", "ART-WI", "packSize", 1, "quantity", 4,
                "slot", 1, "toConventional", true);
        mockMvc.perform(post(API + "/loadUnit/retrieve").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(retrieve)))
                .andExpect(status().isOk());
        assertEquals(6, asrsStock.getQuantity("OB", "ART-WI", "1")); // 10 - 4 to conventional

        Map<String, Object> repack = Map.of("clientNumber", "OB", "articleNumber", "ART-WI", "packSize", 1,
                "sourceLoadUnitCode", "LU-1", "targetLoadUnitCode", "LU-2", "deltaQuantity", 0, "reason", "DEFRAGMENTATION");
        mockMvc.perform(post(API + "/loadUnit/repack").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON).content(json(repack)))
                .andExpect(status().isOk());
    }
}
