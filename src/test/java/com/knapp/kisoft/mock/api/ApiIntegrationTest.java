package com.knapp.kisoft.mock.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knapp.kisoft.mock.service.AsrsStockService;
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
 * API integration tests for the in-scope KiSoft mock endpoints (HIS Appendix): MasterData-Article,
 * Goods-In, Goods-Out, Inventory, Stock Reports and the outgoing webhook doc endpoints.
 * Uses the test profile (bypass-auth, no callback URL) and MockMvc for full HTTP method support.
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

    @Autowired
    private AsrsStockService asrsStock;

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
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @Order(2)
    void packUnit_put_valid_returns200() throws Exception {
        Map<String, Object> article = Map.of(
                "clientNumber", "TEST",
                "articleNumber", "ART-001",
                "articleName", "Test Article");
        Map<String, Object> packUnit = TestFixtures.packUnit(article);
        mockMvc.perform(put(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(packUnit))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    @Order(3)
    void packUnit_get_returnsODataCollection() throws Exception {
        mockMvc.perform(get(API + "/packUnit").contextPath(CTX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['@odata.context']").exists())
                .andExpect(jsonPath("$.value").isArray());
    }

    @Test
    @Order(4)
    void packUnit_put_duplicateArticlePackSize_returns207() throws Exception {
        Map<String, Object> article = Map.of(
                "clientNumber", "TEST",
                "articleNumber", "ART-DUP",
                "articleName", "Dup Article");
        Map<String, Object> packUnit = TestFixtures.packUnit(article);
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
                                "packSize", 1)))))
                .andExpect(status().isOk());
    }

    @Test
    @Order(6)
    void packUnit_put_missingPackSize_returns400() throws Exception {
        Map<String, Object> article = Map.of(
                "clientNumber", "TEST",
                "articleNumber", "ART-NO-PS",
                "articleName", "No pack size");
        Map<String, Object> packUnit = Map.of("article", article);
        mockMvc.perform(put(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(packUnit))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-GENR-0002"));
    }

    @Test
    @Order(7)
    void packUnit_updateSession_CLEANUP_returns200() throws Exception {
        mockMvc.perform(post(API + "/packUnit/updateSession").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("clientNumber", "TEST", "transmissionTag", "CLEANUP"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
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
                        .content(json(List.of(Map.of("article", article, "packSize", TestFixtures.PACK_SIZE, "capacityInformation", TestFixtures.defaultCapacity())))))
                .andExpect(status().isOk());
        Map<String, Object> line = Map.of(
                "lineReference", "L1",
                "articleNumber", "A1",
                "packSize", 1,
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
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    @Order(11)
    void inboundDelivery_post_duplicate_returns400() throws Exception {
        Map<String, Object> line = Map.of(
                "lineReference", "L1",
                "articleNumber", "A1",
                "packSize", 1,
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    @Order(14)
    void openapi_spec_isVersion30_withoutTopLevelWebhooks() throws Exception {
        mockMvc.perform(get(CTX + "/v3/api-docs").contextPath(CTX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.0")))
                .andExpect(jsonPath("$.webhooks").doesNotExist())
                .andExpect(jsonPath("$.paths['/oneapi/v1/_webhooks/inboundDeliveryReply']").exists())
                // Server URL is request-derived (not hardcoded wispelberg.eu)
                .andExpect(jsonPath("$.servers[0].url").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("wispelberg.eu"))))
                .andExpect(jsonPath("$.servers[0].url").value(org.hamcrest.Matchers.containsString("/kisoft")));
    }

    // --- GoodsOutOrder (Goods-Out) ---

    @Test
    @Order(20)
    void goodsOutOrder_post_returns200() throws Exception {
        Map<String, Object> article = Map.of(
                "clientNumber", "C1",
                "articleNumber", "A1",
                "articleName", "Article A1");
        mockMvc.perform(put(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(Map.of("article", article, "packSize", TestFixtures.PACK_SIZE, "capacityInformation", TestFixtures.defaultCapacity())))))
                .andExpect(status().isOk());

        asrsStock.addStock("C1", "A1", "1", 5);

        Map<String, Object> line = Map.of(
                "lineReference", "GL1",
                "requestedQuantity", 5,
                "articleNumber", "A1",
                "packSize", TestFixtures.PACK_SIZE,
                "reservationCode", TestFixtures.RESERVATION_CODE);
        Map<String, Object> order = Map.of(
                "clientNumber", "C1",
                "orderNumber", "GO-001",
                "sheetNumber", 1,
                "loadCarrier", "FULL",
                "goodsOutOrderLines", List.of(line));
        mockMvc.perform(post(API + "/goodsOutOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(order)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    @Order(21)
    void goodsOutOrder_post_duplicate_returns400() throws Exception {
        Map<String, Object> line = Map.of(
                "lineReference", "GL2",
                "requestedQuantity", 1,
                "articleNumber", "A1",
                "reservationCode", TestFixtures.RESERVATION_CODE);
        Map<String, Object> order = Map.of(
                "clientNumber", "C1",
                "orderNumber", "GO-001",
                "sheetNumber", 1,
                "loadCarrier", "FULL",
                "goodsOutOrderLines", List.of(line));
        mockMvc.perform(post(API + "/goodsOutOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(order)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MOVM-0002"));
    }

    @Test
    @Order(22)
    void goodsOutOrder_patch_whenNew_returns200() throws Exception {
        Map<String, Object> body = Map.of(
                "clientNumber", "C1",
                "orderNumber", "GO-001",
                "sheetNumber", 1,
                "priority", 3);
        mockMvc.perform(patch(API + "/goodsOutOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    // --- InventoryRequest (Inventory) ---

    @Test
    @Order(30)
    void inventoryRequest_post_returns200() throws Exception {
        Map<String, Object> line = Map.of("lineReference", "IL1");
        Map<String, Object> request = Map.of(
                "clientNumber", "C1",
                "requestNumber", "INV-001",
                "inventoryRequestLine", line);
        mockMvc.perform(post(API + "/inventoryRequest").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    @Order(31)
    void inventoryRequest_delete_returns200() throws Exception {
        mockMvc.perform(delete(API + "/inventoryRequest").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("clientNumber", "C1", "requestNumber", "INV-001"))))
                .andExpect(status().isOk());
    }

    @Test
    @Order(32)
    void inventoryRequest_delete_unknown_returns400() throws Exception {
        mockMvc.perform(delete(API + "/inventoryRequest").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("clientNumber", "C1", "requestNumber", "DOES-NOT-EXIST"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-MOVM-0003"));
    }

    // --- Stock Reports ---

    @Test
    @Order(40)
    void requestInventoryReport_returns200() throws Exception {
        mockMvc.perform(post(API + "/requestInventoryReport").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestNumber", "REP-001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    @Order(41)
    void requestStorageCapacityReport_returns200() throws Exception {
        mockMvc.perform(post(API + "/requestStorageCapacityReport").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestNumber", "CAP-001", "storageArea", "AREA1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    // --- Webhooks (manual callback triggers) ---

    @Test
    @Order(50)
    void webhooks_inboundDeliveryReply_whenCallbacksDisabled_returns503() throws Exception {
        mockMvc.perform(post(API + "/_webhooks/inboundDeliveryReply").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clientNumber", "C1",
                                "inboundDeliveryNumber", "ID-1",
                                "processingStatus", "NEW"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"));
    }

    @Test
    @Order(51)
    void webhooks_goodsOutOrderReply_whenCallbacksDisabled_returns503() throws Exception {
        mockMvc.perform(post(API + "/_webhooks/goodsOutOrderReply").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("clientNumber", "C1", "orderNumber", "GO-1", "sheetNumber", 1))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"));
    }

    // --- Homepage (renders README.md + links to Swagger) ---

    @Test
    @Order(60)
    void homepage_rendersReadmeAndLinksToSwagger() throws Exception {
        mockMvc.perform(get(CTX + "/").contextPath(CTX))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("KiSoft One")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(CTX + "/swagger-ui.html")));
    }
}
