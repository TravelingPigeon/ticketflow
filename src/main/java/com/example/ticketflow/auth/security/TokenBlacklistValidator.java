package com.example.ticketflow.auth.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class TokenBlacklistValidator implements OAuth2TokenValidator<Jwt> {

    private final TokenBlacklist tokenBlacklist;

    public TokenBlacklistValidator(TokenBlacklist tokenBlacklist) {
        this.tokenBlacklist = tokenBlacklist;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (tokenBlacklist.isBlacklisted(token.getId())) {
            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "令牌已失效，请重新登录",
                    null
            );

            return OAuth2TokenValidatorResult.failure(error);
        }

        return OAuth2TokenValidatorResult.success();
    }
}