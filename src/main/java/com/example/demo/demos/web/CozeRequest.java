// CozeRequest.java
package com.example.demo.demos.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class CozeRequest {
    @JsonProperty("AppConversationID")
    private String appConversationID;

    @JsonProperty("Query")
    private String query = "";  // 设置默认值

    @JsonProperty("ResponseMode")
    private String responseMode = "streaming";  // 默认响应模式

    @JsonProperty("UserID")
    private String userID;

//    @JsonProperty("Inputs")
//    private Map<String, String> inputs;

    // Getter/Setter保持与字段名一致（小写驼峰）

    public String getAppConversationID() { return appConversationID; }
    public void setAppConversationID(String appConversationID) {
        this.appConversationID = appConversationID;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query != null ? query : ""; }

    public String getResponseMode() { return responseMode; }
    public void setResponseMode(String responseMode) {
        this.responseMode = responseMode != null ? responseMode : "streaming";
    }

    public String getUserID() { return userID; }
    public void setUserID(String userID) { this.userID = userID; }

//    public Map<String, String> getInputs() { return inputs; }
//    public void setInputs(Map<String, String> inputs) { this.inputs = inputs; }
}