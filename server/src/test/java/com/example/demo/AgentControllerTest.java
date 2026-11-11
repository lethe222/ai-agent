package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(AgentController.class)
class AgentControllerTest {
    @Autowired
    private WebTestClient client;

    @MockBean
    private ChatModel chatModel;

    @Test
    void chatReturnsServerSentEvents() {
        // 使用模拟模型验证路由，避免测试时调用真实模型并产生费用。
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("你好"))));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));

        client.get().uri("/api/agent/chat?message=hello").exchange()
                .expectStatus().isOk().expectHeader().contentTypeCompatibleWith("text/event-stream")
                .expectBody(String.class).value(body -> org.junit.jupiter.api.Assertions.assertTrue(body.contains("你好")));
    }
}
