package com.knapp.kisoft.mock.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that {@code importFile} confines caller-supplied paths to the configured import directory and
 * does not echo file contents or arbitrary paths back in error responses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryImportControllerTest {

    @Autowired private MockMvc mockMvc;

    private static final String CTX = "/kisoft";
    private static final String IMPORT = CTX + "/oneapi/v1/inventoryItem/operator/importFile";

    private static String json(Object o) throws Exception {
        return new ObjectMapper().writeValueAsString(o);
    }

    @Test
    void importFile_rejectsPathTraversalOutsideImportDir() throws Exception {
        mockMvc.perform(post(IMPORT).contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("path", "../../../../etc/passwd"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("path must resolve inside the configured import directory"));
    }

    @Test
    void importFile_rejectsAbsolutePathOutsideImportDir() throws Exception {
        mockMvc.perform(post(IMPORT).contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("path", "/etc/passwd"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("path must resolve inside the configured import directory"))
                // The rejection must not leak the target path or its contents.
                .andExpect(jsonPath("$.message").value(not(containsString("etc"))));
    }

    @Test
    void importFile_missingFileInsideImportDirReturnsGenericMessage() throws Exception {
        mockMvc.perform(post(IMPORT).contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("path", "does-not-exist.json"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No importable file at the requested path"));
    }
}
