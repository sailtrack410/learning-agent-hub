package com.workforcehub.learningresource.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.learningresource.dto.ContentSafetyPolicyResponse;
import com.workforcehub.learningresource.dto.UpdateContentSafetyPolicyRequest;
import com.workforcehub.learningresource.service.LearningContentSafetyPolicyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/content-safety")
public class AdminContentSafetyController {
    private final LearningContentSafetyPolicyService policyService;

    public AdminContentSafetyController(LearningContentSafetyPolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public R<ContentSafetyPolicyResponse> getPolicy() {
        return R.ok(policyService.getAdminPolicy());
    }

    @PutMapping
    public R<ContentSafetyPolicyResponse> updatePolicy(
        @Valid @RequestBody UpdateContentSafetyPolicyRequest request) {
        return R.ok(policyService.updateAdminPolicy(request));
    }
}
