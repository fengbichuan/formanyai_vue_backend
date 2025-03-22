package com.example.demo.demos.web;

import java.util.List;

public class QuestionMessage {
    private String question;
    private List<String> ais;

    // Getter和Setter
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<String> getAis() { return ais; }
    public void setAis(List<String> ais) { this.ais = ais; }
    @Override
    public String toString() {
        return "QuestionMessage{" +
                "question='" + question + '\'' +
                ", ais=" + ais +
                '}';
    }
}