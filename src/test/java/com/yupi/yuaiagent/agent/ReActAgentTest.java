package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.model.AgentState;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ReActAgent 单元测试
 */
class ReActAgentTest {

    private TestReActAgent agent;
    private ChatModel mockChatModel;

    @BeforeEach
    void setUp() {
        mockChatModel = mock(ChatModel.class);
        agent = new TestReActAgent(mockChatModel);
        agent.setMaxSteps(3);
    }

    @AfterEach
    void tearDown() {
        agent.cleanup();
    }

    /**
     * 测试完整的 think-act 循环
     */
    @Test
    void testThinkActCycle() {
        // given: 配置代理
        agent.setSystemPrompt("测试提示词");
        agent.cleanup();
        agent.setState(AgentState.IDLE);

        // when: 执行单步
        String result = agent.step();

        // then: 检查结果
        assertNotNull(result, "结果不应该为空");
    }

    /**
     * 测试空提示词处理
     */
    @Test
    void testEmptyPrompt() {
        // given: 状态为 IDLE
        agent.cleanup();
        agent.setState(AgentState.IDLE);

        // when & then: 应该抛出异常
        Assertions.assertThrows(RuntimeException.class, () -> {
            agent.run("");
        }, "空提示词应该抛出异常");
    }

    /**
     * 测试 null 提示词处理
     */
    @Test
    void testNullPrompt() {
        // given: 状态为 IDLE
        agent.cleanup();
        agent.setState(AgentState.IDLE);

        // when & then: 应该抛出异常
        Assertions.assertThrows(RuntimeException.class, () -> {
            agent.run(null);
        }, "null 提示词应该抛出异常");
    }

    /**
     * 测试异常处理
     */
    @Test
    void testExceptionHandling() {
        // given: 配置代理抛出异常
        agent.setThrowException(true);
        agent.cleanup();
        agent.setState(AgentState.IDLE);

        // when: 执行
        String result = agent.run("测试");

        // then: 检查错误处理
        assertNotNull(result, "结果不应该为空");
        // 异常会被捕获并记录，状态变为 ERROR
        // 但由于 maxSteps 限制，最终可能是 FINISHED
        assertTrue(result.contains("执行错误") || result.contains("Terminated"), "结果应该包含错误或终止信息");
    }

    /**
     * 测试最大步骤限制
     */
    @Test
    void testMaxSteps() {
        // given: 设置最大步骤为 2
        agent.setMaxSteps(2);
        agent.cleanup();
        agent.setState(AgentState.IDLE);

        // when: 执行
        String result = agent.run("测试");

        // then: 检查最大步骤
        assertNotNull(result, "结果不应该为空");
    }

    /**
     * 测试清理方法
     */
    @Test
    void testCleanup() {
        // given: 状态为 RUNNING
        agent.cleanup();
        agent.setState(AgentState.IDLE);

        // when: 调用 cleanup()
        agent.cleanup();

        // then: 不应该抛出异常
        assertTrue(true, "cleanup 应该成功执行");
    }

    /**
     * 测试初始状态
     */
    @Test
    void testInitialState() {
        // given: 新创建的 agent
        TestReActAgent newAgent = new TestReActAgent(mockChatModel);

        // then: 初始状态应该是 IDLE
        assertEquals(AgentState.IDLE, newAgent.getState(), "初始状态应该是 IDLE");
    }

    /**
     * 测试名称设置
     */
    @Test
    void testSetName() {
        // given: 设置名称
        String name = "TestName";

        // when: 设置名称
        agent.setName(name);

        // then: 检查名称
        assertEquals(name, agent.getName(), "名称应该匹配");
    }

    /**
     * 测试消息列表
     */
    @Test
    void testMessageList() {
        // given: 状态为 IDLE
        agent.cleanup();
        agent.setState(AgentState.IDLE);

        // when: 执行
        try {
            agent.run("测试");
        } catch (Exception e) {
            // 预期异常
        }

        // then: 消息列表不应该为空
        assertFalse(agent.getMessageList().isEmpty(), "消息列表不应该为空");
    }

    // ==================== 内部测试类 ====================

    private static class TestReActAgent extends ReActAgent {

        private boolean throwException = false;

        public TestReActAgent(ChatModel chatModel) {
            super();
            setChatClient(org.springframework.ai.chat.client.ChatClient.builder(chatModel).build());
        }

        @Override
        public boolean think() {
            if (throwException) {
                throw new RuntimeException("think 异常");
            }
            return false;
        }

        @Override
        public String act() {
            if (throwException) {
                throw new RuntimeException("act 异常");
            }
            return "act 结果";
        }

        public void setThrowException(boolean throwException) {
            this.throwException = throwException;
        }
    }
}
