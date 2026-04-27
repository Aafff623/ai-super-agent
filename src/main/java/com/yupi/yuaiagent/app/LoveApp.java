package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.advisor.ReReadingAdvisor;
import com.yupi.yuaiagent.chatmemory.FileBasedChatMemory;
import com.yupi.yuaiagent.rag.LoveAppRagCustomAdvisorFactory;
import com.yupi.yuaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@Slf4j
// 使用 ChatClient：高级封装，可以配置记忆、拦截器、模板等, 而非没有记忆、没有额外处理的 ChatModel
public class LoveApp {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = "扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。" +
            "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；" +
            "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。" +
            "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。";

    /**
     *  构造恋爱大师的聊天客户端
     * @param dashscopeChatModel
     */
    public LoveApp(ChatModel dashscopeChatModel) {
//        // 初始化基于文件的对话记忆
//        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
//        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
        // 初始化基于内存的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        // 建造者模式构造 ChatClient
        chatClient = ChatClient.builder(dashscopeChatModel)
                // 设置系统的提示词 (定义 AI 的固定人设)
                .defaultSystem(SYSTEM_PROMPT)
                // 配置拦截器链
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(), // 在发送请求前把历史对话塞进当前的Prompt里，让AI看到上下文
                        // 自定义日志 Advisor，记录每次请求和响应的日志（方便调试), 检查用户输入是否包含违禁词
                        new MyLoggerAdvisor()
//                        // 让AI重读问题来提高回答质量，可按需开启, 不过会造成 token 翻倍
//                       ,new ReReadingAdvisor()
                )
                .build();
    }

    /**
     * 多轮对话记忆 - 基于内存的对话记忆
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                // 指定对话的 id 和 对话记忆的大小
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    // 想要结构化的输出, 即可以直接在程序里使用的Java对象类示例(将AI的Prompt -> json -> java对象),  定义报告结构（record是Java14+的简洁类）
    record LoveReport(String title, List<String> suggestions) {

    }

    /**
     * AI 恋爱报告功能（实战结构化输出）
     * @param message
     * @param chatId
     * @return
     */
    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表") // 附加的指令
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)) // 多轮对话记忆
                .call()
                // 这个是 转成 java 的实体类, 还有其他的: MapOutputConverter：转成Map, ListOutputConverter：转成List, BeanOutputConverter：转成任意Bean
                .entity(LoveReport.class); // 关键：告诉框架转成 LoveReport
        log.info("loveReport: {}", loveReport);
        return loveReport;
    }

    // AI 恋爱知识库问答功能

    @Resource
    private VectorStore loveAppVectorStore;

    @Resource
    private Advisor loveAppRagCloudAdvisor;

    @Resource
    private VectorStore pgVectorVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    /**
     * 和 RAG 知识库进行对话
     * Spring AI 内置的 QuestionAnswerAdvisor，能自动完成 “检索 → 增强 prompt → 调用模型 ”
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message, String chatId) {
        // 查询重写
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                // 使用改写后的查询
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor()) // 日志
                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore)) // rag增强, 底层: 调用 vectorStore.similaritySearch(userText)，返回最相似的 N 个 Document。
//              .advisors(loveAppRagCloudAdvisor) // 直接使用云检索 Advisor
//              .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore)) // 基于 pgvector 的向量数据库进行 RAG 增强
                 // 应用自定义的 RAG 检索增强服务（文档查询器 + 上下文增强器）
//                .advisors(
//                        LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(
//                                loveAppVectorStore, "单身"
//                        )
//                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // 注入所有注册的工具
    @Resource
    private ToolCallback[] allTools;

    /**
     * AI 恋爱报告功能（支持调用工具), chatClient 内部会判断：若用户问题需要工具，则返回“工具调用请求”，框架自动执行工具，并将结果再次发给AI，直到生成最终答案。
     * 不同的提示词可能会触发不同的工具, 但是 AI 具有随机性, 可能需要微调描述进行选择工具的调整
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt().user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)) // 会话记忆
                .advisors(new MyLoggerAdvisor())    // 开启日志，便于观察效果
                .toolCallbacks(allTools)            // 关键：把工具数组提供给AI
                .call().chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    //Spring AI 自动把每个 MCP 服务暴露的工具包装成 ToolCallback，然后通过这个 Provider 统一提供。
    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * AI 恋爱报告功能（调用 MCP 服务）
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithMcp(String message, String chatId) {

        ChatResponse chatResponse = chatClient
                .prompt().user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)) // 会话记忆
                .advisors(new MyLoggerAdvisor())        // 开启调试日志观察
                // toolCallbackProvider.getToolCallbacks() 会返回一个数组，里面是所有 MCP 服务提供的工具。chatClient.tools() 接受这个数组，AI 就能看到并使用这些工具。
                .toolCallbacks(toolCallbackProvider)    // 关键：把 MCP 提供的工具全部挂上
                .call().chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}
