package com.example.demo.demos.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

@Controller
public class ChatWsController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;  // For sending messages to WebSocket

    // Handle incoming WebSocket messages from the front-end
    @MessageMapping("/ask")
    @SendTo("/topic/answers")  // This will send the final message to all clients subscribed to this topic
    public void handleQuestion(QuestionMessage message) {
        String question = message.getQuestion();
        System.err.println("Received question: " + question);

        // Create a temporary placeholder for the answer (to be updated incrementally)
        // Send a message to indicate we are processing
        messagingTemplate.convertAndSend("/topic/answers", "Answer is being fetched...");

        // Fetch answers from multiple models asynchronously
        CompletableFuture<String> answer1 = chatService.getAnswerFromModel(question, chatService.getApiUrl(), "API_KEY_1");
        CompletableFuture<String> answer2 = chatService.getAnswerFromModel(question, chatService.getModel2ApiUrl(), "API_KEY_2");
        CompletableFuture<String> answer3 = chatService.getAnswerFromModel(question, chatService.getModel3ApiUrl(), "API_KEY_3");

        // When all answers are completed, we combine them and send to WebSocket
        CompletableFuture.allOf(answer1, answer2, answer3).thenRun(() -> {
            try {
                // Combine all answers and send them at once
                String combinedAnswer = "Model 1: " + answer1.get() + "\nModel 2: " + answer2.get() + "\nModel 3: " + answer3.get();
                messagingTemplate.convertAndSend("/topic/answers", combinedAnswer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
