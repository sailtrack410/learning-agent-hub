package com.workforcehub.profile;

import com.workforcehub.course.service.EducationRoleService;
import com.workforcehub.profile.repository.StudentProfileRepository;
import com.workforcehub.profile.service.StudentProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentProfileServiceTest {

    @Test
    void assessmentShouldUpdateMasteryAndErrorDimensionsWithOneNewVersion() {
        EducationRoleService educationRoleService = mock(EducationRoleService.class);
        StudentProfileRepository repository = mock(StudentProfileRepository.class);
        StudentProfileService service = new StudentProfileService(educationRoleService, repository);
        when(repository.lockProfile(2003L)).thenReturn(new StudentProfileRepository.ProfileRow(91L, 2003L, 4));

        int version = service.applyAssessment(2003L, "Java Web 基础诊断", 60, 100,
            List.of("Servlet 生命周期", "接口权限"));

        assertEquals(5, version);
        ArgumentCaptor<String> dimensionCodes = ArgumentCaptor.forClass(String.class);
        verify(repository, times(2)).upsertDimension(anyLong(), eq(91L), dimensionCodes.capture(),
            any(), eq(new BigDecimal("0.9000")), any());
        assertEquals(List.of("MASTERY_LEVEL", "ERROR_PATTERN"), dimensionCodes.getAllValues());
        verify(repository).insertVersion(anyLong(), eq(91L), eq(5), eq("ASSESSMENT"),
            eq("完成《Java Web 基础诊断》后更新掌握程度和易错点"));
        verify(repository, times(2)).insertEvidence(anyLong(), eq(91L), anyLong(), dimensionCodes.capture(),
            eq("ASSESSMENT_RESULT"), any());
        verify(repository).updateCurrentVersion(91L, 5);
    }

    @Test
    void correctionShouldUseHighConfidenceAndPreserveVersionHistory() {
        EducationRoleService educationRoleService = mock(EducationRoleService.class);
        StudentProfileRepository repository = mock(StudentProfileRepository.class);
        StudentProfileService service = new StudentProfileService(educationRoleService, repository);
        when(educationRoleService.requireCurrentRole("STUDENT")).thenReturn(2003L);
        when(repository.lockProfile(2003L)).thenReturn(new StudentProfileRepository.ProfileRow(91L, 2003L, 2));
        when(repository.listDimensions(91L)).thenReturn(List.of(
            new StudentProfileRepository.DimensionRow("LEARNING_GOAL", "旧目标", new BigDecimal("0.7500"),
                "旧证据", java.time.LocalDateTime.now())
        ));
        when(repository.listMessages(91L)).thenReturn(List.of());

        service.correct("learning_goal", "完成一个带权限控制的课程平台");

        verify(repository).upsertDimension(anyLong(), eq(91L), eq("LEARNING_GOAL"),
            eq("完成一个带权限控制的课程平台"), eq(new BigDecimal("0.9500")), any());
        verify(repository).insertVersion(anyLong(), eq(91L), eq(3), eq("CORRECTION"), any());
        verify(repository).updateCurrentVersion(91L, 3);
    }
}
