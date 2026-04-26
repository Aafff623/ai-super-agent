# 第5期：RAG 知识库进阶（调优与核心特性）

## 一、本期目标

第4期入门了 RAG，用 `QuestionAnswerAdvisor` 简单挂上知识库。但企业项目中会遇到：
- 文档切得不好，AI 找不到正确答案
- 向量库性能差，检索慢
- 用户问得含糊，搜不到相关文档
- AI 回答时还是乱编（幻觉）

**第5期教如何把 RAG 做"精"**：文档怎么切、元数据怎么加、查询怎么改写、检索参数怎么调。

---

## 二、RAG 四大步骤回顾

```
文档收集和切割 → 向量转换和存储 → 文档过滤和检索 → 查询增强和关联
```

每个阶段都有对应的优化点。

---

## 三、第一阶段：文档收集和切割（ETL 深入）

### 3.1 为什么 ETL 很重要？

> 文档质量决定了 RAG 的上限，模型调参只能逼近这个上限。

### 3.2 Spring AI 的 ETL 三大组件

- **DocumentReader**：读取源文档 → `List<Document>`
- **DocumentTransformer**：转换文档（切片、增强元数据、格式化） → `List<Document>`
- **DocumentWriter**：写入目标存储（向量库、文件系统等）

```
原始文件 → Reader → List<Document> → Transformer → List<Document> → Writer → 向量库/文件
```

### 3.3 DocumentReader 类型

| Reader | 用途 |
|--------|------|
| `JsonReader` | 读 JSON，支持 JSON Pointer 提取特定字段 |
| `TextReader` | 纯文本 |
| `MarkdownDocumentReader` | Markdown（第4期用的） |
| `PagePdfDocumentReader` | 按页读 PDF |
| `ParagraphPdfDocumentReader` | 按段落读 PDF |
| `HtmlReader` | 基于 jsoup |
| `TikaDocumentReader` | 万能，支持很多格式（Apache Tika） |

**JsonReader 只提取某几个字段**：
```java
JsonReader reader = new JsonReader(resource, "description", "features");
```

**JSON Pointer 取深层数据**：
```java
List<Document> docs = new JsonReader(resource).get("/items");
```

### 3.4 DocumentTransformer – 切片（最关键的优化点）

**为什么要切片？** 大模型上下文窗口有限，整篇文章塞进去无法精准定位。切小了可能丢失上下文，切大了可能引入噪音。

**1. 文本分割器（TextSplitter）**

`TokenTextSplitter`：按 token 数切分（推荐，大模型计费/上下文都按 token）

```java
TokenTextSplitter splitter = new TokenTextSplitter(
    500,   // defaultChunkSize: 每块目标 token 数
    100,   // minChunkSizeChars: 每块最小字符数
    10,    // minChunkLengthToEmbed
    5000,  // maxNumChunks
    true   // keepSeparator
);
List<Document> chunks = splitter.apply(documents);
```

**工作原理**：用 CL100K_BASE 编码 token，按 `defaultChunkSize` 切分，在 `minChunkSizeChars` 之后找句子边界截断。

**调参建议**：
- 专业文档（法律、医疗）：1000-1500 token
- 短文本（FAQ）：200-300 token
- 关键：避免在句子中间切断

**2. 元数据增强器（MetadataEnricher）**

- `KeywordMetadataEnricher`：用 AI 提取关键词，加到 `metadata` 中
- `SummaryMetadataEnricher`：用 AI 生成摘要，可关联前后文档

```java
KeywordMetadataEnricher enricher = new KeywordMetadataEnricher(chatModel, 5);
List<Document> enriched = enricher.apply(documents);
// 效果：自动加 keywords: ["恋爱", "沟通", "约会"]
```

**3. 内容格式化器（ContentFormatter）**

将 Document 的内容和元数据按模板拼成字符串：

```java
DefaultContentFormatter formatter = DefaultContentFormatter.builder()
    .withMetadataTemplate("{key}: {value}")
    .withMetadataSeparator("\n")
    .withTextTemplate("{metadata_string}\n\n{content}")
    .build();
```

### 3.5 DocumentWriter

- `FileDocumentWriter`：写回文件（调试用）
- `VectorStoreWriter`：写入向量库

**完整 ETL 链式调用**：
```java
vectorStore.write(
    enricher.apply(
        splitter.apply(
            pdfReader.read()
        )
    )
);
```

---

## 四、第二阶段：向量转换和存储（深入 VectorStore）

### 4.1 VectorStore 接口

- `add(List<Document>)`：添加文档（自动 embedding）
- `delete`：删除
- `similaritySearch`：相似度检索

**SearchRequest 配置**：
```java
SearchRequest request = SearchRequest.builder()
    .query("什么是编程导航")
    .topK(5)
    .similarityThreshold(0.7)
    .filterExpression("category == 'web' AND date > '2025-01-01'")
    .build();
```

### 4.2 PGVector（生产环境持久化）

`SimpleVectorStore` 存内存，重启丢失。PGVector 让你直接在 PostgreSQL 里存向量，复用现有数据库。

**手动配置（避免多 EmbeddingModel Bean 冲突）**：
```java
@Configuration
@ConditionalOnMissingBean(VectorStore.class)
public class PgVectorConfig {
    @Bean
    public VectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(1536)
            .distanceType(CosineDistance)
            .indexType(HNSW)
            .initializeSchema(true)
            .vectorTableName("vector_store")
            .build();
    }
}
```

