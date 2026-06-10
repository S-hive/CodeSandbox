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

## 项目结构

```
code-sandbox/
├── src/main/java/com/
│   ├── controller/MainController.java      # HTTP 接口
│   ├── JavaCodeSandboxTemplate.java        # 沙箱模板方法
│   ├── Utils/JavaNativeCodeSandbox.java    # 原生进程沙箱（默认）
│   ├── JavaDockerCodeSandbox.java          # Docker 沙箱
│   ├── security/
│   │   ├── SandboxRunner.java              # 安全执行入口
│   │   └── DefaultSecurityManager.java     # JVM 权限控制
│   └── model/                              # 请求/响应模型
├── src/main/resources/
│   ├── application.yml
│   └── testCode/simpleComputeArgs/Main.java  # 示例用户代码
├── Dockerfile
└── pom.xml
```

## 快速开始

### 环境要求

- JDK 8+（需包含 `javac`）
- Maven 3.6+
- （可选）Docker，用于 `JavaDockerCodeSandbox`

### 本地运行

```bash
# 克隆项目
git clone https://github.com/<your-username>/code-sandbox.git
cd code-sandbox

# 编译并启动
mvn clean package -DskipTests
mvn spring-boot:run
```

服务启动后访问：`http://localhost:8090/health`，返回 `ok` 表示正常。

> **注意**：若本机 JDK 版本较高（如 JDK 17+），可能遇到 Lombok 编译兼容问题，建议使用 JDK 8 或在 Docker 中构建。

### Docker 运行

```bash
docker build -t code-sandbox .
docker run -p 8090:8090 code-sandbox
```

## API 文档

### 健康检查

```
GET /health
```

**响应示例：**

```text
ok
```

### 执行代码

```
POST /executeCode
Content-Type: application/json
```

**请求体：**

```json
{
  "code": "public class Main {\n    public static void main(String[] args) {\n        int a = Integer.parseInt(args[0]);\n        int b = Integer.parseInt(args[1]);\n        System.out.println(\"结果：\" + (a + b));\n    }\n}",
  "language": "java",
  "inputList": ["1 2", "3 4"]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | String | 用户 Java 源码，类名须为 `Main` |
| `language` | String | 语言标识（当前主要支持 `java`） |
| `inputList` | List\<String\> | 多组输入，每组为空格分隔的命令行参数 |

**响应体：**

```json
{
  "outputList": ["结果：3", "结果：7"],
  "message": null,
  "status": 1,
  "judgeInfo": {
    "message": null,
    "memory": null,
    "time": 128
  }
}
```

### 执行状态码

| status | 含义 |
|--------|------|
| `1` | 执行成功 |
| `2` | 沙箱内部错误 |
| `3` | 用户代码运行错误 |
| `4` | 编译错误 |

### cURL 示例

```bash
curl -X POST http://localhost:8090/executeCode \
  -H "Content-Type: application/json" \
  -d '{
    "code": "public class Main { public static void main(String[] args) { System.out.println(args[0]); } }",
    "language": "java",
    "inputList": ["hello"]
  }'
```

## 安全机制

用户代码通过独立子进程执行，默认经 `SandboxRunner` 安装 `DefaultSecurityManager`，限制：

- 禁止网络访问
- 禁止执行系统命令、创建子进程
- 禁止随意读写文件（仅允许用户代码目录与 JDK 目录只读）
- 禁止修改 SecurityManager、强制退出 JVM

相关配置（`application.yml`）：

```yaml
sandbox:
  security:
    enabled: true          # Java 21+ 不支持 SecurityManager，可设为 false
    classpath:             # fat jar 部署时需指定安全模块 classpath
```

云托管 Docker 部署时，`Dockerfile` 会将 `security-classes` 拷贝至 `/app/security-classes`，并通过 JVM 参数注入。

## 沙箱实现说明

### 原生进程沙箱（默认）

`MainController` 注入 `JavaNativeCodeSandbox`，流程：

1. 将用户代码写入 `tmpCode/<uuid>/Main.java`
2. 调用 `javac` 编译
3. 通过 `SandboxRunner`（或降级为直接 `Main`）运行
4. 收集 stdout/stderr 与耗时
5. 删除临时目录

### Docker 沙箱（可选）

`JavaDockerCodeSandbox` 将用户代码放入 `openjdk:8-alpine` 容器执行，隔离性更强，但要求宿主机可访问 Docker 守护进程，**不适合微信云托管等无 Docker 权限的环境**。

## 部署到微信云托管

1. 登录 [微信云托管控制台](https://cloud.weixin.qq.com/)
2. 开通云托管（环境需为按量付费）
3. 新建服务，**端口填写 `8090`**
4. 绑定 Git 仓库或上传代码（需包含 `Dockerfile`）
5. 新建版本 → 构建镜像 → 发布

小程序调用示例：

```javascript
const res = await wx.cloud.callContainer({
  path: '/executeCode',
  method: 'POST',
  header: {
    'X-WX-SERVICE': 'code-sandbox',
    'content-type': 'application/json'
  },
  data: {
    code: '...',
    language: 'java',
    inputList: ['1 2']
  }
})
```

## 配置说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | `8090` | HTTP 服务端口 |
| `sandbox.security.enabled` | `true` | 是否启用 SecurityManager |
| `sandbox.security.classpath` | 空 | 安全模块 classpath，fat jar 部署必填 |
| `SANDBOX_SECURITY_CLASSPATH` | - | 环境变量，同上 |

## 开发说明

- 用户代码类名固定为 `Main`，需包含 `public static void main(String[] args)`
- `inputList` 中每组输入以空格分隔，对应 `args` 数组
- 项目采用**模板方法模式**，扩展新沙箱只需继承 `JavaCodeSandboxTemplate` 并重写 `runFile` 等方法

## License

MIT
