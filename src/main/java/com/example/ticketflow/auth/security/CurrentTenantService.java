package com.example.ticketflow.auth.security;

import com.example.ticketflow.common.exception.BusinessException;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

@Component
public class CurrentTenantService {

    public Long requireTenantId(HttpSession session) {
        Object tenantId =
                session.getAttribute("CURRENT_TENANT_ID");

        if (!(tenantId instanceof Long)) {
            throw new BusinessException(
                    "UNAUTHENTICATED",
                    "请先登录"
            );
        }

        return (Long) tenantId;
    }
}