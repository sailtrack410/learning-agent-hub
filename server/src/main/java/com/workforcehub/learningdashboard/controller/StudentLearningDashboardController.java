package com.workforcehub.learningdashboard.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.learningdashboard.dto.StudentLearningDashboardResponse;
import com.workforcehub.learningdashboard.service.StudentLearningDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employee/student/learning-dashboard")
public class StudentLearningDashboardController {
    private final StudentLearningDashboardService service;

    public StudentLearningDashboardController(StudentLearningDashboardService service) {
        this.service = service;
    }

    @GetMapping
    public R<StudentLearningDashboardResponse> current() {
        return R.ok(service.current());
    }
}
