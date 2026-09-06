package com.example.ticketflow.tenant.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateTenant() throws Exception {
        String requestBody = """
                {
                  "code": "auto-test",
                  "name": "自动化测试团队"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/tenants")
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.code").value("auto-test"))
                .andExpect(jsonPath("$.data.name").value("自动化测试团队"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void shouldRejectDuplicateTenantCode() throws Exception {
        String requestBody = """
                {
                  "code": "duplicate-test",
                  "name": "重复编码测试"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/tenants")
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/tenants")
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TENANT_CODE_EXISTS"));
    }

    @Test
    void shouldRejectBlankRequest() throws Exception {
        String requestBody = """
                {
                  "code": "",
                  "name": ""
                }
                """;

        mockMvc.perform(
                        post("/api/v1/tenants")
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}