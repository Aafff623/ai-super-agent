package com.yupi.yuaiagent.tools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 网页抓取工具
 * Jsoup 是Java爬虫利器，会自动处理编码、重定向等
 * 返回整个HTML可能很庞大，可以进一步提取正文（例如 doc.body().text()）。但为了通用性，先返回所有内容。
 * 风险：某些网站有反爬机制，需要添加 User-Agent或延时。
 */
public class WebScrapingTool {

    @Tool(description = "Scrape the content of a web page")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            Document document = Jsoup.connect(url).get(); // Jsoup 发送请求并解析HTML
            return document.html();                       // 返回整个HTML源码
        } catch (Exception e) {
            return "Error scraping web page: " + e.getMessage();
        }
    }
}
