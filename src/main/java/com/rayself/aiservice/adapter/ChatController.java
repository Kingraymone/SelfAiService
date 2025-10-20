package com.rayself.aiservice.adapter;

import com.rayself.aiservice.infrastructure.agent.TestAssistant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;

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

    @RequestMapping(value = "/chat-sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatSse(@RequestBody Object msg) {
        Flux<String> contentFlux = assistant.chat(msg.toString());
        // 转换为 ServerSentEvent 格式
        return contentFlux.map(token -> ServerSentEvent.builder(token).event("message").id("123").retry(Duration.ofSeconds(2)).comment("test").build());
    }

    @RequestMapping(value = "/chat-sse-emitter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatSseEmitter(@RequestBody Object msg) {
        Flux<String> contentFlux = assistant.chat(msg.toString());
        // 转换为 ServerSentEvent 格式
        return contentFlux.map(token -> ServerSentEvent.builder(token).event("message").id("123").retry(Duration.ofSeconds(2)).comment("test").build());
    }

}
