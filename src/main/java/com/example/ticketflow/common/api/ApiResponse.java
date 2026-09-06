package com.example.ticketflow.common.api;

/**
 * Unified response envelope for successful API results.
 *
 * @param success whether the request succeeded
 * @param data    response payload
 */
public record ApiResponse<T>(boolean success, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data);
    }
}
