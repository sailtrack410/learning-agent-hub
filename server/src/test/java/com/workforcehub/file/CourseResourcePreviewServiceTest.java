package com.workforcehub.file;

import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.course.service.CourseResourceAccessService;
import com.workforcehub.file.dto.CourseResourcePreviewResponse;
import com.workforcehub.file.repository.AttachmentPreviewRepository;
import com.workforcehub.file.repository.AttachmentRefRepository;
import com.workforcehub.file.repository.DownloadAttachmentRow;
import com.workforcehub.file.repository.FileObjectRepository;
import com.workforcehub.file.service.CourseResourcePreviewService;
import com.workforcehub.file.service.FileQuotaPolicyService;
import com.workforcehub.file.service.OfficePdfConverter;
import com.workforcehub.file.storage.StorageObjectResource;
import com.workforcehub.file.storage.StorageProvider;
import com.workforcehub.file.storage.StorageProviderRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseResourcePreviewServiceTest {
    @Mock AttachmentRefRepository attachmentRefRepository;
    @Mock AttachmentPreviewRepository attachmentPreviewRepository;
    @Mock StorageProviderRegistry storageProviderRegistry;
    @Mock CourseResourceAccessService courseResourceAccessService;
    @Mock OfficePdfConverter officePdfConverter;
    @Mock FileQuotaPolicyService fileQuotaPolicyService;
    @Mock FileObjectRepository fileObjectRepository;
    @Mock StorageProvider storageProvider;
    @TempDir Path tempDirectory;

    private CourseResourcePreviewService service;
    private final AtomicReference<Runnable> previewTask = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        WfhUserDetails user = mock(WfhUserDetails.class);
        when(user.getUserId()).thenReturn(2002L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        service = new CourseResourcePreviewService(attachmentRefRepository, attachmentPreviewRepository,
            storageProviderRegistry, courseResourceAccessService, officePdfConverter, fileQuotaPolicyService,
            fileObjectRepository, previewTask::set, 1024 * 1024);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldConvertOfficeFileAndPersistPdfPreview() throws Exception {
        byte[] sourceBytes = "docx-content".getBytes();
        DownloadAttachmentRow source = source(8101L, "课程讲义.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", sourceBytes.length);
        Path pdf = tempDirectory.resolve("source.pdf");
        Files.writeString(pdf, "%PDF-1.4 preview");
        when(attachmentRefRepository.getDownloadAttachmentOrThrow(8101L)).thenReturn(source);
        when(storageProviderRegistry.require("LOCAL")).thenReturn(storageProvider);
        when(storageProvider.loadObject("local", "course/source.docx"))
            .thenReturn(new StorageObjectResource(new ByteArrayResource(sourceBytes), (long) sourceBytes.length));
        when(officePdfConverter.convert(any(Path.class), any(Path.class), any(Path.class))).thenReturn(pdf);
        when(fileObjectRepository.findActiveBySha256AndSize(anyString(), anyLong())).thenReturn(null);
        when(attachmentPreviewRepository.saveReady(eq(8101L), eq(7001L), eq(2002L), anyString(),
            eq("LOCAL"), eq("local"), anyString(), anyLong(), anyString(), eq(null))).thenReturn(9101L);
        when(attachmentPreviewRepository.find(8101L)).thenReturn(null, new AttachmentPreviewRepository.PreviewRow(
            8101L, 9101L, "READY", null, LocalDateTime.now()));
        when(attachmentRefRepository.getDownloadAttachmentOrThrow(9101L))
            .thenReturn(source(9101L, "课程讲义-预览.pdf", "application/pdf", 18L));

        CourseResourcePreviewResponse accepted = service.prepare(7001L, 8101L);

        assertEquals("CONVERTING", accepted.previewStatus());
        verify(officePdfConverter, never()).convert(any(Path.class), any(Path.class), any(Path.class));
        assertNotNull(previewTask.get());
        previewTask.get().run();
        CourseResourcePreviewResponse response = service.status(7001L, 8101L);
        assertEquals("READY", response.previewStatus());
        assertEquals("/api/employee/courses/resources/8101/preview", response.previewUrl());
        verify(storageProvider).storeObject(eq("local"), anyString(), eq("application/pdf"), any(), anyLong());
        verify(courseResourceAccessService, times(2)).requireCourseResource(2002L, 7001L, 8101L);
    }

    @Test
    void shouldUseOriginalPdfWithoutConversion() {
        when(attachmentRefRepository.getDownloadAttachmentOrThrow(8102L))
            .thenReturn(source(8102L, "实验说明.pdf", "application/pdf", 120L));

        CourseResourcePreviewResponse response = service.prepare(7001L, 8102L);

        assertEquals("/api/employee/courses/resources/8102", response.previewUrl());
        verify(courseResourceAccessService).requireCourseResource(2002L, 7001L, 8102L);
    }

    private DownloadAttachmentRow source(Long id, String name, String mimeType, long size) {
        return new DownloadAttachmentRow(id, "COURSE_RESOURCE", name, "USER", 2002L,
            null, 0, "LOCAL", "local", "course/source.docx", mimeType, size, "READY", 0);
    }
}
