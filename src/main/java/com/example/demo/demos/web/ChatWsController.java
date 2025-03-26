package com.example.demo.demos.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Controller
public class ChatWsController {
    private static final Logger logger = LoggerFactory.getLogger(ChatWsController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final WebClient webClient;
    private final SimpMessageSendingOperations messagingTemplate;

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.api.key}")
    private String apiKey;

    @Autowired
    public ChatWsController(SimpMessageSendingOperations messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.askmany.chat")
                .defaultHeader("Authorization", "Bearer " + "sk-804e2be556326d2bdea29a89be24ce4a")
                .codecs(config -> config.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    @MessageMapping("/ask")
    public void handleQuestion(QuestionMessage message) {
        message.getAis().forEach(ai -> {
            CompletableFuture.runAsync(() -> {

                if ("coze".equalsIgnoreCase(ai)) {
                    sendCOZERequest(message, ai);
                } else {
                    sendCommonRequest(message, ai);
                }

            });
        });
    }

    private void sendCommonRequest(QuestionMessage message, String ai) {
        final long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-r1-250120");
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", message.getQuestion())
            ));
            requestBody.put("max_tokens", 4000);
            requestBody.put("temperature", 0.7);
            requestBody.put("stream", true);

            logger.info("[请求发送] AI: {} | 问题: {}", ai, message.getQuestion());

            webClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .filter(chunk -> chunk != null && !chunk.isEmpty())
                    .subscribe(
                            chunk -> processStreamChunk(ai, chunk, startTime),
                            error -> handleStreamError(ai, error, startTime),
                            () -> sendStreamCompletion(ai, startTime)  // 修改最终发送逻辑
                    );
        } catch (Exception e) {
            logger.error("[初始化异常] {}", ai, e);
        }
    }


    private void sendCOZERequest(QuestionMessage message, String ai) {
        final long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-r1-250120");
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", message.getQuestion())
            ));
            requestBody.put("max_tokens", 4000);
            requestBody.put("temperature", 0.7);
            requestBody.put("stream", true);

            logger.info("[请求发送] AI: {} | 问题: {}", ai, message.getQuestion());

            webClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .filter(chunk -> chunk != null && !chunk.isEmpty())
                    .subscribe(
                            chunk -> processStreamChunk(ai, chunk, startTime),
                            error -> handleStreamError(ai, error, startTime),
                            () -> sendStreamCompletion(ai, startTime)  // 修改最终发送逻辑
                    );
        } catch (Exception e) {
            logger.error("[初始化异常] {}", ai, e);
        }
    }

    private void processStreamChunk(String ai, String chunk, long startTime) {
        try {
            logger.debug("[原始数据] AI: {} | Chunk: {}", ai, chunk);

            String jsonStr = chunk.trim();
            if ("[DONE]".equals(jsonStr)) {
                logger.debug("[流结束] {}", ai);
                return;
            }

            JsonNode node = objectMapper.readTree(jsonStr);
            JsonNode choices = node.path("choices");

            if (choices.isEmpty() || !choices.get(0).has("delta")) {
                logger.warn("[异常结构] AI: {} | 数据: {}", ai, chunk);
                return;
            }

            JsonNode delta = choices.get(0).path("delta");

            // 直接提取当前chunk的内容
            String content = delta.path("content").asText("");
            String reasoning = delta.path("reasoning_content").asText("");

            // 立即转发到前端（无需拼接）
            sendStreamChunk(ai, content, reasoning, startTime);
        } catch (Exception e) {
            logger.error("[解析异常] AI: {} | 错误: {} | 原始数据: {}",
                    ai, e.getMessage(), chunk);
        }
    }

    private void sendStreamChunk(String ai, String content, String reasoning, long startTime) {
        Map<String, Object> response = new HashMap<>();
        response.put("ai", ai);
        response.put("content", content);      // 当前chunk的content
        response.put("reasoning", reasoning);  // 当前chunk的reasoning
        response.put("done", false);
        response.put("time", System.currentTimeMillis() - startTime);

        messagingTemplate.convertAndSend("/topic/answers", response);
        logger.debug("[实时转发] {} | 主内容: {} | 推理链: {}", ai, content, reasoning);
    }

    // 修改最终响应方法（不再发送完整内容）
    private void sendStreamCompletion(String ai, long startTime) {
        Map<String, Object> response = new HashMap<>();
        response.put("ai", ai);
        response.put("done", true);
        response.put("time", System.currentTimeMillis() - startTime);

        messagingTemplate.convertAndSend("/topic/answers", response);
        logger.info("[流式传输完成] {}", ai);
    }

    // 保留错误处理（去除缓存相关操作）
    private void handleStreamError(String ai, Throwable error, long startTime) {
        logger.error("[流错误] {} | 原因: {}", ai, error.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("ai", ai);
        response.put("error", "服务响应异常: " + error.getMessage());
        response.put("done", true);
        response.put("time", System.currentTimeMillis() - startTime);

        messagingTemplate.convertAndSend("/topic/answers", response);
    }
}
