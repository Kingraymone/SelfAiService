package com.rayself.aiservice.app;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.time.Duration.ofSeconds;

@Service
@Slf4j
public class AgentAppService {
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String USER_MSG_TEMPLATE = "[用户操作系统]：%s" +
            "\n" +
            "[用户消息]：%s";
    @Autowired
    ToolAppService toolAppService;


    public String agentChat(String message) {
        // 模型
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(System.getenv("deepseek-api-key"))
                .modelName("deepseek-chat")
                .temperature(0.5)
                .timeout(ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
        // todo 系统消息增加
        List<ChatMessage> messageList = new ArrayList<>();
        SystemMessage systemMessage = SystemMessage.from("You are a coding agent at {os.getcwd()}. Use bash to solve tasks. Act, don't explain.");
        messageList.add(systemMessage);
        messageList.add(UserMessage.from(String.format(USER_MSG_TEMPLATE, IS_WINDOWS ? "window" : "linux", message)));
        agentLoop(model, messageList);
        return messageList.get(messageList.size() - 1).toString();
    }

    private void agentLoop(OpenAiChatModel model, List<ChatMessage> messageList) {
        while (true) {
            // 消息
            ChatRequest request = ChatRequest.builder()
                    .messages(messageList)
                    .parameters(ChatRequestParameters.builder()
                            .temperature(0.5)
                            .toolSpecifications(toolSpecification())
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
                    JSONObject jsonObject = JSONObject.parseObject(executionRequest.arguments());
                    Object result = method.invoke(toolAppService, jsonObject);
                    toolResultContent = result.toString();
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    return;
                }
                messageList.add(ToolExecutionResultMessage.from(executionRequest, toolResultContent));
            }
        }
    }

    public ToolSpecification toolSpecification() {
        return ToolSpecification.builder()
                .name("executeCommand")
                .description("Run a shell command.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("command", "bash执行的命令")
                        .required("command") // 必须明确指定必需的属性
                        .build())
                .build();
    }

    public Map<String, Method> findToolMap() {
        Map<String, Method> map = new HashMap<>();
        for (Method method : ToolAppService.class.getDeclaredMethods()) {
            map.put(method.getName(), method);
        }
        return map;
    }


    public static void main(String[] args) {
//        System.out.println(executeCommand("dir"));
    }
}
