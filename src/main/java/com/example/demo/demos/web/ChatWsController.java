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
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class ChatWsController {
    private static final Logger logger = LoggerFactory.getLogger(ChatWsController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 双缓存结构（线程安全）
    private final Map<String, Map<String, StringBuilder>> contentCache = new ConcurrentHashMap<>();
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
                final long startTime = System.currentTimeMillis();
                // 初始化双缓存
                contentCache.put(ai, new HashMap<>() {{
                    put("content", new StringBuilder());
                    put("reasoning", new StringBuilder());
                }});

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
                                    () -> sendFinalResponse(ai, startTime)
                            );
                } catch (Exception e) {
                    logger.error("[初始化异常] {}", ai, e);
                    cleanupCache(ai);
                }
            });
        });
    }

    private void processStreamChunk(String ai, String chunk, long startTime) {
        try {
            logger.debug("[原始数据] AI: {} | Chunk: {}", ai, chunk);

            String jsonStr = chunk.trim();
            logger.debug("[解析前] AI: {} | JSON: {}", ai, jsonStr);

            if ("[DONE]".equals(jsonStr)) {
                logger.debug("[流结束] {}", ai);
                return;
            }

            JsonNode node = objectMapper.readTree(jsonStr);
            JsonNode choices = node.path("choices");
            logger.debug("[完整JSON] AI: {} | Node: {}", ai, node.toPrettyString());

            if (choices.isEmpty() || !choices.get(0).has("delta")) {
                logger.warn("[异常结构] AI: {} | 数据: {}", ai, chunk);
                return;
            }

            JsonNode delta = choices.get(0).path("delta");
            logger.debug("[Delta内容] AI: {} | Delta: {}", ai, delta.toPrettyString());

            // 同时处理主内容和推理内容
            String content = delta.path("content").asText("");
            String reasoning = delta.path("reasoning_content").asText("");

            // 更新双缓存
            if (!content.isEmpty() || !reasoning.isEmpty()) {
                synchronized (contentCache) {
                    Map<String, StringBuilder> aiCache = contentCache.get(ai);
                    if (content != null) aiCache.get("content").append(content);
                    if (reasoning != null) aiCache.get("reasoning").append(reasoning);
                }
                sendStreamChunk(ai, content, reasoning, startTime);
            }
        } catch (Exception e) {
            logger.error("[解析异常] AI: {} | 错误: {} | 原始数据: {}",
                    ai, e.getMessage(), chunk);
        }
    }

    private void sendStreamChunk(String ai, String content, String reasoning, long startTime) {
        Map<String, Object> response = new HashMap<>();
        response.put("ai", ai);
        response.put("content", content);
        response.put("reasoning", reasoning);
        response.put("done", false);
        response.put("time", System.currentTimeMillis() - startTime);

        messagingTemplate.convertAndSend("/topic/answers", response);
        logger.debug("[双流数据] {} | 主内容: {} | 推理链: {}", ai, content.length(), reasoning.length());
    }

    private void sendFinalResponse(String ai, long startTime) {
        try {
            Map<String, StringBuilder> aiCache = contentCache.get(ai);
            String fullContent = aiCache.get("content").toString();
            String fullReasoning = aiCache.get("reasoning").toString();

            logger.info("[最终内容] AI: {} | 主内容长度: {} | 推理链长度: {}",
                    ai, fullContent.length(), fullReasoning.length());

            Map<String, Object> response = new HashMap<>();
            response.put("ai", ai);
            response.put("content", fullContent);
            response.put("reasoning", fullReasoning);
            response.put("done", true);
            response.put("time", System.currentTimeMillis() - startTime);

            messagingTemplate.convertAndSend("/topic/answers", response);
        } finally {
            cleanupCache(ai);
        }
    }

    private void handleStreamError(String ai, Throwable error, long startTime) {
        logger.error("[流错误] {} | 原因: {}", ai, error.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("ai", ai);
        response.put("error", "服务响应异常: " + error.getMessage());
        response.put("done", true);
        response.put("time", System.currentTimeMillis() - startTime);

        messagingTemplate.convertAndSend("/topic/answers", response);
        cleanupCache(ai);
    }

    private void cleanupCache(String ai) {
        contentCache.remove(ai);
        logger.debug("[缓存清理] {}", ai);
    }
}