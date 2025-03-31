package com.example.demo.demos.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

@Configuration
public class OpenAIConfig {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${ai.model4.api.key}")
    private String model4ApiKey;

    @Value("${ai.model4.api.url}")
    private String model4ApiUrl;

    @Bean
    @Qualifier("openaiRestTemplate")
    public RestTemplate openaiRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        // 为 RestTemplate 增加拦截器
        restTemplate.getInterceptors().add((request, body, execution) -> {
            // 打印出请求的 URL 和方法，用于调试
            System.out.println("Request URL: " + request.getURI());
            System.out.println("Request Method: " + request.getMethod());
            // 打印出请求体(将字节流转化为字符打印出来）
            System.out.println("Request Body: " + new String(body));

            // 根据请求 URL 判断并添加合适的认证头
            if (request.getURI().toString().contains(model4ApiUrl)) {
                request.getHeaders().set("Apikey", model4ApiKey);
//                request.getHeaders().set("Content-Type", "application/json");
                System.out.println("Request Method: " + request.getHeaders());
            } else {
                request.getHeaders().set("Authorization", "Bearer " + openaiApiKey);
            }

            // 执行请求
            return execution.execute(request, body);
        });

        return restTemplate;
    }
}
