package org.jeecg.modules.ainote.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.HTMLUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import tech.catheu.katex.Katex;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Markdown 预编译服务：将 Markdown 中的 LaTeX 公式预编译为 KaTeX HTML/MathML。
 */
@Slf4j
@Service
public class MarkdownPrecompileService {

    private static final String PLACEHOLDER_PREFIX = "AINOTEKATEXPLACEHOLDER";
    private static final Safelist MARKDOWN_HTML_WHITELIST = buildMarkdownHtmlWhitelist();
    private static final Set<String> SAFE_HREF_PROTOCOLS = new HashSet<>(Arrays.asList("http", "https", "mailto"));
    private static final Set<String> SAFE_SRC_PROTOCOLS = new HashSet<>(Arrays.asList("http", "https"));

    // KaTeX/JS 引擎实例线程不安全，按线程隔离复用以避免并发访问同一实例。
    private final ThreadLocal<Katex> katexHolder = ThreadLocal.withInitial(Katex::newInstance);

    public String precompile(String markdown) {
        if (oConvertUtils.isEmpty(markdown)) {
            return "";
        }

        String normalized = normalizeLineEndings(markdown);
        try {
            Map<String, String> placeholders = new LinkedHashMap<>();
            String tokenizedMarkdown = tokenizeLatex(normalized, placeholders);
            String renderedHtml = HTMLUtils.parseMarkdown(tokenizedMarkdown);
            String sanitizedHtml = sanitizeRenderedHtml(renderedHtml);
            return restorePlaceholders(sanitizedHtml, placeholders);
        } catch (Exception e) {
            log.warn("Markdown 预编译失败，回退原始 LaTeX: {}", safeShort(e.getMessage()));
            return fallbackRender(normalized);
        }
    }

    @PreDestroy
    private void clearKatexThreadLocal() {
        destroyKatexForCurrentThread();
    }

    private Katex getKatexForCurrentThread() {
        return katexHolder.get();
    }

    private void destroyKatexForCurrentThread() {
        katexHolder.remove();
    }

    private String tokenizeLatex(String markdown, Map<String, String> placeholders) {
        StringBuilder output = new StringBuilder(markdown.length() + 128);
        boolean lineStart = true;
        char fenceChar = 0;
        int fenceLength = 0;
        int inlineCodeTicks = 0;
        int index = 0;

        while (index < markdown.length()) {
            if (fenceChar != 0) {
                if (lineStart && markdown.charAt(index) == fenceChar) {
                    int repeated = countRepeated(markdown, index, fenceChar);
                    if (repeated >= fenceLength) {
                        int nextIndex = appendCurrentLine(markdown, index, output);
                        index = nextIndex;
                        lineStart = isLineStart(markdown, index);
                        fenceChar = 0;
                        fenceLength = 0;
                        continue;
                    }
                }
                char current = markdown.charAt(index);
                output.append(current);
                lineStart = current == '\n';
                index++;
                continue;
            }

            if (inlineCodeTicks > 0) {
                if (markdown.charAt(index) == '`') {
                    int repeated = countRepeated(markdown, index, '`');
                    if (repeated == inlineCodeTicks) {
                        output.append(markdown, index, index + repeated);
                        index += repeated;
                        inlineCodeTicks = 0;
                        lineStart = false;
                        continue;
                    }
                }
                char current = markdown.charAt(index);
                output.append(current);
                lineStart = current == '\n';
                index++;
                continue;
            }

            if (lineStart && (markdown.startsWith("```", index) || markdown.startsWith("~~~", index))) {
                fenceChar = markdown.charAt(index);
                fenceLength = countRepeated(markdown, index, fenceChar);
                int nextIndex = appendCurrentLine(markdown, index, output);
                index = nextIndex;
                lineStart = isLineStart(markdown, index);
                continue;
            }

            if (markdown.charAt(index) == '`') {
                inlineCodeTicks = countRepeated(markdown, index, '`');
                output.append(markdown, index, index + inlineCodeTicks);
                index += inlineCodeTicks;
                lineStart = false;
                continue;
            }

            MathSegment segment = tryMatchLatex(markdown, index);
            if (segment != null) {
                String placeholder = PLACEHOLDER_PREFIX + placeholders.size() + "TOKEN";
                placeholders.put(placeholder, renderLatex(segment.formula, segment.displayMode, segment.original));
                output.append(placeholder);
                index = segment.endExclusive;
                lineStart = false;
                continue;
            }

            char current = markdown.charAt(index);
            output.append(current);
            lineStart = current == '\n';
            index++;
        }

        return output.toString();
    }

