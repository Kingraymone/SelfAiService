package com.rayself.aiservice.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConversationCompactManager {

    private static final int KEEP_RECENT = 5; // Assuming a value for KEEP_RECENT
    private static final Path TRANSCRIPT_DIR = Paths.get("transcripts");
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /**
     * Rough token count: ~4 chars per token.
     */
    public static int estimateTokens(List<ChatMessage> messages) {
        try {
            String messagesAsString = objectMapper.writeValueAsString(messages);
            return messagesAsString.length() / 4;
        } catch (JsonProcessingException e) {
            // Log the exception or handle it as needed
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Layer 1: micro_compact - replace old tool results with placeholders.
     */
    public static List<ChatMessage> microCompact(List<ChatMessage> messages) {
        LinkedHashMap<Integer, ChatMessage> toolResults = new LinkedHashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof ToolExecutionResultMessage) {
                toolResults.put(i, msg);
            }
        }
        if (toolResults.size() <= KEEP_RECENT) {
            return messages;
        }
        int loop = toolResults.size() - KEEP_RECENT;
        for (Map.Entry<Integer, ChatMessage> messageEntry : toolResults.entrySet()) {
            int msgIndex = messageEntry.getKey();
            ChatMessage msg = messageEntry.getValue();
            ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) msg;
            String toolName = toolExecutionResultMessage.toolName();
            String id = toolExecutionResultMessage.id();
            messages.remove(msgIndex);
            messages.add(msgIndex, ToolExecutionResultMessage.from(id, toolName, String.format("[Previous: used %s]", toolName)));
            loop--;
            if (loop <= 0) {
                break;
            }
        }
        // The list is modified in place, but we return it for consistency with the python function
        return messages;
    }

    /**
     * Layer 2: auto_compact - save transcript, summarize, replace messages.
     */
    public static List<ChatMessage> autoCompact(OpenAiChatModel model, List<ChatMessage> messages) {
        // Save full transcript to disk
        // Ask LLM to summarize
        String conversationText;
        Path transcriptPath;
        try {
            Files.createDirectories(TRANSCRIPT_DIR);
            transcriptPath = TRANSCRIPT_DIR.resolve(String.format("transcript_%d.jsonl", Instant.now().getEpochSecond()));

            StringBuilder sb = new StringBuilder();
            for (ChatMessage msg : messages) {
                sb.append(objectMapper.writeValueAsString(msg)).append("\n");
            }
            Files.writeString(transcriptPath, sb.toString());
            System.out.println("[transcript saved: " + transcriptPath + "]");

            // Limit to ~80000 chars as in python
            String fullText = objectMapper.writeValueAsString(messages);
            conversationText = fullText.substring(0, Math.min(fullText.length(), 80000));
        } catch (Exception e) {
            throw new RuntimeException("Could not serialize messages for summary", e);
        }

        UserMessage userMessage = UserMessage.from("Summarize this conversation for continuity. Include: "
                + "1) What was accomplished, 2) Current state, 3) Key decisions made. "
                + "Be concise but preserve critical details.\n\n" + conversationText);
        // 消息
        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .parameters(ChatRequestParameters.builder()
                        .temperature(0.3)
                        .build())
                .build();
        ChatResponse chatResponse = model.chat(request);
        String summary = chatResponse.aiMessage().text();
        // Replace all messages with compressed summary
        List<ChatMessage> newMessages = new ArrayList<>();
        newMessages.add(messages.get(0));
        newMessages.add(UserMessage.from(String.format("[Conversation compressed. Transcript: %s]\n\n%s", transcriptPath, summary)));
        newMessages.add(AiMessage.from("Understood. I have the context from the summary. Continuing."));

        return newMessages;
    }

}
