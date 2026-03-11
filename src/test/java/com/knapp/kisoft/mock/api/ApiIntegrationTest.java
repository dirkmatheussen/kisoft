package com.knapp.kisoft.mock.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API integration tests for all KiSoft mock endpoints (PackUnit, InboundDelivery, StorageOrder).
 * Uses test profile with bypass-auth so no Bearer token is required. Uses MockMvc for full HTTP method support (including PATCH).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(ApiIntegrationTest.TestResultLogger.class)
class ApiIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ApiIntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;

    static class TestResultLogger implements TestWatcher {
        @Override
        public void testSuccessful(ExtensionContext context) {
            log.info("PASSED  {}", context.getDisplayName());
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            log.error("FAILED  {}: {}", context.getDisplayName(), cause.getMessage());
        }

        @Override
        public void testAborted(ExtensionContext context, Throwable cause) {
            log.warn("ABORTED {}", context.getDisplayName());
        }
    }

    private static String json(Object o) throws Exception {
        return new ObjectMapper().writeValueAsString(o);
    }

    private static final String CTX = "/kisoft";
    private static final String API = CTX + "/oneapi/v1";

    // --- PackUnit (MasterData-Article) ---

    @Test
    @Order(1)
    void packUnit_updateSession_SET_returns200() throws Exception {
        mockMvc.perform(post(API + "/packUnit/updateSession").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("clientNumber", "TEST", "transmissionTag", "SET"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.transmissionTag").value("SET"));
    }

    @Test
    @Order(2)
    void packUnit_put_valid_returns200() throws Exception {
        Map<String, Object> article = Map.of(
                "clientNumber", "TEST",
                "articleNumber", "ART-001",
                "articleName", "Test Article");
        Map<String, Object> packUnit = Map.of("article", article, "packSize", "EU");
        mockMvc.perform(put(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(packUnit))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    @Order(3)
    void packUnit_get_returns200() throws Exception {
        mockMvc.perform(get(API + "/packUnit").contextPath(CTX))
                .andExpect(status().isOk());
    }

    @Test
    @Order(4)
    void packUnit_put_duplicateArticlePackSize_returns207() throws Exception {
        Map<String, Object> article = Map.of(
                "clientNumber", "TEST",
                "articleNumber", "ART-DUP",
                "articleName", "Dup Article");
        Map<String, Object> packUnit = Map.of("article", article, "packSize", "EU");
        mockMvc.perform(put(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(packUnit, packUnit))))
                .andExpect(status().isMultiStatus()); // 207
    }

    @Test
    @Order(5)
    void packUnit_delete_withBody_noStock_returns200() throws Exception {
        mockMvc.perform(delete(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(Map.of(
                                "clientNumber", "TEST",
                                "articleNumber", "ART-001",
                                "packSize", "EU")))))
                .andExpect(status().isOk());
    }

    @Test
    @Order(6)
    void packUnit_delete_existingRef_returns200() throws Exception {
        Map<String, Object> article = Map.of(
                "clientNumber", "TEST",
                "articleNumber", "ART-STOCK",
                "articleName", "Stock Article");
        mockMvc.perform(put(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(Map.of("article", article, "packSize", "EU")))))
                .andExpect(status().isOk());
        mockMvc.perform(delete(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(Map.of(
                                "clientNumber", "TEST",
                                "articleNumber", "ART-STOCK",
                                "packSize", "EU")))))
                .andExpect(status().isOk());
    }

    @Test
    @Order(7)
    void packUnit_updateSession_CLEANUP_returns200() throws Exception {
        mockMvc.perform(post(API + "/packUnit/updateSession").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("clientNumber", "TEST", "transmissionTag", "CLEANUP"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transmissionTag").value("CLEANUP"));
    }

    // --- InboundDelivery (Goods-In) ---

    @Test
    @Order(10)
    void inboundDelivery_post_requiresPackUnit() throws Exception {
        Map<String, Object> article = Map.of(
                "clientNumber", "C1",
                "articleNumber", "A1",
                "articleName", "Article 1");
        mockMvc.perform(put(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(Map.of("article", article, "packSize", "EU")))))
                .andExpect(status().isOk());
        Map<String, Object> line = Map.of(
                "lineReference", "L1",
                "articleNumber", "A1",
                "packSize", "EU",
                "expectedQuantity", 10);
        Map<String, Object> delivery = Map.of(
                "clientNumber", "C1",
                "inboundDeliveryNumber", "ID-001",
                "supplierNumber", "SUP1",
                "inboundDeliveryLines", List.of(line));
        mockMvc.perform(post(API + "/inboundDelivery").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(delivery)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").value("NEW"));
    }

    @Test
    @Order(11)
    void inboundDelivery_post_duplicate_returns400() throws Exception {
        Map<String, Object> line = Map.of(
                "lineReference", "L1",
                "articleNumber", "A1",
                "packSize", "EU",
                "expectedQuantity", 5);
        Map<String, Object> delivery = Map.of(
                "clientNumber", "C1",
                "inboundDeliveryNumber", "ID-001",
                "supplierNumber", "SUP1",
                "inboundDeliveryLines", List.of(line));
        mockMvc.perform(post(API + "/inboundDelivery").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(delivery)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(12)
    void inboundDelivery_patch_returns200() throws Exception {
        Map<String, Object> body = Map.of(
                "clientNumber", "C1",
                "inboundDeliveryNumber", "ID-001",
                "priority", 2);
        mockMvc.perform(patch(API + "/inboundDelivery").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(13)
    void inboundDelivery_delete_whenNew_returns200() throws Exception {
        mockMvc.perform(delete(API + "/inboundDelivery").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("clientNumber", "C1", "inboundDeliveryNumber", "ID-001"))))
                .andExpect(status().isOk());
    }

    // --- StorageOrder (Goods-In) ---

    @Test
    @Order(20)
    void storageOrder_post_returns200() throws Exception {
        Map<String, Object> targetPosition = Map.of("storageArea", "AREA1", "locationNumber", "L01");
        Map<String, Object> order = Map.of(
                "clientNumber", "C1",
                "orderNumber", "SO-001",
                "loadUnitCode", "LU-001",
                "loadCarrier", "PAL",
                "targetPosition", targetPosition,
                "contentType", "FULL");
        mockMvc.perform(post(API + "/storageOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(order)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").value("NEW"));
    }

    @Test
    @Order(21)
    void storageOrder_post_duplicate_returns400() throws Exception {
        Map<String, Object> targetPosition = Map.of("storageArea", "AREA1");
        Map<String, Object> order = Map.of(
                "clientNumber", "C1",
                "orderNumber", "SO-001",
                "loadUnitCode", "LU-002",
                "loadCarrier", "PAL",
                "targetPosition", targetPosition,
                "contentType", "FULL");
        mockMvc.perform(post(API + "/storageOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(order)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(22)
    void storageOrder_get_returns200() throws Exception {
        mockMvc.perform(get(API + "/storageOrder").contextPath(CTX))
                .andExpect(status().isOk());
    }

    @Test
    @Order(23)
    void storageOrder_patch_returns200() throws Exception {
        Map<String, Object> targetPosition = Map.of("storageArea", "AREA2", "locationNumber", "L02");
        Map<String, Object> body = Map.of(
                "clientNumber", "C1",
                "orderNumber", "SO-001",
                "targetPosition", targetPosition);
        mockMvc.perform(patch(API + "/storageOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(24)
    void storageOrder_delete_whenNew_returns200() throws Exception {
        mockMvc.perform(delete(API + "/storageOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("clientNumber", "C1", "orderNumber", "SO-001"))))
                .andExpect(status().isOk());
    }

    // --- Webhooks doc endpoints (documentation-only) ---

    @Test
    @Order(30)
    void webhooks_inboundDeliveryReply_doc_returns200() throws Exception {
        mockMvc.perform(post(API + "/_webhooks/inboundDeliveryReply").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("clientNumber", "C1", "inboundDeliveryNumber", "ID-1"))))
                .andExpect(status().isOk());
    }

    @Test
    @Order(31)
    void webhooks_storageOrderReply_doc_returns200() throws Exception {
        mockMvc.perform(post(API + "/_webhooks/storageOrderReply").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("clientNumber", "C1", "orderNumber", "O1"))))
                .andExpect(status().isOk());
    }
}
