package com.yupi.yuaiagent.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 集中的工具注册类 在 loveApp 中进行绑定
 */
@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    /**
     * 集中注册点：新增/删除工具只需改这里一处。
     * @return 其他地方可以直接@Resource注入 ToolCallback[] 来获取所有工具的回调对象
     */
    @Bean
    public ToolCallback[] allTools() {

        // 注册 文件下载, 网页搜索, 网页抓取, 资源下载, 终端操作, PDF生成, 终止工具 等工具
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        TerminateTool terminateTool = new TerminateTool();

        // ToolCallbacks.from 将多个工具对象转换成统一的 ToolCallback 数组,  ToolCallbacks.from 是工厂方法，将普通对象适配成Spring AI需要的接口。
        return ToolCallbacks.from(
                fileOperationTool, webSearchTool, webScrapingTool,
                resourceDownloadTool, terminalOperationTool, pdfGenerationTool,
                terminateTool
        );
    }
}
