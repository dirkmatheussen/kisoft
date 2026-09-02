package com.knapp.kisoft.mock.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * KiSoft-side integration tests aligned with the ASRS use case specs in
 * {@code asrs-specs/usecases/MA-01-part-master-data.md} and
 * {@code asrs-specs/usecases/IB-02-decanting-into-asrs.md}. Focus is on the
 * KiSoft exceptions identified in those use cases.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AsrsKiSoftIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String CTX = "/kisoft";
    private static final String API = CTX + "/oneapi/v1";

    private static String json(Object o) throws Exception {
        return new ObjectMapper().writeValueAsString(o);
    }

    private void putPackUnit(String client, String article, int packSize) throws Exception {
        Map<String, Object> a = Map.of(
                "clientNumber", client,
                "articleNumber", article,
                "articleName", article + " name");
        mockMvc.perform(put(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(TestFixtures.packUnit(a)))))
                .andExpect(status().isOk());
    }

    private void postInbound(String client, String idn, String article, int packSize, int qty) throws Exception {
        Map<String, Object> line = Map.of(
                "lineReference", "L1",
                "articleNumber", article,
                "packSize", packSize,
                "expectedQuantity", qty,
                "reservationCode", TestFixtures.RESERVATION_CODE);
        Map<String, Object> body = Map.of(
                "clientNumber", client,
                "inboundDeliveryNumber", idn,
                "supplierNumber", "SUP",
                "inboundDeliveryLines", List.of(line));
        mockMvc.perform(post(API + "/inboundDelivery").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());
    }

    // ---------- MA-01 KiSoft exceptions ----------

    @Test
    @Order(1)
    void ma01_putPackUnit_oversizeBatch_returns400() throws Exception {
        // MA-01 FR-003: batch larger than 10,000 records must be rejected by KiSoft.
        StringBuilder body = new StringBuilder("[");
        for (int i = 0; i < 10_001; i++) {
            if (i > 0) body.append(',');
            body.append("{\"article\":{\"clientNumber\":\"BATCH\",\"articleNumber\":\"A").append(i)
                    .append("\",\"articleName\":\"n\"},\"packSize\":1,\"capacityInformation\":[{\"loadCarrier\":\"FULL\",\"maximumStoredQuantity\":100}]}");
        }
        body.append(']');
        mockMvc.perform(put(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-GENR-0002"));
    }

    @Test
    @Order(2)
    void ma01_e1_deletePackUnit_blockedWhenAsrsStockExists() throws Exception {
        // MA-01 E1: KiSoft must not allow part removal while ASRS holds inventory.
        putPackUnit("MA01", "ART-E1", TestFixtures.PACK_SIZE);
        postInbound("MA01", "IB-MA01-1", "ART-E1", TestFixtures.PACK_SIZE, 5);

        mockMvc.perform(post(API + "/inboundDelivery/operator/start").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "MA01",
                                "inboundDeliveryNumber", "IB-MA01-1"))))
                .andExpect(status().isOk());

        mockMvc.perform(post(API + "/inboundDelivery/operator/loadUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "MA01",
                                "inboundDeliveryNumber", "IB-MA01-1",
                                "lineReference", "L1",
                                "loadUnitCode", "TOTE-A",
                                "compartment", "C1",
                                "quantity", 5))))
                .andExpect(status().isOk());

        // Now stock exists; delete must be rejected with E-AKO-STOC-0002 (207).
        mockMvc.perform(delete(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(Map.of(
                                "clientNumber", "MA01",
                                "articleNumber", "ART-E1",
                                "packSize", 1)))))
                .andExpect(status().isMultiStatus())
                .andExpect(jsonPath("$[0].codes[0]").value("E-AKO-STOC-0002"));
    }

    // ---------- IB-02 KiSoft exceptions ----------

    @Test
    @Order(10)
    void ib02_post_duplicateArticleInLines_returns400() throws Exception {
        // IB-02: KiSoft does not accept two lines with the same part in one inbound delivery.
        putPackUnit("IB02", "ART-DUP", TestFixtures.PACK_SIZE);
        Map<String, Object> l1 = Map.of(
                "lineReference", "L1", "articleNumber", "ART-DUP", "packSize", 1, "expectedQuantity", 1);
        Map<String, Object> l2 = Map.of(
                "lineReference", "L2", "articleNumber", "ART-DUP", "packSize", 1, "expectedQuantity", 2);
        Map<String, Object> body = Map.of(
                "clientNumber", "IB02",
                "inboundDeliveryNumber", "IB-DUP",
                "supplierNumber", "SUP",
                "inboundDeliveryLines", List.of(l1, l2));
        mockMvc.perform(post(API + "/inboundDelivery").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MAST-0007"));
    }

    @Test
    @Order(11)
    void ib02_post_unknownArticle_returnsE_AKO_MAST_0001() throws Exception {
        Map<String, Object> body = Map.of(
                "clientNumber", "IB02",
                "inboundDeliveryNumber", "IB-NOART",
                "supplierNumber", "SUP",
                "inboundDeliveryLines", List.of(Map.of(
                        "lineReference", "L1",
                        "articleNumber", "DOES-NOT-EXIST",
                        "packSize", 1,
                        "expectedQuantity", 1)));
        mockMvc.perform(post(API + "/inboundDelivery").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MAST-0001"));
    }

    @Test
    @Order(13)
    void ib02_efK409_deleteActiveInbound_returns409() throws Exception {
        putPackUnit("IB02", "ART-DEL", TestFixtures.PACK_SIZE);
        postInbound("IB02", "IB-DEL", "ART-DEL", TestFixtures.PACK_SIZE, 3);

        mockMvc.perform(post(API + "/inboundDelivery/operator/start").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "IB-DEL"))))
                .andExpect(status().isOk());

        mockMvc.perform(delete(API + "/inboundDelivery").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "IB-DEL"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MOVM-0005"));
    }

    @Test
    @Order(14)
    void ib02_delete_whenNew_cancelsAndRemoves() throws Exception {
        putPackUnit("IB02", "ART-CANCEL", TestFixtures.PACK_SIZE);
        postInbound("IB02", "IB-CANCEL", "ART-CANCEL", TestFixtures.PACK_SIZE, 2);

        mockMvc.perform(delete(API + "/inboundDelivery").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "IB-CANCEL"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch(API + "/inboundDelivery").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "IB-CANCEL",
                                "priority", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MOVM-0003"));
    }

    @Test
    @Order(15)
    void ib02_efK409_patchActiveInbound_returns409() throws Exception {
        // IB-02 EF-K409: PatchInboundDelivery while active must return 409.
        putPackUnit("IB02", "ART-K409", TestFixtures.PACK_SIZE);
        postInbound("IB02", "IB-K409", "ART-K409", TestFixtures.PACK_SIZE, 3);

        mockMvc.perform(post(API + "/inboundDelivery/operator/start").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "IB-K409"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch(API + "/inboundDelivery").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "IB-K409",
                                "priority", 5))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MOVM-0005"));
    }

    @Test
    @Order(16)
    void ib02_operator_start_unknownDelivery_returns400() throws Exception {
        // IB-02 EF-02 (A2): putaway task not found in WCS.
        mockMvc.perform(post(API + "/inboundDelivery/operator/start").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "DOES-NOT-EXIST"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MOVM-0003"));
    }

    @Test
    @Order(17)
    void ib02_loadUnit_qtyExceedsOpen_returnsE_AKO_MOVM_0006() throws Exception {
        // IB-02 EF-04 (A4): confirmed qty > remaining open qty.
        putPackUnit("IB02", "ART-EF04", TestFixtures.PACK_SIZE);
        postInbound("IB02", "IB-EF04", "ART-EF04", TestFixtures.PACK_SIZE, 2);

        mockMvc.perform(post(API + "/inboundDelivery/operator/start").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "IB-EF04"))))
                .andExpect(status().isOk());

        mockMvc.perform(post(API + "/inboundDelivery/operator/loadUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "IB-EF04",
                                "lineReference", "L1",
                                "loadUnitCode", "TOTE-EF04",
                                "compartment", "C1",
                                "quantity", 99))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MOVM-0006"));
    }

    @Test
    @Order(18)
    void ib02_loadUnit_compartmentNotEmpty_returnsE_AKO_MOVM_0008() throws Exception {
        // IB-02 EF-06 (A6): non-empty compartment / topping-up not allowed.
        putPackUnit("IB02", "ART-EF06", TestFixtures.PACK_SIZE);
        postInbound("IB02", "IB-EF06", "ART-EF06", TestFixtures.PACK_SIZE, 5);

        mockMvc.perform(post(API + "/inboundDelivery/operator/start").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "IB-EF06"))))
                .andExpect(status().isOk());

        // First load unit fills compartment C1 of TOTE-EF06 with 2 pieces.
        mockMvc.perform(post(API + "/inboundDelivery/operator/loadUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "IB-EF06",
                                "lineReference", "L1",
                                "loadUnitCode", "TOTE-EF06",
                                "compartment", "C1",
                                "quantity", 2))))
                .andExpect(status().isOk());

        // Second confirmation reuses the same compartment -> topping-up rejected.
        mockMvc.perform(post(API + "/inboundDelivery/operator/loadUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "IB-EF06",
                                "lineReference", "L1",
                                "loadUnitCode", "TOTE-EF06",
                                "compartment", "C1",
                                "quantity", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MOVM-0008"));
    }

    @Test
    @Order(19)
    void ib02_happyPath_singleTote_completesAndAutoFinishes() throws Exception {
        // Page7 happy path: NEW -> STARTED -> 1 PostStockReceived -> FINISHED.
        putPackUnit("IB02", "ART-OK", TestFixtures.PACK_SIZE);
        postInbound("IB02", "IB-OK", "ART-OK", TestFixtures.PACK_SIZE, 3);

        mockMvc.perform(post(API + "/inboundDelivery/operator/start").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "IB-OK"))))
                .andExpect(status().isOk());

        mockMvc.perform(post(API + "/inboundDelivery/operator/loadUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "IB-OK",
                                "lineReference", "L1",
                                "loadUnitCode", "TOTE-OK",
                                "compartment", "C1",
                                "quantity", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("FINISHED")));

        // Once FINISHED, patch must yield 409 again.
        mockMvc.perform(patch(API + "/inboundDelivery").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "IB-OK",
                                "priority", 9))))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(20)
    void ib02_partialReceipt_thenManualFinish_succeeds() throws Exception {
        // IB-02 EF-05 (A5): short receipt closed via operator finish (FINISHED reply).
        putPackUnit("IB02", "ART-SHORT", TestFixtures.PACK_SIZE);
        postInbound("IB02", "IB-SHORT", "ART-SHORT", TestFixtures.PACK_SIZE, 5);

        mockMvc.perform(post(API + "/inboundDelivery/operator/start").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "IB-SHORT"))))
                .andExpect(status().isOk());

        mockMvc.perform(post(API + "/inboundDelivery/operator/loadUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "IB-SHORT",
                                "lineReference", "L1",
                                "loadUnitCode", "TOTE-SHORT",
                                "compartment", "C1",
                                "quantity", 2))))
                .andExpect(status().isOk());

        mockMvc.perform(post(API + "/inboundDelivery/operator/finish").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "IB02",
                                "inboundDeliveryNumber", "IB-SHORT"))))
                .andExpect(status().isOk());
    }
}
