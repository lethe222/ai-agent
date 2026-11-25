package com.example.demo;

/*Controller（控制层）：只负责接待前台顾客，处理 HTTP 请求参数与返回值（比如 UserController）。*/
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@CrossOrigin(origins = "*")
public class AgentController {
    private final ChatClient chatClient;

    public AgentController(ChatClient.Builder builder) {
        ChatMemory chatMemory = new WindowChatMemory();
        // Advisor 将对应会话的历史消息加入请求，并保存用户消息和模型回答。
        this.chatClient = builder
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
                .defaultFunctions("searchWebTool")
                .build();
    }

    @GetMapping(value = "/api/agent/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @RequestParam(defaultValue = "最近有什么最新动态？") String message,
            @RequestParam(defaultValue = "default-session") String chatId) {
        // 每次请求重新获取北京时间，避免一直使用启动时的时间。
        String currentTime = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE HH:mm", Locale.CHINA));
        String systemPrompt = "你是一个具有尖锐幽默感、风格类似 Grok 的智能体。如果遇到实时问题，善于利用工具进行检索。\n"
                + "【系统环境信息】当前真实的北京时间为：" + currentTime
                + "。涉及时间推断或查询最近新闻时请以此为准。\n"
                + "仅当问题涉及实时动态、近期新闻或你明确不知道的客观事实时才调用搜索工具；日常闲聊、编程基础或常识问题直接回答，严禁无意义搜索。";

        return chatClient.prompt()
                .system(systemPrompt)
                .user(message)
                .advisors(spec -> spec
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        // 当前用户消息占一条，因此历史最多取五条，合计不超过六条。
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 5))
                .stream()
                .content();
    }
}
