package com.workforcehub.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.workforcehub.agent.model.AgentModelProperties;
import com.workforcehub.agent.model.AgentModelException;
import com.workforcehub.agent.model.AgentModelRequest;
import com.workforcehub.agent.model.OpenAiCompatibleAgentModelClient;
import com.workforcehub.agent.model.SpringAiOpenAiChatClientFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent 模型客户端的 Spring AI OpenAI 兼容接入测试。
 */
class AgentModelClientSpringAiTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static HttpServer modelServer;
    private static String modelEndpoint;
    private static final AtomicReference<JsonNode> lastRequestPayload = new AtomicReference<>();

    @BeforeAll
    static void startModelServer() throws IOException {
        modelServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        modelServer.createContext("/v1/chat/completions", exchange -> {
            JsonNode payload = OBJECT_MAPPER.readTree(exchange.getRequestBody());
            lastRequestPayload.set(payload);
            if (payload.path("stream").asBoolean(false)) {
                byte[] response = String.join("\n\n",
                    "data: {\"id\":\"chatcmpl-stream\",\"object\":\"chat.completion.chunk\",\"created\":1710000000,\"model\":\"stub-chat\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"流式\"},\"finish_reason\":null}]}",
                    "data: {\"id\":\"chatcmpl-stream\",\"object\":\"chat.completion.chunk\",\"created\":1710000000,\"model\":\"stub-chat\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"模型回复\"},\"finish_reason\":null}]}",
                    "data: {\"id\":\"chatcmpl-stream\",\"object\":\"chat.completion.chunk\",\"created\":1710000000,\"model\":\"stub-chat\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}",
                    "data: [DONE]", "").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
                return;
            }
            byte[] response = """
                {
                  "id": "chatcmpl-spring-ai-test",
                  "object": "chat.completion",
                  "created": 1710000000,
                  "model": "stub-chat",
                  "usage": {
                    "prompt_tokens": 8,
                    "completion_tokens": 4,
                    "total_tokens": 12
                  },
                  "choices": [
                    {
                      "index": 0,
                      "finish_reason": "stop",
                      "message": {
                        "role": "assistant",
                        "content": "Spring AI 模型回复"
                      }
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        modelServer.start();
        modelEndpoint = "http://127.0.0.1:" + modelServer.getAddress().getPort() + "/v1/chat/completions";
    }

    @AfterAll
    static void stopModelServer() {
        if (modelServer != null) {
            modelServer.stop(0);
        }
    }

    @Test
    @DisplayName("OpenAI兼容客户端应通过Spring AI调用模型服务")
    void openAiCompatibleClientShouldUseSpringAi() {
        AgentModelProperties properties = new AgentModelProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(modelEndpoint);
        properties.setApiKey("test-key");
        properties.setModel("stub-chat");
        properties.setTimeout(Duration.ofSeconds(5));
        properties.setMaxTokens(64);
        properties.setTemperature(0.1D);

        OpenAiCompatibleAgentModelClient client = new OpenAiCompatibleAgentModelClient(
            properties,
            OBJECT_MAPPER,
            new SpringAiOpenAiChatClientFactory()
        );

        String reply = client.generateReply(new AgentModelRequest(
            1L,
            2L,
            "测试助手",
            "你是测试助手",
            "请回答一句话",
            "{\"jsonObjectResponse\":true}"
        )).text();

        assertEquals("Spring AI 模型回复", reply);
        JsonNode payload = lastRequestPayload.get();
        assertNotNull(payload);
        assertEquals("stub-chat", payload.path("model").asText());
        assertEquals(64, payload.path("max_tokens").asInt());
        assertEquals(0.1D, payload.path("temperature").asDouble(), 0.0001D);
        assertEquals("system", payload.path("messages").path(0).path("role").asText());
        assertEquals("你是测试助手", payload.path("messages").path(0).path("content").asText());
        assertEquals("user", payload.path("messages").path(1).path("role").asText());
        assertEquals("请回答一句话", payload.path("messages").path(1).path("content").asText());
        assertEquals("json_object", payload.path("response_format").path("type").asText());
        assertTrue(usesSpringAiFactory(OpenAiCompatibleAgentModelClient.class));
    }

    @Test
    @DisplayName("OpenAI兼容客户端应逐步返回流式模型内容")
    void openAiCompatibleClientShouldStreamContent() {
        AgentModelProperties properties = new AgentModelProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(modelEndpoint);
        properties.setApiKey("test-key");
        properties.setModel("stub-chat");
        properties.setTimeout(Duration.ofSeconds(5));
        properties.setMaxTokens(64);
        properties.setTemperature(0.1D);
        OpenAiCompatibleAgentModelClient client = new OpenAiCompatibleAgentModelClient(
            properties, OBJECT_MAPPER, new SpringAiOpenAiChatClientFactory());
        AtomicReference<String> latestContent = new AtomicReference<>();

        String reply = client.generateStreamingReply(new AgentModelRequest(
            1L, 2L, "测试助手", "你是测试助手", "请流式回答", null
        ), latestContent::set).text();

        assertEquals("流式模型回复", reply);
        assertEquals("流式模型回复", latestContent.get());
        assertTrue(lastRequestPayload.get().path("stream").asBoolean());
    }

    @Test
    @DisplayName("流式调用超过总时限时应取消上游且不重试")
    void streamingTimeoutShouldCancelUpstreamWithoutRetry() {
        AtomicBoolean cancelled = new AtomicBoolean();
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        when(chatClient.stream(any(Prompt.class)))
            .thenReturn(Flux.interval(Duration.ofMillis(20))
                .map(ignored -> chunk("持续正文"))
                .doOnCancel(() -> cancelled.set(true)));
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofMillis(150));
        AtomicReference<String> latestContent = new AtomicReference<>();

        AgentModelException exception = assertThrows(AgentModelException.class,
            () -> client.generateStreamingReply(streamingRequest(), latestContent::set));

        assertEquals("TIMEOUT", exception.failureType());
        assertTrue(latestContent.get().startsWith("持续正文"));
        assertTrue(cancelled.get());
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("流订阅建立阻塞时仍应按总时限返回")
    void blockingStreamSubscriptionShouldStillRespectTotalTimeout() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        when(chatClient.stream(any(Prompt.class))).thenAnswer(ignored -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return Flux.just(chunk("迟到正文"));
        });
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofMillis(100));
        long startedAt = System.nanoTime();

        AgentModelException exception = assertThrows(AgentModelException.class,
            () -> client.generateStreamingReply(streamingRequest(), ignored -> {
            }));

        long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        assertEquals("TIMEOUT", exception.failureType());
        assertTrue(durationMs < 450, "总超时不应等待阻塞订阅完成");
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("流式阶段与同步降级应共享同一总时限")
    void streamingAndSynchronousFallbackShouldShareOneTimeoutBudget() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        when(chatClient.stream(any(Prompt.class))).thenReturn(
            Flux.just(chunk("部分正文"))
                .delayElements(Duration.ofMillis(50))
                .concatWith(Flux.error(responseException(503))));
        when(chatClient.call(any(Prompt.class))).thenAnswer(ignored -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return chunk("降级正文");
        });
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofMillis(400));

        AgentModelException exception = assertThrows(AgentModelException.class,
            () -> client.generateStreamingReply(streamingRequest(), ignored -> {
            }));

        assertEquals("TIMEOUT", exception.failureType());
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient).call(any(Prompt.class));
    }

    @Test
    @DisplayName("流式408、429和5xx应同步降级一次")
    void retryableStatusesShouldFallbackToSynchronousCallOnce() {
        for (int statusCode : List.of(408, 429, 500, 503)) {
            OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
            when(chatClient.stream(any(Prompt.class))).thenReturn(Flux.error(responseException(statusCode)));
            when(chatClient.call(any(Prompt.class))).thenReturn(chunk("降级成功"));
            OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));

            String response = client.generateStreamingReply(streamingRequest(), ignored -> {
            }).text();

            assertEquals("降级成功", response);
            verify(chatClient).stream(any(Prompt.class));
            verify(chatClient).call(any(Prompt.class));
        }
    }

    @Test
    @DisplayName("流式传输异常应同步降级一次")
    void transportFailureShouldFallbackToSynchronousCallOnce() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        WebClientRequestException transportException = new WebClientRequestException(
            new ConnectException("连接被重置"), HttpMethod.POST,
            URI.create("https://model.example/v1/chat/completions"), HttpHeaders.EMPTY);
        when(chatClient.stream(any(Prompt.class))).thenReturn(Flux.error(transportException));
        when(chatClient.call(any(Prompt.class))).thenReturn(chunk("传输恢复"));
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));

        String response = client.generateStreamingReply(streamingRequest(), ignored -> {
        }).text();

        assertEquals("传输恢复", response);
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient).call(any(Prompt.class));
    }

    @Test
    @DisplayName("被包装的流式传输异常仍应同步降级")
    void wrappedTransportFailureShouldFallbackToSynchronousCall() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        WebClientRequestException transportException = new WebClientRequestException(
            new ConnectException("连接被重置"), HttpMethod.POST,
            URI.create("https://model.example/v1/chat/completions"), HttpHeaders.EMPTY);
        when(chatClient.stream(any(Prompt.class)))
            .thenReturn(Flux.error(new RuntimeException(transportException)));
        when(chatClient.call(any(Prompt.class))).thenReturn(chunk("包装异常恢复"));
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));

        String response = client.generateStreamingReply(streamingRequest(), ignored -> {
        }).text();

        assertEquals("包装异常恢复", response);
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient).call(any(Prompt.class));
    }

    @Test
    @DisplayName("Spring AI流适配层在部分内容后空指针应清空并同步降级")
    void springAiStreamingNullPointerShouldFallbackWithCleanBuffer() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        when(chatClient.stream(any(Prompt.class))).thenReturn(
            Flux.concat(Flux.just(chunk("半截内容")), Flux.error(new NullPointerException("流块元数据缺失"))));
        when(chatClient.call(any(Prompt.class))).thenReturn(chunk("完整同步内容"));
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));
        AtomicReference<String> latestContent = new AtomicReference<>();
        AtomicBoolean clearedBeforeFallback = new AtomicBoolean();

        String response = client.generateStreamingReply(streamingRequest(), content -> {
            latestContent.set(content);
            if (content.isEmpty()) {
                clearedBeforeFallback.set(true);
            }
        }).text();

        assertEquals("完整同步内容", response);
        assertEquals("完整同步内容", latestContent.get());
        assertTrue(clearedBeforeFallback.get());
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient).call(any(Prompt.class));
    }

    @Test
    @DisplayName("传输超时应精确分类且不重试")
    void transportTimeoutShouldNotRetry() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        WebClientRequestException timeoutException = new WebClientRequestException(
            new SocketTimeoutException("读取超时"), HttpMethod.POST,
            URI.create("https://model.example/v1/chat/completions"), HttpHeaders.EMPTY);
        when(chatClient.stream(any(Prompt.class))).thenReturn(Flux.error(timeoutException));
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));

        AgentModelException exception = assertThrows(AgentModelException.class,
            () -> client.generateStreamingReply(streamingRequest(), ignored -> {
            }));

        assertEquals("TIMEOUT", exception.failureType());
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("同步降级仍失败时应保留精确的供应商错误分类")
    void failedSynchronousFallbackShouldPreserveProviderFailureType() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        when(chatClient.stream(any(Prompt.class))).thenReturn(Flux.error(responseException(503)));
        when(chatClient.call(any(Prompt.class))).thenThrow(responseException(503));
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));

        AgentModelException exception = assertThrows(AgentModelException.class,
            () -> client.generateStreamingReply(streamingRequest(), ignored -> {
            }));

        assertEquals("PROVIDER_ERROR", exception.failureType());
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient).call(any(Prompt.class));
    }

    @Test
    @DisplayName("同步降级仍限流时应保留限流分类")
    void rateLimitedSynchronousFallbackShouldRemainRateLimited() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        when(chatClient.stream(any(Prompt.class))).thenReturn(Flux.error(responseException(429)));
        when(chatClient.call(any(Prompt.class))).thenThrow(responseException(429));
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));

        AgentModelException exception = assertThrows(AgentModelException.class,
            () -> client.generateStreamingReply(streamingRequest(), ignored -> {
            }));

        assertEquals("RATE_LIMITED", exception.failureType());
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient).call(any(Prompt.class));
    }

    @Test
    @DisplayName("同步降级仍返回HTTP超时时应保留超时分类")
    void timedOutSynchronousFallbackShouldRemainTimeout() {
        for (int statusCode : List.of(408, 504)) {
            OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
            when(chatClient.stream(any(Prompt.class))).thenReturn(Flux.error(responseException(statusCode)));
            when(chatClient.call(any(Prompt.class))).thenThrow(responseException(statusCode));
            OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));

            AgentModelException exception = assertThrows(AgentModelException.class,
                () -> client.generateStreamingReply(streamingRequest(), ignored -> {
                }));

            assertEquals("TIMEOUT", exception.failureType());
            verify(chatClient).stream(any(Prompt.class));
            verify(chatClient).call(any(Prompt.class));
        }
    }

    @Test
    @DisplayName("Spring AI包装的同步504应映射为超时")
    void springAiTransient504ShouldMapToTimeout() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        when(chatClient.stream(any(Prompt.class)))
            .thenReturn(Flux.error(new TransientAiException("504 - gateway timeout")));
        when(chatClient.call(any(Prompt.class)))
            .thenThrow(new TransientAiException("504 - gateway timeout"));
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));

        AgentModelException exception = assertThrows(AgentModelException.class,
            () -> client.generateStreamingReply(streamingRequest(), ignored -> {
            }));

        assertEquals("TIMEOUT", exception.failureType());
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient).call(any(Prompt.class));
    }

    @Test
    @DisplayName("认证失败应精确分类且不重试")
    void authenticationFailureShouldNotRetry() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        when(chatClient.stream(any(Prompt.class))).thenReturn(Flux.error(responseException(401)));
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));

        AgentModelException exception = assertThrows(AgentModelException.class,
            () -> client.generateStreamingReply(streamingRequest(), ignored -> {
            }));

        assertEquals("AUTH_FAILED", exception.failureType());
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("部分正文后连接中断应清空旧内容并同步降级")
    void providerFailureAfterContentShouldFallbackWithCleanBuffer() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        when(chatClient.stream(any(Prompt.class))).thenReturn(
            Flux.concat(Flux.just(chunk("部分正文")), Flux.error(responseException(503))));
        when(chatClient.call(any(Prompt.class))).thenReturn(chunk("完整正文"));
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));
        AtomicReference<String> latestContent = new AtomicReference<>();
        AtomicBoolean clearedBeforeRetry = new AtomicBoolean();

        String response = client.generateStreamingReply(streamingRequest(), content -> {
            latestContent.set(content);
            if (content.isEmpty()) {
                clearedBeforeRetry.set(true);
            }
        }).text();

        assertEquals("完整正文", response);
        assertEquals("完整正文", latestContent.get());
        assertTrue(clearedBeforeRetry.get());
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient).call(any(Prompt.class));
    }

    @Test
    @DisplayName("部分输出中断且同步降级失败时应保留供应商错误")
    void failedSynchronousFallbackAfterContentShouldRemainProviderError() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        when(chatClient.stream(any(Prompt.class))).thenReturn(
            Flux.concat(Flux.just(chunk("截断正文")), Flux.error(responseException(503))));
        when(chatClient.call(any(Prompt.class))).thenThrow(responseException(503));
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));
        AtomicBoolean clearedBeforeRetry = new AtomicBoolean();

        AgentModelException exception = assertThrows(AgentModelException.class,
            () -> client.generateStreamingReply(streamingRequest(), content -> {
                if (content.isEmpty()) {
                    clearedBeforeRetry.set(true);
                }
            }));

        assertEquals("PROVIDER_ERROR", exception.failureType());
        assertTrue(clearedBeforeRetry.get());
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient).call(any(Prompt.class));
    }

    @Test
    @DisplayName("结构化流式正文无效时应清空并同步降级一次")
    void invalidStructuredStreamShouldFallbackToSynchronousJson() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        when(chatClient.stream(any(Prompt.class))).thenReturn(Flux.just(chunk("{\"broken\":")));
        when(chatClient.call(any(Prompt.class))).thenReturn(chunk("{\"valid\":true}"));
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));
        AtomicReference<String> latestContent = new AtomicReference<>();
        AtomicBoolean clearedBeforeFallback = new AtomicBoolean();

        String response = client.generateStreamingReply(structuredStreamingRequest(), content -> {
            latestContent.set(content);
            if (content.isEmpty()) {
                clearedBeforeFallback.set(true);
            }
        }).text();

        assertEquals("{\"valid\":true}", response);
        assertEquals("{\"valid\":true}", latestContent.get());
        assertTrue(clearedBeforeFallback.get());
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient).call(any(Prompt.class));
    }

    @Test
    @DisplayName("有效结构化流应直接成功且不调用同步接口")
    void validStructuredStreamShouldNotFallback() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        when(chatClient.stream(any(Prompt.class)))
            .thenReturn(Flux.just(chunk("{\"second\":2,\"first\":1}")));
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));

        String response = client.generateStreamingReply(structuredStreamingRequest(), ignored -> {
        }).text();

        assertEquals("{\"second\":2,\"first\":1}", response);
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("结构化空流应同步降级一次")
    void emptyStructuredStreamShouldFallbackOnce() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        when(chatClient.stream(any(Prompt.class))).thenReturn(Flux.empty());
        when(chatClient.call(any(Prompt.class))).thenReturn(chunk("{\"valid\":true}"));
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));

        String response = client.generateStreamingReply(structuredStreamingRequest(), ignored -> {
        }).text();

        assertEquals("{\"valid\":true}", response);
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient).call(any(Prompt.class));
    }

    @Test
    @DisplayName("环境回退模型不声明结构化能力时仍应严格校验业务JSON")
    void environmentFallbackShouldValidateJsonWithoutRequestingResponseFormat() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        when(chatClient.stream(any(Prompt.class))).thenReturn(Flux.just(chunk("{\"broken\":")));
        when(chatClient.call(any(Prompt.class))).thenReturn(chunk("{\"valid\":true}"));
        AtomicReference<SpringAiOpenAiChatClientFactory.ResolvedAgentModelConfig> resolvedConfig =
            new AtomicReference<>();
        SpringAiOpenAiChatClientFactory factory = mock(SpringAiOpenAiChatClientFactory.class);
        when(factory.create(any())).thenAnswer(invocation -> {
            resolvedConfig.set(invocation.getArgument(0));
            return chatClient;
        });
        AgentModelProperties properties = new AgentModelProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://model.example");
        properties.setApiKey("test-key");
        properties.setModel("stub-chat");
        properties.setTimeout(Duration.ofSeconds(2));
        properties.setMaxTokens(64);
        properties.setTemperature(0.1D);
        OpenAiCompatibleAgentModelClient client = new OpenAiCompatibleAgentModelClient(
            properties, OBJECT_MAPPER, factory);

        String response = client.generateStreamingReply(new AgentModelRequest(
            1L, 2L, "测试助手", "你是测试助手", "请流式回答",
            "{\"requireJsonObjectResponse\":true}"), ignored -> {
        }).text();

        assertEquals("{\"valid\":true}", response);
        assertFalse(resolvedConfig.get().jsonObjectResponse());
        assertTrue(resolvedConfig.get().requireJsonObjectResponse());
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient).call(any(Prompt.class));
    }

    @Test
    @DisplayName("结构化同步降级仍无效时应停止并标记响应无效")
    void invalidStructuredSynchronousFallbackShouldStop() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        when(chatClient.stream(any(Prompt.class))).thenReturn(Flux.just(chunk("{\"broken\":")));
        when(chatClient.call(any(Prompt.class))).thenReturn(chunk("仍然不是JSON"));
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));

        AgentModelException exception = assertThrows(AgentModelException.class,
            () -> client.generateStreamingReply(structuredStreamingRequest(), ignored -> {
            }));

        assertEquals("INVALID_RESPONSE", exception.failureType());
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient).call(any(Prompt.class));
    }

    @Test
    @DisplayName("网络降级后同步内容仍无效时不得再次降级")
    void invalidStructuredNetworkFallbackShouldNotFallbackTwice() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        when(chatClient.stream(any(Prompt.class))).thenReturn(Flux.error(responseException(503)));
        when(chatClient.call(any(Prompt.class))).thenReturn(chunk("仍然不是JSON"));
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));

        AgentModelException exception = assertThrows(AgentModelException.class,
            () -> client.generateStreamingReply(structuredStreamingRequest(), ignored -> {
            }));

        assertEquals("INVALID_RESPONSE", exception.failureType());
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient).call(any(Prompt.class));
    }

    @Test
    @DisplayName("结构化输出应拒绝数组、标量、截断内容和多个JSON对象")
    void structuredResponseShouldRequireExactlyOneJsonObject() {
        for (String invalidResponse : List.of("[]", "1", "{\"broken\":", "{} {}")) {
            OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
            when(chatClient.stream(any(Prompt.class))).thenReturn(Flux.just(chunk(invalidResponse)));
            when(chatClient.call(any(Prompt.class))).thenReturn(chunk(invalidResponse));
            OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));

            AgentModelException exception = assertThrows(AgentModelException.class,
                () -> client.generateStreamingReply(structuredStreamingRequest(), ignored -> {
                }));

            assertEquals("INVALID_RESPONSE", exception.failureType(), invalidResponse);
            verify(chatClient).stream(any(Prompt.class));
            verify(chatClient).call(any(Prompt.class));
        }
    }

    @Test
    @DisplayName("流式内容为空应标记响应无效且不重试")
    void emptyStreamingResponseShouldNotRetry() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        when(chatClient.stream(any(Prompt.class))).thenReturn(Flux.empty());
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));

        AgentModelException exception = assertThrows(AgentModelException.class,
            () -> client.generateStreamingReply(streamingRequest(), ignored -> {
            }));

        assertEquals("INVALID_RESPONSE", exception.failureType());
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("流式响应解析失败应标记响应无效且不重试")
    void streamingParseFailureShouldNotRetry() {
        OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        JsonProcessingException parseException = new JsonProcessingException("无效SSE JSON") {
        };
        when(chatClient.stream(any(Prompt.class)))
            .thenReturn(Flux.error(new RuntimeException(parseException)));
        OpenAiCompatibleAgentModelClient client = streamingClient(chatClient, Duration.ofSeconds(2));

        AgentModelException exception = assertThrows(AgentModelException.class,
            () -> client.generateStreamingReply(streamingRequest(), ignored -> {
            }));

        assertEquals("INVALID_RESPONSE", exception.failureType());
        verify(chatClient).stream(any(Prompt.class));
        verify(chatClient, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("管理端完整模型配置不应受环境回退开关限制")
    void explicitAdminModelConfigShouldWorkWhenEnvironmentFallbackIsDisabled() {
        AgentModelProperties properties = new AgentModelProperties();
        properties.setEnabled(false);
        OpenAiCompatibleAgentModelClient client = new OpenAiCompatibleAgentModelClient(
            properties, OBJECT_MAPPER, new SpringAiOpenAiChatClientFactory());

        String config = """
            {"baseUrl":"%s","apiKey":"admin-key","model":"stub-chat","maxTokens":32,"temperature":0.1,"timeout":"5s"}
            """.formatted(modelEndpoint);
        String reply = client.generateReply(new AgentModelRequest(
            null, 2L, "资源生成智能体", "你是测试助手", "请回答一句话", config)).text();

        assertEquals("Spring AI 模型回复", reply);
        assertEquals("stub-chat", lastRequestPayload.get().path("model").asText());
    }

    private boolean usesSpringAiFactory(Class<?> targetClass) {
        for (java.lang.reflect.Field field : targetClass.getDeclaredFields()) {
            if (field.getType().equals(SpringAiOpenAiChatClientFactory.class)) {
                return true;
            }
        }
        return false;
    }

    private OpenAiCompatibleAgentModelClient streamingClient(OpenAiChatClient chatClient, Duration timeout) {
        SpringAiOpenAiChatClientFactory factory = mock(SpringAiOpenAiChatClientFactory.class);
        when(factory.create(any())).thenReturn(chatClient);
        AgentModelProperties properties = new AgentModelProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://model.example");
        properties.setApiKey("test-key");
        properties.setModel("stub-chat");
        properties.setTimeout(timeout);
        properties.setMaxTokens(64);
        properties.setTemperature(0.1D);
        return new OpenAiCompatibleAgentModelClient(properties, OBJECT_MAPPER, factory);
    }

    private AgentModelRequest streamingRequest() {
        return new AgentModelRequest(1L, 2L, "测试助手", "你是测试助手", "请流式回答", null);
    }

    private AgentModelRequest structuredStreamingRequest() {
        return new AgentModelRequest(1L, 2L, "测试助手", "你是测试助手", "请流式回答",
            "{\"jsonObjectResponse\":true}");
    }

    private ChatResponse chunk(String content) {
        return new ChatResponse(List.of(new Generation(content)));
    }

    private WebClientResponseException responseException(int statusCode) {
        return new WebClientResponseException(statusCode, "model error", HttpHeaders.EMPTY,
            new byte[0], StandardCharsets.UTF_8);
    }
}
