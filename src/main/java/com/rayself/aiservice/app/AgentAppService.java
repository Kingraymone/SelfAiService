package com.rayself.aiservice.app;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static java.time.Duration.ofSeconds;

@Service
@Slf4j
public class AgentAppService {
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    public String agentChat(String message) {
        // 模型
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("deepseek-api-key"))
                .modelName("deepseek-chat")
                .temperature(0.5)
                .timeout(ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
        // todo 系统消息增加
        // 消息
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from(message))
                .parameters(ChatRequestParameters.builder()
                        .temperature(0.5)
                        .toolSpecifications(toolSpecification())
                        .build())
                .build();
        while(true){
            ChatResponse chatResponse = model.chat(request);
            if (!chatResponse.aiMessage().hasToolExecutionRequests()) {
                break;
            }
            // todo 获取执行结果中存在方法调用的信息，调用命令行参数
            UserMessage toolResult = UserMessage.from("tool_result");
            request.messages().add(toolResult);
        }

        return null;
    }

    public ToolSpecification toolSpecification(){
        return ToolSpecification.builder()
                .name("bash")
                .description("bash命令行工具")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("command", "bash执行的命令")
                        .required("command") // 必须明确指定必需的属性
                        .build())
                .build();
    }

    /**
     * 获取系统相关的命令解释器
     */
    public static List<String> getShellCommand(String command) {
        List<String> cmdList = new ArrayList<>();

        if (IS_WINDOWS) {
            // Windows使用cmd.exe
            cmdList.add("powershell.exe");
            cmdList.add("-Command");
            cmdList.add(command);
        } else {
            // Linux/Unix/Mac使用/bin/sh
            cmdList.add("/bin/sh");
            cmdList.add("-c");
            cmdList.add(command);
        }

        return cmdList;
    }

    public static String executeCommand(String command) {
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        try {
            Process process = Runtime.getRuntime().exec(getShellCommand(command).toArray(new String[0]));

            // 读取正常输出流
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            // 读取错误流
            Thread errorReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            outputReader.start();
            errorReader.start();

            int exitCode = process.waitFor();
            outputReader.join();
            errorReader.join();

            if (exitCode != 0) {
                System.err.println("Command failed with exit code: " + exitCode);
                System.err.println("Error: " + error.toString());
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return output.toString();
    }

    public static void main(String[] args) {
        System.out.println(executeCommand("dir"));
    }
}
