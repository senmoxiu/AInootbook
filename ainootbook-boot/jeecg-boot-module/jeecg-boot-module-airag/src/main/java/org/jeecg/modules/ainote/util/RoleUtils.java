package org.jeecg.modules.ainote.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色编码工具类：逗号分割后 Set 精确匹配，避免 contains() 子串误判
 */
public final class RoleUtils {

    private RoleUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 解析逗号分隔的 roleCode 为角色集合
     */
    public static Set<String> parseRoles(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(roleCode.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 精确判断 roleCode 中是否包含指定角色
     */
    public static boolean hasRole(String roleCode, String target) {
        if (target == null || target.isBlank()) {
            return false;
        }
        return parseRoles(roleCode).contains(target.trim());
    }
}
