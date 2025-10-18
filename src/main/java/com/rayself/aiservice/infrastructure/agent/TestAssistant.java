package com.rayself.aiservice.infrastructure.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService(chatModel = "openAiChatModel", chatMemoryProvider = "openAiMemoryProvider")
public interface TestAssistant {
    @SystemMessage("You are a polite assistant")
    String chat(@MemoryId int memoryId, @UserMessage String message);
}
