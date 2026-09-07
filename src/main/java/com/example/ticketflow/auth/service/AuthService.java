package com.example.ticketflow.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.domain.enums.UserStatus;
import com.example.ticketflow.user.dto.LoginRequest;
import com.example.ticketflow.user.dto.UserResponse;
import com.example.ticketflow.user.mapper.UserAccountMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserAccountMapper userAccountMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public UserAccount verifyCredentials(LoginRequest request) {
        UserAccount user = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(
                                UserAccount::getTenantId,
                                request.tenantId()
                        )
                        .eq(
                                UserAccount::getUsername,
                                request.username()
                        )
        );

        if (user == null) {
            throw new BusinessException(
                    "INVALID_CREDENTIALS",
                    "用户名或密码错误"
            );
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new BusinessException(
                    "USER_LOCKED",
                    "用户已被锁定"
            );
        }

        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        );

        if (!passwordMatches) {
            throw new BusinessException(
                    "INVALID_CREDENTIALS",
                    "用户名或密码错误"
            );
        }

        return user;
    }

    public UserResponse login(LoginRequest request) {
        UserAccount user = verifyCredentials(request);
        return UserResponse.from(user);
    }
}