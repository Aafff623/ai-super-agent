package com.yupi.yuaiagent.rag;

import org.junit.jupiter.api.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * QueryRewriter 单元测试
 */
class QueryRewriterTest {

    private QueryRewriter queryRewriter;
    private org.springframework.ai.chat.model.ChatModel mockChatModel;

    @BeforeEach
    void setUp() {
        mockChatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
        queryRewriter = new QueryRewriter(mockChatModel);
    }

    @AfterEach
    void tearDown() {
        // 清理资源
    }

    // ==================== 基础测试 ====================

    /**
     * 测试基本查询重写
     */
    @Test
    void testBasicQueryRewrite() {
        // given: 原始查询
        String originalQuery = "怎么学编程";

        // when: 执行查询重写
        String rewrittenQuery = queryRewriter.doQueryRewrite(originalQuery);

        // then: 检查重写后的查询
        assertNotNull(rewrittenQuery, "重写后的查询不应该为 null");
        assertTrue(rewrittenQuery.length() > 0, "重写后的查询应该有内容");
    }

    /**
     * 测试空查询处理
     */
    @Test
    void testEmptyQuery() {
        // given: 空查询
        String emptyQuery = "";

        // when: 执行查询重写
        String rewrittenQuery = queryRewriter.doQueryRewrite(emptyQuery);

        // then: 应该返回空值（不做处理）
        assertEquals("", rewrittenQuery, "空查询应该返回空值");
    }

    /**
     * 测试 null 查询处理
     */
    @Test
    void testNullQuery() {
        // given: null 查询
        String nullQuery = null;

        // when: 执行查询重写
        String rewrittenQuery = queryRewriter.doQueryRewrite(nullQuery);

        // then: 应该返回 null（不做处理）
        assertNull(rewrittenQuery, "null 查询应该返回 null");
    }

    /**
     * 测试特殊字符
     */
    @Test
    void testSpecialCharacters() {
        String specialQuery = "如何??? 学习编程???";
        String rewrittenQuery = queryRewriter.doQueryRewrite(specialQuery);

        assertNotNull(rewrittenQuery, "重写后的查询不应该为 null");
    }

    /**
     * 测试长查询
     */
    @Test
    void testLongQuery() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("这是一段测试文本 ").append(i).append(" ");
        }
        String longQuery = sb.toString();

        String rewrittenQuery = queryRewriter.doQueryRewrite(longQuery);

        assertNotNull(rewrittenQuery, "重写后的查询不应该为 null");
    }

    // ==================== 边界测试 ====================

    /**
     * 测试重复调用
     */
    @Test
    void testRepeatedCalls() {
        String query = "重复测试";

        String result1 = queryRewriter.doQueryRewrite(query);
        String result2 = queryRewriter.doQueryRewrite(query);
        String result3 = queryRewriter.doQueryRewrite(query);

        assertNotNull(result1, "结果1不应该为 null");
        assertNotNull(result2, "结果2不应该为 null");
        assertNotNull(result3, "结果3不应该为 null");
    }

    // ==================== 性能测试 ====================

    /**
     * 测试批量查询
     */
    @Test
    void testBatchQuery() {
        int count = 10;
        List<String> results = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String query = "批量查询 " + i;
            String rewritten = queryRewriter.doQueryRewrite(query);
            results.add(rewritten);
        }

        assertEquals(count, results.size(), "结果数量应该匹配");
        for (String result : results) {
            assertNotNull(result, "每个结果都不应该为 null");
        }
    }

    /**
     * 测试并发查询
     */
    @Test
    void testConcurrentQuery() throws InterruptedException {
        int threadCount = 5;
        String[] results = new String[threadCount];
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = queryRewriter.doQueryRewrite("并发查询 " + index);
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        for (String result : results) {
            assertNotNull(result, "并发结果不应该为 null");
        }
    }
}
