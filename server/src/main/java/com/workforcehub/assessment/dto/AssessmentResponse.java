package com.workforcehub.assessment.dto;

import java.util.List;

public record AssessmentResponse(
    Long assessmentId,
    Long courseId,
    String title,
    String description,
    int totalScore,
    int durationMinutes,
    int questionCount,
    List<Question> questions
) {
    public record Question(
        Long questionId,
        Long knowledgePointId,
        String knowledgePointName,
        String questionType,
        String prompt,
        List<Option> options,
        int score
    ) {
    }

    public record Option(String value, String label) {
    }
}