    private MathSegment tryMatchLatex(String markdown, int index) {
        if (isEscaped(markdown, index)) {
            return null;
        }

        if (markdown.startsWith("$$", index)) {
            int end = findClosingDoubleDollar(markdown, index + 2);
            if (end > index + 2) {
                return new MathSegment(markdown.substring(index + 2, end), true,
                        markdown.substring(index, end + 2), end + 2);
            }
            return null;
        }

        if (markdown.startsWith("\\[", index)) {
            int end = findClosingBracketFormula(markdown, index + 2, "\\]");
            if (end > index + 2) {
                return new MathSegment(markdown.substring(index + 2, end), true,
                        markdown.substring(index, end + 2), end + 2);
            }
            return null;
        }

        if (markdown.startsWith("\\(", index)) {
            int end = findClosingBracketFormula(markdown, index + 2, "\\)");
            if (end > index + 2) {
                return new MathSegment(markdown.substring(index + 2, end), false,
                        markdown.substring(index, end + 2), end + 2);
            }
            return null;
        }

        if (markdown.charAt(index) == '$') {
            if (index + 1 >= markdown.length() || markdown.charAt(index + 1) == '$') {
                return null;
            }
            if (Character.isWhitespace(markdown.charAt(index + 1))) {
                return null;
            }
            int end = findClosingSingleDollar(markdown, index + 1);
            if (end > index + 1) {
                return new MathSegment(markdown.substring(index + 1, end), false,
                        markdown.substring(index, end + 1), end + 1);
            }
        }
        return null;
    }

    private int findClosingDoubleDollar(String markdown, int start) {
        for (int i = start; i < markdown.length() - 1; i++) {
            if (markdown.charAt(i) == '$' && markdown.charAt(i + 1) == '$' && !isEscaped(markdown, i)) {
                return i;
            }
        }
        return -1;
    }

    private int findClosingBracketFormula(String markdown, int start, String closingTag) {
        for (int i = start; i < markdown.length() - 1; i++) {
            if (markdown.startsWith(closingTag, i) && !isEscaped(markdown, i)) {
                return i;
            }
        }
        return -1;
    }

    private int findClosingSingleDollar(String markdown, int start) {
        for (int i = start; i < markdown.length(); i++) {
            char current = markdown.charAt(i);
            if (current == '\n') {
                return -1;
            }
            if (current == '$' && !isEscaped(markdown, i)) {
                if (i > start && Character.isWhitespace(markdown.charAt(i - 1))) {
                    continue;
                }
                return i;
            }
        }
        return -1;
    }

    private String renderLatex(String formula, boolean displayMode, String originalLatex) {
        String candidate = formula == null ? "" : formula.trim();
        if (candidate.isEmpty()) {
            return escapeHtml(originalLatex);
        }
        boolean resetKatex = false;
        try {
            String rendered = getKatexForCurrentThread().renderToString(candidate, displayMode);
            if (oConvertUtils.isEmpty(rendered) || rendered.contains("katex-error")) {
                log.warn("KaTeX 预编译失败，回退原始 LaTeX: {}", safeShort(originalLatex));
                return escapeHtml(originalLatex);
            }
            return rendered;
        } catch (Exception e) {
            resetKatex = true;
            log.warn("KaTeX 预编译异常，回退原始 LaTeX: {}", safeShort(originalLatex));
            return escapeHtml(originalLatex);
        } finally {
            if (resetKatex) {
                destroyKatexForCurrentThread();
            }
        }
    }

