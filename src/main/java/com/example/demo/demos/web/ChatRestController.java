package com.example.demo.demos.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
public class ChatRestController {

    @Autowired
    private ChatService chatService;

    @Async
    @PostMapping("/api/ask")
    public CompletableFuture<AnswerMessage> ask(@RequestBody QuestionMessage request) {
        String question = request.getQuestion();

        // Asynchronously get the answer from multiple models
        CompletableFuture<String> combinedAnswerFuture = chatService.getCombinedAnswer(question);

        return combinedAnswerFuture.thenApply(answer -> new AnswerMessage(answer));
    }
}
