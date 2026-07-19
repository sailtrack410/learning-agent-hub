package com.workforcehub.assessment.controller;

import com.workforcehub.assessment.dto.AssessmentAttemptResponse;
import com.workforcehub.assessment.dto.AssessmentResponse;
import com.workforcehub.assessment.dto.AssessmentSubmitRequest;
import com.workforcehub.assessment.service.AssessmentService;
import com.workforcehub.common.response.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/employee/courses/{courseId}/assessments")
public class StudentAssessmentController {
    private final AssessmentService assessmentService;

    public StudentAssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping
    public R<List<AssessmentResponse>> list(@PathVariable Long courseId) {
        return R.ok(assessmentService.list(courseId));
    }

    @GetMapping("/{assessmentId}")
    public R<AssessmentResponse> detail(@PathVariable Long courseId, @PathVariable Long assessmentId) {
        return R.ok(assessmentService.detail(courseId, assessmentId));
    }

    @PostMapping("/{assessmentId}/attempts")
    public R<AssessmentAttemptResponse> submit(@PathVariable Long courseId,
                                               @PathVariable Long assessmentId,
                                               @Valid @RequestBody AssessmentSubmitRequest request) {
        return R.ok(assessmentService.submit(courseId, assessmentId, request));
    }
}
