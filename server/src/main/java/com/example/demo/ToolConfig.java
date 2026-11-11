/*Service（业务层）：负责具体的业务算账逻辑（比如算打折、发邮件等）。*/
package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.web.client.RestClient;

import java.util.function.Function;

@Configuration
public class ToolConfig {

    // WebFlux 环境中为 Spring AI 提供同步 HTTP 客户端构造器。
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }


    // 1. 定义入参 Record（相当于工具的参数声明）
    public record WebSearchRequest(String query) {}

    // 2. 定义出参 Record（工具执行后给模型返回的结构）
    public record WebSearchResponse(String searchResult) {}

    // 3. 注册为 Bean，通过 @Description 告诉大模型这个工具是干什么的
    @Bean
    @Description("当用户询问最新资讯、实时动态或特定主题的新闻时，调用此函数进行网络检索")
    public Function<WebSearchRequest, WebSearchResponse> searchWebTool() {
        return request -> {
            System.out.println("⚡ [Java 本地执行] 智能体调用了 searchWebTool，入参: " + request.query());

            // 这里可以是你写的抓取逻辑，或者直接模拟查询结果
            String mockResult = "最新速报：类 Grok 架构的 Java 智能体原型在 Spring Boot 项目中成功跑通！";
            return new WebSearchResponse(mockResult);
        };
    }
}
