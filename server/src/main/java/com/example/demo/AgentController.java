/*Controller（控制层）：只负责接待前台顾客，处理 HTTP 请求参数与返回值（比如 UserController）。*/
package com.example.demo;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@CrossOrigin(origins = "*") // 允许前端本地跨域调试
public class AgentController {

  private final ChatModel chatModel;

  public AgentController(ChatModel chatModel) {
    this.chatModel = chatModel;
  }

  /**
   * produces = MediaType.TEXT_EVENT_STREAM_VALUE 是核心！ 它告诉浏览器这是一个 SSE 流式长连接 (Content-Type:
   * text/event-stream)
   */
  @GetMapping(value = "/api/agent/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<String> streamChat(@RequestParam(defaultValue = "最近有什么最新动态？") String message) {
    return chatModel.stream(createPrompt(message))
        .map(response -> {
          String text = response.getResult().getOutput().getContent();
          return text != null ? text : "";
        });
  }

  /*每次向 AI 发送消息前，组装请求内容的地方*/
  private Prompt createPrompt(String message) {

    // 动态获取当前精确的北京时间（精确到星期几）
    String currentTime =
        ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
            .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE HH:mm", Locale.CHINA));

    SystemMessage systemMessage =
        new SystemMessage(
            "你是一个具有尖锐幽默感、风格类似 Grok 的智能体。如果遇到实时问题，善于利用工具进行检索。\n"
                + "【系统环境信息】当前真实的北京时间为："
                + currentTime
                + "。涉及时间推断或查询最近新闻时请以此为准。\n"
                + "仅当问题涉及实时动态、近期新闻或你明确不知道的客观事实时才调用搜索工具；日常闲聊、编程基础或常识问题直接回答，严禁无意义搜索。");

    UserMessage userMessage = new UserMessage(message);

    OpenAiChatOptions options = OpenAiChatOptions.builder().withFunction("searchWebTool").build();

    return new Prompt(List.of(systemMessage, userMessage), options);
  }
}
