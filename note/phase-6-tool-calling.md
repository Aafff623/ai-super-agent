# 第6期：工具调用（让 AI 有手有脚）

## 一、为什么要做工具调用？

之前 AI 应用只是"知识问答助手"，有了工具调用，AI 就不再只有大脑，而是有手有脚的"智能体"了。

**工具调用（Tool Calling）**：让 AI 大模型借用外部工具来完成它自己做不到的事情。AI 本身并不执行工具，只是提出"我需要用 XX 工具"，真正执行的是我们的应用程序。

---

## 二、工具调用工作原理

```
用户提问 → 程序传给大模型 → 大模型判断需要工具 → 大模型输出工具名+参数
→ 程序执行工具 → 工具返回结果 → 程序把结果传回大模型 → 大模型生成最终回答
```

**关键设计**：AI 模型永远无法直接接触你的 API 或系统资源，所有操作必须通过你的程序执行——安全性保障。

> Function Calling = Tool Calling，只是叫法不同。

---

## 三、Spring AI 工具开发

### 3.1 定义工具的两种模式

| 特性 | Methods 方式（推荐） | Functions 方式 |
|------|---------------------|----------------|
| 定义方式 | `@Tool` + `@ToolParam` 注解 | 函数式接口 + `@Bean` |
| 语法复杂度 | 简单直观 | 较复杂，需定义请求/响应对象 |
| 支持参数类型 | 大多数 Java 类型 | 不支持基本类型、Optional、集合 |
| 支持返回类型 | 几乎所有可序列化类型 | 不支持基本类型、Optional、集合 |

**Methods 模式示例**：
```java
class WeatherTools {
    @Tool(description = "获取指定城市的当前天气情况")
    String getWeather(@ToolParam(description = "城市名称") String city) {
        return "北京今天晴朗，气温25°C";
    }
}
```

**Functions 模式示例**（了解即可）：
```java
@Configuration
public class ToolConfig {
    @Bean
    @Description("Get current weather for a location")
    public Function<WeatherRequest, WeatherResponse> weatherFunction() {
        return request -> new WeatherResponse("Weather in " + request.getCity());
    }
}
```

### 3.2 注解式 vs 编程式

- **注解式**：`@Tool` 注解标记方法，简单直观
- **编程式**：运行时动态创建，更灵活（把注解参数改成方法调用）

```java
// 编程式示例
Method method = ReflectionUtils.findMethod(WeatherTools.class, "getWeather", String.class);
ToolCallback toolCallback = MethodToolCallback.builder()
    .toolDefinition(ToolDefinition.builder(method).description("获取天气").build())
    .toolMethod(method)
    .toolObject(new WeatherTools())
    .build();
```

### 3.3 使用工具的四种方式

1. **按需使用**：`chatClient.prompt().tools(new WeatherTools()).call()`
2. **全局使用**：`ChatClient.builder(chatModel).defaultTools(new WeatherTools()).build()`
3. **底层使用**：`ToolCallingChatOptions.builder().toolCallbacks(weatherTools).build()` → 给 `ChatModel` 绑定
4. **动态解析**：`ToolCallbackResolver` 运行时按名称解析工具（高级用法）

### 3.4 不支持的类型

工具方法的参数和返回值不支持：Optional、异步类型（CompletableFuture）、响应式类型（Mono/Flux）、函数式类型（Function/Supplier）。

---

## 四、6 大主流工具开发

### 4.1 文件常量

```java
public interface FileConstant {
    String FILE_SAVE_DIR = System.getProperty("user.dir") + "/tmp";
}
```

> 建议将 `/tmp` 加入 `.gitignore`。

### 4.2 文件操作工具

```java
public class FileOperationTool {
    private final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";

    @Tool(description = "Read content from a file")
    public String readFile(@ToolParam(description = "Name of the file to read") String fileName) {
        String filePath = FILE_DIR + "/" + fileName;
        try {
            return FileUtil.readUtf8String(filePath);
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Write content to a file")
    public String writeFile(
        @ToolParam(description = "Name of the file to write") String fileName,
        @ToolParam(description = "Content to write to the file") String content) {
        String filePath = FILE_DIR + "/" + fileName;
        try {
            FileUtil.mkdir(FILE_DIR);
            FileUtil.writeUtf8String(content, filePath);
            return "File written successfully to: " + filePath;
        } catch (Exception e) {
            return "Error writing to file: " + e.getMessage();
        }
    }
}
```

