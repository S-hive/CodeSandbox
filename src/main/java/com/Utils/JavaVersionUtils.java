package com.Utils;

/**
 * JDK 版本工具
 */
public class JavaVersionUtils {

    private JavaVersionUtils() {
    }

    /**
     * @return JDK 主版本号，如 Java 8 返回 8，Java 17 返回 17
     */
    public static int getMajorVersion() {
        String version = System.getProperty("java.version");
        if (version == null || version.isEmpty()) {
            return 8;
        }
        if (version.startsWith("1.")) {
            // Java 8 及更早：1.8.0_202
            int dot = version.indexOf('.', 2);
            String minor = dot > 2 ? version.substring(2, dot) : version.substring(2, 3);
            return Integer.parseInt(minor);
        }
        int dot = version.indexOf('.');
        int dash = version.indexOf('-');
        int end = dot > 0 ? dot : (dash > 0 ? dash : version.length());
        return Integer.parseInt(version.substring(0, end));
    }

    /**
     * Java 17+ 才需要 -Djava.security.manager=allow；Java 8 传该参数会导致 JVM 启动失败
     */
    public static boolean needSecurityManagerAllowFlag() {
        return getMajorVersion() >= 17;
    }
}
