package com.workforcehub.learningresource.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.learningresource.dto.CreateLearningResourceTaskRequest;
import com.workforcehub.learningresource.dto.GeneratedLearningResourceResponse;
import com.workforcehub.learningresource.dto.LearningResourceFeedbackRequest;
import com.workforcehub.learningresource.dto.LearningResourceFeedbackResponse;
import com.workforcehub.learningresource.dto.LearningResourceTaskResponse;
import com.workforcehub.learningresource.service.LearningResourceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/employee/learning-resources")
public class StudentLearningResourceController {
    private final LearningResourceService learningResourceService;

    public StudentLearningResourceController(LearningResourceService learningResourceService) {
        this.learningResourceService = learningResourceService;
    }

    @PostMapping("/tasks")
    public R<LearningResourceTaskResponse> createTask(
        @Valid @RequestBody CreateLearningResourceTaskRequest request) {
        return R.ok(learningResourceService.createTask(request));
    }

    @GetMapping("/tasks")
    public R<List<LearningResourceTaskResponse>> listTasks(
        @RequestParam(required = false) Long courseId) {
        return R.ok(learningResourceService.listStudentTasks(courseId));
    }

    @GetMapping("/tasks/{taskId}")
    public R<LearningResourceTaskResponse> getTask(@PathVariable Long taskId) {
        return R.ok(learningResourceService.getStudentTask(taskId));
    }

    @GetMapping(value = "/tasks/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTask(@PathVariable Long taskId) {
        return learningResourceService.streamStudentTask(taskId);
    }

    @PostMapping("/tasks/{taskId}/retry")
    public R<LearningResourceTaskResponse> retryTask(@PathVariable Long taskId) {
        return R.ok(learningResourceService.retryStudentTask(taskId));
    }

    @GetMapping
    public R<List<GeneratedLearningResourceResponse>> listResources(
        @RequestParam(required = false) Long courseId,
        @RequestParam(required = false) String resourceType) {
        return R.ok(learningResourceService.listStudentResources(courseId, resourceType));
    }

    @GetMapping("/{resourceId}")
    public R<GeneratedLearningResourceResponse> getResource(@PathVariable Long resourceId) {
        return R.ok(learningResourceService.getStudentResource(resourceId));
    }

    @PostMapping("/{resourceId}/view")
    public R<Void> recordView(@PathVariable Long resourceId) {
        learningResourceService.recordStudentResourceView(resourceId);
        return R.ok();
    }

    @GetMapping("/{resourceId}/feedback")
    public R<LearningResourceFeedbackResponse> getFeedback(@PathVariable Long resourceId) {
        return R.ok(learningResourceService.getStudentResourceFeedback(resourceId));
    }

    @PostMapping("/{resourceId}/feedback")
    public R<LearningResourceFeedbackResponse> saveFeedback(
        @PathVariable Long resourceId,
        @Valid @RequestBody LearningResourceFeedbackRequest request) {
        return R.ok(learningResourceService.saveStudentResourceFeedback(resourceId, request));
    }
}
