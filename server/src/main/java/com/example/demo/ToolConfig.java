/*Service（业务层）：负责具体的业务算账逻辑（比如算打折、发邮件等）。*/
package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.web.client.RestClient;

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

  // 每个固定的计数周期内，整个后端进程最多发起 3 次搜索。
  private static final int MAX_SEARCHES_PER_MINUTE = 3;
  private static final long ONE_MINUTE_MILLIS = 60_000L;
  private long searchWindowStart = System.currentTimeMillis();
  private int searchesInWindow = 0;

  // 同时有多个请求时，保证检查次数和占用次数是一个完整操作。
  private synchronized boolean tryAcquireSearchSlot() {
    long now = System.currentTimeMillis();
    // 周期结束后清零，重新开始计数。
    if (now - searchWindowStart >= ONE_MINUTE_MILLIS) {
      searchWindowStart = now;
      searchesInWindow = 0;
    }
    // 达到上限后，不允许继续向 Tavily 发起搜索。
    if (searchesInWindow >= MAX_SEARCHES_PER_MINUTE) {
      return false;
    }
    searchesInWindow++;
    return true;
  }

  public record WebSearchRequest(String query) {}

  // 2. 定义出参 Record（工具执行后给模型返回的结构）
  public record WebSearchResponse(String searchResult) {}

  // 3. 注册为 Bean，通过 @Description 告诉大模型这个工具是干什么的
  @Bean
  @Description("当用户询问最新资讯、实时动态或特定主题的新闻时，调用此函数进行网络检索")
  public Function<WebSearchRequest, WebSearchResponse> searchWebTool() {
    return request -> {
      // 空关键词不发起搜索，也不占用搜索次数。
      if (request == null || request.query() == null || request.query().isBlank()) {
        return new WebSearchResponse("搜索关键词不能为空");
      }
      // 将过长的关键词截断后再用于日志和实际请求。
      String safeQuery =
          request.query().length() > 100 ? request.query().substring(0, 100) : request.query();
      // 先占用一次机会，超限时直接返回，避免请求 Tavily。
      if (!tryAcquireSearchSlot()) {
        return new WebSearchResponse("搜索次数暂时已达上限，请稍后再试");
      }
      System.out.println("⚡ [Java 真实联网执行] 智能体调用了 searchWebTool，正在检索: " + safeQuery);

      try {
        // 1. 组装请求 Tavily 搜索接口的 JSON 参数
        String requestBody =
            objectMapper.writeValueAsString(
                new Object() {
                  public final String api_key = tavilyApiKey;
                  public final String query = safeQuery;
                  public final int max_results = 3; // 取前3条最相关的新闻
                });

        // 2. 发起真实 HTTP POST 请求
        HttpRequest httpRequest =
            HttpRequest.newBuilder()
                .uri(URI.create("https://api.tavily.com/search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> httpResponse =
            httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

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
            // 限制单条摘要最多 200 字，防止大段网页废话烧光上下文 Token
            if (content.length() > 200) {
              content = content.substring(0, 200) + "...";
            }
            summary.append("【").append(title).append("】: ").append(content).append("\n");
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
