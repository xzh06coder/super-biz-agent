package org.example.dto;

import lombok.Data;

/**
 * SSE消息类
 * 用于表示通过SSE发送的消息，包含会话ID、消息类型、消息内容等信息
 */
@Data
public class SseMessage {
    private  String type;
    private  String data;
    //创建消息实例
    public  static SseMessage content(String data) {
        SseMessage message = new SseMessage();
        message.setType("content");
        message.setData(data);
        return message;
    }
    //创建错误消息实例
    public  static SseMessage error(String data) {
        SseMessage message = new SseMessage();
        message.setType("error");
        message.setData(data);
        return message;
    }
    //创建完成消息实例
    public  static SseMessage done() {
        SseMessage message = new SseMessage();
        message.setType("done");
        message.setData("");
        return message;
    }
}
