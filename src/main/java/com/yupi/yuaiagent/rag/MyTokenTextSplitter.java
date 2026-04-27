package com.yupi.yuaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自定义基于 Token 的切词器
 */
@Component
class MyTokenTextSplitter {
    // 使用默认参数切分
    public List<Document> splitDocuments(List<Document> documents) {
        TokenTextSplitter splitter = new TokenTextSplitter();
        return splitter.apply(documents);
    }

    // 自定义参数切分
    public List<Document> splitCustomized(List<Document> documents) {
        // 按 token 数切分，并尝试在句子边界（句号、换行）处切断，保留语义完整性
            // 更好的切割策略：智能切分（云平台）+ 人工二次校验。
        TokenTextSplitter splitter = new TokenTextSplitter(200, 100, 10, 5000, true);
        return splitter.apply(documents);
    }
}
