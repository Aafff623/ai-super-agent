package com.yupi.yuaiagent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 图片搜索工具（使用 Pexels API）
 */
@Service
public class ImageSearchTool {

    // todo: API Key  和 请求地址 (替换成自己的 key ), 可以通过 value 注入
    private static final String API_KEY = "改为你的 API Key";
    private static final String API_URL = "https://api.pexels.com/v1/search";

    /**
     * 使用 Pexels API 搜索图片
     * @param query
     * @return 返回中等尺寸的图片链接列表（逗号分隔）
     */
    @Tool(description = "search image from web")
    public String searchImage(@ToolParam(description = "Search query keyword") String query) {
        try {
            return String.join(",", searchMediumImages(query));
        } catch (Exception e) {
            return "Error search image: " + e.getMessage();
        }
    }

    /**
     * 搜索中等尺寸的图片列表 (真正请求 Pexels 的方法)
     * @param query
     * @return
     */
    public List<String> searchMediumImages(String query) {
        // 构造请求头, 通过请求头传送 API Key
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", API_KEY);

        // 设置请求参数, 用户搜索的关键词
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);

        // 发送 HTTP GET 请求 (Hutool 工具包)
        String response = HttpUtil.createGet(API_URL)
                .addHeaders(headers)
                .form(params)
                .execute()
                .body();

        // 解析图片的地址, 响应JSON （假设响应结构包含"photos"数组，每个元素包含"medium"字段）
        return JSONUtil.parseObj(response)
                .getJSONArray("photos")
                .stream()
                .map(photoObj -> (JSONObject) photoObj)
                .map(photoObj -> photoObj.getJSONObject("src"))
                .map(photo -> photo.getStr("medium"))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }
}
