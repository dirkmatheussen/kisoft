package com.knapp.kisoft.mock.api;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class InboundReservationKeyTest {

    private static final String CTX = "/kisoft";
    private static final String API = CTX + "/oneapi/v1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void inboundAutoStock_differentReservationCodes_createSeparateInventoryRows() throws Exception {
        Map<String, Object> article = Map.of(
                "clientNumber", "IB-COO", "articleNumber", "ART-COO", "articleName", "Reservation key");
        mockMvc.perform(put(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(TestFixtures.packUnit(article)))))
                .andExpect(status().isOk());

        postInbound("IB-COO-PL", inboundLine("L-PL", "PL", 3));
        postInbound("IB-COO-SE", inboundLine("L-SE", "SE", 7));

        mockMvc.perform(get(API + "/inventoryItem").contextPath(CTX)
                        .param("$filter", "packUnit.clientNumber eq 'IB-COO' and packUnit.articleNumber eq 'ART-COO'")
                        .param("$count", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['@odata.count']").value(2))
                .andExpect(jsonPath("$.value[?(@.reservationCode == 'PL')].quantity").value(3))
                .andExpect(jsonPath("$.value[?(@.reservationCode == 'SE')].quantity").value(7));
    }

    private void postInbound(String deliveryNumber, Map<String, Object> line) throws Exception {
        Map<String, Object> delivery = Map.of(
                "clientNumber", "IB-COO",
                "inboundDeliveryNumber", deliveryNumber,
                "supplierNumber", "SUP",
                "inboundDeliveryLines", List.of(line));
        mockMvc.perform(post(API + "/inboundDelivery").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(delivery)))
                .andExpect(status().isOk());
    }

    private static Map<String, Object> inboundLine(String lineReference, String reservationCode, int quantity) {
        return Map.of(
                "lineReference", lineReference,
                "articleNumber", "ART-COO",
                "packSize", TestFixtures.PACK_SIZE,
                "expectedQuantity", quantity,
                "reservationCode", reservationCode);
    }
}
