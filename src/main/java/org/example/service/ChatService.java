package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.example.agent.tool.DateTimeTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
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
    /**
     * 系统提示词
     */
    private  static final String SYSTEM_PROMPT = """
            你是一个专业的智能助手，可以调用工具来获取实时信息。

            规则：
            1. 当用户询问当前时间、今天的日期、星期几时，必须调用 getCurrentDateTime 工具获取真实时间，
               严禁凭记忆编造或猜测。
            2. 如果工具调用失败，如实告知用户失败原因，不要编造结果。
            3. 用中文回答，简洁准确。
            """;
    @Autowired//自动注入 DashScopeChatModel Bean
    private DashScopeChatModel chatModel;
    @Autowired
    private DateTimeTools dateTimeTools;
    //创建ReactAgent

    /**
     * 创建ReactAgent,methodTools传的是普通java对象，框架会自动扫描方法注解，返回结果
     * @return
     */
    private ReactAgent createReactAgent() {
        return ReactAgent.builder()
                .name("intelligentAssistant")
                .model(chatModel)
                .systemPrompt(SYSTEM_PROMPT)
                .methodTools(dateTimeTools)
                .build();
    }
    /**
     * 组装消息列表 历史消息 + 本次提问
     * @param question 本次提问
     * @param history 历史消息
     * @return
     */
    private List<Message> buildMessages(String question, List<Message> history) {
        List<Message> messages = new ArrayList<>(history);
        messages.add(new UserMessage(question));
        return messages;
    }



    //非流式调用
    public String chat (String question, List<Message> history) throws GraphRunnerException {
        logger.info("Agent 对话（非流式），历史 {} 条，问题长度 {} 字符",
                history.size(), question.length());
        ReactAgent agent = createReactAgent();
        AssistantMessage answer = agent.call(buildMessages(question, history));
        String text = answer.getText();
        logger.info("Agent 对话（非流式），返回长度 {} 字符", text == null ? 0 : text.length());
        return text;
    }

    public Flux<String> chatStream(String question, List<Message> history) throws GraphRunnerException {
        logger.info("Agent 对话（流式），历史 {} 条，问题长度 {} 字符",
                history.size(), question.length());
        ReactAgent agent = createReactAgent();
        return agent.stream(buildMessages(question, history))
                // NodeOutput 是个大杂烩，只保留 StreamingOutput
                .filter(output -> output instanceof StreamingOutput)
                .map(output -> (StreamingOutput<?>) output)
                // 再只挑"模型正在输出正文"的事件，工具调用的事件丢掉
                .filter(output -> output.getOutputType() == OutputType.AGENT_MODEL_STREAMING)
                .map(output -> {
                    Message message = output.message();
                    String text = (message == null) ? null : message.getText();
                    return text == null ? "" : text;
                })
                .filter(text -> !text.isEmpty());

    }
}
