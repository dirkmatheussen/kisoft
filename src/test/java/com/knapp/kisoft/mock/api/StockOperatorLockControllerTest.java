package com.knapp.kisoft.mock.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knapp.kisoft.mock.api.dto.StockLockChanged;
import com.knapp.kisoft.mock.persistence.AsrsStockRepository;
import com.knapp.kisoft.mock.service.AsrsStockAttributes;
import com.knapp.kisoft.mock.service.AsrsStockService;
import com.knapp.kisoft.mock.service.ReplyCallbackService;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StockOperatorLockControllerTest {

    private static final String CTX = "/kisoft";
    private static final String API = CTX + "/oneapi/v1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AsrsStockService asrsStock;
    @Autowired AsrsStockRepository repo;
    @MockBean ReplyCallbackService callbacks;

    @BeforeEach
    void seed() {
        repo.deleteAll();
        asrsStock.addStock("VPNA-TAC", "VO 25133699", "1", 6,
                new AsrsStockAttributes(null, null, null, null, "PL", null));
    }

    private Map<String, Object> body(String action, List<String> reasons) {
        Map<String, Object> m = new HashMap<>();
        m.put("action", action);
        m.put("clientNumber", "VPNA-TAC");
        m.put("articleNumber", "VO 25133699");
        m.put("packSize", 1);
        m.put("reservationCode", "PL");
        m.put("stockLockReasons", reasons);
        m.put("stationName", "MOCK-STATION");
        return m;
    }

    @Test
    void lock_persistsReasons_andEmitsStockLockChanged() throws Exception {
        mockMvc.perform(post(API + "/stock/operator/lock?wait=false").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("LOCK", List.of("QS_REQ")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        ArgumentCaptor<StockLockChanged> captor = ArgumentCaptor.forClass(StockLockChanged.class);
        verify(callbacks).sendStockLockChanged(captor.capture());
        StockLockChanged event = captor.getValue();
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.stockLockRequestReference()).isNull();
        assertThat(event.processedQuantity()).isEqualTo(6);
        assertThat(event.addedStockLocks()).containsExactly("QS_REQ");
        assertThat(event.removedStockLocks()).isNull();
        assertThat(event.stationName()).isEqualTo("MOCK-STATION");
        assertThat(event.reason()).isEqualTo("OPERATOR_LOCK");
        assertThat(event.processedStock().packUnit().articleNumber()).isEqualTo("VO 25133699");
        assertThat(event.processedStock().packUnit().packSize()).isEqualTo(1);
        assertThat(event.processedStock().quantity()).isEqualTo(6);
        assertThat(event.processedStock().reservationCode()).isEqualTo("PL");
        assertThat(event.processedStock().stockLockReasons()).containsExactly("QS_REQ");

        mockMvc.perform(get(API + "/inventoryItem").contextPath(CTX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value[0].stockLockReasons[0]").value("QS_REQ"))
                .andExpect(jsonPath("$.value[0].quantity").value(6));
    }

    @Test
    void unlock_removesReasons_emptyListRemovesAll() throws Exception {
        mockMvc.perform(post(API + "/stock/operator/lock?wait=false").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("LOCK", List.of("HOST", "QS_REQ")))))
                .andExpect(status().isOk());

        mockMvc.perform(post(API + "/stock/operator/lock?wait=false").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("UNLOCK", List.of()))))
                .andExpect(status().isOk());

        ArgumentCaptor<StockLockChanged> captor = ArgumentCaptor.forClass(StockLockChanged.class);
        verify(callbacks, org.mockito.Mockito.times(2)).sendStockLockChanged(captor.capture());
        StockLockChanged unlock = captor.getAllValues().get(1);
        assertThat(unlock.addedStockLocks()).isNull();
        assertThat(unlock.removedStockLocks()).containsExactly("HOST", "QS_REQ");
        assertThat(unlock.reason()).isEqualTo("OPERATOR_UNLOCK");
        assertThat(unlock.processedStock().stockLockReasons()).isNull();
        assertThat(AsrsStockService.readLockReasons(
                repo.findByClientNumberAndArticleNumberAndPackSize("VPNA-TAC", "VO 25133699", "1").orElseThrow()))
                .isNull();
    }

    @Test
    void invalidReason_returns400_E_AKO_GENR_0002() throws Exception {
        mockMvc.perform(post(API + "/stock/operator/lock?wait=false").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("LOCK", List.of("BOGUS")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-GENR-0002"))
                .andExpect(jsonPath("$.message").value(containsString("BOGUS")));

        mockMvc.perform(post(API + "/stock/operator/lock?wait=false").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("LOCK", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-GENR-0002"));
    }

    @Test
    void reservationCodeMismatch_orUnknownArticle_returns404_E_AKO_STOC_0003() throws Exception {
        Map<String, Object> wrongCoo = body("LOCK", List.of("HOST"));
        wrongCoo.put("reservationCode", "SE");
        mockMvc.perform(post(API + "/stock/operator/lock?wait=false").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongCoo)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-STOC-0003"))
                .andExpect(jsonPath("$.articleNumber").value("VO 25133699"));

        Map<String, Object> unknown = body("LOCK", List.of("HOST"));
        unknown.put("articleNumber", "UNKNOWN");
        mockMvc.perform(post(API + "/stock/operator/lock?wait=false").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknown)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-STOC-0003"));
    }
}
