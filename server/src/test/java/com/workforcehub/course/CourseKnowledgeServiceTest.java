package com.workforcehub.course;

import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.common.enums.UserType;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.course.dto.CourseKnowledgeDocumentRequest;
import com.workforcehub.course.dto.CourseKnowledgeDocumentResponse;
import com.workforcehub.course.repository.CourseKnowledgeRepository;
import com.workforcehub.course.repository.CourseRepository;
import com.workforcehub.course.service.CourseKnowledgeService;
import com.workforcehub.course.service.CourseResourceAccessService;
import com.workforcehub.ragflow.dto.RagflowDocumentResponse;
import com.workforcehub.ragflow.service.RagflowCourseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class CourseKnowledgeServiceTest {
    private final CourseResourceAccessService accessService = mock(CourseResourceAccessService.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final CourseKnowledgeRepository knowledgeRepository = mock(CourseKnowledgeRepository.class);
    private final RagflowCourseService ragflowCourseService = mock(RagflowCourseService.class);
    private final CourseKnowledgeService service = new CourseKnowledgeService(
        accessService, courseRepository, knowledgeRepository, ragflowCourseService);

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnExistingDocumentForDuplicateSubmission() {
        authenticate(2002L);
        CourseKnowledgeDocumentRequest request = request();
        when(knowledgeRepository.findBindingId(8100001L, 8200001L)).thenReturn(8300001L);
        when(knowledgeRepository.findSpaceId(8100001L)).thenReturn(8400001L);
        when(ragflowCourseService.listDocuments(8400001L)).thenReturn(List.of(document()));
        when(knowledgeRepository.listRelations(8100001L)).thenReturn(java.util.Map.of(
            8500001L, new CourseKnowledgeRepository.Relation(8110001L, "Servlet", List.of())));

        CourseKnowledgeDocumentResponse response = service.addDocument(8100001L, request);

        assertEquals(8500001L, response.getDocumentId());
        assertEquals(35, response.getIngestProgressPercent());
        assertEquals("SPLIT_CHUNKS", response.getIngestStage());
        assertEquals("切分文本", response.getIngestStageName());
        verify(accessService).requireCourseResource(2002L, 8100001L, 8200001L);
        verifyNoInteractions(courseRepository);
    }

    @Test
    void shouldRejectKnowledgePointOutsideCourseBeforeCreatingSpace() {
        authenticate(2002L);
        CourseKnowledgeDocumentRequest request = request();
        org.mockito.Mockito.doThrow(new BizException(400, "知识点不属于当前课程"))
            .when(knowledgeRepository).validateRelations(8100001L, 8110001L, List.of(8120001L));

        assertThrows(BizException.class, () -> service.addDocument(8100001L, request));

        verify(accessService).requireCourseResource(2002L, 8100001L, 8200001L);
        verifyNoInteractions(ragflowCourseService);
    }

    @Test
    void shouldRejectRetryForDocumentOutsideCourse() {
        authenticate(2002L);
        when(knowledgeRepository.findSpaceId(8100001L)).thenReturn(8400001L);
        when(knowledgeRepository.listRelations(8100001L)).thenReturn(java.util.Map.of());

        assertThrows(BizException.class, () -> service.retry(8100001L, 8500001L));

        verify(accessService).requireTeacherCourse(2002L, 8100001L);
        verifyNoInteractions(ragflowCourseService);
    }

    @Test
    void shouldRecheckDuplicateAfterCourseLock() {
        authenticate(2002L);
        CourseKnowledgeDocumentRequest request = request();
        when(knowledgeRepository.findBindingId(8100001L, 8200001L)).thenReturn(null, 8300001L);
        when(knowledgeRepository.findSpaceId(8100001L)).thenReturn(8400001L);
        when(ragflowCourseService.listDocuments(8400001L)).thenReturn(List.of(document()));
        when(knowledgeRepository.listRelations(8100001L)).thenReturn(java.util.Map.of(
            8500001L, new CourseKnowledgeRepository.Relation(8110001L, "Servlet", List.of())));

        CourseKnowledgeDocumentResponse response = service.addDocument(8100001L, request);

        assertEquals(8500001L, response.getDocumentId());
        verify(knowledgeRepository).lockCourse(8100001L);
        verify(knowledgeRepository, times(2)).findBindingId(8100001L, 8200001L);
        verifyNoInteractions(courseRepository);
    }

    private CourseKnowledgeDocumentRequest request() {
        CourseKnowledgeDocumentRequest request = new CourseKnowledgeDocumentRequest();
        request.setAttachmentRefId(8200001L);
        request.setChapterId(8110001L);
        request.setKnowledgePointIds(List.of(8120001L));
        return request;
    }

    private RagflowDocumentResponse document() {
        RagflowDocumentResponse response = new RagflowDocumentResponse();
        response.setDocumentId(8500001L);
        response.setAttachmentRefId(8200001L);
        response.setTitle("Servlet 讲义.pdf");
        response.setIngestStatus("PENDING");
        response.setIngestStatusName("等待处理");
        response.setIngestProgressPercent(35);
        response.setIngestStage("SPLIT_CHUNKS");
        response.setIngestStageName("切分文本");
        response.setVectorStatus("PENDING");
        response.setChunkCount(0);
        return response;
    }

    private void authenticate(Long userId) {
        WfhUserDetails user = mock(WfhUserDetails.class);
        when(user.getUserId()).thenReturn(userId);
        when(user.getUserType()).thenReturn(UserType.USER);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
