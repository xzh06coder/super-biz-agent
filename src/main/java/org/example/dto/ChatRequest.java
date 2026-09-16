package org.example.dto;
//入参类
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
@Data
public class ChatRequest {
    // 会话ID
    @JsonProperty("Id")
    @JsonAlias({"id", "ID"})
    private String id;
    // 问题
    @JsonProperty("Question")
    @JsonAlias({"question", "QUESTION"})
    private String question;
}