    private String restorePlaceholders(String renderedHtml, Map<String, String> placeholders) {
        String restored = renderedHtml;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            restored = restored.replace(entry.getKey(), entry.getValue());
        }
        return restored;
    }

    private String fallbackRender(String markdown) {
        try {
            return sanitizeRenderedHtml(HTMLUtils.parseMarkdown(markdown));
        } catch (Exception e) {
            log.warn("Markdown 回退渲染失败，返回原始内容: {}", safeShort(e.getMessage()));
            return escapeHtml(markdown);
        }
    }

    /**
     * 先清洗 Markdown 渲染结果，再恢复可信的 KaTeX 占位符，避免破坏公式 HTML。
     */
    private String sanitizeRenderedHtml(String renderedHtml) {
        if (oConvertUtils.isEmpty(renderedHtml)) {
            return "";
        }
        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        String sanitized = Jsoup.clean(renderedHtml, "", MARKDOWN_HTML_WHITELIST, outputSettings);
        Document document = Jsoup.parseBodyFragment(sanitized);
        document.outputSettings().prettyPrint(false);
        sanitizeUrlAttributes(document);
        return document.body().html();
    }

    private void sanitizeUrlAttributes(Document document) {
        for (Element link : document.select("a[href]")) {
            if (!isSafeUrl(link.attr("href"), SAFE_HREF_PROTOCOLS)) {
                link.removeAttr("href");
            }
        }
        for (Element media : document.select("[src]")) {
            if (!isSafeUrl(media.attr("src"), SAFE_SRC_PROTOCOLS)) {
                media.removeAttr("src");
            }
        }
    }

    private boolean isSafeUrl(String url, Set<String> allowedProtocols) {
        if (oConvertUtils.isEmpty(url)) {
            return false;
        }
        String normalized = normalizeUrl(url);
        if (normalized.isEmpty()) {
            return false;
        }
        if (normalized.startsWith("#")
                || normalized.startsWith("/")
                || normalized.startsWith("./")
                || normalized.startsWith("../")
                || normalized.startsWith("?")) {
            return true;
        }

        int protocolSeparatorIndex = normalized.indexOf(':');
        if (protocolSeparatorIndex < 0) {
            return true;
        }
        if (normalized.startsWith("//")) {
            return false;
        }
        String protocol = normalized.substring(0, protocolSeparatorIndex).toLowerCase(Locale.ROOT);
        return allowedProtocols.contains(protocol);
    }

    private String normalizeUrl(String url) {
        String unescaped = HtmlUtils.htmlUnescape(url).trim();
        StringBuilder normalized = new StringBuilder(unescaped.length());
        for (int i = 0; i < unescaped.length(); i++) {
            char current = unescaped.charAt(i);
            if (!Character.isWhitespace(current) && !Character.isISOControl(current)) {
                normalized.append(current);
            }
        }
        return normalized.toString();
    }

    private String escapeHtml(String content) {
        return HtmlUtils.htmlEscape(content == null ? "" : content);
    }

    private String normalizeLineEndings(String markdown) {
        return markdown.replace("\r\n", "\n").replace('\r', '\n');
    }

    private int countRepeated(String text, int start, char target) {
        int count = 0;
        while (start + count < text.length() && text.charAt(start + count) == target) {
            count++;
        }
        return count;
    }

    private int appendCurrentLine(String text, int start, StringBuilder output) {
        int end = start;
        while (end < text.length() && text.charAt(end) != '\n') {
            end++;
        }
        if (end < text.length()) {
            end++;
        }
        output.append(text, start, end);
        return end;
    }

    private boolean isLineStart(String text, int index) {
        return index == 0 || (index <= text.length() && text.charAt(index - 1) == '\n');
    }

    private boolean isEscaped(String text, int index) {
        int slashCount = 0;
        for (int i = index - 1; i >= 0 && text.charAt(i) == '\\'; i--) {
            slashCount++;
        }
        return slashCount % 2 == 1;
    }

    private String safeShort(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 160 ? trimmed : trimmed.substring(0, 160);
    }

    private static Safelist buildMarkdownHtmlWhitelist() {
        return Safelist.none()
                .addTags(
                        "p", "br", "hr",
                        "h1", "h2", "h3", "h4", "h5", "h6",
                        "ul", "ol", "li",
                        "blockquote",
                        "strong", "em",
                        "code", "pre",
                        "a",
                        "table", "thead", "tbody", "tfoot", "tr", "th", "td",
                        "img"
                )
                .addAttributes("a", "href", "title")
                .addAttributes("img", "src", "alt", "title")
                .addAttributes("th", "colspan", "rowspan")
                .addAttributes("td", "colspan", "rowspan");
    }

    private static final class MathSegment {
        private final String formula;
        private final boolean displayMode;
        private final String original;
        private final int endExclusive;

        private MathSegment(String formula, boolean displayMode, String original, int endExclusive) {
            this.formula = formula;
            this.displayMode = displayMode;
            this.original = original;
            this.endExclusive = endExclusive;
        }
    }
}
