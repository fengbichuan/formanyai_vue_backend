package com.example.demo.demos.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class ChatService {

    @Autowired
    @Qualifier("openaiRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper; // 添加ObjectMapper的自动注入

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

    // 在 ChatService 类中添加以下字段
    @Value("${ai.model4.api.url}")
    private String model4ApiUrl;

    @Value("${ai.model4.api.key}")
    private String model4ApiKey;

    @Value("${ai.model4.api.appid}")
    private String model4AppId;

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

    // 添加获取URL的方法
    public String getModel4ApiUrl() {
        return model4ApiUrl;
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
        // 创建Coze会话并立即提问
        CompletableFuture<String> answer4 = createCozeConversation()
                .thenCompose(conversationId -> chatWithCoze(conversationId, prompt));
        // Call all AI models in parallel and combine the answers
        CompletableFuture<String> answer1 = getAnswerFromModel(prompt, apiUrl, "API_KEY_1");
        CompletableFuture<String> answer2 = getAnswerFromModel(prompt, model2ApiUrl, model2ApiKey);
        CompletableFuture<String> answer3 = getAnswerFromModel(prompt, model3ApiUrl, model3ApiKey);

        // Combine all answers after they are completed
        return Map.of(
                "answer1", answer1.join().trim(),
                "answer2", answer2.join().trim(),
                "answer3", answer3.join().trim(),
                "answer4", answer4.join().trim()  // 新增第四个回答
        );

    }
    // 创建Coze对话
    public CompletableFuture<String> createCozeConversation() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 确保URL正确拼接
//                String url = String.format("%s/create_conversation", model4ApiUrl.endsWith("/")
//                        ? model4ApiUrl.substring(0, model4ApiUrl.length()-1)
//                        : model4ApiUrl);

                String url = model4ApiUrl+ "/create_conversation";


                HttpHeaders headers = new HttpHeaders();
                headers.set("Apikey", model4ApiKey);
                headers.set("Content-Type", "application/json");

                CozeRequest request = new CozeRequest();
                request.setAppID(model4AppId.trim()); // 去除可能的空格
                request.setUserID("2120240810");
//                request.setInputs(Map.of("init", "true")); // 根据API文档要求参数
                request.setQuery(""); // 必须的查询字段

                HttpEntity<CozeRequest> entity = new HttpEntity<>(request, headers);

                // 打印调试信息
                // 打印调试信息：请求头、请求体和URL
                System.out.println("[规范调试] 请求URL: " + url);
//                System.out.println("[规范调试] 请求头: " + headers);
                System.out.println("[规范调试] 序列化请求体: "
                        + objectMapper.writeValueAsString(request));

//                CozeResponse response = restTemplate.postForObject(url, request, CozeResponse.class);
//
//                if (response == null || response.getAppConversationID() == null) {
//                    throw new RuntimeException("API返回无效响应，原始数据："
//                            + (response != null ? objectMapper.writeValueAsString(response) : "null"));
//                }
//                return response.getAppConversationID();
//                TempResponseData response = restTemplate.postForObject(url, entity, TempResponseData.class);
//                if (response == null || response.getConversation() == null)
//                    throw new RuntimeException("API返回无效响应，原始数据："
//                            + (response != null ? objectMapper.writeValueAsString(response) : "null"));
//                return response.getConversation().getAppConversationID();
                JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);
                if (response != null && response.has("Conversation") && response.get("Conversation").has("AppConversationID")) {
                    return response.get("Conversation").get("AppConversationID").asText();
                } else {
                    throw new RuntimeException("API返回无效响应，原始数据：" + (response != null ? response.toString() : "null"));
                }
            } catch (Exception e) {
                throw new RuntimeException("创建对话失败: " + e.getMessage(), e);
            }
        });
    }

    /*
    {
    "Conversation": {
        "AppConversationID": "cvin5gv5usogqsuoen0g",
        "ConversationName": "新的会话",
        "CreateTime": "",
        "LastChatTime": "",
        "EmptyConversation": false
    },
    "BaseResp": null
}
     */

    // 修改提问方法
    public CompletableFuture<String> chatWithCoze(String conversationId, String question) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = String.format("%s/chat_query", model4ApiUrl.endsWith("/")
                        ? model4ApiUrl.substring(0, model4ApiUrl.length()-1)
                        : model4ApiUrl);

                HttpHeaders headers = new HttpHeaders();
                headers.set("Apikey", model4ApiKey);
                headers.set("Content-Type", "application/json");

                CozeRequest request = new CozeRequest();
                request.setAppID(model4AppId.trim());
                request.setAppConversationID(conversationId);
                request.setQuery(question);
                request.setResponseMode("blocking");
                request.setUserID("2120240810");

                HttpEntity<CozeRequest> entity = new HttpEntity<>(request, headers);

                // 打印调试信息：请求头、请求体和URL
                System.out.println("[规范调试] 请求URL: " + url);
                System.out.println("[规范调试] 请求头: " + headers);
                System.out.println("[规范调试] 请求体: " + objectMapper.writeValueAsString(request));

//                CozeResponse response = restTemplate.postForObject(url, entity, CozeResponse.class);
//
//                if (response == null || response.getAnswer() == null) {
//                    throw new RuntimeException("API返回空回答，响应："
//                            + (response != null ? objectMapper.writeValueAsString(response) : "null"));
//                }
//                return response.getAnswer();
                // {"event":"message_end","task_id":"01JQC5F0FJV0639FQ05SN1STCM","id":"01JQC5F0FJV0639FQ05SN1STCM","conversation_id":"01JQC1VJP5JXQ0YA2Q3A8WJ51M","answer":"xxxxx","created_at":0,"agent_configuration":{"retriever_resource":{"enabled":false}}}
//                JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);
//                if (response != null && response.has("answer")) {
//                    return response.get("answer").asText();
//                } else {
//                    throw new RuntimeException("API返回无效响应，原始数据：" + (response != null ? response.toString() : "null"));
//                }
                String response = restTemplate.postForObject(url, entity, String.class);
                System.err.println(response);
                /*
                event:text\ndata:data: {}
                 */
                if (response != null) {
                    ObjectMapper mapper = new ObjectMapper();
                    String pattern = "data:data: ";
                    int index = response.indexOf(pattern);
                    if (index != -1) {
                        JsonNode jsonNode = mapper.readTree(response.substring(index + pattern.length()));
                        if (jsonNode.has("answer")) {
                            String tempAnswer = jsonNode.get("answer").asText();
                            // 处理中文乱码问题
                            return new String(tempAnswer.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                        }
                    }
                }
                throw new RuntimeException("API返回无效响应，原始数据：" + (response != null ? response : "null"));

            } catch (Exception e) {
                throw new RuntimeException("提问失败: " + e.getMessage());
            }
        });
    }

}
