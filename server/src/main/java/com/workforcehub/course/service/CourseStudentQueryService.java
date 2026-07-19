package com.workforcehub.course.service;

import com.workforcehub.course.dto.CourseResponse;
import com.workforcehub.course.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class CourseStudentQueryService {
    private final CourseRepository courseRepository;
    private final EducationRoleService educationRoleService;

    public CourseStudentQueryService(CourseRepository courseRepository,
                                     EducationRoleService educationRoleService) {
        this.courseRepository = courseRepository;
        this.educationRoleService = educationRoleService;
    }

    public List<CourseResponse> listPublishedCourses() {
        Long studentUserId = educationRoleService.requireCurrentRole("STUDENT");
        Set<Long> enrolledCourseIds = courseRepository.listEnrolledCourseIds(studentUserId);
        List<CourseResponse> courses = courseRepository.listCourses(true);
        courses.forEach(course -> course.setEnrolled(enrolledCourseIds.contains(course.getCourseId())));
        return courses;
    }

    public CourseResponse getPublishedCourse(Long courseId) {
        Long studentUserId = educationRoleService.requireCurrentRole("STUDENT");
        CourseResponse course = courseRepository.getCourse(courseId, true);
        course.setEnrolled(courseRepository.isStudentEnrolled(courseId, studentUserId));
        return course;
    }

    @Transactional
    public CourseResponse enroll(Long courseId) {
        Long studentUserId = educationRoleService.requireCurrentRole("STUDENT");
        CourseResponse course = courseRepository.getCourse(courseId, true);
        courseRepository.enrollStudent(courseId, studentUserId);
        course.setEnrolled(true);
        return course;
    }
}
