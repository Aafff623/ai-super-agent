package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.model.AgentState;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * BaseAgent 单元测试
 */
class BaseAgentTest {

    private TestBaseAgent agent;
    private ChatModel mockChatModel;

    @BeforeEach
    void setUp() {
        mockChatModel = mock(ChatModel.class);
        agent = new TestBaseAgent();
        agent.setMaxSteps(5);
        agent.setName("TestBaseAgent");

        // 设置 ChatClient
        ChatClient chatClient = ChatClient.builder(mockChatModel).build();
        agent.setChatClient(chatClient);
    }

    @AfterEach
    void tearDown() {
        agent.cleanup();
    }

    // ==================== 初始化测试 ====================

    /**
     * 测试初始状态
     */
    @Test
    void testInitialState() {
        assertEquals(AgentState.IDLE, agent.getState(), "初始状态应该是 IDLE");
    }

    /**
     * 测试名称设置
     */
    @Test
    void testSetName() {
        String name = "TestName";
        agent.setName(name);
        assertEquals(name, agent.getName(), "名称应该匹配");
    }

    /**
     * 测试提示词设置
     */
    @Test
    void testPrompts() {
        agent.setSystemPrompt("系统提示词");
        agent.setNextStepPrompt("下一步提示词");

        assertEquals("系统提示词", agent.getSystemPrompt(), "系统提示词应该匹配");
        assertEquals("下一步提示词", agent.getNextStepPrompt(), "下一步提示词应该匹配");
    }

    // ==================== 执行测试 ====================

    /**
     * 测试正常执行
     */
    @Test
    void testSuccessfulExecution() {
        // given: 状态为 IDLE
        agent.cleanup();
        agent.setState(AgentState.IDLE);

        // when: 执行
        String result = agent.run("测试提示词");

        // then: 检查结果
        assertNotNull(result, "结果不应该为空");
        assertTrue(result.contains("Step"), "结果应该包含步骤信息");
        assertEquals(AgentState.FINISHED, agent.getState(), "状态应该是 FINISHED");
    }

    /**
     * 测试空提示词异常
     */
    @Test
    void testEmptyPrompt() {
        agent.cleanup();
        agent.setState(AgentState.IDLE);

        Assertions.assertThrows(RuntimeException.class, () -> {
            agent.run("");
        }, "空提示词应该抛出异常");
    }

    /**
     * 测试 null 提示词异常
     */
    @Test
    void testNullPrompt() {
        agent.cleanup();
        agent.setState(AgentState.IDLE);

        Assertions.assertThrows(RuntimeException.class, () -> {
            agent.run(null);
        }, "null 提示词应该抛出异常");
    }

    /**
     * 测试运行中状态异常
     */
    @Test
    void testRunningState() {
        agent.setState(AgentState.RUNNING);

        Assertions.assertThrows(RuntimeException.class, () -> {
            agent.run("测试");
        }, "RUNNING 状态应该抛出异常");
    }

    /**
     * 测试错误状态异常
     */
    @Test
    void testErrorState() {
        agent.setState(AgentState.ERROR);

        Assertions.assertThrows(RuntimeException.class, () -> {
            agent.run("测试");
        }, "ERROR 状态应该抛出异常");
    }

    /**
     * 测试异常处理
     */
    @Test
    void testExceptionHandling() {
        // given: 配置代理抛出异常
        agent.setShouldFail(true);
        agent.cleanup();
        agent.setState(AgentState.IDLE);

        // when: 执行
        String result = agent.run("测试");

        // then: 检查错误处理
        assertNotNull(result, "结果不应该为空");
        assertTrue(result.contains("执行错误"), "结果应该包含错误信息");
        assertEquals(AgentState.ERROR, agent.getState(), "状态应该是 ERROR");
    }

    /**
     * 测试最大步骤限制
     */
    @Test
    void testMaxSteps() {
        // given: 设置最大步骤为 2
        agent.setMaxSteps(2);
        agent.setReturnShouldAct(true);
        agent.cleanup();
        agent.setState(AgentState.IDLE);

        // when: 执行
        String result = agent.run("测试");

        // then: 检查最大步骤
        assertNotNull(result, "结果不应该为空");
        assertTrue(result.contains("Terminated"), "结果应该包含终止信息");
    }

    /**
     * 测试消息记录
     */
    @Test
    void testMessageRecording() {
        agent.cleanup();
        agent.setState(AgentState.IDLE);

        try {
            agent.run("测试");
        } catch (Exception e) {
            // 预期异常
        }

        assertFalse(agent.getMessageList().isEmpty(), "消息列表不应该为空");
    }

    /**
     * 测试清理方法
     */
    @Test
    void testCleanup() {
        agent.cleanup();
        agent.setState(AgentState.IDLE);

        agent.run("测试");

        // when: 调用 cleanup()
        agent.cleanup();

        assertTrue(true, "cleanup 应该成功执行");
    }

    /**
     * 测试状态枚举值
     */
    @Test
    void testStateEnum() {
        assertEquals("IDLE", AgentState.IDLE.name());
        assertEquals("RUNNING", AgentState.RUNNING.name());
        assertEquals("FINISHED", AgentState.FINISHED.name());
        assertEquals("ERROR", AgentState.ERROR.name());
    }

    // ==================== 内部测试类 ====================

    private static class TestBaseAgent extends BaseAgent {

        private boolean shouldFail = false;
        private boolean returnShouldAct = false;

        @Override
        public String step() {
            if (shouldFail) {
                throw new RuntimeException("测试异常");
            }
            return returnShouldAct ? "步骤 - 需要继续" : "步骤 - 无需行动";
        }

        public void setShouldFail(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }

        public void setReturnShouldAct(boolean returnShouldAct) {
            this.returnShouldAct = returnShouldAct;
        }
    }
}
