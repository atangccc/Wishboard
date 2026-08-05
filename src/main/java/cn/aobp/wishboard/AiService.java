package cn.aobp.wishboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.infra.utils.JsonUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * AI 服务：暖心回复、情绪标签、内容审核。
 * 支持 OpenAI 兼容接口（OpenAI / DeepSeek / 通义千问）。
 */
@Slf4j
@Service
public class AiService {

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    /**
     * 生成暖心回复
     */
    public Mono<String> generateWarmReply(String content, String apiBase,
                                          String apiKey, String model,
                                          String systemPrompt) {
        return callChat(apiBase, apiKey, model, systemPrompt, content)
            .onErrorResume(e -> {
                log.warn("[Wishboard AI] Warm reply failed: {}", e.getMessage());
                return Mono.just("");
            });
    }

    /**
     * 识别情绪标签，返回单个 emoji
     */
    public Mono<String> detectEmotion(String content, String apiBase,
                                      String apiKey, String model) {
        String prompt = "分析以下文字的情绪，只返回一个最合适的emoji，不要返回其他任何内容。";
        return callChat(apiBase, apiKey, model, prompt, content)
            .map(String::trim)
            .onErrorResume(e -> {
                log.warn("[Wishboard AI] Emotion detect failed: {}", e.getMessage());
                return Mono.just("💭");
            });
    }

    /**
     * 内容审核，返回 true 表示通过
     */
    public Mono<Boolean> reviewContent(String content, String nickname,
                                       String apiBase, String apiKey, String model) {
        String prompt = "你是内容审核员。判断以下用户的昵称和留言内容是否包含违规内容" +
            "（色情、暴力、广告、政治敏感、人身攻击、不雅词汇）。" +
            "只回答 PASS 或 REJECT，不要解释。";
        String combined = "昵称: " + nickname + "\n内容: " + content;
        return callChat(apiBase, apiKey, model, prompt, combined)
            .map(reply -> reply.trim().toUpperCase().contains("PASS"))
            .onErrorResume(e -> {
                log.warn("[Wishboard AI] Content review failed: {}", e.getMessage());
                return Mono.just(false);
            });
    }

    private Mono<String> callChat(String apiBase, String apiKey, String model,
                                  String systemPrompt, String userContent) {
        return Mono.fromCallable(() -> {
            ObjectMapper mapper = JsonUtils.DEFAULT_JSON_MAPPER;
            var messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
            );
            var body = Map.of(
                "model", model,
                "messages", messages,
                "max_tokens", 100,
                "temperature", 0.7
            );
            String jsonBody = mapper.writeValueAsString(body);
            String url = apiBase.replaceAll("/+$", "") + "/v1/chat/completions";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new RuntimeException(
                    "AI API error: HTTP " + statusCode + " - " + response.body());
            }
            JsonNode root = mapper.readTree(response.body());
            return root.path("choices").path(0)
                .path("message").path("content").asText("");
        });
    }
}
