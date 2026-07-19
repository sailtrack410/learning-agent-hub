package com.workforcehub.agent.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.codec.DecodingException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring AI OpenAI 兼容 Chat Completions 模型客户端。
 */
// LLM 客户端：封装 OpenAI 兼容 API 调用，支持流式/同步、tool-call 解析、错误分类
@Service
@EnableConfigurationProperties(AgentModelProperties.class)
public class OpenAiCompatibleAgentModelClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAgentModelClient.class);
    private static final String CHAT_COMPLETIONS_SUFFIX = "/chat/completions";
    private static final String VERSIONED_CHAT_COMPLETIONS_SUFFIX = "/v1/chat/completions";
    private static final Duration STREAM_FALLBACK_DELAY = Duration.ofMillis(250);
    private static final Pattern SPRING_AI_STATUS_MESSAGE = Pattern.compile("^(\\d{3})\\s+-\\s+.*", Pattern.DOTALL);

    private final AgentModelProperties properties;
    private final ObjectMapper objectMapper;
    private final SpringAiOpenAiChatClientFactory chatClientFactory;

    public OpenAiCompatibleAgentModelClient(AgentModelProperties properties,
                                            ObjectMapper objectMapper,
                                            SpringAiOpenAiChatClientFactory chatClientFactory) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.chatClientFactory = chatClientFactory;
    }

    public AgentModelResponse generateReply(AgentModelRequest request) {
        SpringAiOpenAiChatClientFactory.ResolvedAgentModelConfig config = resolveConfig(request.modelConfigJson());
        try {
            OpenAiChatClient chatClient = chatClientFactory.create(config);
            ChatResponse response = chatClient.call(buildPrompt(request));
            return parseResponse(response);
        } catch (AgentModelException e) {
            throw e;
        } catch (NonTransientAiException e) {
            throw mapSpringAiStatusException(e);
        } catch (TransientAiException e) {
            throw mapSpringAiStatusException(e);
        } catch (RestClientResponseException e) {
            throw mapResponseException(e);
        } catch (RestClientException e) {
            if (e.contains(java.net.http.HttpTimeoutException.class)) {
                throw new AgentModelException("TIMEOUT", "模型服务调用超时", e);
            }
            throw new AgentModelException("PROVIDER_ERROR", "模型服务调用失败", e);
        }
    }

    public AgentModelResponse generateStreamingReply(AgentModelRequest request, Consumer<String> contentListener) {
        SpringAiOpenAiChatClientFactory.ResolvedAgentModelConfig config = resolveConfig(request.modelConfigJson());
        StringBuilder content = new StringBuilder();
        AtomicBoolean synchronousFallbackUsed = new AtomicBoolean();
        try {
            OpenAiChatClient chatClient = chatClientFactory.create(config);
            Prompt prompt = buildPrompt(request);
            return Flux.defer(() -> chatClient.stream(prompt))
                // Spring AI 在建立流订阅时可能同步阻塞，必须先切换调度器才能让总超时立即开始计时。
                .subscribeOn(Schedulers.boundedElastic())
                // 先让流内容与错误在同一调度队列中保持顺序，避免降级清空后又追加旧块。
                .publishOn(Schedulers.boundedElastic(), 1)
                .onErrorResume(this::isRetryableStreamingFailure, exception ->
                    synchronousFallback(chatClient, prompt, content, contentListener, synchronousFallbackUsed).flux())
                .doOnNext(response -> appendResponse(content, contentListener, response))
                // Flux.timeout(Duration) 只约束相邻数据块的空闲时间；转为终止 Mono 后才是整段流的总时限。
                .then(Mono.defer(() -> {
                    if (config.requireJsonObjectResponse() && !isJsonObjectResponse(content)) {
                        if (synchronousFallbackUsed.get()) {
                            return Mono.error(new AgentModelException("INVALID_RESPONSE", "模型服务返回格式无效"));
                        }
                        return synchronousFallback(chatClient, prompt, content, contentListener, synchronousFallbackUsed)
                            .map(response -> {
                                appendResponse(content, contentListener, response);
                                if (!isJsonObjectResponse(content)) {
                                    throw new AgentModelException("INVALID_RESPONSE", "模型服务返回格式无效");
                                }
                                return new AgentModelResponse(content.toString().trim());
                            });
                    }
                    if (!StringUtils.hasText(content)) {
                        return Mono.error(new AgentModelException("INVALID_RESPONSE", "模型服务返回内容为空"));
                    }
                    return Mono.just(new AgentModelResponse(content.toString().trim()));
                }))
                .timeout(config.timeout())
                .block();
        } catch (RuntimeException exception) {
            AgentModelException mapped = mapStreamingException(exception);
            Throwable cause = Exceptions.unwrap(exception);
            Throwable rootCause = cause;
            while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
                rootCause = rootCause.getCause();
            }
            log.warn("模型流式调用失败，failureType={}，exceptionType={}，rootCauseType={}",
                mapped.failureType(), cause.getClass().getSimpleName(), rootCause.getClass().getSimpleName());
            throw mapped;
        }
    }

    private Mono<ChatResponse> synchronousFallback(OpenAiChatClient chatClient,
                                                    Prompt prompt,
                                                    StringBuilder content,
                                                    Consumer<String> contentListener,
                                                    AtomicBoolean fallbackUsed) {
        return Mono.defer(() -> {
            if (!fallbackUsed.compareAndSet(false, true)) {
                return Mono.error(new AgentModelException("PROVIDER_ERROR", "模型服务同步降级已执行"));
            }
            content.setLength(0);
            contentListener.accept("");
            return Mono.delay(STREAM_FALLBACK_DELAY)
                .then(Mono.fromCallable(() -> chatClient.call(prompt))
                    .subscribeOn(Schedulers.boundedElastic()));
        });
    }

    private void appendResponse(StringBuilder content,
                                Consumer<String> contentListener,
                                ChatResponse response) {
        String chunk = responseText(response);
        if (!chunk.isEmpty()) {
            content.append(chunk);
            contentListener.accept(content.toString());
        }
    }

    private boolean isJsonObjectResponse(CharSequence content) {
        String normalized = content == null ? "" : content.toString().trim();
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return false;
        }
        try {
            JsonNode parsed = objectMapper.reader()
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(normalized.substring(start, end + 1));
            return parsed != null && parsed.isObject();
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean isRetryableStreamingFailure(Throwable exception) {
        Throwable cause = Exceptions.unwrap(exception);
        if (containsCause(cause, AgentModelException.class)
            || isTimeoutFailure(cause)
            || isParsingFailure(cause)) {
            return false;
        }
        Throwable current = cause;
        while (current != null) {
            if (current instanceof WebClientResponseException responseException) {
                return isRetryableStatus(responseException.getStatusCode().value());
            }
            if (current instanceof RestClientResponseException responseException) {
                return isRetryableStatus(responseException.getStatusCode().value());
            }
            if (current instanceof NonTransientAiException || current instanceof TransientAiException) {
                Integer statusCode = extractSpringAiStatusCode(current);
                if (statusCode != null) {
                    return isRetryableStatus(statusCode);
                }
                return current instanceof TransientAiException;
            }
            // Spring AI 0.8.1 处理部分 OpenAI 兼容 SSE 块时可能因缺失元数据抛出 NPE。
            // 该分支位于内容 listener 之前，因此只会降级模型流上游异常。
            if (current instanceof NullPointerException) {
                return true;
            }
            if (current instanceof WebClientRequestException || current instanceof RestClientException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private AgentModelException mapStreamingException(Throwable exception) {
        Throwable cause = Exceptions.unwrap(exception);
        Throwable current = cause;
        while (current != null) {
            if (current instanceof AgentModelException modelException) {
                return modelException;
            }
            current = current.getCause();
        }
        if (isTimeoutFailure(cause)) {
            return new AgentModelException("TIMEOUT", "模型服务调用超时", cause);
        }
        if (isParsingFailure(cause)) {
            return new AgentModelException("INVALID_RESPONSE", "模型服务返回格式无效", cause);
        }
        current = cause;
        while (current != null) {
            if (current instanceof WebClientResponseException responseException) {
                return mapStatusException(responseException.getStatusCode().value(), responseException);
            }
            if (current instanceof RestClientResponseException responseException) {
                return mapResponseException(responseException);
            }
            if (current instanceof NonTransientAiException || current instanceof TransientAiException) {
                return mapSpringAiStatusException(current);
            }
            current = current.getCause();
        }
        return new AgentModelException("PROVIDER_ERROR", "模型服务调用失败", cause);
    }

    private boolean containsCause(Throwable exception, Class<? extends Throwable> type) {
        Throwable current = exception;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isTimeoutFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof TimeoutException
                || current instanceof HttpTimeoutException
                || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isParsingFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof JsonProcessingException || current instanceof DecodingException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private SpringAiOpenAiChatClientFactory.ResolvedAgentModelConfig resolveConfig(String modelConfigJson) {
        JsonNode node = readConfig(modelConfigJson);
        boolean explicitConnection = hasText(node, "baseUrl") && hasText(node, "apiKey") && hasText(node, "model");
        if (!properties.isEnabled() && !explicitConnection) {
            throw new AgentModelException("CONFIG_MISSING", "AI模型服务未启用");
        }
        String baseUrl = normalizeBaseUrl(stringValue(node, "baseUrl", properties.getBaseUrl()));
        String apiKey = stringValue(node, "apiKey", properties.getApiKey());
        String model = stringValue(node, "model", properties.getModel());
        Integer maxTokens = intValue(node, "maxTokens", properties.getMaxTokens());
        Double temperature = doubleValue(node, "temperature", properties.getTemperature());
        Duration timeout = durationValue(node, "timeout", properties.getTimeout());
        boolean jsonObjectResponse = node.path("jsonObjectResponse").asBoolean(false);
        boolean requireJsonObjectResponse = node.path("requireJsonObjectResponse").asBoolean(jsonObjectResponse);
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(apiKey) || !StringUtils.hasText(model)) {
            throw new AgentModelException("CONFIG_MISSING", "AI模型配置缺失");
        }
        return new SpringAiOpenAiChatClientFactory.ResolvedAgentModelConfig(
            baseUrl,
            apiKey.trim(),
            model.trim(),
            maxTokens,
            temperature,
            timeout,
            jsonObjectResponse,
            requireJsonObjectResponse
        );
    }

    private boolean hasText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value != null && !value.isNull() && StringUtils.hasText(value.asText());
    }

    private Prompt buildPrompt(AgentModelRequest request) {
        List<Message> messages = new ArrayList<>();
        if (StringUtils.hasText(request.systemPrompt())) {
            messages.add(new SystemMessage(request.systemPrompt().trim()));
        }
        messages.add(new UserMessage(request.userMessage() == null ? "" : request.userMessage()));
        return new Prompt(messages);
    }

    private AgentModelResponse parseResponse(ChatResponse response) {
        String text = responseText(response);
        if (!StringUtils.hasText(text)) {
            throw new AgentModelException("INVALID_RESPONSE", "模型服务返回内容为空");
        }
        return new AgentModelResponse(text.trim());
    }

    private String responseText(ChatResponse response) {
        return response == null || response.getResult() == null
            || response.getResult().getOutput() == null
            || response.getResult().getOutput().getContent() == null
            ? ""
            : response.getResult().getOutput().getContent();
    }

    private AgentModelException mapSpringAiStatusException(Throwable e) {
        Integer statusCode = extractSpringAiStatusCode(e);
        if (statusCode == null) {
            return new AgentModelException("PROVIDER_ERROR", "模型服务请求失败", e);
        }
        return mapStatusException(statusCode, e);
    }

    private Integer extractSpringAiStatusCode(Throwable e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            return null;
        }
        Matcher matcher = SPRING_AI_STATUS_MESSAGE.matcher(message);
        if (!matcher.matches()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private AgentModelException mapResponseException(RestClientResponseException e) {
        return mapStatusException(e.getStatusCode().value(), e);
    }

    private AgentModelException mapStatusException(int statusCode, Throwable cause) {
        if (statusCode == 401 || statusCode == 403) {
            return new AgentModelException("AUTH_FAILED", "模型服务认证失败", cause);
        }
        if (statusCode == 408 || statusCode == 504) {
            return new AgentModelException("TIMEOUT", "模型服务调用超时", cause);
        }
        if (statusCode == 429) {
            return new AgentModelException("RATE_LIMITED", "模型服务限流", cause);
        }
        if (statusCode >= 500) {
            return new AgentModelException("PROVIDER_ERROR", "模型服务返回错误", cause);
        }
        return new AgentModelException("PROVIDER_ERROR", "模型服务请求失败", cause);
    }

    private JsonNode readConfig(String modelConfigJson) {
        if (!StringUtils.hasText(modelConfigJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(modelConfigJson);
        } catch (Exception e) {
            throw new AgentModelException("CONFIG_MISSING", "AI模型配置不是合法JSON", e);
        }
    }

    private String stringValue(JsonNode node, String fieldName, String fallback) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull() || !StringUtils.hasText(valueNode.asText())) {
            return fallback;
        }
        return valueNode.asText();
    }

    private Integer intValue(JsonNode node, String fieldName, Integer fallback) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return fallback;
        }
        return valueNode.asInt(fallback == null ? 1024 : fallback);
    }

    private Double doubleValue(JsonNode node, String fieldName, Double fallback) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return fallback;
        }
        return valueNode.asDouble(fallback == null ? 0.2D : fallback);
    }

    private Duration durationValue(JsonNode node, String fieldName, Duration fallback) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull() || !StringUtils.hasText(valueNode.asText())) {
            return fallback;
        }
        try {
            return DurationStyle.detectAndParse(valueNode.asText().trim());
        } catch (Exception e) {
            throw new AgentModelException("CONFIG_MISSING", "AI模型超时配置不合法", e);
        }
    }

    private String normalizeBaseUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = trimTrailingSlash(value.trim());
        if (trimmed.endsWith(VERSIONED_CHAT_COMPLETIONS_SUFFIX)) {
            return trimmed.substring(0, trimmed.length() - VERSIONED_CHAT_COMPLETIONS_SUFFIX.length());
        }
        if (trimmed.endsWith(CHAT_COMPLETIONS_SUFFIX)) {
            return trimmed.substring(0, trimmed.length() - CHAT_COMPLETIONS_SUFFIX.length());
        }
        if (trimmed.endsWith("/v1")) {
            return trimmed.substring(0, trimmed.length() - "/v1".length());
        }
        return trimmed;
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
