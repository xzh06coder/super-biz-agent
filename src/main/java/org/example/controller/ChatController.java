package org.example.controller;

import jakarta.annotation.PreDestroy;
import org.example.dto.ApiResponse;
import org.example.dto.ChatRequest;
import org.example.dto.ChatResponse;
import org.example.dto.SseMessage;
import org.example.service.ChatService;
import org.example.session.SessionInfo;
import org.example.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private ChatService chatService;
    @Autowired
    private SessionManager sessionManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();

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
            SessionInfo session = sessionManager.getOrCreate(request.getId());
            String answer = chatService.chat(request.getQuestion(), session.getHistory());
            session.addMessage(request.getQuestion(), answer);
            logger.info("对话成功 - sessionId: {}, 当前历史: {}",
                    session.getSessionId(), session.getMessagePairCount());
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.success(answer)));

        } catch (Exception e) {
            logger.error("对话失败", e);
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.error(e.getMessage())));
        }
    }
    // ==================== 流式 ====================

    @PostMapping(value = "/chat_stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {

        // 5 分钟超时（模型最长可以慢慢吐 5 分钟）
        SseEmitter emitter = new SseEmitter(300_000L);

        // 参数校验也必须走 SSE，否则前端解析会出错
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            logger.warn("问题内容为空");
            try {
                emitter.send(SseEmitter.event().name("message")
                        .data(SseMessage.error("问题内容不能为空"), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        // 把耗时的模型调用丢到独立线程，Web 线程立刻还回去
        executor.execute(() -> {
            try {
                SessionInfo session = sessionManager.getOrCreate(request.getId());
                List<Message> history = session.getHistory();

                logger.info("流式对话开始 - sessionId: {}, 历史: {} 轮",
                        session.getSessionId(), history.size() / 2);

                // 累积完整答案，流结束后存进会话历史
                StringBuilder fullAnswer = new StringBuilder();

                chatService.chatStream(request.getQuestion(), history).subscribe(

                        // ① 每收到一个增量片段
                        chunk -> {
                            try {
                                fullAnswer.append(chunk);
                                emitter.send(SseEmitter.event().name("message")
                                        .data(SseMessage.content(chunk), MediaType.APPLICATION_JSON));
                            } catch (IOException e) {
                                logger.error("推送流式内容失败", e);
                                throw new RuntimeException(e);
                            }
                        },

                        // ② 出错
                        error -> {
                            logger.error("流式对话失败", error);
                            try {
                                emitter.send(SseEmitter.event().name("message")
                                        .data(SseMessage.error(error.getMessage()), MediaType.APPLICATION_JSON));
                            } catch (IOException ex) {
                                logger.error("推送错误消息失败", ex);
                            }
                            emitter.complete();
                        },

                        // ③ 流正常结束
                        () -> {
                            try {
                                session.addMessage(request.getQuestion(), fullAnswer.toString());
                                logger.info("流式对话完成 - sessionId: {}, 答案长度: {}, 当前历史: {} 轮",
                                        session.getSessionId(), fullAnswer.length(),
                                        session.getMessagePairCount());

                                emitter.send(SseEmitter.event().name("message")
                                        .data(SseMessage.done(), MediaType.APPLICATION_JSON));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        }
                );

            } catch (Exception e) {
                logger.error("流式对话初始化失败", e);
                try {
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.error(e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    logger.error("推送错误消息失败", ex);
                }
                emitter.complete();
            }
        });


        return emitter;
    }

    @PreDestroy
    public void shutdownExecutor() {
        logger.info("正在关闭流式对话线程池...");
        executor.shutdown();
    }
}