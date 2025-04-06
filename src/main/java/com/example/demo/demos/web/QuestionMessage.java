package com.example.demo.demos.web;

import java.util.List;

public class QuestionMessage {
    private String question;
    private List<String> ais;

    // 如果用了coze，则需要传入会话id
    private String cozeConversationID;

    // Getter和Setter
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<String> getAis() { return ais; }
    public void setAis(List<String> ais) { this.ais = ais; }
    public String getCozeConversationID() { return cozeConversationID; }
    public void setCozeConversationID(String cozeConversationID) { this.cozeConversationID = cozeConversationID; }
    @Override
    public String toString() {
        return "QuestionMessage{" +
                "question='" + question + '\'' +
                ", ais=" + ais +
                '}';
    }
}