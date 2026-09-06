package com.example.ticketflow.controller;

import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.common.exception.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PingController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.success(Map.of("message", "TicketFlow v0.1 is running"));
    }

    /**
     * Temporary endpoint used to verify the global exception response.
     */
    @GetMapping("/ping/error")
    public ApiResponse<Void> errorDemo() {
        throw new BusinessException("DEMO_ERROR", "这是一个错误示例");
    }
}
