package com.workforcehub.course.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.course.dto.CourseOutlineSaveRequest;
import com.workforcehub.course.dto.CourseArticleSaveRequest;
import com.workforcehub.course.dto.CourseInvitationAcceptRequest;
import com.workforcehub.course.dto.CourseInvitationResponse;
import com.workforcehub.course.dto.CourseKnowledgeDocumentRequest;
import com.workforcehub.course.dto.CourseKnowledgeDocumentResponse;
import com.workforcehub.course.dto.CourseLearningOverviewResponse;
import com.workforcehub.course.dto.CourseResponse;
import com.workforcehub.course.dto.CourseSaveRequest;
import com.workforcehub.course.service.CourseService;
import com.workforcehub.course.service.CourseKnowledgeService;
import com.workforcehub.course.service.CourseLearningAnalyticsService;
import com.workforcehub.course.service.CourseCommunicationService;
import com.workforcehub.file.dto.CourseResourcePreviewResponse;
import com.workforcehub.file.service.CourseResourcePreviewService;
import com.workforcehub.im.dto.ImConversationSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/employee/teacher/courses")
public class TeacherCourseController {
    private final CourseService courseService;
    private final CourseResourcePreviewService courseResourcePreviewService;
    private final CourseKnowledgeService courseKnowledgeService;
    private final CourseLearningAnalyticsService courseLearningAnalyticsService;
    private final CourseCommunicationService courseCommunicationService;

    public TeacherCourseController(CourseService courseService,
                                   CourseResourcePreviewService courseResourcePreviewService,
                                   CourseKnowledgeService courseKnowledgeService,
                                   CourseLearningAnalyticsService courseLearningAnalyticsService,
                                   CourseCommunicationService courseCommunicationService) {
        this.courseService = courseService;
        this.courseResourcePreviewService = courseResourcePreviewService;
        this.courseKnowledgeService = courseKnowledgeService;
        this.courseLearningAnalyticsService = courseLearningAnalyticsService;
        this.courseCommunicationService = courseCommunicationService;
    }

    @GetMapping
    public R<List<CourseResponse>> listCourses() {
        return R.ok(courseService.listTeacherCourses());
    }

    @GetMapping("/{courseId}")
    public R<CourseResponse> getCourse(@PathVariable Long courseId) {
        return R.ok(courseService.getTeacherCourse(courseId));
    }

    @GetMapping("/{courseId}/learning-overview")
    public R<CourseLearningOverviewResponse> getLearningOverview(@PathVariable Long courseId) {
        return R.ok(courseLearningAnalyticsService.overview(courseId));
    }

    @PostMapping("/{courseId}/students/{studentUserId}/private-conversation")
    public R<ImConversationSummaryResponse> startStudentPrivateConversation(@PathVariable Long courseId,
                                                                             @PathVariable Long studentUserId) {
        return R.ok(courseCommunicationService.startStudentPrivateConversation(courseId, studentUserId));
    }

    @PostMapping("/{courseId}/classes/{classId}/conversation")
    public R<ImConversationSummaryResponse> openClassGroup(@PathVariable Long courseId,
                                                            @PathVariable Long classId) {
        return R.ok(courseCommunicationService.openClassGroup(courseId, classId));
    }

    @PostMapping
    public R<CourseResponse> createCourse(@Valid @RequestBody CourseSaveRequest request) {
        return R.ok(courseService.createTeacherCourse(request));
    }

    @PutMapping("/{courseId}")
    public R<CourseResponse> updateCourse(@PathVariable Long courseId, @Valid @RequestBody CourseSaveRequest request) {
        return R.ok(courseService.updateTeacherCourse(courseId, request));
    }

    @PutMapping("/{courseId}/outline")
    public R<CourseResponse> saveOutline(@PathVariable Long courseId,
                                         @Valid @RequestBody CourseOutlineSaveRequest request) {
        return R.ok(courseService.saveTeacherOutline(courseId, request));
    }

    @PutMapping("/{courseId}/chapters/{chapterId}/article")
    public R<CourseResponse> saveArticle(@PathVariable Long courseId, @PathVariable Long chapterId,
                                         @Valid @RequestBody CourseArticleSaveRequest request) {
        return R.ok(courseService.saveTeacherArticle(courseId, chapterId, request));
    }

    @PostMapping("/{courseId}/resources/{attachmentRefId}/preview")
    public R<CourseResourcePreviewResponse> prepareResourcePreview(@PathVariable Long courseId,
                                                                   @PathVariable Long attachmentRefId) {
        return R.ok(courseResourcePreviewService.prepare(courseId, attachmentRefId));
    }

    @GetMapping("/{courseId}/resources/{attachmentRefId}/preview-status")
    public R<CourseResourcePreviewResponse> resourcePreviewStatus(@PathVariable Long courseId,
                                                                  @PathVariable Long attachmentRefId) {
        return R.ok(courseResourcePreviewService.status(courseId, attachmentRefId));
    }

    @GetMapping("/{courseId}/knowledge-documents")
    public R<List<CourseKnowledgeDocumentResponse>> listKnowledgeDocuments(@PathVariable Long courseId) {
        return R.ok(courseKnowledgeService.listDocuments(courseId));
    }

    @PostMapping("/{courseId}/knowledge-documents")
    public R<CourseKnowledgeDocumentResponse> addKnowledgeDocument(
        @PathVariable Long courseId, @Valid @RequestBody CourseKnowledgeDocumentRequest request) {
        return R.ok(courseKnowledgeService.addDocument(courseId, request));
    }

    @PostMapping("/{courseId}/knowledge-documents/{documentId}/retry")
    public R<CourseKnowledgeDocumentResponse> retryKnowledgeDocument(
        @PathVariable Long courseId, @PathVariable Long documentId) {
        return R.ok(courseKnowledgeService.retry(courseId, documentId));
    }

    @DeleteMapping("/{courseId}")
    public R<Void> deleteCourse(@PathVariable Long courseId) {
        courseService.deleteTeacherCourse(courseId);
        return R.ok();
    }

    @PostMapping("/{courseId}/invitations")
    public R<CourseInvitationResponse> createInvitation(@PathVariable Long courseId) {
        return R.ok(courseService.createTeacherInvitation(courseId));
    }

    @PostMapping("/invitations/accept")
    public R<CourseResponse> acceptInvitation(@Valid @RequestBody CourseInvitationAcceptRequest request) {
        return R.ok(courseService.acceptTeacherInvitation(request.getToken()));
    }
}
