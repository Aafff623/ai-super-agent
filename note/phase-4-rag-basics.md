# 第4期：RAG 知识库基础（恋爱大师知识问答）

## 一、为什么要做"知识问答"？

AI 只会根据内置系统提示词和预训练知识回答，遇到私有数据（如付费课程、用户案例）要么不知道，要么乱编。

**RAG（检索增强生成）**：让 AI 回答前先去知识库找答案，再基于查到的内容回答。

---

## 二、RAG 核心概念

> AI 回答前先去"小抄本"里翻翻再说话。

**传统 AI**：凭记忆（训练数据）回答。
**RAG AI**：用户提问 → 搜知识库相关段落 → 段落+问题一起喂给 AI → AI 根据材料回答。

**为什么需要 RAG？**
- 知识库随时更新（不用重新训练模型）
- 回答基于真实文档，减少幻觉
- 数据私有，不泄露给模型厂商

**RAG 四个核心步骤**（面试常考）：
1. **文档收集和切割**：长文档切成小段（chunk）
2. **向量转换和存储**：每段文字转成向量，存入向量数据库
3. **文档过滤和检索**：用户问题转成向量，找最相似的段
4. **查询增强和关联**：找到的文档段拼到用户问题后面，发给大模型

**关键术语**：
- **Embedding**：文字转成向量，语义相近的文字向量也相近
- **向量数据库**：专门存向量、能快速找相似的数据库
- **召回**：快速找一批可能相关的候选（不求精确，但要快）
- **精排**：对召回的候选用复杂算法精算相似度

---

## 三、RAG 实战：Spring AI + 本地知识库

### 3.1 准备恋爱文档

在 `src/main/resources/document/` 下放 Markdown 文档：单身篇、恋爱篇、已婚篇。

### 3.2 读取文档（DocumentReader）

Spring AI 提供 Reader 读取不同格式文件，用的是 `MarkdownDocumentReader`。

**代码**：`LoveAppDocumentLoader.java`
```java
@Component
@Slf4j
public class LoveAppDocumentLoader {
    private final ResourcePatternResolver resolver;

    public LoveAppDocumentLoader(ResourcePatternResolver resolver) {
        this.resolver = resolver;
    }

    public List<Document> loadMarkdowns() {
        List<Document> all = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources("classpath:document/*.md");
            for (Resource r : resources) {
                String fileName = r.getFilename();
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)   // --- 分割线切断开
                        .withIncludeCodeBlock(false)               // 不要代码块
                        .withIncludeBlockquote(false)              // 不要引用块
                        .withAdditionalMetadata("filename", fileName)  // 存元数据
                        .build();
                MarkdownDocumentReader reader = new MarkdownDocumentReader(r, config);
                all.addAll(reader.get());
            }
        } catch (IOException e) {
            log.error("文档加载失败", e);
        }
        return all;
    }
}
```

**要点**：
- `ResourcePatternResolver`：Spring 通配加载资源的工具
- `withHorizontalRuleCreateDocument(true)`：`---` 分割线切分成独立文档
- `additionalMetadata`：额外信息，检索时可按 `filename` 过滤
- `reader.get()` 返回 `List<Document>`，每个包含 `content`（文本）和 `metadata`（元数据）

### 3.3 向量转换与存储（VectorStore）

**代码**：`LoveAppVectorStoreConfig.java`
```java
@Configuration
public class LoveAppVectorStoreConfig {
    @Resource private LoveAppDocumentLoader loader;

    @Bean
    public VectorStore loveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        List<Document> docs = loader.loadMarkdowns();
        store.add(docs);
        return store;
    }
}
```

**要点**：
- `EmbeddingModel`：Spring AI 接口，把文本变成向量。`dashscopeEmbeddingModel` 由 Spring AI Alibaba 自动配置
- `store.add(docs)` 做两件事：调用 `embeddingModel.embed(doc)` 得到向量，然后存向量+原文+元数据
- `SimpleVectorStore` 仅内存，重启丢失。**生产用 JdbcVectorStore 或 PGvector**

