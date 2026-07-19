package com.workforcehub.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.course.dto.CourseOutlineSaveRequest;
import com.workforcehub.course.dto.CourseArticleSaveRequest;
import com.workforcehub.course.dto.CourseResponse;
import com.workforcehub.course.dto.CourseSaveRequest;
import com.workforcehub.course.repository.CourseRepository;
import com.workforcehub.course.service.CourseService;
import com.workforcehub.course.service.CourseResourceAccessService;
import com.workforcehub.course.service.CourseKnowledgeService;
import com.workforcehub.course.service.EducationRoleService;
import com.workforcehub.course.service.CourseStudentQueryService;
import com.workforcehub.learningpath.service.LearningPathService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.contains;

class CourseServiceTest {
    private final CourseRepository repository = mock(CourseRepository.class);
    private final EducationRoleService educationRoleService = mock(EducationRoleService.class);
    private final CourseResourceAccessService courseResourceAccessService = mock(CourseResourceAccessService.class);
    private final CourseKnowledgeService courseKnowledgeService = mock(CourseKnowledgeService.class);
    private final CourseStudentQueryService courseStudentQueryService = mock(CourseStudentQueryService.class);
    private final LearningPathService learningPathService = mock(LearningPathService.class);
    private final CourseService service = new CourseService(repository, educationRoleService, courseResourceAccessService,
        new ObjectMapper(), courseKnowledgeService, courseStudentQueryService, learningPathService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNormalizeCourseCodeWhenCreatingCourse() {
        authenticate();
        CourseSaveRequest request = new CourseSaveRequest();
        request.setCourseCode("java_web");
        request.setCourseName("Java Web 应用开发");
        request.setStatus("published");
        when(repository.getCourse(anyLong(), eq(false))).thenReturn(new CourseResponse());

        service.createCourse(request);

        verify(repository).existsCourseCode("JAVA_WEB", null);
        verify(repository).insertCourse(anyLong(), eq("JAVA_WEB"), eq("Java Web 应用开发"),
            eq(null), eq(null), eq(null), eq(null), eq(null), eq(0), eq("PUBLISHED"), eq(8001L));
    }

    @Test
    void shouldRejectCyclicKnowledgeDependencies() {
        CourseOutlineSaveRequest request = outlineRequest();
        CourseOutlineSaveRequest.Dependency reverse = new CourseOutlineSaveRequest.Dependency();
        reverse.setPrerequisitePointCode("SPRING_BOOT");
        reverse.setDependentPointCode("SERVLET");
        request.getDependencies().add(reverse);
        when(repository.getCourse(8100001L, false)).thenReturn(new CourseResponse());

        assertThrows(BizException.class, () -> service.saveOutline(8100001L, request));
    }

    @Test
    void shouldExposeOnlyPublishedCoursesToStudents() {
        authenticate();
        service.listPublishedCourses();

        verify(courseStudentQueryService).listPublishedCourses();
    }

    @Test
    void shouldRejectStudentFromTeacherCourseManagement() {
        authenticate();
        org.mockito.Mockito.doThrow(new BizException(403, "当前账号无权访问该教育功能"))
            .when(educationRoleService).requireCurrentRole("TEACHER");

        BizException exception = assertThrows(BizException.class, service::listTeacherCourses);

        assertEquals(403, exception.getHttpStatus().value());
    }

    @Test
    void shouldRejectTeacherFromStudentCourseArea() {
        authenticate();
        org.mockito.Mockito.doThrow(new BizException(403, "当前账号无权访问该教育功能"))
            .when(courseStudentQueryService).listPublishedCourses();

        BizException exception = assertThrows(BizException.class, service::listPublishedCourses);

        assertEquals(403, exception.getHttpStatus().value());
    }

    @Test
    void shouldListOnlyCoursesManagedByCurrentTeacher() {
        authenticate();
        when(educationRoleService.requireCurrentRole("TEACHER")).thenReturn(8001L);

        service.listTeacherCourses();

        verify(repository).listTeacherCourses(8001L);
        verify(repository, never()).listCourses(false);
    }

    @Test
    void shouldRejectTeacherOutsideCourseFromUpdatingCourse() {
        authenticate();
        when(educationRoleService.requireCurrentRole("TEACHER")).thenReturn(8001L);
        BizException forbidden = new BizException(404, "课程不存在或无权管理");
        org.mockito.Mockito.doThrow(forbidden).when(courseResourceAccessService)
            .requireTeacherCourse(8001L, 8100001L);

        BizException exception = assertThrows(BizException.class,
            () -> service.updateTeacherCourse(8100001L, courseRequest()));

        assertEquals(404, exception.getHttpStatus().value());
        verify(repository, never()).updateCourse(eq(8100001L), anyString(), anyString(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
            anyString(), eq(8001L));
    }

    @Test
    void shouldRejectCollaboratorFromDeletingCourse() {
        authenticate();
        when(educationRoleService.requireCurrentRole("TEACHER")).thenReturn(8001L);
        when(repository.isCourseOwner(8100001L, 8001L)).thenReturn(false);

        BizException exception = assertThrows(BizException.class,
            () -> service.deleteTeacherCourse(8100001L));

        assertEquals(403, exception.getHttpStatus().value());
        verify(repository, never()).deleteCourse(8100001L);
    }

    @Test
    void shouldDeleteCourseKnowledgeBeforeDeletingCourse() {
        authenticate();
        when(educationRoleService.requireCurrentRole("TEACHER")).thenReturn(8001L);
        when(repository.isCourseOwner(8100001L, 8001L)).thenReturn(true);
        when(repository.getCourse(8100001L, false)).thenReturn(new CourseResponse());

        service.deleteTeacherCourse(8100001L);

        org.mockito.InOrder deletion = org.mockito.Mockito.inOrder(learningPathService, courseKnowledgeService, repository);
        deletion.verify(learningPathService).deleteCoursePaths(8100001L);
        deletion.verify(courseKnowledgeService).deleteCourseKnowledge(8100001L, 8001L);
        deletion.verify(repository).deleteCourse(8100001L);
    }

    @Test
    void shouldAcceptValidInvitationAsCollaborator() {
        authenticate();
        when(educationRoleService.requireCurrentRole("TEACHER")).thenReturn(8001L);
        when(repository.lockInvitation(anyString())).thenReturn(new CourseRepository.InvitationRow(
            8300001L, 8100001L, "ACTIVE", 8002L, LocalDateTime.now().plusDays(1), null));
        when(repository.getTeacherCourse(8100001L, 8001L)).thenReturn(new CourseResponse());

        service.acceptTeacherInvitation("invite-token");

        verify(repository).insertCourseTeacher(anyLong(), eq(8100001L), eq(8001L), eq("COLLABORATOR"), eq(8002L));
        verify(repository).acceptInvitation(eq(8300001L), eq(8001L), org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    @Test
    void shouldRejectExpiredInvitation() {
        authenticate();
        when(educationRoleService.requireCurrentRole("TEACHER")).thenReturn(8001L);
        when(repository.lockInvitation(anyString())).thenReturn(new CourseRepository.InvitationRow(
            8300001L, 8100001L, "ACTIVE", 8002L, LocalDateTime.now().minusMinutes(1), null));

        BizException exception = assertThrows(BizException.class,
            () -> service.acceptTeacherInvitation("expired-token"));

        assertEquals(410, exception.getHttpStatus().value());
        verify(repository, never()).insertCourseTeacher(anyLong(), anyLong(), anyLong(), anyString(), anyLong());
    }

    @Test
    void shouldSaveArticleAndValidateEmbeddedCourseResources() {
        authenticate();
        when(educationRoleService.requireCurrentRole("TEACHER")).thenReturn(8001L);
        when(repository.getTeacherCourse(8100001L, 8001L)).thenReturn(new CourseResponse());
        CourseArticleSaveRequest request = new CourseArticleSaveRequest();
        request.setTitle("Servlet 生命周期");
        request.setSummary("理解初始化、服务与销毁阶段");
        request.setStatus("PUBLISHED");
        request.setContentJson("""
            {"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"正文"}]},
              {"type":"video","attrs":{"attachmentRefId":"9001"}}
            ]}
            """);

        service.saveTeacherArticle(8100001L, 8110001L, request);

        verify(courseResourceAccessService).requireCourseResource(8001L, 8100001L, 9001L);
        verify(repository).updateArticle(eq(8100001L), eq(8110001L), eq("Servlet 生命周期"),
            eq("理解初始化、服务与销毁阶段"), contains("attachmentRefId"), eq("PUBLISHED"));
    }

    @Test
    void shouldRejectPublishingArticleWithOnlyEmptyParagraph() {
        authenticate();
        when(educationRoleService.requireCurrentRole("TEACHER")).thenReturn(8001L);
        CourseArticleSaveRequest request = new CourseArticleSaveRequest();
        request.setTitle("空文章");
        request.setStatus("PUBLISHED");
        request.setContentJson("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}");

        BizException exception = assertThrows(BizException.class,
            () -> service.saveTeacherArticle(8100001L, 8110001L, request));

        assertEquals(400, exception.getHttpStatus().value());
        verify(repository, never()).updateArticle(eq(8100001L), eq(8110001L), anyString(),
            org.mockito.ArgumentMatchers.any(), anyString(), anyString());
    }

    @Test
    private CourseOutlineSaveRequest outlineRequest() {
        CourseOutlineSaveRequest.KnowledgePoint servlet = new CourseOutlineSaveRequest.KnowledgePoint();
        servlet.setPointCode("SERVLET");
        servlet.setPointName("Servlet 生命周期");
        CourseOutlineSaveRequest.KnowledgePoint springBoot = new CourseOutlineSaveRequest.KnowledgePoint();
        springBoot.setPointCode("SPRING_BOOT");
        springBoot.setPointName("Spring Boot 自动配置");
        CourseOutlineSaveRequest.Chapter chapter = new CourseOutlineSaveRequest.Chapter();
        chapter.setChapterName("Web 开发基础");
        chapter.setKnowledgePoints(List.of(servlet, springBoot));
        CourseOutlineSaveRequest.Dependency dependency = new CourseOutlineSaveRequest.Dependency();
        dependency.setPrerequisitePointCode("SERVLET");
        dependency.setDependentPointCode("SPRING_BOOT");
        CourseOutlineSaveRequest request = new CourseOutlineSaveRequest();
        request.setChapters(List.of(chapter));
        request.setDependencies(new java.util.ArrayList<>(List.of(dependency)));
        return request;
    }

    private CourseSaveRequest courseRequest() {
        CourseSaveRequest request = new CourseSaveRequest();
        request.setCourseCode("JAVA_WEB");
        request.setCourseName("Java Web 应用开发");
        request.setStatus("PUBLISHED");
        return request;
    }

    private void authenticate() {
        WfhUserDetails user = mock(WfhUserDetails.class);
        when(user.getUserId()).thenReturn(8001L);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, List.of())
        );
    }
}
