package com.rayself.aiservice.infrastructure.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

@AiService(chatModel = "openAiChatModel", chatMemoryProvider = "openAiMemoryProvider", streamingChatModel = "openAiStreamingChatModel")
public interface TestAssistant2 {
    @SystemMessage("You are a polite assistant")
    TokenStream chat(@UserMessage String message);
}
