package com.example.demo.demos.web;

import java.util.List;

// 与 OpenAI ChatGPT API 响应格式对应的 DTO
import java.util.List;

public class ChatResponse {

    private List<Choice> choices;  // 存储 Choice 对象的列表

    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    // Choice 类，表示 OpenAI 返回的一个回答选项
    public static class Choice {
        private Message message;  // 包含一个 Message 对象

        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }

        // 其他字段，如 index 可以根据 OpenAI API 响应格式进行添加
    }

    // Message 类，表示 OpenAI 返回的消息内容
    public static class Message {
        private String role;     // 消息角色（user 或 assistant）
        private String content;  // 消息内容

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}
