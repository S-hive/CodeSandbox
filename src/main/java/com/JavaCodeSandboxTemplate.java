package com;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.Utils.JavaVersionUtils;
import com.Utils.ProcessExecutor;
import com.Utils.ProcessUtils;
import com.model.ExecuteCodeRequest;
import com.model.ExecuteCodeResponse;
import com.model.ExecuteMessage;
import com.model.JudgeInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class JavaCodeSandboxTemplate implements CodeSandbox {
    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    @Value("${sandbox.security.enabled:true}")
    private boolean securityManagerEnabled;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        File userCodeFile = saveCodeToFile(code);
        try {
            ExecuteMessage compileFileExecuteMessage = compileFile(userCodeFile);
            System.out.println("compileFileExecuteMessage = " + compileFileExecuteMessage);
            if (compileFileExecuteMessage.getExitValue() != 0) {
                return getCompileErrorResponse(compileFileExecuteMessage);
            }
            List<ExecuteMessage> executeMessageList = runFile(userCodeFile, inputList);
            return getOutputResponse(executeMessageList);
        } catch (Exception e) {
            log.error("代码沙箱执行异常", e);
            return getErrorResponse(e);
        } finally {
            boolean deleted = deleteFile(userCodeFile);
            if (!deleted) {
                log.error("删除文件失败, userCodeParentPath = {}", userCodeFile.getParentFile().getAbsolutePath());
            }
        }
    }

    public File saveCodeToFile(String code) {
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        return FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
    }

    public ExecuteMessage compileFile(File userCodeFile) {
        try {
            Process compileProcess = ProcessExecutor.startCommand(Arrays.asList(
                    ProcessExecutor.resolveJavacCommandFromRuntime(),
                    "-encoding", "utf-8", userCodeFile.getAbsolutePath()));
            return ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        String securityClasspath = getSecurityClasspath();
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        String classpath = userCodeParentPath + File.pathSeparator + securityClasspath;
        for (String inputArgs : inputList) {
            try {
                StopWatch stopWatch = new StopWatch();
                stopWatch.start();
                Process runProcess = startUserCodeProcess(userCodeParentPath, classpath, inputArgs);
                ExecuteMessage executeMessage = ProcessUtils.runInteractProcessAndGetMessage(runProcess, "运行", inputArgs);
                executeMessageList.add(executeMessage);
                stopWatch.stop();
                executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
                log.info("运行完成 exit={} stdout={} stderr={}",
                        executeMessage.getExitValue(),
                        executeMessage.getMessage(),
                        executeMessage.getErrorMessage());
            } catch (Exception e) {
                log.error("运行失败 inputArgs={}", inputArgs, e);
                ExecuteMessage errorMessage = new ExecuteMessage();
                errorMessage.setErrorMessage(e.getMessage());
                executeMessageList.add(errorMessage);
            }
        }
        return executeMessageList;
    }

    /**
     * 获取包含 SandboxRunner / DefaultSecurityManager 的 classpath（当前应用 jar 或 classes 目录）
     */
    /**
     * 启动用户代码进程：优先 SandboxRunner+SecurityManager，不支持时降级直接运行 Main
     */
    private Process startUserCodeProcess(String sandboxDir, String classpath, String inputArgs) throws IOException {
        List<String> jvmArgs = new ArrayList<>();
        jvmArgs.add("-Dfile.encoding=UTF-8");
        if (securityManagerEnabled) {
            if (JavaVersionUtils.needSecurityManagerAllowFlag()) {
                jvmArgs.add("-Djava.security.manager=allow");
            }
            jvmArgs.add("-Dsandbox.dir=" + sandboxDir);
            return ProcessExecutor.startJava(jvmArgs, classpath, "com.security.SandboxRunner", inputArgs);
        }
        return ProcessExecutor.startJava(jvmArgs, classpath, "Main", inputArgs);
    }

    private String getSecurityClasspath() {
        try {
            URI uri = this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            return new File(uri).getAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException("解析安全模块 classpath 失败", e);
        }
    }

    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        Long maxTime = 0L;
        boolean hasError = false;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            Integer exitValue = executeMessage.getExitValue();
            if (StrUtil.isNotBlank(errorMessage)
                    || (exitValue != null && exitValue != 0)
                    || StrUtil.isBlank(executeMessage.getMessage())) {
                String msg = StrUtil.isNotBlank(errorMessage)
                        ? errorMessage
                        : (exitValue != null && exitValue != 0
                        ? "进程异常退出，exitCode=" + exitValue
                        : "程序无输出");
                executeCodeResponse.setMessage(msg);
                executeCodeResponse.setStatus(3);
                hasError = true;
                break;
            }
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(time, maxTime);
            }
        }
        if (!hasError && outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    public boolean deleteFile(File userCodeFile) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }

    private ExecuteCodeResponse getCompileErrorResponse(ExecuteMessage compileMessage) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(compileMessage.getErrorMessage());
        executeCodeResponse.setStatus(4);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}
