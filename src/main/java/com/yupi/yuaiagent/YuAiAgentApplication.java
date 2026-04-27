package com.yupi.yuaiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
// 启动类排除 PGVector 的自动配置，否则会因为多个 EmbeddingModel Bean 报错, 一个DashScope的一个ollama的
// @SpringBootApplication(exclude = PgVectorStoreAutoConfiguration.class)
@SpringBootApplication(exclude = {
        // 为了便于大家开发调试和部署，取消数据库自动配置，需要使用 PgVector 时把 DataSourceAutoConfiguration.class 删除
        DataSourceAutoConfiguration.class
})
public class YuAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuAiAgentApplication.class, args);
    }

}
