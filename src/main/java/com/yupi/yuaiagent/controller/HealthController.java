package com.yupi.yuaiagent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查的接口
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    /**
     * 简单的健康检查接口，返回 "ok" 表示服务正常运行
     * @return
     */
    @GetMapping
    public String healthCheck() {
        return "ok";
    }
}
