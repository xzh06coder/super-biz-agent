package org.example.dto;
//业务结果类
import lombok.Data;
@Data
public class ChatResponse {
    private boolean success;
    private String errorMessage;
    private String answer;

    public static ChatResponse success(String answer) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setAnswer(answer);
        return response;
    }
    public static ChatResponse error(String errorMessage) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        return response;
    }
}
