package com.knapp.kisoft.mock.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderReply;
import com.knapp.kisoft.mock.service.AsrsStockService;
import com.knapp.kisoft.mock.service.ReplyCallbackService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GoodsOutFinalCheckReplyTest {

    private static final String CTX = "/kisoft";
    private static final String API = CTX + "/oneapi/v1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AsrsStockService asrsStock;
    @MockBean ReplyCallbackService callbacks;

    @Test
    void finalCheck_keepsShortPickQuantityErrorFromPickReply() throws Exception {
        Map<String, Object> article = Map.of(
                "clientNumber", "VPNA-TAC", "articleNumber", "VO 85120291", "articleName", "VO 85120291");
        mockMvc.perform(put(API + "/packUnit").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(TestFixtures.packUnit(article)))))
                .andExpect(status().isOk());
        asrsStock.addStock("VPNA-TAC", "VO 85120291", "1", "US", 4, null);

        Map<String, Object> line = Map.of(
                "lineReference", "206356",
                "requestedQuantity", 2,
                "articleNumber", "VO 85120291",
                "packSize", TestFixtures.PACK_SIZE,
                "reservationCode", "US");
        mockMvc.perform(post(API + "/goodsOutOrder").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientNumber", "VPNA-TAC",
                                "orderNumber", "ASRS1178781",
                                "sheetNumber", 1,
                                "loadCarrier", "AeroBox",
                                "goodsOutOrderLines", List.of(line)))))
                .andExpect(status().isOk());

        Map<String, Object> ref = Map.of(
                "clientNumber", "VPNA-TAC", "orderNumber", "ASRS1178781", "sheetNumber", 1);
        mockMvc.perform(post(API + "/goodsOutOrder/operator/start").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ref)))
                .andExpect(status().isOk());

        mockMvc.perform(post(API + "/goodsOutOrder/operator/pick").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientNumber", "VPNA-TAC",
                                "orderNumber", "ASRS1178781",
                                "sheetNumber", 1,
                                "lines", List.of(Map.of(
                                        "lineReference", "206356",
                                        "pickedQuantity", 1,
                                        "sourceLoadUnitCode", "LOU_00000004",
                                        "slot", 1,
                                        "damaged", false))))))
                .andExpect(status().isOk());

        mockMvc.perform(post(API + "/goodsOutOrder/operator/finalCheck").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ref)))
                .andExpect(status().isOk());

        ArgumentCaptor<GoodsOutOrderReply> captor = ArgumentCaptor.forClass(GoodsOutOrderReply.class);
        verify(callbacks, atLeast(3)).sendGoodsOutOrderReply(captor.capture());
        GoodsOutOrderReply finished = captor.getAllValues().stream()
                .filter(r -> "FINISHED".equals(r.processingStatus()))
                .reduce((a, b) -> b)
                .orElseThrow();
        var finishedLine = finished.goodsOutOrderLines().get(0);
        assertThat(finishedLine.processedQuantity()).isEqualTo(1);
        assertThat(finishedLine.processingResult()).isEqualTo("QUANTITY_ERROR");
        assertThat(finishedLine.pickedStock()).isNotEmpty();
        assertThat(finishedLine.pickedStock().get(0).processedQuantity()).isEqualTo(1);
    }
}
