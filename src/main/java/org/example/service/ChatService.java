package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;



/**
 * 会话服务，负责处理会话相关的业务逻辑。
 */
@Service
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    @Autowired//自动注入 DashScopeChatModel Bean
    private DashScopeChatModel chatModel;
    public String chat(String question) {
        logger.info("调用DashScopeChatModel.chat方法，问题长度：{}", question.length());
        ChatResponse response= chatModel.call(new Prompt(question));
        String answer= response.getResult().getOutput().getText();
        logger.info("DashScopeChatModel.chat方法返回长度：{}", answer.length());
        return answer;
    }
}
