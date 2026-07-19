package com.workforcehub.learningresource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.model.AgentModelException;
import com.workforcehub.agent.model.AgentRuntimeModelConfigService;
import com.workforcehub.agent.model.AgentModelRequest;
import com.workforcehub.agent.model.AgentModelResponse;
import com.workforcehub.agent.model.OpenAiCompatibleAgentModelClient;
import com.workforcehub.course.repository.CourseKnowledgeRepository;
import com.workforcehub.learningresource.repository.LearningResourceRepository;
import com.workforcehub.learningresource.service.LearningContentSafetyPolicyService;
import com.workforcehub.learningresource.service.LearningResourceGenerationService;
import com.workforcehub.ragflow.repository.RagflowSearchHitRow;
import com.workforcehub.ragflow.service.RagflowKnowledgeSearchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningResourceGenerationServiceTest {

    @Test
    void generationRolesShouldUseEnoughOutputTokensForStructuredResources() throws Exception {
        LearningResourceRepository repository = mock(LearningResourceRepository.class);
        CourseKnowledgeRepository courseKnowledgeRepository = mock(CourseKnowledgeRepository.class);
        RagflowKnowledgeSearchService knowledgeSearchService = mock(RagflowKnowledgeSearchService.class);
        OpenAiCompatibleAgentModelClient modelClient = mock(OpenAiCompatibleAgentModelClient.class);
        LearningContentSafetyPolicyService contentSafetyPolicyService = mock(LearningContentSafetyPolicyService.class);
        AgentRuntimeModelConfigService runtimeModelConfigService = mock(AgentRuntimeModelConfigService.class);
        when(runtimeModelConfigService.resolve()).thenReturn(new AgentRuntimeModelConfigService.RuntimeModelConfig(
            "https://model.example/v1", "test-key", "test-model", 0.2D,
            java.time.Duration.ofSeconds(30), true, "ADMIN"));
        ObjectMapper objectMapper = new ObjectMapper();
        LearningResourceGenerationService service = new LearningResourceGenerationService(
            repository, courseKnowledgeRepository, knowledgeSearchService, modelClient, runtimeModelConfigService,
            contentSafetyPolicyService, objectMapper, new ImmediateTransactionManager());

        LocalDateTime now = LocalDateTime.now();
        LearningResourceRepository.TaskRow task = new LearningResourceRepository.TaskRow(
            1L, 2L, 3L, "Java Web 应用开发", 4L, "Servlet 与 Web 基础", 5L,
            "HTTP 请求与响应", 11, "{\"dimensions\":[]}", "补齐请求响应基础",
            "PENDING", 0, null, 1, null, null, null, null, null, null, now, now);
        LearningResourceRepository.CoursePointRow point = new LearningResourceRepository.CoursePointRow(
            3L, "Java Web 应用开发", 4L, "Servlet 与 Web 基础", 5L,
            "HTTP 请求与响应", "理解请求参数、状态码与响应体");
        when(repository.getTask(1L)).thenReturn(task);
        when(repository.markRunning(1L, 1, "test-model")).thenReturn(1);
        when(contentSafetyPolicyService.current()).thenReturn(new LearningContentSafetyPolicyService.Policy(
            true, new BigDecimal("0.9500"), List.of("ignore previous"), 1, now));
        when(repository.getPublishedCoursePoint(3L, 5L)).thenReturn(point);
        when(courseKnowledgeRepository.findSpaceId(3L)).thenReturn(6L);
        when(knowledgeSearchService.searchRows(eq(6L), anyString(), eq(5))).thenReturn(List.of(
            new RagflowSearchHitRow(7L, 8L, "Servlet 课程讲义", 1,
                "HttpServletRequest 提供请求参数，HttpServletResponse 设置状态码和响应体。",
                null, new BigDecimal("0.920000"))));
        List<AgentModelResponse> responses = List.of(
            response("{\"learnerSummary\":\"基础薄弱\",\"difficulty\":\"基础\",\"focuses\":[\"请求参数\"]}"),
            response("{\"objective\":\"理解请求响应\",\"prerequisites\":[],\"sequence\":[\"请求\",\"响应\"],\"estimatedMinutes\":30}"),
            response("""
                {"explanation":{"title":"请求响应讲解","summary":"核心概念","markdown":"# 讲解"},
                 "mindMap":{"title":"请求响应导图","summary":"关系图","mermaid":"mindmap\\n  root((HTTP))"},
                 "reading":{"title":"拓展阅读","summary":"深入理解","markdown":"# 拓展"}}
                """),
            response("""
                {"exercise":{"title":"HTTP 练习","summary":"检验掌握度","markdown":"# 练习"},
                 "codeLab":{"title":"Servlet 实操","summary":"完成接口","markdown":"# 实操"}}
                """),
            response("""
                {"decision":"PASSED","confidence":0.92,"reason":"均由课程讲义支持",
                 "resourceChecks":[]}
                """)
        );
        AtomicInteger responseIndex = new AtomicInteger();
        when(modelClient.generateStreamingReply(any(AgentModelRequest.class), any())).thenAnswer(invocation -> {
            AgentModelResponse response = responses.get(responseIndex.getAndIncrement());
            Consumer<String> listener = invocation.getArgument(1);
            listener.accept(response.text());
            return response;
        });

        service.execute(1L);

        ArgumentCaptor<AgentModelRequest> requests = ArgumentCaptor.forClass(AgentModelRequest.class);
        verify(modelClient, times(5)).generateStreamingReply(requests.capture(), any());
        verify(repository, times(5)).updateStepLiveOutput(anyLong(), anyString());
        List<Integer> maxTokens = requests.getAllValues().stream()
            .map(AgentModelRequest::modelConfigJson)
            .map(config -> readMaxTokens(objectMapper, config))
            .toList();
        assertEquals(List.of(1024, 1536, 3072, 3072, 2048), maxTokens);
        List<String> timeouts = requests.getAllValues().stream()
            .map(AgentModelRequest::modelConfigJson)
            .map(config -> readTimeout(objectMapper, config))
            .toList();
        assertEquals(List.of("60s", "60s", "180s", "180s", "120s"), timeouts);
        requests.getAllValues().forEach(request -> {
            try {
                JsonNode config = objectMapper.readTree(request.modelConfigJson());
                assertEquals("https://model.example/v1", config.path("baseUrl").asText());
                assertEquals("test-model", config.path("model").asText());
                assertEquals("test-key", config.path("apiKey").asText());
                assertEquals(true, config.path("jsonObjectResponse").asBoolean());
                assertEquals(true, config.path("requireJsonObjectResponse").asBoolean());
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        verify(repository).markSucceeded(eq(1L), anyLong());
        verify(repository, times(5)).insertResource(anyLong(), eq(1L), eq(2L), eq(point),
            anyString(), anyString(), anyString(), anyString(), anyString(), eq("NEEDS_REVIEW"), anyString(),
            eq(new BigDecimal("0.9200")));
    }

    @Test
    void missingRuntimeModelConfigShouldMoveTaskToFailedState() {
        LearningResourceRepository repository = mock(LearningResourceRepository.class);
        AgentRuntimeModelConfigService runtimeModelConfigService = mock(AgentRuntimeModelConfigService.class);
        when(runtimeModelConfigService.resolve()).thenThrow(
            new AgentModelException("CONFIG_MISSING", "管理端默认文本模型配置不完整"));
        LocalDateTime now = LocalDateTime.now();
        LearningResourceRepository.TaskRow task = new LearningResourceRepository.TaskRow(
            11L, 2L, 3L, "Java Web 应用开发", 4L, "Servlet", 5L, "请求响应", 8,
            "{\"dimensions\":[]}", null, "PENDING", 0, null, 1, null, null, null,
            null, null, null, now, now);
        when(repository.getTask(11L)).thenReturn(task);
        when(repository.markRunning(11L, 1, null)).thenReturn(1);

        LearningResourceGenerationService service = new LearningResourceGenerationService(
            repository, mock(CourseKnowledgeRepository.class), mock(RagflowKnowledgeSearchService.class),
            mock(OpenAiCompatibleAgentModelClient.class), runtimeModelConfigService,
            mock(LearningContentSafetyPolicyService.class), new ObjectMapper(), new ImmediateTransactionManager());

        service.execute(11L);

        verify(repository).markFailed(11L, "CONFIG_MISSING", "管理端默认文本模型配置不完整", 0L);
    }

    @Test
    void practiceTimeoutShouldFailTaskAndCloseEveryStartedStep() {
        LearningResourceRepository repository = mock(LearningResourceRepository.class);
        CourseKnowledgeRepository courseKnowledgeRepository = mock(CourseKnowledgeRepository.class);
        RagflowKnowledgeSearchService knowledgeSearchService = mock(RagflowKnowledgeSearchService.class);
        OpenAiCompatibleAgentModelClient modelClient = mock(OpenAiCompatibleAgentModelClient.class);
        LearningContentSafetyPolicyService contentSafetyPolicyService = mock(LearningContentSafetyPolicyService.class);
        AgentRuntimeModelConfigService runtimeModelConfigService = mock(AgentRuntimeModelConfigService.class);
        when(runtimeModelConfigService.resolve()).thenReturn(new AgentRuntimeModelConfigService.RuntimeModelConfig(
            "https://model.example/v1", "test-key", "test-model", 0.2D,
            java.time.Duration.ofSeconds(30), false, "ENVIRONMENT"));
        LearningResourceGenerationService service = new LearningResourceGenerationService(
            repository, courseKnowledgeRepository, knowledgeSearchService, modelClient, runtimeModelConfigService,
            contentSafetyPolicyService, new ObjectMapper(), new ImmediateTransactionManager());

        LocalDateTime now = LocalDateTime.now();
        LearningResourceRepository.TaskRow task = new LearningResourceRepository.TaskRow(
            21L, 2L, 3L, "Java Web 应用开发", 4L, "Servlet 与 Web 基础", 5L,
            "HTTP 请求与响应", 11, "{\"dimensions\":[]}", "补齐请求响应基础",
            "PENDING", 0, null, 1, null, null, null, null, null, null, now, now);
        LearningResourceRepository.CoursePointRow point = new LearningResourceRepository.CoursePointRow(
            3L, "Java Web 应用开发", 4L, "Servlet 与 Web 基础", 5L,
            "HTTP 请求与响应", "理解请求参数、状态码与响应体");
        when(repository.getTask(21L)).thenReturn(task);
        when(repository.markRunning(21L, 1, "test-model")).thenReturn(1);
        when(contentSafetyPolicyService.current()).thenReturn(new LearningContentSafetyPolicyService.Policy(
            true, new BigDecimal("0.9500"), List.of("ignore previous"), 1, now));
        when(repository.getPublishedCoursePoint(3L, 5L)).thenReturn(point);
        when(courseKnowledgeRepository.findSpaceId(3L)).thenReturn(6L);
        when(knowledgeSearchService.searchRows(eq(6L), anyString(), eq(5))).thenReturn(List.of(
            new RagflowSearchHitRow(7L, 8L, "Servlet 课程讲义", 1,
                "HttpServletRequest 提供请求参数，HttpServletResponse 设置状态码和响应体。",
                null, new BigDecimal("0.920000"))));
        when(modelClient.generateStreamingReply(any(AgentModelRequest.class), any()))
            .thenReturn(
                response("{\"learnerSummary\":\"基础薄弱\",\"difficulty\":\"基础\",\"focuses\":[\"请求参数\"]}"),
                response("{\"objective\":\"理解请求响应\",\"prerequisites\":[],\"sequence\":[\"请求\",\"响应\"],\"estimatedMinutes\":30}"),
                response("""
                    {"explanation":{"title":"请求响应讲解","summary":"核心概念","markdown":"# 讲解"},
                     "mindMap":{"title":"请求响应导图","summary":"关系图","mermaid":"mindmap\\n  root((HTTP))"},
                     "reading":{"title":"拓展阅读","summary":"深入理解","markdown":"# 拓展"}}
                    """))
            .thenThrow(new AgentModelException("TIMEOUT", "模型服务调用超时"));

        service.execute(21L);

        ArgumentCaptor<Long> startedStepIds = ArgumentCaptor.forClass(Long.class);
        verify(repository, times(4)).insertRunningStep(startedStepIds.capture(), eq(21L), eq(1),
            anyString(), anyString(), any(Integer.class), anyString(), eq("test-model"));
        ArgumentCaptor<Long> succeededStepIds = ArgumentCaptor.forClass(Long.class);
        verify(repository, times(3)).finishStep(succeededStepIds.capture(), anyString(), anyLong());
        ArgumentCaptor<Long> failedStepId = ArgumentCaptor.forClass(Long.class);
        verify(repository).failStep(failedStepId.capture(), eq("模型服务调用超时"), anyLong());

        assertEquals(startedStepIds.getAllValues().subList(0, 3), succeededStepIds.getAllValues());
        assertEquals(startedStepIds.getAllValues().get(3), failedStepId.getValue());
        verify(repository).markFailed(eq(21L), eq("TIMEOUT"), eq("模型服务调用超时"), anyLong());
        verify(repository, never()).markSucceeded(anyLong(), anyLong());
        verify(repository, never()).insertResource(anyLong(), anyLong(), anyLong(), any(), anyString(),
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    private AgentModelResponse response(String text) {
        return new AgentModelResponse(text);
    }

    private int readMaxTokens(ObjectMapper objectMapper, String config) {
        try {
            JsonNode node = objectMapper.readTree(config);
            return node.path("maxTokens").asInt();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private String readTimeout(ObjectMapper objectMapper, String config) {
        try {
            return objectMapper.readTree(config).path("timeout").asText();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class ImmediateTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
