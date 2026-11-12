/*Service（业务层）：负责具体的业务算账逻辑（比如算打折、发邮件等）。*/
package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Function;

@Configuration
public class ToolConfig {

    // 为 Spring AI 提供同步 HTTP 客户端构造器。
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }


    // 可以在 IDEA 环境变量或者 application.properties 里配置 TAVILY_API_KEY
    @Value("${TAVILY_API_KEY:你的Tavily_Key}")
    private String tavilyApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public record WebSearchRequest(String query) {
    }

    // 2. 定义出参 Record（工具执行后给模型返回的结构）
    public record WebSearchResponse(String searchResult) {
    }

    // 3. 注册为 Bean，通过 @Description 告诉大模型这个工具是干什么的
    @Bean
    @Description("当用户询问最新资讯、实时动态或特定主题的新闻时，调用此函数进行网络检索")
    public Function<WebSearchRequest, WebSearchResponse> searchWebTool() {
        return request -> {
            System.out.println("⚡ [Java 真实联网执行] 智能体调用了 searchWebTool，正在检索: " + request.query());

            try {
                // 1. 组装请求 Tavily 搜索接口的 JSON 参数
                String requestBody = objectMapper.writeValueAsString(new Object() {
                    public final String api_key = tavilyApiKey;
                    public final String query = request.query();
                    public final int max_results = 3; // 取前3条最相关的新闻
                });

                // 2. 发起真实 HTTP POST 请求
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.tavily.com/search"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                // 3. 解析搜索返回结果
                JsonNode root = objectMapper.readTree(httpResponse.body());
                // 取其中的 results 属性
                JsonNode results = root.path("results");
                // 准备一个可变的高性能文本拼接容器。
                StringBuilder summary = new StringBuilder();
                if (results.isArray()) {
                    for (JsonNode item : results) {
                        String title = item.path("title").asText();
                        String content = item.path("content").asText();
                        summary.append("【").append(title).append("】: ").append(content).append("\n\n");
                    }
                }

                String finalResult = summary.length() > 0 ? summary.toString() : "未检索到相关有效资讯";
                return new WebSearchResponse(finalResult);

            } catch (Exception e) {
                System.err.println("❌ 搜索请求失败: " + e.getMessage());
                return new WebSearchResponse("联网搜索服务暂时不可用: " + e.getMessage());
            }
        };
    }
}
