package com.rayself.aiservice.adapter;

import com.rayself.aiservice.infrastructure.agent.TestAssistant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
public class ChatController {
    @Autowired
    TestAssistant assistant;

    @GetMapping("/deep-chat")
    public String chat(String message) {
        return assistant.chat(1, message);
    }

    @GetMapping("/chat")
    public String test(@RequestParam String msg) {
        return assistant.chat(2, msg);
    }

    @PostMapping("/chat-stream")
    public String chatStream(@RequestBody Object msg) {
        return assistant.chat(3, msg.toString());
    }

}
