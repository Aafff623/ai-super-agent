# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands / 构建与运行命令

```bash
# Run main application / 运行主应用
mvn spring-boot:run

# Run MCP server (separate Spring Boot app) / 运行 MCP 服务器（独立 Spring Boot 应用）
cd yu-image-search-mcp-server && mvn spring-boot:run

# Run all tests / 运行所有测试
mvn test

# Run a single test class / 运行单个测试类
mvn test -Dtest=BaseAgentTest
mvn test -Dtest=ToolCallAgentTest
mvn test -Dtest=QueryRewriterTest

# Compile only (fast check) / 仅编译（快速检查）
mvn compile -q

# Run frontend dev server / 运行前端开发服务器
cd yu-ai-agent-frontend && npm run dev
```

**Environment setup / 环境配置**: Edit `src/main/resources/application.yml` — fill in `dashscope.api-key` and `search-api.api-key`. PgVector and MCP client configs are commented out for easier local dev; uncomment when those services are running.

> 编辑 `src/main/resources/application.yml`，填写 `dashscope.api-key` 和 `search-api.api-key`。PgVector 和 MCP 客户端配置默认注释掉，方便本地开发；使用时取消注释即可。

## Architecture / 架构

This project has **two AI paradigms** coexisting / 本项目包含**两种并存的 AI 范式**:

1. **ChatClient + Advisor pattern / ChatClient + Advisor 模式** (LoveApp) — request-response, simpler, memory-managed by Spring AI / 请求-响应式，更简单，内存由 Spring AI 管理
2. **Autonomous Agent pattern / 自主智能体模式** (YuManus) — multi-step ReAct loop with tool calling, self-managed context / 多步 ReAct 循环加工具调用，自行管理上下文

### Agent Inheritance Chain / 智能体继承链

```
BaseAgent (abstract / 抽象基类)     — state machine (IDLE→RUNNING→FINISHED/ERROR), step loop, SSE streaming / 状态机，步骤循环，SSE 流式输出
  └─ ReActAgent (abstract / 抽象)   — think()/act() cycle / 思考-行动循环
      └─ ToolCallAgent              — tool calling with manual context management / 工具调用，手动上下文管理
          └─ YuManus (@Component)   — concrete agent, 20 max steps, uses all 7 tools / 具体智能体，最大 20 步，使用全部 7 个工具
```

Key design choice / 关键设计决策: `ToolCallAgent` disables Spring AI's built-in tool execution (`internalToolExecutionEnabled=false`) and manages the message context and tool calling loop itself for full control over the think-act cycle.

> `ToolCallAgent` 禁用了 Spring AI 内置的工具执行机制，自行管理消息上下文和工具调用循环，以完全控制思考-行动周期。

**Per-request instantiation / 每次请求新建实例**: `AiController` creates a new `YuManus` per `/manus/chat` request — no session persistence across requests.

> `AiController` 每次收到 `/manus/chat` 请求时创建新的 `YuManus` 实例，请求间无会话持久化。

### Package Structure / 包结构

```
com.yupi.yuaiagent
  ├── agent/          # Agent framework (BaseAgent → ReActAgent → ToolCallAgent → YuManus) / 智能体框架
  ├── agent/model/    # AgentState enum / 智能体状态枚举
  ├── advisor/        # Spring AI Advisors (MyLoggerAdvisor, ReReadingAdvisor) / Spring AI 增强器
  ├── app/            # LoveApp — ChatClient-based relationship advisor / 恋爱大师应用
  ├── chatmemory/     # FileBasedChatMemory (JSON files in ./chat-memory/) / 文件聊天记忆
  ├── config/         # CorsConfig, MCP client config / 跨域与 MCP 客户端配置
  ├── constant/       # FileConstant / 文件常量
  ├── controller/     # AiController, HealthController / 控制器
  ├── demo/invoke/    # Demo invocations (HTTP, SDK, Spring AI, LangChain4j, Ollama) / 调用示例
  ├── demo/rag/       # RAG demo (MultiQueryExpanderDemo) / RAG 示例
  ├── rag/            # RAG pipeline (document loader, vector store configs, query rewriter, enrichers) / RAG 管道
  └── tools/          # 7 agent tools + ToolRegistration / 7 个智能体工具 + 工具注册
```

