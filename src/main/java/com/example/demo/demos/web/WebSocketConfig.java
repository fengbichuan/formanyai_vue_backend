package com.example.demo.demos.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 配置消息代理，指定接收消息的目标前缀（主题）
        config.enableSimpleBroker("/topic");              // 推送消息的前缀&#8203;:contentReference[oaicite:1]{index=1}
        config.setApplicationDestinationPrefixes("/app"); // 客户端发送消息的前缀&#8203;:contentReference[oaicite:2]{index=2}
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册一个 WebSocket 端点，供客户端连接。这里设置路径为 /chat
        registry.addEndpoint("/chat")
                .setAllowedOriginPatterns("*");
//                .withSockJS();           // 开启 SockJS 支持，方便不支持 WebSocket 的浏览器
    }
}
