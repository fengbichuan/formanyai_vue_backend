package com.example.demo.demos.web;

import com.example.demo.demos.web.ChatRequest;
import com.example.demo.demos.web.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ChatService {

    @Autowired  // 自定义的 RestTemplate Bean，用于调用 OpenAI 接口
    @Qualifier("openaiRestTemplate")
    private RestTemplate restTemplate;

    @Value("${deepseek.model}")
    private String model;             // 模型名称（例如 "gpt-3.5-turbo"）
    @Value("${deepseek.api.url}")
    private String apiUrl;            // API调用地址

    public String getAnswer(String prompt, boolean stream) {
        // 构造请求体
        ChatRequest request = new ChatRequest(model, prompt, stream);
        // 调用 OpenAI ChatGPT 接口，获取响应&#8203;:contentReference[oaicite:9]{index=9}
        ChatResponse response = restTemplate.postForObject(apiUrl, request, ChatResponse.class);
        // 简单的结果检查和提取
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return "AI未返回有效回复";
        }
        return response.getChoices().get(0).getMessage().getContent();  // 返回第一条回答内容
    }
}