### Tool Registration / 工具注册

All tools use `@Tool` + `@ToolParam` annotations. `ToolRegistration` (`@Configuration`) creates a single `allTools` bean via `ToolCallbacks.from()`, collecting all 7 tool instances / 所有工具使用 `@Tool` + `@ToolParam` 注解。`ToolRegistration`（`@Configuration`）通过 `ToolCallbacks.from()` 创建 `allTools` Bean，收集全部 7 个工具实例:

| Tool / 工具 | Purpose / 用途 |
|------|---------|
| `FileOperationTool` | Read/write/list files / 文件读写与列表 |
| `WebSearchTool` | Search via SearchAPI / 通过 SearchAPI 搜索 |
| `WebScrapingTool` | Scrape web pages (Jsoup) / 网页抓取 |
| `ResourceDownloadTool` | Download resources / 资源下载 |
| `TerminalOperationTool` | Execute shell commands / 执行终端命令 |
| `PDFGenerationTool` | Generate PDFs (iText 9 + CJK) / 生成 PDF（iText 9 + 中文支持） |
| `TerminateTool` | Signal agent completion / 标记智能体完成 |

To add a new tool / 新增工具: create a class with `@Tool` methods, add it to `ToolRegistration.allTools()` / 创建带 `@Tool` 方法的类，添加到 `ToolRegistration.allTools()`。

### RAG Pipeline / RAG 管道

```
Markdown files (classpath:document/*.md)
  → LoveAppDocumentLoader (MarkdownDocumentReader)
  → MyKeywordEnricher (auto-generate keyword metadata / 自动生成关键词元数据)
  → LoveAppVectorStoreConfig (SimpleVectorStore + DashScope embeddings / 向量存储 + DashScope 嵌入)
  → QueryRewriter (RewriteQueryTransformer / 查询改写)
  → QuestionAnswerAdvisor / RetrievalAugmentationAdvisor
```

Three vector store backends / 三种向量存储后端: SimpleVectorStore (active, in-memory / 当前使用，内存存储), PgVector (commented out, persistent / 已注释，持久化存储), DashScope Cloud (commented out / 已注释，云端存储). Switch by uncommenting the corresponding `@Configuration` and config.

> 切换方式：取消对应 `@Configuration` 和配置的注释。

### MCP Integration / MCP 集成

`mcp-servers.json` defines two MCP servers / 定义了两个 MCP 服务器: amap-maps (npx-based / 基于 npx) and yu-image-search-mcp-server (Java JAR, stdio transport / Java JAR，stdio 传输). The MCP client config in `application.yml` is commented out by default — uncomment when MCP servers are running.

> `application.yml` 中的 MCP 客户端配置默认注释掉，运行 MCP 服务器时取消注释。

The image search MCP server (`yu-image-search-mcp-server/`) is a **fully independent Spring Boot app** with its own `pom.xml`, using `spring-ai-starter-mcp-server-webmvc` for SSE transport.

> 图片搜索 MCP 服务器是一个**完全独立的 Spring Boot 应用**，有自己的 `pom.xml`，使用 `spring-ai-starter-mcp-server-webmvc` 提供 SSE 传输。

### API Endpoints / API 端点

Server runs on port 8123 with context path `/api` / 服务运行在 8123 端口，上下文路径为 `/api`。

