package com.example.demo.demos.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatRestController {

    @Autowired
    private ChatService chatService;

    @PostMapping("/api/ask")
    public AnswerMessage ask(@RequestBody QuestionMessage request) {
        String question = request.getQuestion();
        // 调用AI服务获取回答
        String reply = chatService.getAnswer(question, false);
        // 封装为响应对象返回，Spring 会序列化为 JSON
        return new AnswerMessage(reply);
    }
}
