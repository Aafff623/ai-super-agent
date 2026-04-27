# 第7期：MCP 模型上下文协议（AI 的"USB 接口")

## 一、简单总结

**MCP（Model Context Protocol）**：一种开放标准，让 AI 应用能通过统一的方式调用外部服务（工具、资源、提示词）。类比：AI 的"USB 接口"。

**为什么需要**：之前工具都是自己从头开发，如果别人写好了高德地图服务，我们希望直接拿来用。MCP 定义了标准，形成服务市场，开发者可以共享和复用。

**核心架构**：客户端（你的 AI 应用） + 服务端（提供工具的服务），通过 `stdio`（本地进程）或 `SSE`（HTTP 远程）通信。

**三种使用方式**：
1. 云平台（如阿里云百炼）直接添加 MCP 服务
2. 软件客户端（如 Cursor、Cherry Studio）配置 MCP
3. 程序中使用 Spring AI 的 MCP 客户端依赖，加载 MCP 服务提供的工具

---

## 二、需求 → 技术 → 为什么

| 需求 | 技术/配置 | 为什么 |
|------|----------|--------|
| 让 AI 调用高德地图查约会地点 | 阿里云百炼添加"高德地图 MCP 服务" | 不用自己写地图 API，直接用现成标准服务 |
| 在 Cursor 中使用 MCP | 配置 `mcp.json`，指定命令和参数 | 本地运行 MCP 进程，Cursor 自动识别提供给 AI |
| Spring Boot 程序调用 MCP | `spring-ai-mcp-client-spring-boot-starter` + `mcp-servers.json` + `ToolCallbackProvider` | 框架自动启动子进程，把工具暴露给 ChatClient |
| 开发 MCP 服务端 | `spring-ai-mcp-server-webmvc-spring-boot-starter` + `@Tool` + `ToolCallbackProvider` | 几行代码把 Java 方法变成 MCP 工具，支持 stdio/SSE |
| stdio 模式传 API Key | `mcp-servers.json` 的 `env` 字段 | 敏感信息不进代码，通过环境变量传给子进程 |
| MCP 服务支持远程调用（SSE） | `spring.ai.mcp.server.stdio=false` + SSE endpoint | 服务可独立运行，多客户端共享 |
| MCP 部署到云端 | 阿里云百炼 MCP 市场 + 函数计算 | 免运维，按量付费 |

---

## 三、代码及配置详细注释

### 3.1 MCP 客户端依赖

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-client-spring-boot-starter</artifactId>
</dependency>
```

- **作用**：让 Spring Boot 项目具备 MCP 客户端能力
- **还有**：`spring-ai-mcp-client-webflux-spring-boot-starter` 支持响应式异步调用

### 3.2 MCP 客户端配置 `mcp-servers.json`

```json
{
  "mcpServers": {
    "amap-maps": {
      "command": "npx",
      "args": ["-y", "@amap/amap-maps-mcp-server"],
      "env": {
        "AMAP_MAPS_API_KEY": "改成你的 API Key"
      }
    }
  }
}
```

- `amap-maps`：MCP 服务名称，可自定义
- `command`：启动服务的命令，`npx` 直接运行别人写好的包
- `args`：`-y` 表示自动确认安装
- `env`：环境变量，服务内部通过 `process.env.AMAP_MAPS_API_KEY` 读取
- **Windows 注意**：命令可能需要写成 `"command": "npx.cmd"`，否则报"找不到命令"

### 3.3 Spring 配置引用 MCP

```yaml
spring:
  ai:
    mcp:
      client:
        stdio:
          servers-configuration: classpath:mcp-servers.json
```

- Spring Boot 启动时自动为每个 MCP 服务启动子进程，建立 stdio 通信

### 3.4 在 LoveApp 中使用 MCP 工具

```java
@Resource
private ToolCallbackProvider toolCallbackProvider;

public String doChatWithMcp(String message, String chatId) {
    ChatResponse response = chatClient
        .prompt()
        .user(message)
        .advisors(...)
        .tools(toolCallbackProvider)   // 关键：把 MCP 提供的工具全部挂上
        .call()
        .chatResponse();
    return content;
}
```

- `ToolCallbackProvider`：Spring AI 自动把每个 MCP 服务暴露的工具包装成 `ToolCallback`
- `toolCallbackProvider.getToolCallbacks()` 返回所有 MCP 工具数组

### 3.5 MCP 服务端依赖

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-server-webmvc-spring-boot-starter</artifactId>
</dependency>
```

**三种 flavor**：
- `spring-ai-mcp-server-spring-boot-starter`：仅 stdio（无 Web）
- `*-webmvc-*`：stdio + SSE（基于 Spring MVC）← 一般选这个
- `*-webflux-*`：响应式 SSE

### 3.6 MCP 服务端配置 — stdio 模式

