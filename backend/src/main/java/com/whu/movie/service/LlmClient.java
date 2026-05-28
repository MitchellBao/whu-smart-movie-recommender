package com.whu.movie.service;

import com.whu.movie.dto.LlmStatusResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final RestTemplate restTemplate;

    @Value("${llm.api.base-url}")
    private String llmBaseUrl;

    @Value("${llm.api.key:}")
    private String llmApiKey;

    @Value("${llm.api.model}")
    private String llmModel;

    @Value("${llm.api.enabled:false}")
    private Boolean llmEnabled;

    @Value("${llm.api.provider:deepseek}")
    private String llmProvider;

    @Value("${llm.api.thinking-enabled:false}")
    private Boolean thinkingEnabled;

    @Value("${llm.api.reasoning-effort:high}")
    private String reasoningEffort;

    public LlmClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public LlmStatusResponse status() {
        LlmStatusResponse response = new LlmStatusResponse();
        response.setCode(0);
        response.setEnabled(Boolean.TRUE.equals(llmEnabled));
        response.setConfigured(hasRealApiKey());
        response.setProvider(llmProvider);
        response.setModel(llmModel);
        response.setBaseUrl(llmBaseUrl);
        return response;
    }

    public String explainRecommendation(String username, String movieTitle, String genres, Float score) {
        String prompt = "你是一个电影推荐助手。用户是" + username
                + "，系统推荐电影《" + movieTitle + "》，类型为" + genres
                + "，算法推荐分约为" + String.format("%.2f", score)
                + "。请用不超过80字的中文口语解释推荐原因，避免提到你是大模型。";
        return chatCompletion(prompt);
    }

    public String answerUserQuery(String username, String queryText, String userContext) {
        String prompt = "你是一个电影推荐问答助手。用户是" + username
                + "，用户提问：" + queryText
                + "。\n以下是系统已经读取到的真实上下文，请只基于这些数据回答，不要说用户未提供评分：\n"
                + userContext
                + "\n回答要求："
                + "1. 如果用户问“第一名”，必须指当前推荐列表第1名；"
                + "2. 必须参考用户评分、想看、收藏、不感兴趣中的可用信息；"
                + "3. 不要推荐用户已标记“不感兴趣”的电影；"
                + "4. 请给出简洁中文建议，不超过160字。";
        return chatCompletion(prompt);
    }

    private String chatCompletion(String prompt) {
        if (!Boolean.TRUE.equals(llmEnabled) || !hasRealApiKey()) {
            return "当前为离线模式：未启用 DeepSeek 密钥，返回默认解释。";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", llmModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", "你是一个专业、简洁的电影推荐助手。"),
                Map.of("role", "user", "content", prompt)
        ));
        body.put("temperature", 0.5);
        body.put("stream", false);
        if (Boolean.TRUE.equals(thinkingEnabled)) {
            body.put("thinking", Map.of("type", "enabled"));
            body.put("reasoning_effort", reasoningEffort);
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    llmBaseUrl + "/chat/completions",
                    HttpMethod.POST,
                    request,
                    Map.class
            );
            Map responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("choices")) {
                log.warn("LLM response missing choices. provider={}, baseUrl={}, model={}", llmProvider, llmBaseUrl, llmModel);
                return "DeepSeek 响应为空，使用默认解释。";
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices.isEmpty()) {
                log.warn("LLM response choices empty. provider={}, baseUrl={}, model={}", llmProvider, llmBaseUrl, llmModel);
                return "DeepSeek 未返回候选结果，使用默认解释。";
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) {
                log.warn("LLM response message null. provider={}, baseUrl={}, model={}", llmProvider, llmBaseUrl, llmModel);
                return "DeepSeek 内容为空，使用默认解释。";
            }
            Object content = message.get("content");
            return content == null ? "DeepSeek 内容为空，使用默认解释。" : content.toString();
        } catch (HttpStatusCodeException ex) {
            String resp = ex.getResponseBodyAsString();
            if (resp != null && resp.length() > 500) {
                resp = resp.substring(0, 500) + "...(truncated)";
            }
            log.error("LLM HTTP error. provider={}, status={}, baseUrl={}, model={}, body={}",
                    llmProvider, ex.getStatusCode(), llmBaseUrl, llmModel, resp);
            return "DeepSeek 调用失败，使用默认解释。";
        } catch (Exception ex) {
            log.error("LLM call failed. provider={}, baseUrl={}, model={}, error={}",
                    llmProvider, llmBaseUrl, llmModel, ex.toString());
            return "DeepSeek 调用失败，使用默认解释。";
        }
    }

    private boolean hasRealApiKey() {
        if (llmApiKey == null || llmApiKey.isBlank()) {
            return false;
        }
        String normalized = llmApiKey.trim().toLowerCase();
        return !normalized.equals("change_me")
                && !normalized.equals("replace_with_real_api_key")
                && !normalized.equals("your_deepseek_api_key");
    }
}
