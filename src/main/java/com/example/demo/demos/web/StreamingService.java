package com.example.demo.demos.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class StreamingService {

    private final WebClient webClient = WebClient.create();

    // OpenAI流式API调用
    public Flux<String> streamOpenAI(String prompt, String apiKey) {
        return webClient.post()
                .uri("https://api.askmany.chat/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "model", "deepseek-r1-250120",
                        "messages", List.of(Map.of("role","user","content", prompt)),
                        "stream", true
                ))
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(30))
                .filter(data -> data.startsWith("data: {"))
                .map(data -> {
                    try {
                        JsonNode node = new ObjectMapper().readTree(data.substring(6));
                        return node.path("choices").get(0).path("delta").path("content").asText("");
                    } catch (Exception e) {
                        return "[解析错误]";
                    }
                });
    }

    // DeepSeek流式API（示例）
    public Flux<String> streamDeepSeek(String prompt, String apiKey) {
        return webClient.post()
                .uri("https://api.askmany.chat/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(Map.of(
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "stream", true
                ))
                .retrieve()
                .bodyToFlux(String.class)
                .map(data -> {/* 类似OpenAI的解析逻辑 */
                    return data;
                });
    }
}