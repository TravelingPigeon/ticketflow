package com.example.ticketflow.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.audit.service.AuditLogService;
import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.common.exception.ErrorCode;
import com.example.ticketflow.customer.domain.Customer;
import com.example.ticketflow.customer.domain.enums.CustomerStatus;
import com.example.ticketflow.customer.dto.CustomerLoginRequest;
import com.example.ticketflow.customer.dto.RegisterCustomerRequest;
import com.example.ticketflow.customer.mapper.CustomerMapper;
import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.tenant.mapper.TenantMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class CustomerService {

    private final CustomerMapper customerMapper;
    private final TenantMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public CustomerService(
            CustomerMapper customerMapper,
            TenantMapper tenantMapper,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        this.customerMapper = customerMapper;
        this.tenantMapper = tenantMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public Customer register(RegisterCustomerRequest request) {
        Tenant tenant = requireTenant(request.tenantCode());
        String email = normalizeEmail(request.email());

        Customer existing = findCustomer(tenant.getId(), email);

        if (existing != null) {
            throw new BusinessException(
                    ErrorCode.CUSTOMER_EMAIL_EXISTS,
                    "该邮箱已在本租户注册"
            );
        }

        Customer customer = new Customer();
        customer.setTenantId(tenant.getId());
        customer.setEmail(email);
        customer.setPasswordHash(
                passwordEncoder.encode(request.password())
        );
        customer.setDisplayName(request.displayName().trim());
        customer.setStatus(CustomerStatus.ACTIVE);

        customerMapper.insert(customer);

        return customerMapper.selectById(customer.getId());
    }

    public Customer authenticate(CustomerLoginRequest request) {
        // 这里不能用 requireTenant：它查不到就直接抛异常，
        // 那样"租户编码写错"这一种尝试就留不下审计。审计要先写、异常后抛。
        Tenant tenant = tenantMapper.selectOne(
                new LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getCode, request.tenantCode().trim())
        );

        if (tenant == null) {
            auditLogService.recordLoginFailure(
                    ActorType.CUSTOMER,
                    null,
                    null,
                    request.tenantCode(),
                    request.email(),
                    "TENANT_NOT_FOUND"
            );

            throw new BusinessException(
                    ErrorCode.TENANT_NOT_FOUND,
                    "租户不存在"
            );
        }

        String email = normalizeEmail(request.email());

        Customer customer = findCustomer(tenant.getId(), email);

        if (customer == null) {
            auditLogService.recordLoginFailure(
                    ActorType.CUSTOMER,
                    tenant.getId(),
                    null,
                    request.tenantCode(),
                    email,
                    "EMAIL_NOT_FOUND"
            );

            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS,
                    "邮箱或密码错误"
            );
        }

        if (customer.getStatus() == CustomerStatus.LOCKED) {
            auditLogService.recordLoginFailure(
                    ActorType.CUSTOMER,
                    tenant.getId(),
                    customer.getId(),
                    request.tenantCode(),
                    email,
                    "CUSTOMER_LOCKED"
            );

            throw new BusinessException(
                    ErrorCode.CUSTOMER_LOCKED,
                    "账号已被锁定"
            );
        }

        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                customer.getPasswordHash()
        );

        if (!passwordMatches) {
            auditLogService.recordLoginFailure(
                    ActorType.CUSTOMER,
                    tenant.getId(),
                    customer.getId(),
                    request.tenantCode(),
                    email,
                    "BAD_PASSWORD"
            );

            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS,
                    "邮箱或密码错误"
            );
        }

        auditLogService.recordLoginSuccess(
                ActorType.CUSTOMER,
                tenant.getId(),
                customer.getId(),
                request.tenantCode(),
                email
        );

        return customer;
    }

    private Tenant requireTenant(String tenantCode) {
        Tenant tenant = tenantMapper.selectOne(
                new LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getCode, tenantCode.trim())
        );

        if (tenant == null) {
            throw new BusinessException(
                    ErrorCode.TENANT_NOT_FOUND,
                    "租户不存在"
            );
        }

        return tenant;
    }

    private Customer findCustomer(Long tenantId, String email) {
        return customerMapper.selectOne(
                new LambdaQueryWrapper<Customer>()
                        .eq(Customer::getTenantId, tenantId)
                        .eq(Customer::getEmail, email)
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}