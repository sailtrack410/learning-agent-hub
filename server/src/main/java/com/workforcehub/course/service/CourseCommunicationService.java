package com.workforcehub.course.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.course.repository.CourseLearningAnalyticsRepository;
import com.workforcehub.im.dto.ImConversationSummaryResponse;
import com.workforcehub.im.service.ImGroupService;
import com.workforcehub.im.service.ImService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;

@Service
public class CourseCommunicationService {
    private final EducationRoleService educationRoleService;
    private final CourseLearningAnalyticsRepository repository;
    private final ImService imService;
    private final ImGroupService imGroupService;

    public CourseCommunicationService(EducationRoleService educationRoleService,
                                      CourseLearningAnalyticsRepository repository,
                                      ImService imService,
                                      ImGroupService imGroupService) {
        this.educationRoleService = educationRoleService;
        this.repository = repository;
        this.imService = imService;
        this.imGroupService = imGroupService;
    }

    public ImConversationSummaryResponse startStudentPrivateConversation(Long courseId, Long studentUserId) {
        Long teacherUserId = educationRoleService.requireCurrentRole("TEACHER");
        requireTeacherCourse(teacherUserId, courseId);
        if (!repository.existsEnrolledStudent(courseId, studentUserId)) {
            throw new BizException(404, "学生未加入该课程");
        }
        return imService.startCurrentEmployeeCoursePrivateConversation(studentUserId);
    }

    public ImConversationSummaryResponse openClassGroup(Long courseId, Long classId) {
        Long teacherUserId = educationRoleService.requireCurrentRole("TEACHER");
        CourseLearningAnalyticsRepository.CourseRow course = requireTeacherCourse(teacherUserId, courseId);
        String className = repository.getActiveClassName(classId);
        if (className == null) {
            throw new BizException(404, "班级不存在");
        }
        List<Long> studentUserIds = repository.listEnrolledStudentIdsByClass(courseId, classId);
        if (studentUserIds.isEmpty()) {
            throw new BizException(400, "该班级暂无已加入课程的学生");
        }
        LinkedHashSet<Long> memberUserIds = new LinkedHashSet<>(repository.listCourseTeacherIds(courseId));
        memberUserIds.addAll(studentUserIds);
        String title = course.courseName() + " · " + className;
        if (title.length() > 64) title = title.substring(0, 64);
        return imGroupService.createOrSyncCurrentEmployeeCourseGroup(
            "COURSE_CLASS:" + courseId + ":" + classId,
            title,
            memberUserIds.stream().toList()
        );
    }

    private CourseLearningAnalyticsRepository.CourseRow requireTeacherCourse(Long teacherUserId, Long courseId) {
        CourseLearningAnalyticsRepository.CourseRow course = repository.getTeacherCourse(teacherUserId, courseId);
        if (course == null) throw new BizException(404, "课程不存在或无权管理");
        return course;
    }
}
