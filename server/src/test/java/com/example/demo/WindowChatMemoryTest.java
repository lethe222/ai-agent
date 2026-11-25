package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

class WindowChatMemoryTest {
  @Test
  void keepsOnlyRecentMessagesAndSeparatesConversations() {
    WindowChatMemory memory = new WindowChatMemory();
    // 连续写入八条，确认最早两条被移除。
    for (int i = 1; i <= 8; i++) {
      memory.add("first", new UserMessage("消息" + i));
    }
    memory.add("second", new UserMessage("另一会话"));

    assertEquals(6, memory.get("first", 20).size());
    assertEquals("消息3", memory.get("first", 20).get(0).getContent());
    assertEquals("消息4", memory.get("first", 5).get(0).getContent());
    assertEquals("另一会话", memory.get("second", 20).get(0).getContent());
    memory.clear("first");
    assertEquals(List.of(), memory.get("first", 20));
    assertEquals(1, memory.get("second", 20).size());
  }
}
