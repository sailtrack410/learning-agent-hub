package com.workforcehub.profile.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.profile.dto.StudentProfileAnswerRequest;
import com.workforcehub.profile.dto.StudentProfileCorrectionRequest;
import com.workforcehub.profile.dto.StudentProfileResponse;
import com.workforcehub.profile.service.StudentProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employee/student/profile")
public class StudentProfileController {
    private final StudentProfileService studentProfileService;

    public StudentProfileController(StudentProfileService studentProfileService) {
        this.studentProfileService = studentProfileService;
    }

    @GetMapping
    public R<StudentProfileResponse> current() {
        return R.ok(studentProfileService.current());
    }

    @PostMapping("/messages")
    public R<StudentProfileResponse> answer(@Valid @RequestBody StudentProfileAnswerRequest request) {
        return R.ok(studentProfileService.answer(request.getMessage()));
    }

    @PutMapping("/dimensions/{dimensionCode}")
    public R<StudentProfileResponse> correct(@PathVariable String dimensionCode,
                                             @Valid @RequestBody StudentProfileCorrectionRequest request) {
        return R.ok(studentProfileService.correct(dimensionCode, request.getConclusion()));
    }
}
