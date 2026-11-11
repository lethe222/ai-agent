package com.example.demo;

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

import java.util.List;

@RestController
@CrossOrigin(origins = "*") // 允许前端本地跨域调试
public class AgentController {

    private final ChatModel chatModel;

    public AgentController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * produces = MediaType.TEXT_EVENT_STREAM_VALUE 是核心！
     * 它告诉浏览器这是一个 SSE 流式长连接 (Content-Type: text/event-stream)
     */
    @GetMapping(value = "/api/agent/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam(defaultValue = "最近有什么最新动态？") String message) {
        return chatModel.stream(createPrompt(message))
                .map(response -> {
                    String text = response.getResult().getOutput().getContent();
                    return text != null ? text : "";
                });
    }

    private Prompt createPrompt(String message) {
        SystemMessage systemMessage = new SystemMessage(
                "你是一个具有尖锐幽默感、风格类似 Grok 的智能体。如果遇到实时问题，善于利用工具进行检索。"
        );
        UserMessage userMessage = new UserMessage(message);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withFunction("searchWebTool")
                .build();

        return new Prompt(List.of(systemMessage, userMessage), options);
    }
}
