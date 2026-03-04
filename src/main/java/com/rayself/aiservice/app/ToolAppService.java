package com.rayself.aiservice.app;

import com.alibaba.fastjson.JSONObject;
import com.rayself.aiservice.infrastructure.utils.FileUtils;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ToolAppService {
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    /**
     * 获取系统相关的命令解释器
     */
    public static List<String> getShellCommand(String command) {
        List<String> cmdList = new ArrayList<>();

        if (IS_WINDOWS) {
            // Windows使用cmd.exe
            cmdList.add("cmd.exe");
            cmdList.add("/c");
            cmdList.add(command);
        } else {
            // Linux/Unix/Mac使用/bin/sh
            cmdList.add("/bin/sh");
            cmdList.add("-c");
            cmdList.add(command);
        }

        return cmdList;
    }

    @Tool
    public static String runBash(JSONObject jsonObject) {
        String command = jsonObject.getString("command");
        return FileUtils.runBash(command);
    }

    public static String readFile(JSONObject jsonObject) {
        String path = jsonObject.getString("path");
        Integer limit = jsonObject.getInteger("limit");
        return FileUtils.runRead(path, limit);
    }

    public static String writeFile(JSONObject jsonObject) {
        String path = jsonObject.getString("path");
        String content = jsonObject.getString("content");
        return FileUtils.runWrite(path, content);
    }

    public static String editFile(JSONObject jsonObject) {
        String path = jsonObject.getString("path");
        String oldText = jsonObject.getString("oldText");
        String newText = jsonObject.getString("newText");
        return FileUtils.runEdit(path, oldText, newText);
    }

    public List<ToolSpecification> toolSpecification() {
        List<ToolSpecification> tools = new ArrayList<>();
        tools.add(ToolSpecification.builder()
                .name("runBash")
                .description("Run a shell command.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("command", "bash执行的命令")
                        .required("command") // 必须明确指定必需的属性
                        .build())
                .build());
        tools.add(ToolSpecification.builder()
                .name("readFile")
                .description("Read file contents.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "文件路径")
                        .addIntegerProperty("limit", "最大行数")
                        .required("path") // 必须明确指定必需的属性
                        .build())
                .build());
        tools.add(ToolSpecification.builder()
                .name("writeFile")
                .description("Write content to file.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "文件路径")
                        .addStringProperty("content", "文件内容")
                        .required("path", "content") // 必须明确指定必需的属性
                        .build())
                .build());
        tools.add(ToolSpecification.builder()
                .name("editFile")
                .description("Replace exact text in file.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "文件路径")
                        .addStringProperty("oldText", "旧文件内容")
                        .addStringProperty("newText", "新文件内容")
                        .required("path", "oldText", "newText") // 必须明确指定必需的属性
                        .build())
                .build());
        return tools;
    }


}