```yaml
# application-stdio.yml
spring:
  ai:
    mcp:
      server:
        name: yu-image-search-mcp-server
        version: 0.0.1
        type: SYNC
        stdio: true
  main:
    web-application-type: none   # 关闭 Web 服务器
    banner-mode: off
```

- `stdio: true`：通过标准输入输出通信，不启动 HTTP 端口
- `web-application-type: none`：不需要 Web，关掉节省资源避免端口冲突

### 3.7 MCP 服务端配置 — SSE 模式

```yaml
# application-sse.yml
spring:
  ai:
    mcp:
      server:
        name: yu-image-search-mcp-server
        version: 0.0.1
        type: SYNC
        stdio: false      # 关闭 stdio
```

- SSE 自动暴露两个端点：`/sse`（建立连接）和 `/mcp/message`（消息收发）

### 3.8 主配置文件切换 profile

```yaml
spring:
  application:
    name: yu-image-search-mcp-server
  profiles:
    active: stdio      # 改成 sse 即可切换模式
server:
  port: 8127
```

- 同一套代码，切换 profile 就能发布成 stdio 或 SSE

### 3.9 MCP 服务端 — 图片搜索工具类

```java
@Service
public class ImageSearchTool {
    private static final String API_KEY = "你的 API Key";
    private static final String API_URL = "https://api.pexels.com/v1/search";

    @Tool(description = "search image from web")
    public String searchImage(@ToolParam(description = "Search query keyword") String query) {
        try {
            return String.join(",", searchMediumImages(query));
        } catch (Exception e) {
            return "Error search image: " + e.getMessage();
        }
    }

    public List<String> searchMediumImages(String query) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", API_KEY);
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);

        String response = HttpUtil.createGet(API_URL)
                .addHeaders(headers)
                .form(params)
                .execute()
                .body();

        return JSONUtil.parseObj(response)
                .getJSONArray("photos")
                .stream()
                .map(obj -> (JSONObject) obj)
                .map(photoObj -> photoObj.getJSONObject("src"))
                .map(src -> src.getStr("medium"))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }
}
```

- `@Tool` 注解跟普通工具调用完全一样，MCP 服务端自动扫描并暴露
- 返回逗号拼接的图片 URL 字符串（MCP 要求返回文本）
- 捕获异常避免进程崩溃

### 3.10 MCP 服务端 — 注册工具

```java
@SpringBootApplication
public class YuImageSearchMcpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(YuImageSearchMcpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider imageSearchTools(ImageSearchTool imageSearchTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(imageSearchTool)
                .build();
    }
}
```

- `MethodToolCallbackProvider`：将 `@Tool` 注解对象转成 `ToolCallback`
- 需要显式提供 `ToolCallbackProvider` Bean，告诉 MCP 服务端哪些 Bean 要暴露给客户端

### 3.11 打包 MCP 服务端

```bash
mvn package
```

生成 `yu-image-search-mcp-server-0.0.1-SNAPSHOT.jar`

### 3.12 客户端 stdio 模式调用自己的 MCP 服务

```json
{
  "mcpServers": {
    "yu-image-search-mcp-server": {
      "command": "java",
      "args": [
        "-Dspring.ai.mcp.server.stdio=true",
        "-Dspring.main.web-application-type=none",
        "-Dlogging.pattern.console=",
        "-jar",
        "yu-image-search-mcp-server/target/yu-image-search-mcp-server-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

- 用 `java` 而不是 `npx`，因为 MCP 服务是 Java 写的
- `-Dxxx` VM 参数可在启动时覆盖配置文件属性
- `-jar` 路径相对于客户端项目根目录

### 3.13 客户端 SSE 模式调用远程 MCP 服务

```yaml
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            server1:
              url: http://localhost:8127
```

- SSE 模式下 `mcp-servers.json` 不需要了，直接写 URL
- 前提是 MCP 服务已以 SSE 模式启动

### 3.14 环境变量传递参数

```json
"env": {
  "AMAP_MAPS_API_KEY": "你的key"
}
```

服务端读取：`String apiKey = System.getenv("AMAP_MAPS_API_KEY");`

> **重要**：stdio 模式下千万不要用 `System.out.println` 打印变量值，会干扰 MCP 的 JSON-RPC 通信！

---

## 四、最佳实践与安全

### 最佳实践
- **慎用 MCP**：优先普通工具，MCP 增加额外进程开销
- **跨平台兼容**：Windows 下命令要加 `.cmd`（如 `npx.cmd`）
- **异常处理**：工具内部捕获异常，避免进程崩溃
- **超时控制**：SSE 模式注意设置合理超时

### 安全问题
- MCP 并非绝对安全，恶意 MCP 可能窃取数据或控制 AI
- 要用沙箱、信任来源、检查参数
- 环境变量传敏感信息，不要硬编码在代码里