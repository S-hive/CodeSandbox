# Code Sandbox

基于 Spring Boot 的 Java 在线代码沙箱服务，用于远程编译并执行用户提交的 Java 代码，适用于 OJ（在线判题）、编程教育、AI 代码执行等场景。

## 功能特性

- **远程代码执行**：通过 HTTP API 接收 Java 源码，自动编译并运行
- **多组测试用例**：支持一次请求传入多组 `inputList`，逐组执行并返回输出
- **JVM 安全隔离**：通过 `SecurityManager` 限制用户代码的文件、网络、进程等敏感操作
- **模板方法架构**：`JavaCodeSandboxTemplate` 统一处理保存、编译、运行、清理流程，便于扩展不同隔离方案
- **多种沙箱实现**：
  - `JavaNativeCodeSandbox`：原生进程执行（当前默认）
  - `JavaDockerCodeSandbox`：Docker 容器隔离（需本地 Docker 环境）
- **容器化部署**：提供 `Dockerfile`，支持 Docker / 微信云托管一键部署

## 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Spring Boot 2.7.6 |
| 语言 | Java 8 |
| 工具库 | Hutool、Lombok |
| 容器 | Docker Java API 3.3.6 |
| 服务端口 | `8090` |

## 快速开始

### 环境要求

- JDK 8+（需包含 `javac`）
- Maven 3.6+
- （可选）Docker，用于 `JavaDockerCodeSandbox`

## 开发说明

- 用户代码类名固定为 `Main`，需包含 `public static void main(String[] args)`
- `inputList` 中每组输入以空格分隔，对应 `args` 数组
- 项目采用**模板方法模式**，扩展新沙箱只需继承 `JavaCodeSandboxTemplate` 并重写 `runFile` 等方法
