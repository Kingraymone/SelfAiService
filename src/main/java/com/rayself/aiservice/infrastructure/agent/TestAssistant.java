package com.rayself.aiservice.infrastructure.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

@AiService(chatModel = "openAiChatModel")
public interface TestAssistant {
    @SystemMessage("You are a polite assistant")
    String chat(String userMessage);
}
