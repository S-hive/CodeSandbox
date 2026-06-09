package com.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 跨平台进程启动（避免 Windows 下 Runtime.exec(String) 拆坏 classpath）
 */
public class ProcessExecutor {

    public static Process startJava(List<String> jvmArgs, String classpath, String mainClass, String programArgs)
            throws IOException {
        List<String> command = new ArrayList<>();
        command.add(resolveJavaCommandFromRuntime());
        if (jvmArgs != null) {
            command.addAll(jvmArgs);
        }
        command.add("-cp");
        command.add(classpath);
        command.add(mainClass);
        if (programArgs != null && !programArgs.isEmpty()) {
            command.addAll(Arrays.asList(programArgs.trim().split("\\s+")));
        }
        return new ProcessBuilder(command)
                .directory(new File(System.getProperty("user.dir")))
                .start();
    }

    public static Process startCommand(List<String> command) throws IOException {
        return new ProcessBuilder(command)
                .directory(new File(System.getProperty("user.dir")))
                .start();
    }

    public static String resolveJavaCommandFromRuntime() {
        return new File(resolveJdkBinDir(), isWindows() ? "java.exe" : "java").getAbsolutePath();
    }

    public static String resolveJavacCommandFromRuntime() {
        return new File(resolveJdkBinDir(), isWindows() ? "javac.exe" : "javac").getAbsolutePath();
    }

    /**
     * 使用当前沙箱进程所在的 JDK，避免 javac 与 java 版本不一致
     */
    private static File resolveJdkBinDir() {
        File javaHome = new File(System.getProperty("java.home"));
        File javacInHome = new File(javaHome, "bin" + File.separator + (isWindows() ? "javac.exe" : "javac"));
        if (javacInHome.exists()) {
            return new File(javaHome, "bin");
        }
        File parentBin = new File(javaHome.getParentFile(), "bin");
        if (new File(parentBin, isWindows() ? "javac.exe" : "javac").exists()) {
            return parentBin;
        }
        String envJavaHome = System.getenv("JAVA_HOME");
        if (envJavaHome != null && !envJavaHome.isEmpty()) {
            File envBin = new File(envJavaHome, "bin");
            if (new File(envBin, isWindows() ? "java.exe" : "java").exists()) {
                return envBin;
            }
        }
        return new File(javaHome, "bin");
    }

    private static boolean isWindows() {
        return File.separatorChar == '\\';
    }
}
