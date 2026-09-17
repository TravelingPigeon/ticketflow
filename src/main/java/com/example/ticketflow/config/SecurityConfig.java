package com.example.ticketflow.config;

import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.role.service.PermissionService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        // 只放行健康检查与登录入口，其余接口一律要求已认证身份。
                        .requestMatchers(
                                "/api/v1/ping",
                                "/api/v1/auth/login",
                                "/api/v1/portal/auth/register",
                                "/api/v1/portal/auth/login",
                                "/api/v1/tenants/register",
                                "/actuator/health"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                jwtAuthenticationConverter
                        ))
                        .authenticationEntryPoint(
                                (request, response, authException) ->
                                        writeErrorResponse(
                                                response,
                                                objectMapper,
                                                HttpStatus.UNAUTHORIZED,
                                                "UNAUTHENTICATED",
                                                "请先登录"
                                        )
                        )
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(
                                (request, response, authException) ->
                                        writeErrorResponse(
                                                response,
                                                objectMapper,
                                                HttpStatus.UNAUTHORIZED,
                                                "UNAUTHENTICATED",
                                                "请先登录"
                                        )
                        )
                        .accessDeniedHandler(
                                (request, response, accessDeniedException) ->
                                        writeErrorResponse(
                                                response,
                                                objectMapper,
                                                HttpStatus.FORBIDDEN,
                                                "FORBIDDEN",
                                                "没有权限执行此操作"
                                        )
                        )
                );

        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(
            PermissionService permissionService
    ) {
        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> memberPermissions(jwt, permissionService)
        );

        return converter;
    }

    /**
     * 把令牌里的身份换成数据库里的权限。
     *
     * <p>关键取舍：令牌只携带"你是谁"（tenantId / actorId / actorType），
     * 不携带"你能做什么"。每次请求都按当前的数据库配置解析权限，
     * 因此管理员调整了角色权限后，已经登录的用户无需重新登录就能生效；
     * 如果权限写进令牌，就得等令牌过期（最长 2 小时）。</p>
     */
    private Collection<GrantedAuthority> memberPermissions(
            Jwt jwt,
            PermissionService permissionService
    ) {
        String actorType = jwt.getClaimAsString("actorType");

        if (!ActorType.MEMBER.name().equals(actorType)) {
            // 客户走门户入口和自己的可见范围，不参与角色权限体系
            return List.of();
        }

        Object tenantId = jwt.getClaim("tenantId");
        Object actorId = jwt.getClaim("actorId");

        if (!(tenantId instanceof Number tenantNumber)
                || !(actorId instanceof Number actorNumber)) {
            // 令牌里缺身份信息就直接当成没有任何权限，失败即拒绝
            return List.of();
        }

        return permissionService
                .permissionsOf(
                        tenantNumber.longValue(),
                        actorNumber.longValue()
                )
                .stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.failure(code, message)
        );
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
