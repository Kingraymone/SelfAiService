package com.rayself.aiservice.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConversationManager {

    private static final int KEEP_RECENT = 5; // Assuming a value for KEEP_RECENT
    private static final Path TRANSCRIPT_DIR = Paths.get("transcripts");
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    // Placeholder for the LLM client
    private final LlmService llmService;

    public ConversationManager(LlmService llmService) {
        this.llmService = llmService;
    }

    /**
     * Rough token count: ~4 chars per token.
     */
    public int estimateTokens(List<Message> messages) {
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
    public List<Message> microCompact(List<Message> messages) {
        List<ToolResultInfo> toolResults = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if ("user".equals(msg.getRole()) && msg.getContent() instanceof List) {
                List<ContentPart> contentParts = (List<ContentPart>) msg.getContent();
                for (int j = 0; j < contentParts.size(); j++) {
                    ContentPart part = contentParts.get(j);
                    if ("tool_result".equals(part.getType())) {
                        toolResults.add(new ToolResultInfo(i, j, part));
                    }
                }
            }
        }

        if (toolResults.size() <= KEEP_RECENT) {
            return messages;
        }

        Map<String, String> toolNameMap = messages.stream()
                .filter(msg -> "assistant".equals(msg.getRole()) && msg.getContent() instanceof List)
                .flatMap(msg -> ((List<ContentPart>) msg.getContent()).stream())
                .filter(part -> "tool_use".equals(part.getType()))
                .collect(Collectors.toMap(ContentPart::getId, ContentPart::getName, (a, b) -> a));

        List<ToolResultInfo> toClear = toolResults.subList(0, toolResults.size() - KEEP_RECENT);

        for (ToolResultInfo resultInfo : toClear) {
            ContentPart resultPart = resultInfo.part;
            if (resultPart.getContent() instanceof String && ((String) resultPart.getContent()).length() > 100) {
                String toolId = resultPart.getToolUseId();
                String toolName = toolNameMap.getOrDefault(toolId, "unknown");
                resultPart.setContent(String.format("[Previous: used %s]", toolName));
            }
        }
        
        // The list is modified in place, but we return it for consistency with the python function
        return messages;
    }

    /**
     * Layer 2: auto_compact - save transcript, summarize, replace messages.
     */
    public List<Message> autoCompact(List<Message> messages) throws IOException {
        // Save full transcript to disk
        Files.createDirectories(TRANSCRIPT_DIR);
        Path transcriptPath = TRANSCRIPT_DIR.resolve(String.format("transcript_%d.jsonl", Instant.now().getEpochSecond()));

        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            sb.append(objectMapper.writeValueAsString(msg)).append("\n");
        }
        Files.writeString(transcriptPath, sb.toString());
        System.out.println("[transcript saved: " + transcriptPath + "]");

        // Ask LLM to summarize
        String conversationText;
        try {
            // Limit to ~80000 chars as in python
            String fullText = objectMapper.writeValueAsString(messages);
            conversationText = fullText.substring(0, Math.min(fullText.length(), 80000));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not serialize messages for summary", e);
        }

        String prompt = "Summarize this conversation for continuity. Include: "
                + "1) What was accomplished, 2) Current state, 3) Key decisions made. "
                + "Be concise but preserve critical details.\n\n" + conversationText;

        String summary = llmService.createCompletion(prompt);

        // Replace all messages with compressed summary
        List<Message> newMessages = new ArrayList<>();
        newMessages.add(new Message("user", String.format("[Conversation compressed. Transcript: %s]\n\n%s", transcriptPath, summary)));
        newMessages.add(new Message("assistant", "Understood. I have the context from the summary. Continuing."));

        return newMessages;
    }

    // --- Data Classes to represent the message structure ---

    public static class Message {
        private String role;
        private Object content; // Can be String or List<ContentPart>

        public Message() {}

        public Message(String role, Object content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public Object getContent() { return content; }
        public void setContent(Object content) { this.content = content; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContentPart {
        private String type;
        private Object content; // Can be String
        private String id;
        private String name;
        @JsonProperty("tool_use_id")
        private String toolUseId;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Object getContent() { return content; }
        public void setContent(Object content) { this.content = content; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getToolUseId() { return toolUseId; }
        public void setToolUseId(String toolUseId) { this.toolUseId = toolUseId; }
    }
    
    private static class ToolResultInfo {
        final int msgIndex;
        final int partIndex;
        final ContentPart part;

        ToolResultInfo(int msgIndex, int partIndex, ContentPart part) {
            this.msgIndex = msgIndex;
            this.partIndex = partIndex;
            this.part = part;
        }
    }

    // --- Placeholder for LLM Service ---

    public interface LlmService {
        String createCompletion(String prompt);
    }
}