### 4.3 批处理策略（BatchingStrategy）

一次 add 几千个文档可能超过 embedding 模型上下文窗口或触发 API 速率限制。

```java
@Bean
public BatchingStrategy customBatch() {
    return new TokenCountBatchingStrategy(
        EncodingType.CL100K_BASE,
        8000,   // 每批最大 token 数
        0.1     // 安全边界
    );
}
```

---

## 五、第三阶段：文档过滤和检索

### 5.1 预检索：优化用户查询

**QueryTransformer 类型**：

1. **RewriteQueryTransformer**：用 AI 重写查询
   ```java
   QueryTransformer rewriter = RewriteQueryTransformer.builder()
       .chatClientBuilder(chatClientBuilder)
       .build();
   // "啥是鱼皮？" → "请解释程序员鱼皮是谁，他的主要成就和影响是什么？"
   ```

2. **TranslationQueryTransformer**：翻译成目标语言（成本高）

3. **CompressionQueryTransformer**：将对话历史压缩成一个独立问题

**MultiQueryExpander**：一个问题扩展成多个不同表述的查询
```java
MultiQueryExpander expander = MultiQueryExpander.builder()
    .chatClientBuilder(chatClientBuilder)
    .numberOfQueries(3)
    .build();
// "编程导航有啥功能？" → 3个不同表述的查询
```
代价：3倍调用量，需权衡。

### 5.2 检索中：DocumentRetriever 配置

```java
DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
    .vectorStore(vectorStore)
    .similarityThreshold(0.7)
    .topK(5)
    .filterExpression(new FilterExpressionBuilder()
        .eq("status", "married")
        .build())
    .build();
```

**调参建议**：
- 相似度阈值：0.6-0.7 开始测试
- topK：片段短（200 token）→ 5-10；片段长（800 token）→ 2-3
- 元数据过滤：按状态、日期、类型过滤能大幅提升精度

### 5.3 检索后：DocumentJoiner

多查询扩展或多向量库检索时，用 `ConcatenationDocumentJoiner` 去重合并。

---

## 六、第四阶段：查询增强和关联（Advisor 进阶）

### 6.1 RetrievalAugmentationAdvisor（比 QuestionAnswerAdvisor 更强大）

```java
Advisor advisor = RetrievalAugmentationAdvisor.builder()
    .documentRetriever(VectorStoreDocumentRetriever.builder()
        .vectorStore(vectorStore)
        .similarityThreshold(0.5)
        .topK(4)
        .build())
    .build();
```

### 6.2 组合查询转换器

```java
Advisor advisor = RetrievalAugmentationAdvisor.builder()
    .queryTransformers(
        RewriteQueryTransformer.builder()
            .chatClientBuilder(chatClientBuilder)
            .build()
    )
    .documentRetriever(...)
    .build();
```

**执行顺序**：用户查询 → 查询转换器（重写） → 文档检索器 → 增强 prompt → 调用模型

### 6.3 处理空上下文（没检索到文档）

```java
ContextualQueryAugmenter augmenter = ContextualQueryAugmenter.builder()
    .allowEmptyContext(true)
    .emptyContextPromptTemplate(new PromptTemplate(
        "请礼貌地告诉用户无法回答恋爱以外的问题，并引导至官网 https://threetwoa.me"
    ))
    .build();

Advisor advisor = RetrievalAugmentationAdvisor.builder()
    .documentRetriever(...)
    .queryAugmenter(augmenter)
    .build();
```

---

## 七、最佳实践与调优总结

### 文档层面
- 用 Markdown 标题、列表，便于切割时识别边界
- 首选 Markdown/纯文本，PDF 尽量转成可解析格式
- 手动或自动添加标签（如 `status=married`、`category=communication`）
- 云平台推荐"智能切分"；本地用 `TokenTextSplitter` 需调整参数

### 向量存储层面
- 小项目/学习：`SimpleVectorStore`（内存）
- 生产/团队：`PGVector` 或独立向量数据库（Milvus/Pinecone）
- 嵌入模型选择：根据语言、领域选择

### 检索层面
- 相似度阈值：0.6-0.7 开始测试
- topK：总上下文控制在 2k-4k token 为宜
- 元数据过滤：一定要加
- 查询重写：含糊时有用，但增加一次 AI 调用，成本翻倍

### 大模型幻觉与评估
- RAG + 引用标注 + 限定知识范围可有效减少幻觉
- 评估指标：召回率、精确率、NDCG（检索阶段）；事实准确性、引用准确率（生成阶段）
- 简单项目用云平台"命中测试"人工看；大项目需要建立自动化评估集

---

## 八、高级知识（面试用）

### 混合检索
- **并行**：同时跑向量检索 + 全文检索（Elasticsearch），用 RRF 合并结果
- **级联**：先向量检索取 top 100，再用关键词过滤
- **动态**：根据查询类型路由

### 大模型幻觉
- 原因：模型是概率预测，不真的"知道"事实
- 缓解：RAG + 强制要求引用
- 检测：用另一模型验证事实、人工抽检

### 高级 RAG 架构（了解名称即可）
- **C-RAG**：生成后自我纠错，重新检索
- **Self-RAG**：模型判断是否需要检索，避免无谓调用
- **RAPTOR**：对复杂问题递归检索
- **多智能体 RAG**：不同 specialist agent 处理不同子任务