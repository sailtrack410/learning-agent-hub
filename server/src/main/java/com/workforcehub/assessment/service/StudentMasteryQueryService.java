package com.workforcehub.assessment.service;

import com.workforcehub.assessment.repository.AssessmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StudentMasteryQueryService {
    private final AssessmentRepository assessmentRepository;

    public StudentMasteryQueryService(AssessmentRepository assessmentRepository) {
        this.assessmentRepository = assessmentRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> courseMastery(Long studentUserId, Long courseId) {
        return assessmentRepository.listCourseMastery(studentUserId, courseId).stream()
            .collect(Collectors.toMap(AssessmentRepository.MasteryRow::knowledgePointId,
                AssessmentRepository.MasteryRow::masteryScore));
    }
}
