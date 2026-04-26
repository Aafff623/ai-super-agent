# 第3期：AI 应用开发（恋爱大师）

## 核心要点

### 1. ChatClient vs ChatModel
- **ChatClient**：高级封装，可配置记忆、拦截器（Advisor）、模板等
- **ChatModel**：底层接口，无记忆、无额外处理
- 项目使用 ChatClient 的建造者模式构造

### 2. ChatClient 构建流程
```java
chatClient = ChatClient.builder(dashscopeChatModel)
    .defaultSystem(SYSTEM_PROMPT)           // 设置系统提示词（定义 AI 固定人设）
    .defaultAdvisors(
        MessageChatMemoryAdvisor.builder(chatMemory).build(),  // 把历史对话塞进当前 Prompt，让 AI 看到上下文
        new MyLoggerAdvisor()                                   // 记录请求/响应日志，检查违禁词
    )
    .build();
```

### 3. 多轮对话记忆
- `InMemoryChatMemoryRepository`：基于内存的对话记忆（重启丢失）
- `MessageChatMemoryAdvisor`：发送请求前把历史对话塞进 Prompt
- `ChatMemory.CONVERSATION_ID`：通过 chatId 区分不同会话
- `maxMessages(20)`：限制记忆窗口大小，避免 token 爆炸

### 4. 文件持久化对话记忆（FileBasedChatMemory）
- 使用 **Kryo** 高性能序列化库（不需要无参构造，支持复杂继承关系）
- 实现 ChatMemory 的三个方法：`add`（添加消息）、`get`（读取最近N条）、`clear`（删除会话）
- 如需更换存储方式，只需改 `getOrCreateConversation` 和 `saveConversation` 的读写逻辑

### 5. 自定义日志 Advisor（MyLoggerAdvisor）
- 原因：默认 `SimpleLoggerAdvisor` 只在 debug 级别输出，格式不够清晰
- 同时实现 `CallAdvisor`（同步）和 `StreamAdvisor`（流式）两个接口
- 流式调用需用 `MessageAggregator` 把小片段聚合成完整消息

### 6. 结构化输出
- `ChatClient.call().entity(LoveReport.class)`：让 AI 输出直接转成 Java 对象
- 流程：AI Prompt → JSON → Java 对象
- 其他转换器：`MapOutputConverter`（转Map）、`ListOutputConverter`（转List）、`BeanOutputConverter`（转任意Bean）
- 用 `record` 定义输出结构（Java 14+ 简洁类）

### 7. RAG 知识库问答
- `QuestionAnswerAdvisor`：Spring AI 内置，自动完成"检索 → 增强 prompt → 调用模型"
- 底层：调用 `vectorStore.similaritySearch(userText)`，返回最相似的 N 个 Document
- 可选后端：本地 SimpleVectorStore / 云端 DashScope / PgVector

### 8. ReReadingAdvisor（推理增强）
- 让 AI 重读问题来提高回答质量
- 代价：token 翻倍，按需开启