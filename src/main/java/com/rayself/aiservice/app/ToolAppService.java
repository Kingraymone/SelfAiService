package com.rayself.aiservice.app;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.rayself.aiservice.infrastructure.utils.FileUtils;
import com.rayself.aiservice.infrastructure.utils.TodoManager;
import com.rayself.aiservice.skill.SkillLoader;
import com.rayself.aiservice.task.TaskManager;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.rayself.aiservice.app.AgentAppService.SUB_SYSTEM_MESSAGE;
import static com.rayself.aiservice.app.AgentAppService.findToolMap;
import static java.time.Duration.ofSeconds;

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

    public static String todo(String arguments) {
        JSONObject jsonObject = JSONObject.parseObject(arguments);
        Object itemsArray = jsonObject.get("items");
        String itemsArrayStr = JSONObject.toJSONString(itemsArray);
        List<Map<String, Object>> items = JSONObject.parseObject(itemsArrayStr, new TypeReference<List<Map<String, Object>>>() {
        });
        TodoManager todoManager = new TodoManager();
        return todoManager.update(items);
    }

    public String task(String arguments) {
        JSONObject jsonObject = JSONObject.parseObject(arguments);
        String prompt = jsonObject.getString("prompt");
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(System.getenv("deepseek-api-key"))
                .modelName("deepseek-chat")
                .temperature(0.3)
                .timeout(ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
        // todo 系统消息增加
        List<ChatMessage> messageList = new ArrayList<>();
        SystemMessage systemMessage = SystemMessage.from(SUB_SYSTEM_MESSAGE);
        messageList.add(systemMessage);
        messageList.add(UserMessage.from(String.format(prompt)));
        return subAgentLoop(model, messageList);
    }

    public String loadSkill(String arguments) {
        JSONObject jsonObject = JSONObject.parseObject(arguments);
        String name = jsonObject.getString("name");
        return SkillLoader.SKILL_LOADER.getContent(name);
    }

    public String compact(String arguments) {
        log.info("Manual compression requested.");
        return "Compressing...";
    }

    public String taskCreate(String arguments) {
        JSONObject jsonObject = JSONObject.parseObject(arguments);
        String subject = jsonObject.getString("subject");
        String description = jsonObject.getString("description");
        TaskManager.create(subject, description);
        return "task create success";
    }

    public String taskUpdate(String arguments) {
        JSONObject jsonObject = JSONObject.parseObject(arguments);
        Integer taskId = jsonObject.getInteger("taskId");
        String status = jsonObject.getString("status");
        JSONArray addBlockedByArray = jsonObject.getJSONArray("addBlockedBy");
        List<Integer> addBlockedBy = ObjectUtils.isEmpty(addBlockedByArray) ? null : addBlockedByArray.toJavaList(Integer.class);
        JSONArray addBlocksArray = jsonObject.getJSONArray("addBlocks");
        List<Integer> addBlocks = ObjectUtils.isEmpty(addBlocksArray) ? null : addBlocksArray.toJavaList(Integer.class);
        TaskManager.update(taskId, status, addBlockedBy, addBlocks);
        return "task update success";
    }

    public String taskList(String arguments) {
        return TaskManager.listAll();
    }

    public String taskGet(String arguments) {
        JSONObject jsonObject = JSONObject.parseObject(arguments);
        Integer taskId = jsonObject.getInteger("taskId");
        return TaskManager.get(taskId);
    }

    public String subAgentLoop(OpenAiChatModel model, List<ChatMessage> messageList) {
        for (int i = 0; i < 20; i++) {
            // 消息
            ChatRequest request = ChatRequest.builder()
                    .messages(messageList)
                    .parameters(ChatRequestParameters.builder()
                            .temperature(0.3)
                            .toolSpecifications(childToolSpecification())
                            .build())
                    .build();
            ChatResponse chatResponse = model.chat(request);
            // 模型消息追加
            messageList.add(chatResponse.aiMessage());
            if (!chatResponse.aiMessage().hasToolExecutionRequests()) {
                break;
            }
            // todo 获取执行结果中存在方法调用的信息，调用命令行参数
            Map<String, Method> toolMap = findToolMap();
            for (ToolExecutionRequest executionRequest : chatResponse.aiMessage().toolExecutionRequests()) {
                Method method = toolMap.get(executionRequest.name());
                if (ObjectUtils.isEmpty(method)) {
                    continue;
                }
                String toolResultContent = "";
                try {
                    // 工具方法名匹配，获取参数执行方法调用
                    Object result = method.invoke(this, executionRequest.arguments());
                    toolResultContent = result.toString();
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    return e.getMessage();
                }
                messageList.add(ToolExecutionResultMessage.from(executionRequest, toolResultContent));
            }
        }
        ChatMessage chatMessage = messageList.get(messageList.size() - 1);
        if (chatMessage instanceof AiMessage) {
            return ((AiMessage) chatMessage).text();
        } else if (chatMessage instanceof UserMessage) {
            return ((UserMessage) chatMessage).singleText();
        }
        return StringUtils.hasText(chatMessage.toString()) ? chatMessage.toString() : "(no summary)";
    }

    public List<ToolSpecification> parentToolSpecification() {
        List<ToolSpecification> toolSpecifications = childToolSpecification();
        toolSpecifications.add(ToolSpecification.builder()
                .name("task")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("prompt", "Short description of the task")
                        .required("prompt")
                        .build())
                .description("Spawn a subagent with fresh context. It shares the filesystem but not conversation history.")
                .build());
        toolSpecifications.add(ToolSpecification.builder()
                .name("loadSkill")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("name", "Skill name to load")
                        .required("name")
                        .build())
                .description("Load specialized knowledge by name.")
                .build());
        return toolSpecifications;
    }

    public List<ToolSpecification> childToolSpecification() {
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
                .name("compact")
                .description("Trigger manual conversation compression.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("focus", "What to preserve in the summary")
                        .required("focus") // 必须明确指定必需的属性
                        .build())
                .build());
        tools.add(ToolSpecification.builder()
                .name("taskCreate")
                .description("Create a new task.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("subject")
                        .addStringProperty("description")
                        .required("subject") // 必须明确指定必需的属性
                        .build())
                .build());
        tools.add(ToolSpecification.builder()
                .name("taskUpdate")
                .description("Update a task's status or dependencies.")
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("taskId")
                        .addEnumProperty("status", Arrays.asList("pending", "in_progress", "completed"))
                        .addProperty("addBlockedBy", JsonArraySchema.builder().items(JsonIntegerSchema.builder().build()).build())
                        .addProperty("addBlocks", JsonArraySchema.builder().items(JsonIntegerSchema.builder().build()).build())
                        .required("taskId") // 必须明确指定必需的属性
                        .build())
                .build());
        tools.add(ToolSpecification.builder()
                .name("taskList")
                .description("List all tasks with status summary.")
                .parameters(JsonObjectSchema.builder()
                        .build())
                .build());
        tools.add(ToolSpecification.builder()
                .name("taskGet")
                .description("Get full details of a task by ID.")
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("taskId")
                        .required("taskId")
                        .build())
                .build());
//        tools.add(ToolSpecification.builder()
//                .name("todo")
//                .description("Update task list. Track progress on multi-step tasks.")
//                .parameters(JsonObjectSchema.builder()
//                        .addProperty("items",
//                                JsonArraySchema.builder()
//                                        .items(
//                                                JsonObjectSchema.builder()
//                                                        .addStringProperty("id", "任务的唯一标识符")
//                                                        .addStringProperty("text", "任务的具体内容")
//                                                        .addEnumProperty("status", Arrays.asList("pending", "in_progress", "completed"), "任务的当前状态。")
//                                                        .required("id", "text", "status")
//                                                        .build()
//                                        ).description("一个包含所有待办事项对象的列表。").build())
//                        .required("items")
//                        .build())
//                .build());
        return tools;
    }


}
