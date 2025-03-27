package com.example.demo.demos.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/coze")
public class CozeController {

    @Autowired
    private ChatService chatService;

    // 创建新对话
    @PostMapping("/create")
    public Map<String, String> createConversation() {
        CompletableFuture<String> future = chatService.createCozeConversation();
        return Map.of("conversationId", future.join());
    }

    // 发送问题到指定对话
    @PostMapping("/ask")
    public Map<String, String> chatQuery(@RequestBody Map<String, String> request) {
        String conversationId = request.get("conversationId");
        String question = request.get("question");
        CompletableFuture<String> future = chatService.chatWithCoze(conversationId, question);
        return Map.of("answer", future.join());
    }
}