### 3.4 查询增强（QuestionAnswerAdvisor）

Spring AI 内置 `QuestionAnswerAdvisor`，自动完成"检索 → 增强 prompt → 调用模型"。

**代码**：在 `LoveApp` 里新增方法
```java
@Resource private VectorStore loveAppVectorStore;

public String doChatWithRag(String message, String chatId) {
    ChatResponse response = chatClient
        .prompt()
        .user(message)
        .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                               .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
        .advisors(new MyLoggerAdvisor())
        .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
        .call()
        .chatResponse();
    return response.getResult().getOutput().getText();
}
```

**底层流程**：
1. `QuestionAnswerAdvisor` 调用 `vectorStore.similaritySearch(userText)`，返回最相似的 N 个 Document
2. 修改请求 Prompt，把检索到的文档内容拼接成增强后的 Prompt
3. 发送给大模型

---

## 四、RAG 实战：Spring AI + 云知识库（阿里云百炼）

### 4.1 为什么用云知识库？

本地方案需自己管理文档切割、向量库运维、Embedding 调用。云服务提供：
- 自动文档解析（PDF、Word、Excel 等）
- 智能切片（按语义切，比固定长度切更好）
- 托管向量数据库（高可用、高性能）

**缺点**：收费、数据出网（隐私需谨慎）。

### 4.2 在百炼平台创建知识库

1. 登录阿里云百炼 → 应用数据 → 知识库 → 创建知识库
2. 导入文档，选择智能切分策略
3. 记下**知识库名称**（如 `恋爱大师`）

### 4.3 对接云知识库代码

**配置 Advisor**：
```java
@Configuration
@Slf4j
public class LoveAppRagCloudAdvisorConfig {
    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Bean
    public Advisor loveAppRagCloudAdvisor() {
        DashScopeApi api = new DashScopeApi(apiKey);
        DocumentRetriever retriever = new DashScopeDocumentRetriever(api,
            DashScopeDocumentRetrieverOptions.builder()
                .withIndexName("恋爱大师")
                .build());
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .build();
    }
}
```

**在 LoveApp 中使用**：
```java
@Resource private Advisor loveAppRagCloudAdvisor;

public String doChatWithCloudRag(String message, String chatId) {
    return chatClient.prompt()
        .user(message)
        .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
        .advisors(loveAppRagCloudAdvisor)
        .call()
        .content();
}
```

**区别**：
- 本地：自己读文件、存向量库、调 `QuestionAnswerAdvisor`
- 云端：用 `DashScopeDocumentRetriever` 从云端检索，用 `RetrievalAugmentationAdvisor` 增强

---

## 五、两种 RAG 方式对比

| 维度 | 本地知识库 | 云知识库服务 |
|------|-----------|-------------|
| 控制力 | 完全可控 | 受平台限制 |
| 数据隐私 | 数据不出网 | 数据上传到云端 |
| 运维成本 | 高（自己管向量库） | 低（平台托管） |
| 文档支持 | 需自己写 Reader | 支持多种格式自动解析 |
| 切割策略 | 自写代码或默认规则 | 智能切分（可手动调整） |
| 扩展性 | 可任意定制 | 依赖平台 API |
| 适用场景 | 私有数据敏感、有运维能力 | 快速开发、非机密数据 |

---

## 六、常见问题（FAQ）

**Q1：为什么向量数据库存"向量+原文"？**
检索时只返回向量和相似度，但给大模型的必须是原文文本。

**Q2：本地 SimpleVectorStore 重启后数据丢失怎么办？**
改成 `JdbcVectorStore` + PostgreSQL + pgvector 插件。生产必须持久化。

**Q3：云知识库切片不准确，能手动改吗？**
可以。在百炼控制台知识库详情页编辑切片内容。

**Q4：如何让 AI 回答时引用来源？**
`QuestionAnswerAdvisor` 不包含来源。需自己继承它，在 `after` 方法里从 `AdvisorContext` 取出检索到的文档列表拼到回答里。