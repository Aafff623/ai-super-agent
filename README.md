# AI 超级智能体项目

> 作者：threetwoa

## 项目介绍

这是一套以 **AI 开发实战** 为核心的项目，包含 **AI 恋爱大师应用 + 拥有自主规划能力的超级智能体**。

`AI 恋爱大师应用` 可以依赖 AI 大模型解决用户的情感问题，支持多轮对话、基于自定义知识库进行问答、自主调用工具和 MCP 服务完成任务。

此外，还实现了基于 ReAct 模式的 `自主规划智能体 YuManus`，可以利用网页搜索、资源下载和 PDF 生成工具，帮用户制定完整的约会计划并生成文档。

## 项目功能梳理

- AI 恋爱大师应用：用户在恋爱过程中难免遇到各种难题，让 AI 为用户提供贴心情感指导。支持多轮对话、对话记忆持久化、RAG 知识库检索、工具调用、MCP 服务调用。
- AI 超级智能体：可以根据用户的需求，自主推理和行动，直到完成目标。
- 提供给 AI 的工具：包括联网搜索、文件操作、网页抓取、资源下载、终端操作、PDF 生成。
- AI MCP 服务：可以从特定网站搜索图片。

## 用哪些技术？

- Java 21 + Spring Boot 3 框架
- Spring AI + LangChain4j
- RAG 知识库
- PGvector 向量数据库
- Tool Calling 工具调用
- MCP 模型上下文协议
- ReAct Agent 智能体构建
- SSE 异步推送
- 第三方接口：如 SearchAPI / Pexels API
- Ollama 大模型部署
- 工具库如：Kryo 高性能序列化 + Jsoup 网页抓取 + iText PDF 生成 + Knife4j 接口文档

## 构建与运行

```bash
# 运行主应用
mvn spring-boot:run

# 运行测试
mvn test

# 运行 MCP Server
cd yu-image-search-mcp-server && mvn spring-boot:run
```

**环境配置**：修改 `src/main/resources/application.yml` 填写 apiKey 等配置。

## 学习大纲

第 1 期：项目总览
第 2 期：AI 大模型接入
第 3 期：AI 应用开发
第 4 期：RAG 知识库基础
第 5 期：RAG 知识库进阶
第 6 期：工具调用
第 7 期：MCP
第 8 期：AI 智能体构建
第 9 期：AI 服务化