package com.workforcehub.learningpath;

import com.workforcehub.assessment.service.StudentMasteryQueryService;
import com.workforcehub.course.dto.CourseResponse;
import com.workforcehub.course.service.CourseStudentQueryService;
import com.workforcehub.course.service.EducationRoleService;
import com.workforcehub.learningpath.repository.LearningPathRepository;
import com.workforcehub.learningpath.service.LearningPathService;
import com.workforcehub.profile.service.StudentProfileService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningPathServiceTest {

    @Test
    void completingCurrentNodeShouldPersistProgressAndActivateOnlyTheNextNode() {
        Fixture fixture = new Fixture();
        CourseResponse course = fixture.course();
        LearningPathRepository.PathRow path = new LearningPathRepository.PathRow(
            21L, 2003L, 8100001L, 2, null, 1, "初始路径", LocalDateTime.now());
        when(fixture.roles.requireCurrentRole("STUDENT")).thenReturn(2003L);
        when(fixture.courses.getPublishedCourse(8100001L)).thenReturn(course);
        when(fixture.repository.lockActiveNode(2003L, 8100001L, 11L))
            .thenReturn(new LearningPathRepository.NodeAccessRow(11L, 21L, 101L, "IN_PROGRESS"));
        when(fixture.repository.activateNextNode(21L))
            .thenReturn(new LearningPathRepository.NodeAccessRow(12L, 21L, 102L, "PENDING"));
        when(fixture.repository.getActivePath(2003L, 8100001L)).thenReturn(path);
        when(fixture.repository.listNodes(21L)).thenReturn(List.of());
        when(fixture.repository.listRecommendations(21L)).thenReturn(List.of());

        fixture.service.completeNode(8100001L, 11L);

        verify(fixture.repository).completeNode(21L, 11L);
        verify(fixture.repository).upsertProgress(anyLong(), eq(2003L), eq(8100001L), eq(101L),
            eq(11L), eq("COMPLETED"), eq(100), eq("MANUAL"));
        verify(fixture.repository).upsertProgress(anyLong(), eq(2003L), eq(8100001L), eq(102L),
            eq(12L), eq("IN_PROGRESS"), eq(0), isNull());
    }

    @Test
    void assessmentRegenerationShouldSupersedeOldPathAndPrioritizeWeakPoint() {
        Fixture fixture = new Fixture();
        CourseResponse course = fixture.course();
        when(fixture.roles.requireCurrentRole("STUDENT")).thenReturn(2003L);
        when(fixture.courses.getPublishedCourse(8100001L)).thenReturn(course);
        when(fixture.mastery.courseMastery(2003L, 8100001L)).thenReturn(Map.of(
            101L, new BigDecimal("100"),
            102L, new BigDecimal("40")
        ));
        when(fixture.repository.listProgress(2003L, 8100001L)).thenReturn(Map.of());
        when(fixture.repository.nextVersionNo(2003L, 8100001L)).thenReturn(2);
        when(fixture.repository.getActivePath(2003L, 8100001L)).thenReturn(
            new LearningPathRepository.PathRow(22L, 2003L, 8100001L, 5, 6001L, 2,
                "测验后重排", LocalDateTime.now()));

        fixture.service.regenerateAfterAssessment(2003L, 8100001L, 6001L, 5);

        verify(fixture.repository).supersedeActive(2003L, 8100001L);
        verify(fixture.repository).insertPath(anyLong(), eq(2003L), eq(8100001L), eq(5),
            eq(6001L), eq(2), any());
        verify(fixture.repository).insertNode(anyLong(), anyLong(), eq(101L), eq(201L), eq(1),
            any(), eq("COURSE_ARTICLE"), eq(201L), eq("Servlet 与 Web 基础"), eq(15), any(),
            eq("COMPLETED"), eq(new BigDecimal("100")), any());
        verify(fixture.repository).insertNode(anyLong(), anyLong(), eq(102L), eq(201L), eq(2),
            any(), eq("COURSE_ARTICLE"), eq(201L), eq("Servlet 与 Web 基础"), eq(40), any(),
            eq("IN_PROGRESS"), eq(new BigDecimal("40")), any());
    }

    private static final class Fixture {
        private final EducationRoleService roles = mock(EducationRoleService.class);
        private final CourseStudentQueryService courses = mock(CourseStudentQueryService.class);
        private final StudentProfileService profiles = mock(StudentProfileService.class);
        private final StudentMasteryQueryService mastery = mock(StudentMasteryQueryService.class);
        private final LearningPathRepository repository = mock(LearningPathRepository.class);
        private final LearningPathService service = new LearningPathService(roles, courses, profiles, mastery, repository);

        private CourseResponse course() {
            CourseResponse course = new CourseResponse();
            course.setCourseId(8100001L);
            course.setCourseName("Java Web 应用开发");
            CourseResponse.Chapter chapter = new CourseResponse.Chapter();
            chapter.setChapterId(201L);
            chapter.setChapterNo(1);
            chapter.setChapterName("Servlet 与 Web 基础");
            chapter.setKnowledgePoints(List.of(point(101L, "HTTP", "HTTP 请求响应", 1),
                point(102L, "SERVLET", "Servlet 生命周期", 2)));
            CourseResponse.Dependency dependency = new CourseResponse.Dependency();
            dependency.setPrerequisitePointCode("HTTP");
            dependency.setDependentPointCode("SERVLET");
            course.setChapters(List.of(chapter));
            course.setDependencies(List.of(dependency));
            return course;
        }

        private CourseResponse.KnowledgePoint point(Long id, String code, String name, int order) {
            CourseResponse.KnowledgePoint point = new CourseResponse.KnowledgePoint();
            point.setKnowledgePointId(id);
            point.setPointCode(code);
            point.setPointName(name);
            point.setSummary("理解" + name);
            point.setSortOrder(order);
            return point;
        }
    }
}
