package com.yupi.yuaiagent.rag;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/**
 * 创建上下文查询增强器的工厂 (自定义错误提示 上下文)
 * 当检索不到文档时，AI 会使用这个模板中的内容作为回复，而不是默认的“不知道”, 可以嵌入自己的品牌话术和引导链接
 */
public class LoveAppContextualQueryAugmenterFactory {

    public static ContextualQueryAugmenter createInstance() {
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                你应该输出下面的内容：
                抱歉，我只能回答恋爱相关的问题，别的没办法帮到您哦，
                有问题可以联系客服 https://threetwoa.me
                """);
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)  // 不启用空上下文，但自定义了模板
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }
}
