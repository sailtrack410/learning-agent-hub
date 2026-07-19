package com.workforcehub.learningpath.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.learningpath.dto.LearningPathResponse;
import com.workforcehub.learningpath.service.LearningPathService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employee/courses/{courseId}/learning-path")
public class StudentLearningPathController {
    private final LearningPathService learningPathService;

    public StudentLearningPathController(LearningPathService learningPathService) {
        this.learningPathService = learningPathService;
    }

    @GetMapping
    public R<LearningPathResponse> current(@PathVariable Long courseId) {
        return R.ok(learningPathService.current(courseId));
    }

    @PostMapping("/nodes/{nodeId}/complete")
    public R<LearningPathResponse> completeNode(@PathVariable Long courseId, @PathVariable Long nodeId) {
        return R.ok(learningPathService.completeNode(courseId, nodeId));
    }
}
