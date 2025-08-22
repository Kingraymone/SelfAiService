package com.rayself.aiservice.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
public class ChatController {
    @GetMapping("/chat")
    public String test(@RequestParam String msg) {
        log.info(msg);
        return msg;
    }

    @PostMapping("/chat-stream")
    public String chatStream(@RequestBody Object msg) {
        return msg.toString();
    }

}
