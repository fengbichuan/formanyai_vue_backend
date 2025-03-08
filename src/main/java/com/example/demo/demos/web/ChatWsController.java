package com.example.demo.demos.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatWsController {

    @Autowired
    private ChatService chatService;  // 注入 AI 问答服务

    // 接收前端发送的问题消息（目的地为/app/ask）
    @MessageMapping("/ask")
    @SendTo("/topic/answers")  // 将回复发送到/topic/answers主题，所有订阅该主题的客户端都能收到
    public AnswerMessage handleQuestion(QuestionMessage message) throws Exception {
        System.err.println("收到问题：" + message.getQuestion());
        String question = message.getQuestion();
        // 调用AI服务获取回答（可能需要一定时间，模拟异步处理）
        String reply = chatService.getAnswer(question, true);
        // 返回发送给订阅者的消息，将自动转换为JSON并通过/topic/answers广播
        return new AnswerMessage(reply);
    }
}
