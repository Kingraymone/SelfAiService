package com.rayself.aiservice.infrastructure.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

@AiService(chatModel = "openAiChatModel", chatMemoryProvider = "openAiMemoryProvider", streamingChatModel = "openAiStreamingChatModel")
public interface TestAssistant {
    @SystemMessage("You are a polite assistant")
    String chat(@MemoryId int memoryId, @UserMessage String message);

    Flux<String> chat(@UserMessage String message);
}
