package com.example.ticketflow.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理里那些"不来自业务代码"的响应。
 *
 * <p>这里守的是一个容易被忽略的契约：**请求一个不存在的接口应该是 404，不是 500**。
 * 早期版本让 {@code NoResourceFoundException} 落进了兜底分支返回 500，
 * 结果是"客户端把 URL 写错"会被当成"服务端故障"——生产环境里这种噪音会把 5xx 告警打花。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnNotFoundForUnknownEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/this-endpoint-does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("接口不存在"));
    }

    @Test
    void shouldNotTreatUnknownEndpointAsServerError() throws Exception {
        // 显式断言"不是 5xx"：这才是这个缺陷真正的症状
        mockMvc.perform(get("/api/v1/also-missing"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void shouldReturnBadRequestForUnreadableRequestBody() throws Exception {
        // JSON 语法错误是客户端的问题，不该记在服务端故障账上
        mockMvc.perform(
                        post("/api/v1/tenants/register")
                                .contentType(APPLICATION_JSON)
                                .content("{\"tenantCode\": ")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
