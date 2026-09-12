package com.example.ticketflow.auth.security;

import com.example.ticketflow.customer.domain.Customer;
import com.example.ticketflow.user.domain.UserAccount;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public TokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public TokenResult issueMemberToken(UserAccount user) {
        return issueToken(
                user.getTenantId(),
                user.getId(),
                user.getUsername(),
                ActorType.MEMBER,
                List.of(user.getRole().name())
        );
    }

    public TokenResult issueCustomerToken(Customer customer) {
        return issueToken(
                customer.getTenantId(),
                customer.getId(),
                customer.getEmail(),
                ActorType.CUSTOMER,
                List.of()
        );
    }

    private TokenResult issueToken(
            Long tenantId,
            Long actorId,
            String subject,
            ActorType actorType,
            List<String> roles
    ) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.ttl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("tenantId", tenantId)
                .claim("actorId", actorId)
                .claim("actorType", actorType.name())
                .claim("roles", roles)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();

        return new TokenResult(token, "Bearer", properties.ttl().toSeconds());
    }

    public record TokenResult(
            String accessToken,
            String tokenType,
            long expiresInSeconds
    ) {
    }
}