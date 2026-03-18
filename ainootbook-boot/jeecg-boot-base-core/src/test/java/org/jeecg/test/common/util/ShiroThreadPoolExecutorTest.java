package org.jeecg.test.common.util;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.util.ThreadContext;
import org.jeecg.common.util.ShiroThreadPoolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiroThreadPoolExecutorTest {

    @AfterEach
    void clearThreadContext() {
        ThreadContext.remove();
    }

    @Test
    void shouldExecuteCommandWithoutShiroContext() throws Exception {
        ShiroThreadPoolExecutor executor = newExecutor();
        try {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean executed = new AtomicBoolean(false);

            assertDoesNotThrow(() -> executor.execute(() -> {
                executed.set(true);
                latch.countDown();
            }));

            assertTrue(latch.await(5, TimeUnit.SECONDS), "任务未在预期时间内执行");
            assertTrue(executed.get(), "任务未被执行");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldPropagateBoundSubject() throws Exception {
        DefaultSecurityManager securityManager = new DefaultSecurityManager();
        Subject subject = new Subject.Builder(securityManager)
                .principals(new SimplePrincipalCollection("tester", "testRealm"))
                .authenticated(true)
                .buildSubject();
        ThreadContext.bind(securityManager);
        ThreadContext.bind(subject);

        ShiroThreadPoolExecutor executor = newExecutor();
        try {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Object> principalRef = new AtomicReference<>();

            executor.execute(() -> {
                principalRef.set(SecurityUtils.getSubject().getPrincipal());
                latch.countDown();
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS), "任务未在预期时间内执行");
            assertEquals("tester", principalRef.get(), "Subject 未正确透传到工作线程");
        } finally {
            executor.shutdownNow();
        }
    }

    private ShiroThreadPoolExecutor newExecutor() {
        return new ShiroThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }
}
