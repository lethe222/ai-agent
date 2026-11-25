package com.example.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

// 每个会话只在内存中保留最近六条消息，重启后会清空。
public class WindowChatMemory implements ChatMemory {
  private static final int MAX_MESSAGES = 6;
  private final Map<String, List<Message>> conversations = new ConcurrentHashMap<>();

  @Override
  public void add(String conversationId, List<Message> messages) {
    // 以会话为单位原子更新，避免并发写入破坏消息列表。
    conversations.compute(conversationId, (id, existing) -> {
      List<Message> updated = new ArrayList<>(existing == null ? List.of() : existing);
      updated.addAll(messages);
      int start = Math.max(0, updated.size() - MAX_MESSAGES);
      return List.copyOf(updated.subList(start, updated.size()));
    });
  }

  @Override
  public List<Message> get(String conversationId, int lastN) {
    List<Message> messages = conversations.getOrDefault(conversationId, List.of());
    // 只取最近的指定条数，避免把完整历史放入模型上下文。
    int count = Math.max(0, Math.min(lastN, messages.size()));
    return List.copyOf(messages.subList(messages.size() - count, messages.size()));
  }

  @Override
  public void clear(String conversationId) {
    conversations.remove(conversationId);
  }
}
