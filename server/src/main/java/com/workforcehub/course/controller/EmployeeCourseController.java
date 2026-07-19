package com.workforcehub.course.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.course.dto.CourseResponse;
import com.workforcehub.course.service.CourseService;
import com.workforcehub.file.controller.FileDownloadResponseFactory;
import com.workforcehub.file.dto.DownloadFileResult;
import com.workforcehub.file.service.FileDownloadService;
import com.workforcehub.file.service.CourseResourcePreviewService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/employee/courses")
public class EmployeeCourseController {
    private final CourseService courseService;
    private final FileDownloadService fileDownloadService;
    private final FileDownloadResponseFactory downloadResponseFactory;
    private final CourseResourcePreviewService courseResourcePreviewService;

    public EmployeeCourseController(CourseService courseService,
                                    FileDownloadService fileDownloadService,
                                    FileDownloadResponseFactory downloadResponseFactory,
                                    CourseResourcePreviewService courseResourcePreviewService) {
        this.courseService = courseService;
        this.fileDownloadService = fileDownloadService;
        this.downloadResponseFactory = downloadResponseFactory;
        this.courseResourcePreviewService = courseResourcePreviewService;
    }

    @GetMapping
    public R<List<CourseResponse>> listCourses() {
        return R.ok(courseService.listPublishedCourses());
    }

    @GetMapping("/{courseId}")
    public R<CourseResponse> getCourse(@PathVariable Long courseId) {
        return R.ok(courseService.getPublishedCourse(courseId));
    }

    @PostMapping("/{courseId}/enrollment")
    public R<CourseResponse> enrollCourse(@PathVariable Long courseId) {
        return R.ok(courseService.enrollPublishedCourse(courseId));
    }

    @GetMapping("/resources/{attachmentRefId}")
    public ResponseEntity<Resource> readResource(@PathVariable Long attachmentRefId) {
        DownloadFileResult result = fileDownloadService.readCourseResource(attachmentRefId);
        return downloadResponseFactory.inline(result);
    }

    @GetMapping("/resources/{attachmentRefId}/preview")
    public ResponseEntity<Resource> readResourcePreview(@PathVariable Long attachmentRefId) {
        DownloadFileResult result = courseResourcePreviewService.readPreview(attachmentRefId);
        return downloadResponseFactory.inline(result);
    }
}
