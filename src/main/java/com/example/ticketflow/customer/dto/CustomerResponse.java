package com.example.ticketflow.customer.dto;

import com.example.ticketflow.customer.domain.Customer;
import com.example.ticketflow.customer.domain.enums.CustomerStatus;

import java.time.LocalDateTime;

public record CustomerResponse(
        Long id,
        Long tenantId,
        String email,
        String displayName,
        CustomerStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getTenantId(),
                customer.getEmail(),
                customer.getDisplayName(),
                customer.getStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}