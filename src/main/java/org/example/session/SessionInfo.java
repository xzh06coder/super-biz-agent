package org.example.session;


import lombok.Data;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 会话信息类
 * 用于存储会话的ID、记录、创建时间等信息
 */

public class SessionInfo {
    // 会话窗口大小(最大6轮)最多保留6轮对话记录
    private static final int MAX_WINDOW_SIZE = 6;
    // 会话ID
    private final String sessionId;
    // 会话记录
    private final List<Message> history = new ArrayList<>();//用ArrayList实现会话记录的存储，message本质是org.springframework.ai.chat.messages.Message类的实例
    private final long createTime = System.currentTimeMillis();//会话创建时间，单位毫秒毫秒
    private final ReentrantLock lock = new ReentrantLock();//会话记录的锁，用于线程安全的访问

    // 构造函数
    public SessionInfo(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 添加一轮对话记录
     * 超过最大窗口大小，删除最早的记录
     */
    public void addMessage(String question, String answer) {
        lock.lock();
        try {
            history.add(new UserMessage(question));
            history.add(new AssistantMessage(answer));
            while (history.size() > MAX_WINDOW_SIZE * 2) {
                history.remove(0);//用户消息
                history.remove(0);//助手消息
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取会话记录的副本
     */
    public List<Message> getHistory() {
        lock.lock();
        try {
            return new ArrayList<>(history);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 清除会话记录
     */
    public void clearHistory() {
        lock.lock();
        try {
            history.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取会话记录的轮数
     */
    public int getMessagePairCount() {
        lock.lock();
        try {
            return history.size() / 2;
        } finally {
            lock.unlock();
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getCreateTime() {
        return createTime;
    }

}