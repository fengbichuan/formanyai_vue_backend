package com.example.demo.demos.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Controller
public class ChatWsController {
    private static final Logger logger = LoggerFactory.getLogger(ChatWsController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final WebClient webClient;
    private final WebClient cozeWebClient;
    private final SimpMessageSendingOperations messagingTemplate;

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${ai.model4.api.url}")
    private String model4ApiUrl;

    @Value("${ai.model4.api.key}")
    private String model4ApiKey;


    @Autowired
    public ChatWsController(SimpMessageSendingOperations messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.askmany.chat")
                .defaultHeader("Authorization", "Bearer " + "sk-804e2be556326d2bdea29a89be24ce4a")
                .codecs(config -> config.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        String cozeUrl = "https://coze.nankai.edu.cn";
        this.cozeWebClient = WebClient.builder()
                .baseUrl(cozeUrl)
                .defaultHeader("Apikey", "cvcn5grkphnujaq0nlmg")
                .defaultHeader("Content-Type", "application/json")
                .codecs(config -> config.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .filter(logRequest())
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            // 打印请求方法和 URL
            System.out.println("Request: " + clientRequest.method() + " " + clientRequest.url());
            // 打印所有请求头
            clientRequest.headers()
                    .forEach((name, values) -> values.forEach(value -> System.out.println(name + ": " + value)));
            return Mono.just(clientRequest);
        });
    }

    @MessageMapping("/ask")
    public void handleQuestion(QuestionMessage message) {
        System.err.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
        System.err.println(message.getAis());
        System.err.println(message.getCozeConversationID());
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
        // --- [CRITICAL] Retrieve and VALIDATE Conversation ID ---
        String conversationId = message.getCozeConversationID();

        if (conversationId == null || conversationId.trim().isEmpty()) {
            logger.error("[请求错误] AI: {} | Coze Conversation ID is missing or empty. Cannot query Coze API.", ai);
            Map<String, Object> response = new HashMap<>();
            response.put("ai", ai);
            response.put("error", "Error: A valid Coze session ID is required but was not provided.");
            response.put("done", true);
            response.put("time", System.currentTimeMillis() - startTime);
            messagingTemplate.convertAndSend("/topic/answers", response);
            return;
        }
        // --- [END CRITICAL] ---

        // --- Configuration Value Checks (Keep relevant ones) ---
        if (model4ApiUrl == null || model4ApiUrl.isEmpty()) {
            logger.error("[配置错误] Coze API URL (ai.model4.api.url) is missing or empty!");
            // Send error response...
            Map<String, Object> response = new HashMap<>();
            response.put("ai", ai);
            response.put("error", "Server configuration error: Missing Coze API URL.");
            response.put("done", true);
            response.put("time", System.currentTimeMillis() - startTime);
            messagingTemplate.convertAndSend("/topic/answers", response);
            return;
        }
        if (model4ApiKey == null || model4ApiKey.isEmpty()) {
            logger.warn("[配置警告] Coze API Key (ai.model4.api.key) is missing or empty. Request might fail.");
            // Key is needed for the Apikey header, so maybe error out if missing?
            // Or rely on the API to return 401/403 if the header is missing/invalid
        }
        // AppID check is removed as it's not used here anymore
        // --- [END] Configuration Value Checks ---

        try {
            // --- Build Request Body - CORRECTED according to documentation ---
            Map<String, Object> requestBody = new HashMap<>();
            // Required Fields:
            requestBody.put("AppConversationID", conversationId); // REQUIRED
            requestBody.put("Query", message.getQuestion());     // REQUIRED
            requestBody.put("ResponseMode", "streaming");       // REQUIRED (for streaming)
            requestBody.put("UserID", "2120240810");             // REQUIRED (use dynamic/configured value)

            // 输出
            System.err.println("request map:");
            for (String key : requestBody.keySet()) {
                System.err.println(key + ": " + requestBody.get(key));
            }
            // REMOVED: Do NOT include AppID in the body for chat_query_v2
            // requestBody.put("AppID", model4AppId);

            // Optional/Deprecated Fields (Generally omit unless specifically needed):
            // requestBody.put("AppKey", model4ApiKey); // Deprecated, omit
            // requestBody.put("QueryExtends", ...); // Omit unless using file uploads etc.
            // requestBody.put("PubAgentJump", ...); // Omit unless needed


            // --- Detailed Logging ---
            String requestUrl = "cvcn5grkphnujaq0nlmg" + "/api/proxy/api/v1/chat_query_v2";
            String requestBodyJson = "[Serialization Error]";
            try {
                requestBodyJson = this.objectMapper.writeValueAsString(requestBody);
            } catch (Exception logEx) {
                logger.error("[日志记录异常] Failed to serialize request body map to JSON for AI: {}", ai, logEx);
            }

            logger.info("-------------------- Coze Request Details ({}) --------------------", ai);
            logger.info("[请求准备] Target URL: {}", requestUrl);
            String maskedApiKey = (model4ApiKey != null && model4ApiKey.length() > 5)
                    ? model4ApiKey.substring(0, 5) + "..."
                    : model4ApiKey;
            logger.info("[请求准备] Using Header 'Apikey' starting with: {}", maskedApiKey); // Header is correct
            logger.info("[请求准备] Using Body 'AppConversationID': '{}'", conversationId); // Log required field
            // No longer logging AppID in body
            logger.info("[请求准备] Request Body JSON: {}", requestBodyJson); // Log the actual body
            logger.info("----------------------------------------------------------------------");

            // --- Execute WebClient Request (remains the same) ---
            cozeWebClient.post()
                    .uri("/api/proxy/api/v1/chat_query_v2")
                    .bodyValue(requestBody) // Send the corrected map
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .filter(chunk -> chunk != null && !chunk.isEmpty())
                    .subscribe(
                            chunk -> processCozeStreamChunk(ai, chunk, startTime),
                            error -> { // Keep detailed error handling
                                String errorMsg = error.getMessage();
                                String cozeDetails = "N/A";
                                int statusCode = 0;

                                if (error instanceof WebClientResponseException) {
                                    WebClientResponseException ex = (WebClientResponseException) error;
                                    statusCode = ex.getStatusCode().value();
                                    cozeDetails = ex.getResponseBodyAsString();
                                    logger.error("[流错误] AI: {} | Status: {} | 原因: {} | Coze Response Body: {}",
                                            ai, statusCode != 0 ? statusCode : "N/A", errorMsg, cozeDetails, error);
                                } else {
                                    logger.error("[流错误] AI: {} | 原因: {}", ai, errorMsg, error);
                                }

                                Map<String, Object> response = new HashMap<>();
                                response.put("ai", ai);
                                response.put("error", String.format("Coze API Error (Status: %s): %s. Details: %s",
                                        statusCode != 0 ? statusCode : "N/A", errorMsg, cozeDetails));
                                response.put("done", true);
                                response.put("time", System.currentTimeMillis() - startTime);
                                messagingTemplate.convertAndSend("/topic/answers", response);
                            },
                            () -> sendStreamCompletion(ai, startTime)
                    );

        } catch (Exception e) {
            logger.error("[请求初始化异常] AI: {} | Error: {}", ai, e.getMessage(), e);
            // Send initialization error... (keep existing logic)
            Map<String, Object> response = new HashMap<>();
            response.put("ai", ai);
            response.put("error", "Failed to initiate Coze request: " + e.getMessage());
            response.put("done", true);
            response.put("time", System.currentTimeMillis() - startTime);
            messagingTemplate.convertAndSend("/topic/answers", response);
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

    private void processCozeStreamChunk(String ai, String chunk, long startTime) {
        logger.debug("[原始数据] AI: {} | Chunk: {}", ai, chunk);

        String jsonStr = chunk.trim();

        try {
            JsonNode node = objectMapper.readTree(jsonStr);
            String event = node.path("event").asText("");
            List<String> endEvents = Arrays.asList("message_output_end", "message_end", "message_cost");
            if (endEvents.contains(event)) {
                logger.debug("[流结束] {}", ai);
                return;
            }

            String answer = node.path("answer").asText("");
            sendStreamChunk(ai, answer, "", startTime);

        } catch (Exception e) {
            logger.error("[解析异常] AI: {} | 错误: {} | 原始数据: {}",
                    ai, e.getMessage(), chunk);
        }
    }

}
