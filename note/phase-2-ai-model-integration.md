# 第2期：AI大模型接入

## 一、后端项目初始化（Spring Boot 3）

**环境要求**
- JDK 17 或 21（推荐21，支持虚拟线程）
- Spring Boot 3.4.4+

**新建项目**
- IDEA → Spring Initializr → Server URL = `https://start.spring.io`
- 依赖：Spring Web、Lombok
- 若Lombok报错，手动指定版本：
```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.36</version>
    <optional>true</optional>
</dependency>
```

**整合Hutool + Knife4j**

1. Hutool：
```xml
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-all</artifactId>
    <version>5.8.37</version>
</dependency>
```

2. Knife4j（适用于Spring Boot 3.x）：
```xml
<dependency>
    <groupId>com.github.xiaoymin</groupId>
    <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
    <version>4.4.0</version>
</dependency>
```

3. 测试Controller：
```java
@RestController
@RequestMapping("/health")
public class HealthController {
    @GetMapping
    public String healthCheck() {
        return "ok";
    }
}
```

4. application.yml配置：
```yaml
spring:
  application:
    name: yu-ai-agent
server:
  port: 8123
  servlet:
    context-path: /api

springdoc:
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: alpha
  api-docs:
    path: /v3/api-docs
  group-configs:
    - group: 'default'
      paths-to-match: '/**'
      packages-to-scan: com.yupi.yuaiagent.controller

knife4j:
  enable: true
  setting:
    language: zh_cn
```

启动后访问：`http://localhost:8123/api/doc.html`

> 通用基础代码（异常、包装类、跨域等）非本节重点，可参考其他项目。

---

## 二、程序调用AI大模型（4种方式）

**通用前提**
- 以阿里云百炼/灵积为例
- 申请API Key，存储于接口（仅测试用，生产用环境变量）：
```java
public interface TestApiKey {
    String API_KEY = "sk-xxxx";
}
```

---

### 1. SDK接入（dashscope-sdk-java）

**依赖**：
```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>dashscope-sdk-java</artifactId>
    <version>2.19.1</version>
</dependency>
```

**示例代码**：
```java
import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;

public class SdkAiInvoke {
    public static GenerationResult callWithMessage() throws Exception {
        Generation gen = new Generation();
        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content("You are a helpful assistant.")
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content("你是谁？")
                .build();
        GenerationParam param = GenerationParam.builder()
                .apiKey(TestApiKey.API_KEY)
                .model("qwen-plus")
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        return gen.call(param);
    }

    public static void main(String[] args) {
        try {
            GenerationResult result = callWithMessage();
            System.out.println(JsonUtils.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

---

### 2. HTTP接入（Hutool发送JSON请求）

**依赖**：已引入Hutool

**示例代码**（由AI生成后略作修改）：
```java
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;

public class HttpAiInvoke {
    public static void main(String[] args) {
        String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

        JSONObject requestBody = new JSONObject();
        requestBody.set("model", "qwen-plus");

        JSONObject input = new JSONObject();
        JSONObject[] messages = new JSONObject[2];
        messages[0] = new JSONObject().set("role", "system").set("content", "You are a helpful assistant.");
        messages[1] = new JSONObject().set("role", "user").set("content", "你是谁？");
        input.set("messages", messages);
        requestBody.set("input", input);

        JSONObject parameters = new JSONObject();
        parameters.set("result_format", "message");
        requestBody.set("parameters", parameters);

        HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + TestApiKey.API_KEY)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .execute();

        if (response.isOk()) {
            System.out.println(response.body());
        } else {
            System.out.println("失败：" + response.getStatus());
        }
    }
}
```

---

### 3. Spring AI Alibaba 接入

**依赖**：
```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter</artifactId>
    <version>1.0.0-M6.1</version>
</dependency>
```

**额外仓库**（解决依赖解析）：
```xml
<repositories>
    <repository>
        <id>spring-milestones</id>
        <name>Spring Milestones</name>
        <url>https://repo.spring.io/milestone</url>
    </repository>
</repositories>
```

**application.yml配置**：
```yaml
spring:
  ai:
    dashscope:
      api-key: ${AI_DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-plus
```

**示例代码**（启动时自动调用）：
```java
@Component
public class SpringAiAiInvoke implements CommandLineRunner {
    @Resource
    private ChatModel dashscopeChatModel;

    @Override
    public void run(String... args) throws Exception {
        AssistantMessage output = dashscopeChatModel.call(new Prompt("你好，我是threetwoa"))
                .getResult()
                .getOutput();
        System.out.println(output.getText());
    }
}
```

---

### 4. LangChain4j 接入（DashScope版）

**依赖**：
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-dashscope</artifactId>
    <version>1.0.0-beta2</version>
</dependency>
```

**示例代码**：
```java
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;

public class LangChainAiInvoke {
    public static void main(String[] args) {
        ChatLanguageModel qwenModel = QwenChatModel.builder()
                .apiKey(TestApiKey.API_KEY)
                .modelName("qwen-max")
                .build();
        String answer = qwenModel.chat("我是threetwoa，这是threetwoa.me的原创项目教程");
        System.out.println(answer);
    }
}
```

---

## 三、本地部署 + Spring AI 调用 Ollama

**1. 安装Ollama并运行模型**
- 下载安装Ollama
- 终端执行：`ollama run gemma3:1b`（或其他小模型）
- 访问 `http://localhost:11434` 验证服务

**2. Spring AI调用Ollama**

**依赖**：
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
    <version>1.0.0-M6</version>
</dependency>
```
同样需要加上milestone仓库。

**application.yml**：
```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: gemma3:1b
```

**测试代码**：
```java
@Component
public class OllamaAiInvoke implements CommandLineRunner {
    @Resource
    private ChatModel ollamaChatModel;

    @Override
    public void run(String... args) throws Exception {
        AssistantMessage output = ollamaChatModel.call(new Prompt("你好，我是threetwoa"))
                .getResult()
                .getOutput();
        System.out.println(output.getText());
    }
}
```

启动Spring Boot项目即可看到本地模型的回复。

---

## 四、接入方式对比

| 方式 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| SDK | 类型安全、错误完善 | 依赖特定版本 | 单一模型、深度集成 |
| HTTP | 无语言限制 | 手动处理细节 | 原型验证、临时调用 |
| Spring AI | 统一接口、生态融合 | 抽象可能丢失特殊特性 | Spring项目、多模型切换 |
| LangChain4j | 完整AI工具链 | 学习曲线较陡 | 复杂链式/RAG应用 |

**本教程后续选用 Spring AI Alibaba**，因为生态主流、易用、资源多。