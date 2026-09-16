package org.example.dto;
//统一响应外壳类
import lombok.Data;
@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    // ApiResponse 构造方法
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(data);
        return response;
    }
    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(500);
        response.setMessage(message);
        return response;
    }
}
