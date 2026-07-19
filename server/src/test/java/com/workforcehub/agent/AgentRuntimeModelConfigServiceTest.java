package com.workforcehub.agent;

import com.workforcehub.agent.model.AgentModelException;
import com.workforcehub.agent.model.AgentModelProperties;
import com.workforcehub.agent.model.AgentRuntimeModelConfigService;
import com.workforcehub.agent.repository.AgentRepository;
import com.workforcehub.agent.repository.LlmModelRow;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRuntimeModelConfigServiceTest {
    @Test
    void adminDefaultTextModelShouldOverrideEnvironmentConfiguration() {
        AgentRepository repository = mock(AgentRepository.class);
        AgentModelProperties properties = environmentProperties();
        when(repository.getDefaultActiveTextModel()).thenReturn(model(
            "admin-model", "https://admin.example/v1", "admin-key"));

        AgentRuntimeModelConfigService.RuntimeModelConfig config =
            new AgentRuntimeModelConfigService(repository, properties).resolve();

        assertEquals("ADMIN", config.source());
        assertEquals("admin-model", config.model());
        assertEquals("https://admin.example/v1", config.baseUrl());
        assertEquals("admin-key", config.apiKey());
    }

    @Test
    void environmentConfigurationShouldRemainTheFallback() {
        AgentRepository repository = mock(AgentRepository.class);
        AgentModelProperties properties = environmentProperties();

        AgentRuntimeModelConfigService.RuntimeModelConfig config =
            new AgentRuntimeModelConfigService(repository, properties).resolve();

        assertEquals("ENVIRONMENT", config.source());
        assertEquals("env-model", config.model());
    }

    @Test
    void incompleteAdminDefaultShouldFailInsteadOfSilentlyUsingAnotherModel() {
        AgentRepository repository = mock(AgentRepository.class);
        when(repository.getDefaultActiveTextModel()).thenReturn(model("admin-model", null, null));

        AgentModelException exception = assertThrows(AgentModelException.class,
            () -> new AgentRuntimeModelConfigService(repository, environmentProperties()).resolve());

        assertEquals("CONFIG_MISSING", exception.failureType());
        assertEquals("管理端默认文本模型配置不完整", exception.getMessage());
    }

    private AgentModelProperties environmentProperties() {
        AgentModelProperties properties = new AgentModelProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://env.example/v1");
        properties.setApiKey("env-key");
        properties.setModel("env-model");
        properties.setTimeout(Duration.ofSeconds(45));
        properties.setTemperature(0.3D);
        return properties;
    }

    private LlmModelRow model(String modelName, String endpoint, String apiKey) {
        LocalDateTime now = LocalDateTime.now();
        return new LlmModelRow(1L, "openai", "TEXT", modelName, modelName, endpoint, apiKey,
            true, false, false, false, true, false, "ACTIVE", null, null, null, now, now);
    }
}
