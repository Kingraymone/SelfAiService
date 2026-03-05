package com.rayself.aiservice.app;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.rayself.aiservice.infrastructure.utils.FileUtils;
import com.rayself.aiservice.infrastructure.utils.TodoManager;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.fasterxml.jackson.databind.type.LogicalType.Map;

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
    public static String runBash(String arguments) {
        JSONObject jsonObject = JSONObject.parseObject(arguments);
        String command = jsonObject.getString("command");
        return FileUtils.runBash(command);
    }

    public static String readFile(String arguments) {
        JSONObject jsonObject = JSONObject.parseObject(arguments);
        String path = jsonObject.getString("path");
        Integer limit = jsonObject.getInteger("limit");
        return FileUtils.runRead(path, limit);
    }

    public static String writeFile(String arguments) {
        JSONObject jsonObject = JSONObject.parseObject(arguments);
        String path = jsonObject.getString("path");
        String content = jsonObject.getString("content");
        return FileUtils.runWrite(path, content);
    }

    public static String editFile(String arguments) {
        JSONObject jsonObject = JSONObject.parseObject(arguments);
        String path = jsonObject.getString("path");
        String oldText = jsonObject.getString("oldText");
        String newText = jsonObject.getString("newText");
        return FileUtils.runEdit(path, oldText, newText);
    }

    public static String todo(String arguments){
        JSONObject jsonObject = JSONObject.parseObject(arguments);
        Object itemsArray = jsonObject.get("items");
        String itemsArrayStr = JSONObject.toJSONString(itemsArray);
        List<Map<String, Object>> items = JSONObject.parseObject(itemsArrayStr, new TypeReference<List<Map<String, Object>>>() {
        });
        TodoManager todoManager = new TodoManager();
        return todoManager.update(items);
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
        tools.add(ToolSpecification.builder()
                .name("todo")
                .description("Update task list. Track progress on multi-step tasks.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("items",
                                JsonArraySchema.builder()
                                        .items(
                                                JsonObjectSchema.builder()
                                                        .addStringProperty("id", "任务的唯一标识符")
                                                        .addStringProperty("text", "任务的具体内容")
                                                        .addEnumProperty("status", Arrays.asList("pending", "in_progress", "completed"), "任务的当前状态。")
                                                        .required("id", "text", "status")
                                                        .build()
                                        ).description("一个包含所有待办事项对象的列表。").build())
                        .required("items")
                        .build())
                .build());
        return tools;
    }


}
