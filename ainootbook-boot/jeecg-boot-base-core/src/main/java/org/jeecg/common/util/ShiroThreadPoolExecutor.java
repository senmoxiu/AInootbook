package org.jeecg.common.util;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;

import java.util.concurrent.*;

/**
 * @date 2025-09-04
 * @author scott
 * 
 * @Description: 支持shiro的API，获取当前登录人方法的线程池
 */
public class ShiroThreadPoolExecutor extends ThreadPoolExecutor {

    public ShiroThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    @Override
    public void execute(Runnable command) {
        Subject subject = resolveSubject();
        SecurityManager securityManager = resolveSecurityManager();
        if (subject == null && securityManager == null) {
            super.execute(command);
            return;
        }
        super.execute(() -> {
            boolean subjectBound = false;
            boolean securityManagerBound = false;
            try {
                if (securityManager != null) {
                    ThreadContext.bind(securityManager);
                    securityManagerBound = true;
                }
                if (subject != null) {
                    ThreadContext.bind(subject);
                    subjectBound = true;
                }
                command.run();
            } finally {
                if (subjectBound) {
                    ThreadContext.unbindSubject();
                }
                if (securityManagerBound) {
                    ThreadContext.unbindSecurityManager();
                }
            }
        });
    }

    private Subject resolveSubject() {
        Subject subject = ThreadContext.getSubject();
        if (subject != null) {
            return subject;
        }
        try {
            return SecurityUtils.getSubject();
        } catch (UnavailableSecurityManagerException ex) {
            return null;
        }
    }

    private SecurityManager resolveSecurityManager() {
        SecurityManager securityManager = ThreadContext.getSecurityManager();
        if (securityManager != null) {
            return securityManager;
        }
        try {
            return SecurityUtils.getSecurityManager();
        } catch (UnavailableSecurityManagerException ex) {
            return null;
        }
    }
}
