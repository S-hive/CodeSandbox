package com.security;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FilePermission;
import java.io.IOException;
import java.net.NetPermission;
import java.net.SocketPermission;
import java.security.Permission;
import java.lang.reflect.ReflectPermission;
import java.util.PropertyPermission;

/**
 * OJ 用户代码 JVM 级权限隔离：限制文件、网络、系统命令等敏感操作。
 */
public class DefaultSecurityManager extends SecurityManager {

    private final String allowedCodeDirPrefix;
    private final String javaHomePrefix;

    public DefaultSecurityManager(String allowedCodeDir) {
        try {
            this.allowedCodeDirPrefix = toDirPrefix(allowedCodeDir);
            String javaHome = System.getProperty("java.home");
            this.javaHomePrefix = toDirPrefix(javaHome);
        } catch (IOException e) {
            throw new RuntimeException("初始化安全管理器失败", e);
        }
    }

    private static String toDirPrefix(String dir) throws IOException {
        return new File(dir).getCanonicalPath() + File.separatorChar;
    }

    @Override
    public void checkPermission(Permission perm) {
        checkPermission(perm, null);
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
        if (perm == null) {
            return;
        }

        if (perm instanceof FilePermission) {
            checkFilePermission((FilePermission) perm);
            return;
        }

        if (perm instanceof SocketPermission || perm instanceof NetPermission) {
            throw new SecurityException("禁止网络访问：" + perm);
        }

        if (perm instanceof RuntimePermission) {
            checkRuntimePermission((RuntimePermission) perm);
            return;
        }

        if (perm instanceof PropertyPermission) {
            checkPropertyPermission((PropertyPermission) perm);
            return;
        }

        if (perm instanceof ReflectPermission) {
            // Scanner / Locale / ServiceLoader 等 JDK 内部组件需要
            return;
        }

        throw new SecurityException("权限被禁止：" + perm);
    }

    private void checkFilePermission(FilePermission fp) {
        String path = fp.getName();
        String actions = fp.getActions();

        if (path.contains("<<ALL FILES>>")) {
            throw new SecurityException("禁止访问所有文件：" + fp);
        }

        if (actions.contains("write") || actions.contains("delete") || actions.contains("execute")) {
            throw new SecurityException("文件写/删/执行被禁止：" + fp);
        }

        if (actions.contains("read") && isUnderAllowedReadPath(path)) {
            return;
        }

        throw new SecurityException("文件读被禁止：" + fp);
    }

    private boolean isUnderAllowedReadPath(String path) {
        try {
            String normalizedPath = path;
            if (path.endsWith("/-") || path.endsWith("\\-") || path.endsWith("/*") || path.endsWith("\\*")) {
                normalizedPath = path.substring(0, path.length() - 2);
            }
            String canonical = new File(normalizedPath).getCanonicalPath() + File.separatorChar;
            return canonical.startsWith(allowedCodeDirPrefix) || canonical.startsWith(javaHomePrefix);
        } catch (IOException e) {
            return false;
        }
    }

    private void checkRuntimePermission(RuntimePermission perm) {
        String name = perm.getName();
        if ("setSecurityManager".equals(name) || "setSecurityManager.".equals(name)) {
            throw new SecurityException("禁止修改安全管理器");
        }
        if ("exitVM".equals(name) || name.startsWith("exitVM.")) {
            throw new SecurityException("禁止退出 JVM");
        }
        if ("createProcessBuilder".equals(name)) {
            throw new SecurityException("禁止创建进程");
        }
        // 其余 JVM 运行所需 RuntimePermission 放行（如 createClassLoader、反射等）
    }

    private void checkPropertyPermission(PropertyPermission perm) {
        if ("read".equals(perm.getActions())) {
            // Scanner / Locale 等组件会读取多种系统属性，仅禁止写
            return;
        }
        throw new SecurityException("禁止写系统属性：" + perm);
    }

    @Override
    public void checkWrite(FileDescriptor fd) {
        if (fd == FileDescriptor.out || fd == FileDescriptor.err) {
            return;
        }
        throw new SecurityException("禁止写入该文件描述符");
    }

    @Override
    public void checkConnect(String host, int port) {
        throw new SecurityException("禁止网络连接：" + host + ":" + port);
    }

    @Override
    public void checkExec(String cmd) {
        throw new SecurityException("禁止执行系统命令");
    }

}
