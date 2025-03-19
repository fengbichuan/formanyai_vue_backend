package com.example.demo.demos.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class ChatService {

    @Autowired
    @Qualifier("openaiRestTemplate")
    private RestTemplate restTemplate;

    @Value("${deepseek.model}")
    private String model;
    @Value("${deepseek.api.url}")
    private String apiUrl;

    @Value("${ai.model2.api.url}")
    private String model2ApiUrl;

    @Value("${ai.model2.api.key}")
    private String model2ApiKey;

    @Value("${ai.model3.api.url}")
    private String model3ApiUrl;

    @Value("${ai.model3.api.key}")
    private String model3ApiKey;

    // Getter methods to access the injected URLs
    public String getApiUrl() {
        return apiUrl;
    }

    public String getModel2ApiUrl() {
        return model2ApiUrl;
    }

    public String getModel3ApiUrl() {
        return model3ApiUrl;
    }

    // Standard method for REST API call to multiple models
    public CompletableFuture<String> getAnswerFromModel(String prompt, String modelUrl, String apiKey) {
        // Construct the request body
        ChatRequest request = new ChatRequest(model, prompt, false);

        // Call the model API and get the response
        ChatResponse response = restTemplate.postForObject(modelUrl, request, ChatResponse.class);

        // Handle invalid response
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return CompletableFuture.completedFuture("AI未返回有效回复");
        }

        // Return the answer from the AI model
        return CompletableFuture.completedFuture(response.getChoices().get(0).getMessage().getContent());
    }

    // Method to get answers from multiple models
    public Map<String, String> getCombinedAnswer(String prompt) {
        // Call all AI models in parallel and combine the answers
        CompletableFuture<String> answer1 = getAnswerFromModel(prompt, apiUrl, "API_KEY_1");
        CompletableFuture<String> answer2 = getAnswerFromModel(prompt, model2ApiUrl, model2ApiKey);
        CompletableFuture<String> answer3 = getAnswerFromModel(prompt, model3ApiUrl, model3ApiKey);

        // Combine all answers after they are completed
        return Map.of(
                "answer1", answer1.join().trim(),
                "answer2", answer2.join().trim(),
                "answer3", answer3.join().trim()
        );

    }
}
