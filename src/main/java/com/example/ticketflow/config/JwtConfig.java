package com.example.ticketflow.config;

import com.example.ticketflow.auth.security.JwtProperties;
import com.example.ticketflow.auth.security.TokenBlacklistValidator;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    private final JwtProperties properties;

    public JwtConfig(JwtProperties properties) {
        this.properties = properties;
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    @Bean
    JwtDecoder jwtDecoder(TokenBlacklistValidator tokenBlacklistValidator) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withSecretKey(secretKey()).build();

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefault(),
                        tokenBlacklistValidator
                )
        );

        return decoder;
    }

    private SecretKey secretKey() {
        byte[] keyBytes = Base64.getDecoder().decode(properties.secret());
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}