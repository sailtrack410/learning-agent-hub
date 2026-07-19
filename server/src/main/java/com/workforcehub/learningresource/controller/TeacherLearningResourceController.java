package com.workforcehub.learningresource.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.learningresource.dto.GeneratedLearningResourceResponse;
import com.workforcehub.learningresource.dto.LearningResourceReviewRequest;
import com.workforcehub.learningresource.dto.LearningResourcePushRequest;
import com.workforcehub.learningresource.dto.LearningResourcePushResponse;
import com.workforcehub.learningresource.dto.LearningResourceTaskResponse;
import com.workforcehub.learningresource.dto.LearningResourceUpdateRequest;
import com.workforcehub.learningresource.service.LearningResourceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/employee/teacher/learning-resources")
public class TeacherLearningResourceController {
    private final LearningResourceService learningResourceService;

    public TeacherLearningResourceController(LearningResourceService learningResourceService) {
        this.learningResourceService = learningResourceService;
    }

    @GetMapping("/tasks")
    public R<List<LearningResourceTaskResponse>> listTasks(
        @RequestParam(required = false) Long courseId,
        @RequestParam(required = false) String status) {
        return R.ok(learningResourceService.listTeacherTasks(courseId, status));
    }

    @GetMapping("/tasks/{taskId}")
    public R<LearningResourceTaskResponse> getTask(@PathVariable Long taskId) {
        return R.ok(learningResourceService.getTeacherTask(taskId));
    }

    @GetMapping
    public R<List<GeneratedLearningResourceResponse>> listResources(
        @RequestParam(required = false) Long courseId,
        @RequestParam(required = false) Long knowledgePointId,
        @RequestParam(required = false) String resourceType,
        @RequestParam(required = false) String status) {
        return R.ok(learningResourceService.listTeacherResources(
            courseId, knowledgePointId, resourceType, status));
    }

    @GetMapping("/{resourceId}")
    public R<GeneratedLearningResourceResponse> getResource(@PathVariable Long resourceId) {
        return R.ok(learningResourceService.getTeacherResource(resourceId));
    }

    @PutMapping("/{resourceId}")
    public R<GeneratedLearningResourceResponse> updateResource(
        @PathVariable Long resourceId,
        @Valid @RequestBody LearningResourceUpdateRequest request) {
        return R.ok(learningResourceService.updateTeacherResource(resourceId, request));
    }

    @PostMapping("/{resourceId}/review")
    public R<GeneratedLearningResourceResponse> reviewResource(
        @PathVariable Long resourceId,
        @Valid @RequestBody LearningResourceReviewRequest request) {
        return R.ok(learningResourceService.reviewTeacherResource(resourceId, request));
    }

    @PostMapping("/{resourceId}/publish")
    public R<GeneratedLearningResourceResponse> publishResource(@PathVariable Long resourceId) {
        return R.ok(learningResourceService.publishTeacherResource(resourceId));
    }

    @PostMapping("/{resourceId}/withdraw")
    public R<GeneratedLearningResourceResponse> withdrawResource(@PathVariable Long resourceId) {
        return R.ok(learningResourceService.withdrawTeacherResource(resourceId));
    }

    @PostMapping("/{resourceId}/pushes")
    public R<LearningResourcePushResponse> pushResource(
        @PathVariable Long resourceId,
        @Valid @RequestBody LearningResourcePushRequest request) {
        return R.ok(learningResourceService.pushTeacherResource(resourceId, request));
    }
}
