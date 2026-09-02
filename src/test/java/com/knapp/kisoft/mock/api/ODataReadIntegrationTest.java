package com.knapp.kisoft.mock.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knapp.kisoft.mock.service.AsrsStockService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ODataReadIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AsrsStockService asrsStock;

    private static final String CTX = "/kisoft";
    private static final String API = CTX + "/oneapi/v1";

    private static String json(Object o) throws Exception {
        return new ObjectMapper().writeValueAsString(o);
    }

    @Test
    void getPackUnits_returnsODataCollection() throws Exception {
        Map<String, Object> article = Map.of("clientNumber", "OD", "articleNumber", "ART-OD", "articleName", "OData");
        mockMvc.perform(put(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(TestFixtures.packUnit(article)))))
                .andExpect(status().isOk());

        mockMvc.perform(get(API + "/packUnit").contextPath(CTX)
                        .param("$filter", "clientNumber eq 'OD' and articleNumber eq 'ART-OD'")
                        .param("$count", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['@odata.context']").value("/kisoft/oneapi/v1/$metadata#PackUnits"))
                .andExpect(jsonPath("$['@odata.count']").value(1))
                .andExpect(jsonPath("$.value[0].packSize").value(1));
    }

    @Test
    void getInboundDeliveries_returnsODataCollection() throws Exception {
        Map<String, Object> article = Map.of("clientNumber", "OD", "articleNumber", "ART-X", "articleName", "X");
        mockMvc.perform(put(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(TestFixtures.packUnit(article)))))
                .andExpect(status().isOk());

        Map<String, Object> body = Map.of(
                "clientNumber", "OD",
                "inboundDeliveryNumber", "INB-OD-1",
                "supplierNumber", "SUP-1",
                "inboundDeliveryLines", List.of(Map.of(
                        "lineReference", "IL1",
                        "articleNumber", "ART-X",
                        "packSize", 1,
                        "expectedQuantity", 1)));
        mockMvc.perform(post(API + "/inboundDelivery").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());

        mockMvc.perform(get(API + "/inboundDelivery").contextPath(CTX)
                        .param("$filter", "inboundDeliveryNumber eq 'INB-OD-1'"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['@odata.context']").value("/kisoft/oneapi/v1/$metadata#InboundDeliveries"))
                .andExpect(jsonPath("$.value[0].processingStatus").value("NEW"))
                .andExpect(jsonPath("$.value[0].inboundDelivery.inboundDeliveryNumber").value("INB-OD-1"));
    }

    @Test
    void getGoodsOutOrders_returnsODataCollection() throws Exception {
        Map<String, Object> article = Map.of("clientNumber", "OD", "articleNumber", "ART-GO", "articleName", "GO");
        mockMvc.perform(put(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(TestFixtures.packUnit(article)))))
                .andExpect(status().isOk());
        asrsStock.addStock("OD", "ART-GO", "1", 10);

        Map<String, Object> line = Map.of("lineReference", "GL1", "requestedQuantity", 2,
                "articleNumber", "ART-GO", "packSize", 1, "reservationCode", TestFixtures.RESERVATION_CODE);
        Map<String, Object> order = Map.of("clientNumber", "OD", "orderNumber", "GO-OD-1", "sheetNumber", 1,
                "loadCarrier", "FULL", "goodsOutOrderLines", List.of(line));
        mockMvc.perform(post(API + "/goodsOutOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(order)))
                .andExpect(status().isOk());

        mockMvc.perform(get(API + "/goodsOutOrder").contextPath(CTX)
                        .param("$filter", "orderNumber eq 'GO-OD-1' and sheetNumber eq '1'"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['@odata.context']").value("/kisoft/oneapi/v1/$metadata#GoodsOutOrders"))
                .andExpect(jsonPath("$.value[0].processingStatus").value("NEW"))
                .andExpect(jsonPath("$.value[0].goodsOutOrder.orderNumber").value("GO-OD-1"));
    }

    @Test
    void getInventoryItems_returnsODataCollection() throws Exception {
        asrsStock.addStock("OD", "ART-INV-OD", "1", 7);

        mockMvc.perform(get(API + "/inventoryItem").contextPath(CTX)
                        .param("$filter", "clientNumber eq 'OD' and articleNumber eq 'ART-INV-OD'")
                        .param("$count", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['@odata.context']").value("/kisoft/oneapi/v1/$metadata#InventoryItems"))
                .andExpect(jsonPath("$['@odata.count']").value(1))
                .andExpect(jsonPath("$.value[0].clientNumber").value("OD"))
                .andExpect(jsonPath("$.value[0].articleNumber").value("ART-INV-OD"))
                .andExpect(jsonPath("$.value[0].packSize").value(1))
                .andExpect(jsonPath("$.value[0].quantity").value(7));
    }
}
