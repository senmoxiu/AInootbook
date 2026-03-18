package org.jeecg.modules.ainote.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MarkdownPrecompileService 单元测试")
class MarkdownPrecompileServiceTest {

    private final MarkdownPrecompileService service = new MarkdownPrecompileService();

    @Test
    @DisplayName("precompile: 应将 Markdown 中的 LaTeX 公式预编译为 KaTeX HTML")
    void precompile_shouldRenderLatexIntoKatexHtml() {
        String markdown = "# 标题\n\n行内公式 $a^2+b^2=c^2$。\n\n$$\\int_0^1 x^2 dx$$";

        String rendered = service.precompile(markdown);

        assertThat(rendered).contains("<h1>标题</h1>");
        assertThat(rendered).contains("katex-mathml");
        assertThat(rendered).contains("katex-display");
        assertThat(rendered).doesNotContain("$a^2+b^2=c^2$");
    }

    @Test
    @DisplayName("precompile: KaTeX 预编译失败时应回退原始 LaTeX")
    void precompile_shouldFallbackToOriginalLatex_whenKatexRenderingFails() {
        String markdown = "错误公式 $\\frac{1}{$";

        String rendered = service.precompile(markdown);

        assertThat(rendered).contains("\\frac{1}{");
        assertThat(rendered).doesNotContain("katex-error");
    }

    @Test
    @DisplayName("precompile: 应清洗危险 HTML、事件属性和不安全 URL")
    void precompile_shouldSanitizeUnsafeHtmlAndUrls() {
        String markdown = """
                安全链接 [OpenAI](https://openai.com)

                <script>alert(1)</script>
                <a href="javascript:alert(1)" onclick="alert(1)">危险链接</a>
                <img src="javascript:alert(1)" onerror="alert(1)" alt="bad">
                """;

        String rendered = service.precompile(markdown);

        assertThat(rendered).contains("<a href=\"https://openai.com\">OpenAI</a>");
        assertThat(rendered).doesNotContain("<script");
        assertThat(rendered).doesNotContain("onclick");
        assertThat(rendered).doesNotContain("onerror");
        assertThat(rendered).doesNotContain("javascript:alert(1)");
    }

    @Test
    @DisplayName("precompile: 应保留代码块和行内代码内容")
    void precompile_shouldPreserveCodeBlocksAndInlineCode() {
        String markdown = """
                行内代码 `<script>alert(1)</script>`

                ```html
                <img src=x onerror=alert(1)>
                ```
                """;

        String rendered = service.precompile(markdown);

        assertThat(rendered).contains("<code>&lt;script&gt;alert(1)&lt;/script&gt;</code>");
        assertThat(rendered).contains("<pre><code");
        assertThat(rendered).contains("&lt;img src=x onerror=alert(1)&gt;");
    }

    @Test
    @DisplayName("precompile: 同线程应复用 KaTeX 实例")
    void precompile_shouldReuseKatexWithinSameThread() throws Exception {
        service.precompile("$a+b$");
        int firstIdentity = currentKatexIdentity();

        service.precompile("$c+d$");
        int secondIdentity = currentKatexIdentity();

        assertThat(secondIdentity).isEqualTo(firstIdentity);
    }

    @Test
    @DisplayName("precompile: 不同线程应隔离 KaTeX 实例")
    void precompile_shouldIsolateKatexAcrossThreads() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Callable<Integer> task = () -> {
                service.precompile("$x+y$");
                barrier.await(5, TimeUnit.SECONDS);
                return currentKatexIdentity();
            };

            Future<Integer> first = executor.submit(task);
            Future<Integer> second = executor.submit(task);

            assertThat(first.get(5, TimeUnit.SECONDS)).isNotEqualTo(second.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("precompile: 清理当前线程实例后应重新创建 KaTeX")
    void precompile_shouldRecreateKatexAfterCleanup() throws Exception {
        service.precompile("$a$");
        int firstIdentity = currentKatexIdentity();

        invokeCurrentThreadCleanup();
        int secondIdentity = currentKatexIdentity();

        assertThat(secondIdentity).isNotEqualTo(firstIdentity);
    }

    private int currentKatexIdentity() throws Exception {
        return System.identityHashCode(katexThreadLocal().get());
    }

    @SuppressWarnings("unchecked")
    private ThreadLocal<Object> katexThreadLocal() throws Exception {
        Field field = MarkdownPrecompileService.class.getDeclaredField("katexHolder");
        field.setAccessible(true);
        return (ThreadLocal<Object>) field.get(service);
    }

    private void invokeCurrentThreadCleanup() throws Exception {
        Method method = MarkdownPrecompileService.class.getDeclaredMethod("destroyKatexForCurrentThread");
        method.setAccessible(true);
        method.invoke(service);
    }
}
