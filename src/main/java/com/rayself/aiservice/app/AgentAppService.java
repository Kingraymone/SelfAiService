package com.rayself.aiservice.app;

import com.alibaba.fastjson.JSONObject;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Method;
import java.nio.file.Paths;
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
        SystemMessage systemMessage = SystemMessage.from(String.format("You are a coding agent at %s. OS: %s. Use tools to solve tasks. Act, don't explain.",
                Paths.get(System.getProperty("user.dir")), System.getProperty("os.name").toLowerCase()));
        messageList.add(systemMessage);
        messageList.add(UserMessage.from(String.format(message)));
        agentLoop(model, messageList);
        ChatMessage chatMessage = messageList.get(messageList.size() - 1);
        if (chatMessage instanceof AiMessage) {
            return ((AiMessage) chatMessage).text();
        } else if (chatMessage instanceof UserMessage) {
            return ((UserMessage) chatMessage).singleText();
        }
        return chatMessage.toString();
    }

    private void agentLoop(OpenAiChatModel model, List<ChatMessage> messageList) {
        while (true) {
            // 消息
            ChatRequest request = ChatRequest.builder()
                    .messages(messageList)
                    .parameters(ChatRequestParameters.builder()
                            .temperature(0.5)
                            .toolSpecifications(toolAppService.toolSpecification())
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
