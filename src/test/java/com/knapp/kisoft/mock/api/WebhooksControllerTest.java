package com.knapp.kisoft.mock.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knapp.kisoft.mock.service.ReplyCallbackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "knapp.mock.reply-callback-enabled=true",
        "knapp.mock.reply-callback-url=https://api-test.example/kisoft"
})
class WebhooksControllerTest {

    private static final String CTX = "/kisoft";
    private static final String API = CTX + "/oneapi/v1";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    ReplyCallbackService callbacks;

    @Test
    void inboundDeliveryReply_dispatchesAsyncWhenWaitFalse() throws Exception {
        mockMvc.perform(post(API + "/_webhooks/inboundDeliveryReply?wait=false").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientNumber", "DEFAULT",
                                "inboundDeliveryNumber", "123",
                                "processingStatus", "STARTED"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("inboundDeliveryReply")));

        verify(callbacks).sendInboundDeliveryReply(any());
    }
}
