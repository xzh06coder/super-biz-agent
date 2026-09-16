package org.example.controller;

import org.example.dto.ApiResponse;
import org.example.dto.ChatRequest;
import org.example.dto.ChatResponse;
import org.example.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private ChatService chatService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request) {

        logger.info("收到对话请求 - sessionId: {}, question: {}",
                request.getId(), request.getQuestion());

        // 参数校验：为什么这里必须判 null？见下面"坑"第 1 条
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            logger.warn("问题内容为空");
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.error("问题内容不能为空")));
        }

        try {
            String answer = chatService.chat(request.getQuestion());
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.success(answer)));

        } catch (Exception e) {
            logger.error("对话失败", e);
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.error(e.getMessage())));
        }
    }
}