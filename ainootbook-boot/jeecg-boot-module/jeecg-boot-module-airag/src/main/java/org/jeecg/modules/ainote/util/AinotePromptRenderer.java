package org.jeecg.modules.ainote.util;

import org.jeecg.common.exception.JeecgBootException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词模板渲染工具：替换 {{variable}} 占位符
 */
public final class AinotePromptRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private AinotePromptRenderer() {
    }

    /**
     * 渲染模板，替换所有 {{variable}} 占位符
     *
     * @param template  模板字符串，必须包含 {{content}}
     * @param variables 变量映射
     * @return 渲染后的字符串
     * @throws JeecgBootException 模板为空或缺少必填变量 {{content}}
     */
    public static String render(String template, Map<String, String> variables) {
        if (template == null || !template.contains("{{content}}")) {
            throw new JeecgBootException("prompt template missing required variable: content");
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables != null ? variables.getOrDefault(key, "") : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
