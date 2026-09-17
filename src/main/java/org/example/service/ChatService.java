package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;


/**
 * 会话服务，负责处理会话相关的业务逻辑。
 */
@Service
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    @Autowired//自动注入 DashScopeChatModel Bean
    private DashScopeChatModel chatModel;

    /**
     * 组装 Prompt：历史消息 + 本次提问
     * 这是"多轮对话"的全部秘密 —— 把历史原样带上
     */
    private Prompt buildPrompt(String question, List<Message> history) {
        List<Message> messages = new ArrayList<>(history);
        messages.add(new UserMessage(question));
        return new Prompt(messages);
    }

    //非流式调用
    public String chat(String question, List<Message> history) {
        logger.info("调用DashScopeChatModel.chat方法，问题长度：{}", question.length());
        ChatResponse response = chatModel.call(buildPrompt(question, history));
        String answer = response.getResult().getOutput().getText();
        logger.info("DashScopeChatModel.chat方法返回长度：{}", answer == null ? 0 : answer.length());
        return answer;
    }

    public Flux<String> chatStream(String question, List<Message> history) {
        logger.info("调用大模型（流式），历史 {} 条，问题长度 {} 字符",
                history.size(), question.length());

        return chatModel.stream(buildPrompt(question, history))
                .map(response -> {
                    // DashScope 的尾部分片可能只带用量统计，没有正文，必须逐层判空
                    if (response == null
                            || response.getResult() == null
                            || response.getResult().getOutput() == null) {
                        return "";
                    }
                    String text = response.getResult().getOutput().getText();
                    return text == null ? "" : text;
                })
                .filter(text -> !text.isEmpty());
    }
}
