package com.example.demo.demos.web;

import java.util.ArrayList;
import java.util.List;

// 与 OpenAI ChatGPT API 请求格式对应的 DTO
public class ChatRequest {
    private String model;
    private List<Message> messages;
    private int n = 1;
    private double temperature = 0.7;
    private boolean stream = false;
    public ChatRequest(String model, String prompt, boolean stream) {
        this.model = model;
        this.messages = new ArrayList<>();
        this.messages.add(new Message("user", prompt));
        this.stream = stream;
    }
    // getter 和 setter
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

}