### 4.3 联网搜索工具

使用 Search API（按量计费），从百度搜索引擎获取结果。

```java
public class WebSearchTool {
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";
    private final String apiKey;

    public WebSearchTool(String apiKey) { this.apiKey = apiKey; }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(@ToolParam(description = "Search query keyword") String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            JSONObject jsonObject = JSONUtil.parseObj(response);
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            List<Object> objects = organicResults.subList(0, 5);
            return objects.stream()
                .map(obj -> ((JSONObject) obj).toString())
                .collect(Collectors.joining(","));
        } catch (Exception e) {
            return "Error searching Baidu: " + e.getMessage();
        }
    }
}
```

配置 API Key：
```yaml
search-api:
  api-key: 你的 API Key
```

### 4.4 网页抓取工具

使用 jsoup 解析网页内容。

```java
public class WebScrapingTool {
    @Tool(description = "Scrape the content of a web page")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            Document doc = Jsoup.connect(url).get();
            return doc.html();
        } catch (IOException e) {
            return "Error scraping web page: " + e.getMessage();
        }
    }
}
```

### 4.5 终端操作工具

使用 Java Process API 执行命令。**Windows 需要用 `cmd.exe /c`**。

```java
// Linux/Mac 版本
public class TerminalOperationTool {
    @Tool(description = "Execute a command in the terminal")
    public String executeTerminalCommand(@ToolParam(description = "Command to execute") String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(command);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                output.append("Command failed with exit code: ").append(exitCode);
            }
        } catch (IOException | InterruptedException e) {
            output.append("Error: ").append(e.getMessage());
        }
        return output.toString();
    }
}

// Windows 版本（必须用 ProcessBuilder + cmd.exe）
ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
Process process = builder.start();
```

### 4.6 资源下载工具

使用 Hutool 的 `HttpUtil.downloadFile`。

```java
public class ResourceDownloadTool {
    @Tool(description = "Download a resource from a given URL")
    public String downloadResource(
        @ToolParam(description = "URL of the resource to download") String url,
        @ToolParam(description = "Name of the file to save") String fileName) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/download";
        String filePath = fileDir + "/" + fileName;
        try {
            FileUtil.mkdir(fileDir);
            HttpUtil.downloadFile(url, new File(filePath));
            return "Resource downloaded successfully to: " + filePath;
        } catch (Exception e) {
            return "Error downloading resource: " + e.getMessage();
        }
    }
}
```

### 4.7 PDF 生成工具

使用 iText 9 生成 PDF，内置中文字体 `STSongStd-Light`。

```java
public class PDFGenerationTool {
    @Tool(description = "Generate a PDF file with given content", returnDirect = false)
    public String generatePDF(
        @ToolParam(description = "Name of the file to save") String fileName,
        @ToolParam(description = "Content to be included") String content) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName;
        try {
            FileUtil.mkdir(fileDir);
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                document.setFont(font);
                document.add(new Paragraph(content));
            }
            return "PDF generated successfully to: " + filePath;
        } catch (IOException e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }
}
```

> 生产环境建议自行下载字体文件或上传到 OSS 返回 URL。

### 4.8 集中注册

```java
@Configuration
public class ToolRegistration {
    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Bean
    public ToolCallback[] allTools() {
        return ToolCallbacks.from(
            new FileOperationTool(),
            new WebSearchTool(searchApiKey),
            new WebScrapingTool(),
            new ResourceDownloadTool(),
            new TerminalOperationTool(),
            new PDFGenerationTool()
        );
    }
}
```

**暗含的设计模式**：工厂模式（集中创建）、依赖注入（@Value）、注册模式（中央注册点）、适配器模式（ToolCallbacks.from 统一转换）。

### 4.9 在 LoveApp 中使用

```java
@Resource
private ToolCallback[] allTools;

public String doChatWithTools(String message, String chatId) {
    ChatResponse response = chatClient
        .prompt()
        .user(message)
        .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
        .advisors(new MyLoggerAdvisor())
        .tools(allTools)
        .call()
        .chatResponse();
    return response.getResult().getOutput().getText();
}
```

---

## 五、工具进阶知识（面试加分）

