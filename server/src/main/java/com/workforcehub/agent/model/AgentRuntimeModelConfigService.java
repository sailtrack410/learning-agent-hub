package com.workforcehub.agent.model;

import com.workforcehub.agent.repository.AgentRepository;
import com.workforcehub.agent.repository.LlmModelRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
public class AgentRuntimeModelConfigService {
    private final AgentRepository agentRepository;
    private final AgentModelProperties properties;

    public AgentRuntimeModelConfigService(AgentRepository agentRepository, AgentModelProperties properties) {
        this.agentRepository = agentRepository;
        this.properties = properties;
    }

    public RuntimeModelConfig resolve() {
        LlmModelRow defaultModel = agentRepository.getDefaultActiveTextModel();
        if (defaultModel != null) {
            if (!StringUtils.hasText(defaultModel.endpoint()) || !StringUtils.hasText(defaultModel.apiKey())
                || !StringUtils.hasText(defaultModel.modelName())) {
                throw new AgentModelException("CONFIG_MISSING", "管理端默认文本模型配置不完整");
            }
            return new RuntimeModelConfig(defaultModel.endpoint().trim(), defaultModel.apiKey().trim(),
                defaultModel.modelName().trim(), properties.getTemperature(), properties.getTimeout(),
                Boolean.TRUE.equals(defaultModel.structuredOutputFlag()), "ADMIN");
        }
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getBaseUrl())
            || !StringUtils.hasText(properties.getApiKey()) || !StringUtils.hasText(properties.getModel())) {
            throw new AgentModelException("CONFIG_MISSING", "AI模型配置缺失，请在管理端配置默认文本模型或设置部署环境变量");
        }
        return new RuntimeModelConfig(properties.getBaseUrl().trim(), properties.getApiKey().trim(),
            properties.getModel().trim(), properties.getTemperature(), properties.getTimeout(), false, "ENVIRONMENT");
    }

    public record RuntimeModelConfig(String baseUrl, String apiKey, String model,
                                     Double temperature, Duration timeout,
                                     boolean structuredOutput, String source) {}
}
