package com.workforcehub.learningresource.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.audit.AuditLogWriter;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.learningresource.dto.ContentSafetyPolicyResponse;
import com.workforcehub.learningresource.dto.UpdateContentSafetyPolicyRequest;
import com.workforcehub.learningresource.repository.LearningContentSafetyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class LearningContentSafetyPolicyService {
    private final LearningContentSafetyRepository repository;
    private final ObjectMapper objectMapper;
    private final AuditLogWriter auditLogWriter;

    public LearningContentSafetyPolicyService(LearningContentSafetyRepository repository,
                                              ObjectMapper objectMapper,
                                              AuditLogWriter auditLogWriter) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.auditLogWriter = auditLogWriter;
    }

    public Policy current() {
        LearningContentSafetyRepository.PolicyRow row = repository.getPolicy();
        try {
            List<String> markers = objectMapper.readValue(row.markersJson(), new TypeReference<>() {});
            return new Policy(row.detectionEnabled(), row.reviewThreshold(), List.copyOf(markers),
                row.versionNo(), row.updatedAt());
        } catch (Exception exception) {
            throw new BizException(500, "内容安全策略读取失败");
        }
    }

    public ContentSafetyPolicyResponse getAdminPolicy() {
        return response(current());
    }

    @Transactional(rollbackFor = Exception.class)
    public ContentSafetyPolicyResponse updateAdminPolicy(UpdateContentSafetyPolicyRequest request) {
        List<String> markers = normalizeMarkers(request.getBlockedMarkers());
        if (request.getPromptInjectionDetectionEnabled() && markers.isEmpty()) {
            throw new BizException(400, "启用提示词注入拦截时至少需要一条危险指令标记");
        }
        BigDecimal threshold = request.getReviewConfidenceThreshold().setScale(4, RoundingMode.HALF_UP);
        Long adminId = CurrentUserHolder.requireUserId();
        try {
            String markersJson = objectMapper.writeValueAsString(markers);
            if (repository.updatePolicy(request.getPromptInjectionDetectionEnabled(), threshold, markersJson,
                    request.getExpectedVersion(), adminId) != 1) {
                throw new BizException(409, "内容安全策略已被其他管理员更新，请刷新后重试");
            }
            auditLogWriter.write("ADMIN", adminId, "CONTENT_SAFETY_POLICY_UPDATED", "CONTENT_SAFETY_POLICY", 1L,
                "SUCCESS", objectMapper.writeValueAsString(Map.of(
                    "promptInjectionDetectionEnabled", request.getPromptInjectionDetectionEnabled(),
                    "reviewConfidenceThreshold", threshold,
                    "blockedMarkerCount", markers.size()
                )));
            return response(current());
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(500, "内容安全策略保存失败");
        }
    }

    private List<String> normalizeMarkers(List<String> requestedMarkers) {
        LinkedHashSet<String> markers = new LinkedHashSet<>();
        for (String marker : requestedMarkers) {
            String normalized = marker.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) markers.add(normalized);
        }
        return List.copyOf(markers);
    }

    private ContentSafetyPolicyResponse response(Policy policy) {
        return new ContentSafetyPolicyResponse(policy.promptInjectionDetectionEnabled(),
            policy.reviewConfidenceThreshold(), policy.blockedMarkers(), policy.versionNo(), policy.updatedAt());
    }

    public record Policy(boolean promptInjectionDetectionEnabled,
                         BigDecimal reviewConfidenceThreshold,
                         List<String> blockedMarkers,
                         int versionNo,
                         java.time.LocalDateTime updatedAt) {}
}
