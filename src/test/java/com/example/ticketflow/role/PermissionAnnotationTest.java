package com.example.ticketflow.role;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注解与权限字典的一致性检查。
 *
 * <p>权限串写在注解里只能写字面量，编译器帮不上忙：把 {@code ticket:assign} 打成
 * {@code ticket:assgin}，代码照常编译、照常启动，只是这个接口从此谁都调不了。
 * 这类错误用两个扫描测试兜住。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class PermissionAnnotationTest {

    private static final Pattern HAS_AUTHORITY =
            Pattern.compile("hasAuthority\\('([^']+)'\\)");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldOnlyReferencePermissionsThatExistInDictionary() {
        Set<String> dictionary = new TreeSet<>(
                jdbcTemplate.queryForList(
                        "SELECT code FROM tf_permission",
                        String.class
                )
        );

        List<String> referenced = new ArrayList<>();
        List<String> unknown = new ArrayList<>();

        for (HandlerMethod handler : handlerMapping.getHandlerMethods().values()) {
            PreAuthorize annotation = AnnotationUtils.findAnnotation(
                    handler.getMethod(),
                    PreAuthorize.class
            );

            if (annotation == null) {
                continue;
            }

            Matcher matcher = HAS_AUTHORITY.matcher(annotation.value());

            while (matcher.find()) {
                String code = matcher.group(1);

                referenced.add(code);

                if (!dictionary.contains(code)) {
                    unknown.add(
                            handler.getMethod().getDeclaringClass().getSimpleName()
                                    + "." + handler.getMethod().getName()
                                    + " -> " + code
                    );
                }
            }
        }

        assertEquals(List.of(), unknown, "注解引用了字典里不存在的权限编码");

        // 受保护的接口数量不应该减少——这条是为了防止扫描逻辑本身失效后测试"空转通过"
        assertTrue(referenced.size() >= 10, "受权限保护的接口数量异常");
    }

    @Test
    void shouldProtectEveryWorkbenchEndpoint() {
        List<String> unprotected = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
                : handlerMapping.getHandlerMethods().entrySet()) {

            for (String pattern : entry.getKey().getPatternValues()) {
                if (!isWorkbenchEndpoint(pattern)) {
                    continue;
                }

                if (AnnotationUtils.findAnnotation(
                        entry.getValue().getMethod(),
                        PreAuthorize.class
                ) == null) {
                    unprotected.add(pattern);
                }
            }
        }

        assertEquals(
                List.of(),
                unprotected,
                "工作台接口必须声明权限注解，否则任何已登录身份都能访问"
        );
    }

    /**
     * 工作台 = {@code /api/v1/**} 里排除门户、认证和健康检查之后的部分。
     *
     * <p>门户走 {@code requireCustomer()}，认证接口和租户注册本身就是公开的，
     * 这些都不参与角色权限体系。</p>
     */
    private boolean isWorkbenchEndpoint(String pattern) {
        if (!pattern.startsWith("/api/v1/")) {
            return false;
        }

        return !(pattern.startsWith("/api/v1/portal/")
                || pattern.startsWith("/api/v1/auth/")
                || pattern.startsWith("/api/v1/ping")
                // 租户注册必须匿名可调，否则第一个管理员永远建不出来
                || pattern.startsWith("/api/v1/tenants/register")
                // 通知只作用于收件人自己（查询条件里带 recipientId），
                // 和 /auth/me 一样不存在越权可能，不需要额外的权限串
                || pattern.startsWith("/api/v1/notifications"));
    }
}
