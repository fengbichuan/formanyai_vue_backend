// CozeResponse.java
package com.example.demo.demos.web;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CozeResponse {

    @JsonProperty("answer")
    private String answer;

    @JsonProperty("AppConversationID")
    private String appConversationID;

    // 其他可能的响应字段
    @JsonProperty("Code")
    private Integer code;

    @JsonProperty("Message")
    private String message;

    public Integer getCode() { return code; }
    public String getMessage() { return message; }


    // Getter 和 Setter
    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getAppConversationID() {
        return appConversationID;
    }

    public void setAppConversationID(String appConversationID) {
        appConversationID = appConversationID;
    }
}