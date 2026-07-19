package com.workforcehub.course;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.course.service.CourseResourceAccessService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class CourseResourceAccessServiceTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final CourseResourceAccessService service = new CourseResourceAccessService(jdbcTemplate);

    @Test
    void shouldAllowTeacherToUploadToExistingCourse() {
        mockRole("TEACHER");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(8001L), eq(8100001L))).thenReturn(1);

        service.requireTeacherCourse(8001L, 8100001L);
    }

    @Test
    void shouldRejectStudentFromCourseUpload() {
        mockRole("STUDENT");

        BizException exception = assertThrows(BizException.class,
            () -> service.requireTeacherCourse(8001L, 8100001L));

        assertEquals(403, exception.getHttpStatus().value());
    }

    @Test
    void shouldRejectUnreadableCourseAttachment() {
        mockRole("STUDENT");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(9001L), eq("STUDENT"), eq(8001L), eq("STUDENT")))
            .thenReturn(0);

        BizException exception = assertThrows(BizException.class,
            () -> service.requireReadableResource(8001L, 9001L));

        assertEquals(403, exception.getHttpStatus().value());
    }

    @Test
    void shouldCheckStructuredArticleResourceReferenceForStudent() {
        mockRole("STUDENT");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(9001L), eq("STUDENT"), eq(8001L), eq("STUDENT")))
            .thenReturn(1);

        service.requireReadableResource(8001L, 9001L);

        verify(jdbcTemplate).queryForObject(
            org.mockito.ArgumentMatchers.contains("attachmentRefId"), eq(Integer.class),
            eq(9001L), eq("STUDENT"), eq(8001L), eq("STUDENT"));
    }

    @Test
    void shouldRejectTeacherOutsideCourseFromUpload() {
        mockRole("TEACHER");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(8001L), eq(8100001L))).thenReturn(0);

        BizException exception = assertThrows(BizException.class,
            () -> service.requireTeacherCourse(8001L, 8100001L));

        assertEquals(404, exception.getHttpStatus().value());
    }

    @SuppressWarnings("unchecked")
    private void mockRole(String role) {
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<ResultSetExtractor<String>>any(), eq(8001L)))
            .thenReturn(role);
    }
}