### 5.1 底层数据结构

**ToolCallback 接口**：
```java
public interface ToolCallback {
    ToolDefinition getToolDefinition();  // 名称、描述、参数 → 传给 AI
    ToolMetadata getToolMetadata();      // 附加信息（如 returnDirect）
    String call(String toolInput);       // 无上下文调用
    String call(String toolInput, ToolContext toolContext);  // 有上下文调用
}
```

Spring AI 自动完成：
- `JsonSchemaGenerator`：解析方法签名和注解，自动生成 JSON Schema
- `ToolCallResultConverter`：将各种返回值统一转成字符串
- `MethodToolCallback`：封装注解方法为 ToolCallback

### 5.2 工具上下文（ToolContext）

传递不暴露给 AI 的内部信息（如用户身份、请求 ID）：

```java
// 调用时传递
String response = chatClient
    .prompt("帮我查询用户信息")
    .tools(new CustomerTools())
    .toolContext(Map.of("userName", "yupi"))
    .call()
    .content();

// 工具中使用
class CustomerTools {
    @Tool(description = "Retrieve customer information")
    Customer getCustomerInfo(Long id, ToolContext toolContext) {
        return customerRepository.findById(id, toolContext.get("userName"));
    }
}
```

> ToolContext 本质是 Map，不会传给 AI 模型，只在应用内部使用。

### 5.3 立即返回（returnDirect）

工具结果直接返回给用户，不再经过 AI 处理：

```java
@Tool(description = "Retrieve customer information", returnDirect = true)
Customer getCustomerInfo(Long id) { ... }
```

适用场景：返回二进制数据（图片/文件）、返回大量数据不需要 AI 解释、产生明确结果的操作（数据库操作）。

### 5.4 两种工具执行模式

**1. 框架控制（默认）**：Spring AI 自动检测、执行、返回，开发者只写业务逻辑。

**2. 用户控制**：禁用内部执行，手动管理工具调用循环：

```java
ChatOptions chatOptions = ToolCallingChatOptions.builder()
    .toolCallbacks(ToolCallbacks.from(new WeatherTools()))
    .internalToolExecutionEnabled(false)  // 禁用自动执行
    .build();

ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();
Prompt prompt = new Prompt("获取编程导航的热门项目教程", chatOptions);
ChatResponse chatResponse = chatModel.call(prompt);

while (chatResponse.hasToolCalls()) {
    ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, chatResponse);
    prompt = new Prompt(result.conversationHistory(), chatOptions);
    chatResponse = chatModel.call(prompt);
}
```

> YuManus 智能体就是用用户控制模式，自行管理 think-act 循环。

### 5.5 异常处理

`ToolExecutionExceptionProcessor` 接口，默认策略是将异常信息返回给 AI（让 AI 调整策略），而非直接抛出。

```java
// 自定义：IO 异常返回友好消息，安全异常直接抛出
@Bean
ToolExecutionExceptionProcessor customExceptionProcessor() {
    return exception -> {
        if (exception.getCause() instanceof IOException) {
            return "Unable to access external resource. Please try a different approach.";
        } else if (exception.getCause() instanceof SecurityException) {
            throw exception;
        }
        return "Error executing tool: " + exception.getMessage();
    };
}
```

### 5.6 工具解析（ToolCallbackResolver）

按名称动态解析工具，默认用 `DelegatingToolCallbackResolver` 委托给：
- `SpringBeanToolCallbackResolver`：从 Spring 容器查找
- `StaticToolCallbackResolver`：从预先注册的列表查找

```java
// 按名称调用
chatClient.prompt().toolNames("weatherTool", "timeTool").call();
```

### 5.7 可观测性

目前只有 DEBUG 级别日志：
```yaml
logging:
  level:
    org.springframework.ai: DEBUG
```

高级方式：用代理模式包装 `ToolCallback` 或 `ToolCallingManager`，自定义监控和日志。

---

## 六、扩展思路

1. **更多工具**：邮件发送、时间工具、数据库操作
2. **优化 PDF 生成**：上传到 OSS 返回 URL + `returnDirect` 立即返回
3. **手动控制工具执行**：用 `ToolCallingManager` 补充日志记录
4. **文件解析能力**：用户上传 PDF → 程序解析 → 作为 AI 上下文