package com.rayself.aiservice.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Compact {
    public static final int THRESHOLD = 50000;
    public static final int KEEP_RECENT = 3;

    private final Path workdir;
    private final Path transcriptDir;
    private final ObjectMapper mapper;
    private final LlmClient client;
    private final String model;

    public Compact(Path workdir, ObjectMapper mapper, LlmClient client, String model) {
        this.workdir = workdir;
        this.transcriptDir = workdir.resolve(".transcripts");
        this.mapper = mapper;
        this.client = client;
        this.model = model;
    }

    public static int estimateTokens(List<?> messages) {
        return String.valueOf(messages).length() / 4;
    }

    // -- Layer 1: micro_compact - replace old tool results with placeholders --
    public static List<Map<String, Object>> microCompact(List<Map<String, Object>> messages) {
        List<ToolResultRef> toolResults = new ArrayList<>();
        for (int msgIdx = 0; msgIdx < messages.size(); msgIdx++) {
            Map<String, Object> msg = messages.get(msgIdx);
            Object role = msg.get("role");
            Object content = msg.get("content");
            if ("user".equals(role) && content instanceof List) {
                List<?> parts = (List<?>) content;
                for (int partIdx = 0; partIdx < parts.size(); partIdx++) {
                    Object part = parts.get(partIdx);
                    if (part instanceof Map) {
                        Map<?, ?> partMap = (Map<?, ?>) part;
                        Object type = partMap.get("type");
                        if ("tool_result".equals(type)) {
                            toolResults.add(new ToolResultRef(msgIdx, partIdx, castMap(partMap)));
                        }
                    }
                }
            }
        }

        if (toolResults.size() <= KEEP_RECENT) {
            return messages;
        }

        Map<String, String> toolNameMap = new HashMap<>();
        for (Map<String, Object> msg : messages) {
            if ("assistant".equals(msg.get("role"))) {
                Object content = msg.get("content");
                if (content instanceof List) {
                    for (Object block : (List<?>) content) {
                        if (block instanceof Map) {
                            Map<?, ?> blockMap = (Map<?, ?>) block;
                            Object type = blockMap.get("type");
                            if ("tool_use".equals(type)) {
                                Object id = blockMap.get("id");
                                Object name = blockMap.get("name");
                                if (id != null && name != null) {
                                    toolNameMap.put(String.valueOf(id), String.valueOf(name));
                                }
                            }
                        }
                    }
                }
            }
        }

        List<ToolResultRef> toClear = toolResults.subList(0, toolResults.size() - KEEP_RECENT);
        for (ToolResultRef ref : toClear) {
            Map<String, Object> result = ref.result;
            Object content = result.get("content");
            if (content instanceof String && ((String) content).length() > 100) {
                String toolId = String.valueOf(result.getOrDefault("tool_use_id", ""));
                String toolName = toolNameMap.getOrDefault(toolId, "unknown");
                result.put("content", "[Previous: used " + toolName + "]");
            }
        }

        return messages;
    }

    // -- Layer 2: auto_compact - save transcript, summarize, replace messages --
    public List<Map<String, Object>> autoCompact(List<Map<String, Object>> messages) throws IOException {
        Files.createDirectories(transcriptDir);
        Path transcriptPath = transcriptDir.resolve("transcript_" + Instant.now().getEpochSecond() + ".jsonl");
        try (BufferedWriter writer = Files.newBufferedWriter(transcriptPath, StandardCharsets.UTF_8)) {
            for (Map<String, Object> msg : messages) {
                writer.write(toJson(msg));
                writer.newLine();
            }
        }
        System.out.println("[transcript saved: " + transcriptPath + "]");

        String conversationText = toJson(messages);
        if (conversationText.length() > 80000) {
            conversationText = conversationText.substring(0, 80000);
        }

        String prompt = "Summarize this conversation for continuity. Include: "
            + "1) What was accomplished, 2) Current state, 3) Key decisions made. "
            + "Be concise but preserve critical details.\n\n" + conversationText;

        String summary = client.summarize(model, prompt, 2000);

        List<Map<String, Object>> compressed = new ArrayList<>();
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "[Conversation compressed. Transcript: " + transcriptPath + "]\n\n" + summary);
        compressed.add(userMsg);

        Map<String, Object> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "Understood. I have the context from the summary. Continuing.");
        compressed.add(assistantMsg);

        return compressed;
    }

    private String toJson(Object value) throws JsonProcessingException {
        return mapper.writeValueAsString(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static class ToolResultRef {
        private final int msgIndex;
        private final int partIndex;
        private final Map<String, Object> result;

        private ToolResultRef(int msgIndex, int partIndex, Map<String, Object> result) {
            this.msgIndex = msgIndex;
            this.partIndex = partIndex;
            this.result = result;
        }
    }

    public interface LlmClient {
        String summarize(String model, String prompt, int maxTokens) throws IOException;
    }
}
