package com.knapp.kisoft.mock.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "knapp.mock.ui-auth-enabled=true",
        "knapp.mock.ui-username=tester",
        "knapp.mock.ui-password=secret"
})
class UiAuthIntegrationTest {

    private static final String CTX = "/kisoft";

    @Autowired
    MockMvc mockMvc;

    @Test
    void homepage_requiresBasicAuthWhenEnabled() throws Exception {
        mockMvc.perform(get(CTX + "/").contextPath(CTX))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(CTX + "/").contextPath(CTX).with(httpBasic("tester", "secret")))
                .andExpect(status().isOk());
    }

    @Test
    void swaggerUi_requiresBasicAuthWhenEnabled() throws Exception {
        mockMvc.perform(get(CTX + "/swagger-ui.html").contextPath(CTX))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(CTX + "/swagger-ui.html").contextPath(CTX).with(httpBasic("tester", "secret")))
                .andExpect(status().is3xxRedirection());
    }
}
