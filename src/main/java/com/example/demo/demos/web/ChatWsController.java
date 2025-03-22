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

    // 内容缓存（线程安全）
    private final Map<String, StringBuilder> contentCache = new ConcurrentHashMap<>();
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
                contentCache.put(ai, new StringBuilder());

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
                }
            });
        });
    }

    private void processStreamChunk(String ai, String chunk, long startTime) {
        try {
            // 打印原始 chunk 数据
            logger.debug("[原始数据] AI: {} | Chunk: {}", ai, chunk);

            if (chunk.startsWith("data: ")) {
                String jsonStr = chunk.substring(6).trim();

                // 打印解析前的 JSON 字符串
                logger.debug("[解析前] AI: {} | JSON: {}", ai, jsonStr);

                if ("[DONE]".equals(jsonStr)) {
                    logger.debug("[流结束] {}", ai);
                    return;
                }

                JsonNode node = objectMapper.readTree(jsonStr);
                JsonNode choices = node.path("choices");

                // 打印完整的 JSON 结构（调试用）
                logger.debug("[完整JSON] AI: {} | Node: {}", ai, node.toPrettyString());

                // 验证数据结构
                if (choices.isEmpty() || !choices.get(0).has("delta")) {
                    logger.warn("[异常结构] AI: {} | 数据: {}", ai, chunk);
                    return;
                }

                JsonNode delta = choices.get(0).path("delta");

                // 打印 delta 内容
                logger.debug("[Delta内容] AI: {} | Delta: {}", ai, delta.toPrettyString());

                // 优先从 reasoning_content 提取内容
                String content = delta.path("reasoning_content").asText("");
                // 打印提取的内容
                if (!content.isEmpty()) {
                    logger.debug("[提取内容] AI: {} | 内容: {}", ai, content);
                    contentCache.get(ai).append(content);
                    sendStreamChunk(ai, content, startTime);
                } else {
                    logger.trace("[空内容块] AI: {} | 数据: {}", ai, chunk);
                }
            }
        } catch (Exception e) {
            logger.error("[解析异常] AI: {} | 错误: {} | 原始数据: {}",
                    ai, e.getMessage(), chunk);
        }
    }
    private void sendStreamChunk(String ai, String content, long startTime) {
        Map<String, Object> response = new HashMap<>();
        response.put("ai", ai);
        response.put("content", content);
        response.put("done", false);
        response.put("time", System.currentTimeMillis() - startTime);

        messagingTemplate.convertAndSend("/topic/answers", response);
        logger.debug("[流数据] {} 发送: {}", ai, content);
    }

    private void sendFinalResponse(String ai, long startTime) {
        try {
            String fullContent = contentCache.getOrDefault(ai, new StringBuilder()).toString();

            // 打印最终完整内容
            logger.info("[最终内容] AI: {} | 内容: {}", ai, fullContent);

            Map<String, Object> response = new HashMap<>();
            response.put("ai", ai);
            response.put("content", fullContent);
            response.put("done", true);
            response.put("time", System.currentTimeMillis() - startTime);

            messagingTemplate.convertAndSend("/topic/answers", response);
            logger.info("[完成响应] {} | 耗时: {}ms | 字数: {}",
                    ai, response.get("time"), fullContent.length());
        } finally {
            contentCache.remove(ai);
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
        contentCache.remove(ai);
    }
}