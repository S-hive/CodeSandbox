package com.security;

import java.lang.reflect.Method;

/**
 * 沙箱固定入口：由沙箱服务启动，安装 SecurityManager 后再反射调用用户 Main。
 */
public class SandboxRunner {

    private static final String USER_MAIN_CLASS = "Main";

    public static void main(String[] args) {
        String sandboxDir = System.getProperty("sandbox.dir");
        if (sandboxDir == null || sandboxDir.isEmpty()) {
            System.err.println("缺少 sandbox.dir 系统属性");
            System.exit(1);
        }
        try {
            System.setSecurityManager(new DefaultSecurityManager(sandboxDir));
        } catch (UnsupportedOperationException e) {
            System.err.println("WARN: 当前 JDK 不支持 SecurityManager，降级为无 JVM 隔离运行");
        }
        invokeUserMain(args);
    }

    private static void invokeUserMain(String[] args) {
        try {
            Class<?> mainClass = Class.forName(USER_MAIN_CLASS);
            Method mainMethod = mainClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) args);
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            throw new RuntimeException(t);
        }
    }
}
