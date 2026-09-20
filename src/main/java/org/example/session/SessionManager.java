package org.example.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器
 * 用于管理所有会话的创建、获取、删除等操作
 */
@Component
public class SessionManager {
  private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
  private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();//会话映射表，用于存储所有会话

  public SessionInfo getOrCreate(String sessionId) {
    String id = (sessionId==null||sessionId.isBlank())? UUID.randomUUID().toString():sessionId;
    return sessions.computeIfAbsent(id, key -> {
      logger.info("创建新会话: {}", key);
      return new SessionInfo(key);
    });
  }
  public SessionInfo get(String sessionId) {
    return  sessionId==null?null:sessions.get(sessionId);
  }
  public int  getSessionCount() {//获取当前会话数量
    return sessions.size();
  }
}