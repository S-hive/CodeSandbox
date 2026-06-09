package com.Utils;

import com.model.ExecuteMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * 进程工具类
 */
public class ProcessUtils {

    /**
     * JVM 冷启动 + SecurityManager 初始化需要时间，过早关闭 stdin 会导致 Scanner 读不到输入
     */
    private static final long STDIN_WRITE_DELAY_MS = 300L;

    /**
     * 执行进程并获取信息
     */
    public static ExecuteMessage runProcessAndGetMessage(Process runProcess, String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            StreamResult streamResult = drainStreams(runProcess);
            int exitValue = runProcess.waitFor();
            streamResult.await(5000);

            executeMessage.setExitValue(exitValue);
            executeMessage.setMessage(streamResult.stdout);
            if (exitValue != 0 && StringUtils.isNotBlank(streamResult.stderr)) {
                executeMessage.setErrorMessage(streamResult.stderr);
            } else if (containsExceptionTrace(streamResult.stderr)) {
                executeMessage.setErrorMessage(streamResult.stderr);
            }

            stopWatch.stop();
            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
            if (exitValue == 0) {
                System.out.println(opName + "成功");
            } else {
                System.out.println(opName + "失败，错误码： " + exitValue);
            }
        } catch (Exception e) {
            e.printStackTrace();
            executeMessage.setErrorMessage(e.getMessage());
        }
        return executeMessage;
    }

    /**
     * 执行交互式进程并获取信息（支持 Scanner 从 stdin 读入）
     */
    public static ExecuteMessage runInteractProcessAndGetMessage(Process runProcess, String opName, String args) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        Thread stdinThread = null;
        try {
            StreamResult streamResult = drainStreams(runProcess);
            stdinThread = startStdinWriter(runProcess.getOutputStream(), args);

            int exitValue = runProcess.waitFor();
            if (stdinThread != null) {
                stdinThread.join(3000);
            }
            streamResult.await(5000);

            executeMessage.setExitValue(exitValue);
            executeMessage.setMessage(streamResult.stdout);

            if (exitValue != 0 && StringUtils.isNotBlank(streamResult.stderr)) {
                executeMessage.setErrorMessage(streamResult.stderr);
            } else if (containsExceptionTrace(streamResult.stderr)) {
                // SandboxRunner 捕获异常后可能仍以 0 退出，但 stderr 有堆栈
                executeMessage.setErrorMessage(streamResult.stderr);
            } else if (StringUtils.isBlank(streamResult.stdout) && StringUtils.isNotBlank(streamResult.stderr)) {
                executeMessage.setErrorMessage(streamResult.stderr);
            }
        } catch (Exception e) {
            e.printStackTrace();
            executeMessage.setErrorMessage(e.getMessage());
        } finally {
            if (stdinThread != null && stdinThread.isAlive()) {
                stdinThread.interrupt();
            }
            closeQuietly(runProcess.getOutputStream());
            runProcess.destroy();
        }
        return executeMessage;
    }

    /**
     * 延迟写入 stdin，等待子进程 JVM 完成启动后再喂入测试数据
     */
    private static Thread startStdinWriter(OutputStream outputStream, String args) {
        if (args == null || args.isEmpty()) {
            return null;
        }
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(STDIN_WRITE_DELAY_MS);
                try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                    String[] parts = args.trim().split("\\s+");
                    writer.write(String.join("\n", parts) + "\n");
                    writer.flush();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // 子进程可能已结束
            }
        }, "sandbox-stdin-writer");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void closeQuietly(OutputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static boolean containsExceptionTrace(String stderr) {
        if (StringUtils.isBlank(stderr)) {
            return false;
        }
        return stderr.contains("Exception") || stderr.contains("Error") || stderr.contains("权限被禁止");
    }

    /**
     * 异步读取 stdout/stderr，避免管道死锁
     */
    private static StreamResult drainStreams(Process process) {
        StreamResult result = new StreamResult();

        Thread stdoutThread = new Thread(() -> result.stdout = readStream(process.getInputStream()),
                "sandbox-stdout-reader");
        Thread stderrThread = new Thread(() -> result.stderr = readStream(process.getErrorStream()),
                "sandbox-stderr-reader");
        stdoutThread.setDaemon(true);
        stderrThread.setDaemon(true);
        stdoutThread.start();
        stderrThread.start();

        result.stdoutThread = stdoutThread;
        result.stderrThread = stderrThread;
        return result;
    }

    private static String readStream(InputStream inputStream) {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
        } catch (IOException e) {
            // 进程结束后流可能关闭，忽略
        }
        return builder.toString();
    }

    private static class StreamResult {
        private String stdout = "";
        private String stderr = "";
        private Thread stdoutThread;
        private Thread stderrThread;

        void await(long timeoutMs) throws InterruptedException {
            if (stdoutThread != null) {
                stdoutThread.join(timeoutMs);
            }
            if (stderrThread != null) {
                stderrThread.join(timeoutMs);
            }
        }
    }
}
