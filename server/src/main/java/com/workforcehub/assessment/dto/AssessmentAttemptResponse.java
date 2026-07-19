package com.workforcehub.assessment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AssessmentAttemptResponse(
    Long attemptId,
    Long assessmentId,
    int attemptNo,
    int totalScore,
    int earnedScore,
    int durationSeconds,
    String report,
    boolean profileUpdated,
    LocalDateTime submittedAt,
    List<AnswerResult> answers,
    List<KnowledgeDiagnosis> diagnoses
) {
    public record AnswerResult(
        Long questionId,
        String prompt,
        String questionType,
        String knowledgePointName,
        List<String> selectedValues,
        List<String> correctValues,
        boolean correct,
        int earnedScore,
        int score,
        String errorType,
        String analysis,
        int durationSeconds
    ) {
    }

    public record KnowledgeDiagnosis(
        Long knowledgePointId,
        String knowledgePointName,
        BigDecimal masteryScore,
        int attemptCount,
        int correctCount
    ) {
    }
}