| Endpoint | Description / 描述 |
|----------|-------------------|
| `GET /api/ai/love_app/chat/sync` | Synchronous LoveApp chat / LoveApp 同步聊天 |
| `GET /api/ai/love_app/chat/sse` | SSE streaming LoveApp chat (Flux) / LoveApp SSE 流式聊天（Flux） |
| `GET /api/ai/love_app/chat/server_sent_event` | SSE with ServerSentEvent wrapper / ServerSentEvent 包装的 SSE |
| `GET /api/ai/love_app/chat/sse_emitter` | SSE via SseEmitter (3min timeout) / 通过 SseEmitter 的 SSE（3 分钟超时） |
| `GET /api/ai/manus/chat` | YuManus agent (SseEmitter, per-request, 5min timeout) / YuManus 智能体（SseEmitter，每次请求新建，5 分钟超时） |
| `GET /api/health/` | Health check / 健康检查 |

### Key Dependencies / 主要依赖

- Java 21, Spring Boot 3.4.4, Spring AI 1.0.0, Spring AI Alibaba 1.0.0.2
- DashScope (qwen-plus), Ollama (gemma3:1b), PGVector, MCP Client/Server
- LangChain4j community-dashscope 1.0.0-beta2
- Hutool, Jsoup, iText 9 (PDF + CJK / PDF + 中文支持), Kryo (chat memory serialization / 聊天记忆序列化), Knife4j (API docs / API 文档)
- Lombok 1.18.36, jsonschema-generator 4.38.0

### Frontend / 前端

Vue 3 + Vite + Element Plus frontend in `yu-ai-agent-frontend/`. Two views / Vue 3 + Vite + Element Plus 前端，位于 `yu-ai-agent-frontend/`，包含两个视图:
- **LoveMaster** (`/`) — SSE streaming chat with LoveApp / SSE 流式恋爱大师聊天
- **SuperAgent** (`/super-agent`) — WebSocket streaming chat with YuManus / WebSocket 流式超级智能体聊天

API base URL / API 基础地址: `http://localhost:8080/api` (configured in `src/api/index.js` / 配置在 `src/api/index.js`).

## Development Patterns / 开发模式

### Adding a Tool / 新增工具
1. Create class in `tools/` with `@Tool`-annotated methods / 在 `tools/` 下创建带 `@Tool` 注解方法的类
2. Add instance to `ToolRegistration.allTools()` bean method / 将实例添加到 `ToolRegistration.allTools()` Bean 方法

### Adding an Agent / 新增智能体
1. Extend `ToolCallAgent` or `ReActAgent` / 继承 `ToolCallAgent` 或 `ReActAgent`
2. Set `systemPrompt`, `nextStepPrompt`, `maxSteps` / 设置 `systemPrompt`、`nextStepPrompt`、`maxSteps`
3. Register as Spring Bean (`@Component` or `@Bean`) / 注册为 Spring Bean

### Switching RAG Backend / 切换 RAG 后端
- Uncomment `PgVectorVectorStoreConfig` `@Configuration` + `application.yml` pgvector section for persistent storage / 取消 `PgVectorVectorStoreConfig` `@Configuration` + `application.yml` pgvector 部分的注释，启用持久化存储
- Uncomment `LoveAppRagCloudAdvisorConfig` + DashScope cloud config for cloud knowledge base / 取消 `LoveAppRagCloudAdvisorConfig` + DashScope 云端配置的注释，启用云端知识库
- Comment out `LoveAppVectorStoreConfig` when switching away from in-memory / 切换时注释掉 `LoveAppVectorStoreConfig`

### Debug Tips / 调试技巧
- Spring AI debug logging / Spring AI 调试日志: `logging.level.org.springframework.ai=DEBUG` (already enabled / 已启用)
- `DataSourceAutoConfiguration` excluded by default — remove exclusion when PgVector is active / 默认排除数据库自动配置，启用 PgVector 时移除排除
- SSEEmitter timeout / SSEEmitter 超时: 5 minutes (configured in `BaseAgent.runStream()` / 在 `BaseAgent.runStream()` 中配置)
- API docs available at `/api/swagger-ui.html` (Knife4j) / API 文档地址 `/api/swagger-ui.html`（Knife4j）