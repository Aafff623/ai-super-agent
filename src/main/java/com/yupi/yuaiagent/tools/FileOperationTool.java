package com.yupi.yuaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.yupi.yuaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 文件操作工具类（提供文件读写功能）
 * @Tool 告诉Spring AI：这个方法可以被AI调用。description 很关键，AI会根据描述匹配用户问题。
 * @ToolParam 给出参数说明，AI会按这个说明提取参数。
 * 为什么返回值是String？因为AI最终要“阅读”结果，字符串最通用。如果是复杂对象，Spring AI会自动转JSON
 * 错误处理不抛异常，而是返回带错误信息的字符串，这样AI可以读懂并告诉用户。
 */
public class FileOperationTool {

    // 所有文件保存到项目根目录下的 /tmp/file 目录，避免污染项目文件
    private final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";

    /**
     * 从文件中读取内容
     * @param fileName
     * @return
     */
    @Tool(description = "Read content from a file") // description 帮助AI判断何时使用
    public String readFile(@ToolParam(description = "Name of a file to read") String fileName) {
        String filePath = FILE_DIR + "/" + fileName;
        try {
            return FileUtil.readUtf8String(filePath); // Hutool 工具，自动处理编码
        } catch (Exception e) {
            // 返回错误信息而不是抛出异常，让AI能理解失败原因
            return "Error reading file: " + e.getMessage();
        }
    }

    /**
     * 将内容写入文件
     * @param fileName
     * @param content
     * @return
     */
    @Tool(description = "Write content to a file")
    public String writeFile(@ToolParam(description = "Name of the file to write") String fileName,
                            @ToolParam(description = "Content to write to the file") String content
    ) {
        String filePath = FILE_DIR + "/" + fileName;

        try {
            // 创建目录
            FileUtil.mkdir(FILE_DIR); // 确保目录存在
            FileUtil.writeUtf8String(content, filePath);
            return "File written successfully to: " + filePath;
        } catch (Exception e) {
            return "Error writing to file: " + e.getMessage();
        }
    }
}
