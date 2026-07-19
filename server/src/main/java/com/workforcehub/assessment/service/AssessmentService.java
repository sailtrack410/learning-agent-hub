package com.workforcehub.assessment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.assessment.dto.AssessmentAttemptResponse;
import com.workforcehub.assessment.dto.AssessmentResponse;
import com.workforcehub.assessment.dto.AssessmentSubmitRequest;
import com.workforcehub.assessment.repository.AssessmentRepository;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.course.service.EducationRoleService;
import com.workforcehub.profile.service.StudentProfileService;
import com.workforcehub.learningpath.service.LearningPathService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.workforcehub.common.util.JsonValues.write;

@Service
public class AssessmentService {
    private final EducationRoleService educationRoleService;
    private final AssessmentRepository repository;
    private final StudentProfileService studentProfileService;
    private final ObjectMapper objectMapper;
    private final LearningPathService learningPathService;

    public AssessmentService(EducationRoleService educationRoleService,
                             AssessmentRepository repository,
                             StudentProfileService studentProfileService,
                             ObjectMapper objectMapper,
                             LearningPathService learningPathService) {
        this.educationRoleService = educationRoleService;
        this.repository = repository;
        this.studentProfileService = studentProfileService;
        this.objectMapper = objectMapper;
        this.learningPathService = learningPathService;
    }

    @Transactional(readOnly = true)
    public List<AssessmentResponse> list(Long courseId) {
        educationRoleService.requireCurrentRole("STUDENT");
        return repository.listPublished(courseId).stream().map(row -> response(row, List.of())).toList();
    }

    @Transactional(readOnly = true)
    public AssessmentResponse detail(Long courseId, Long assessmentId) {
        educationRoleService.requireCurrentRole("STUDENT");
        AssessmentRepository.AssessmentRow assessment = repository.getPublished(courseId, assessmentId);
        if (assessment == null) throw new BizException(404, "测验不存在或未发布");
        return response(assessment, repository.listQuestions(assessmentId));
    }

    @Transactional(rollbackFor = Exception.class)
    public AssessmentAttemptResponse submit(Long courseId, Long assessmentId, AssessmentSubmitRequest request) {
        Long studentUserId = educationRoleService.requireCurrentRole("STUDENT");
        AssessmentRepository.AssessmentRow assessment = repository.lockPublished(courseId, assessmentId);
        if (assessment == null) throw new BizException(404, "测验不存在或未发布");

        String idempotencyKey = request.getIdempotencyKey().trim();
        AssessmentRepository.AttemptKeyRow existing = repository.findAttemptByIdempotencyKey(
            studentUserId, idempotencyKey);
        if (existing != null) {
            if (!assessmentId.equals(existing.assessmentId())) {
                throw new BizException(409, "该幂等键已用于其他测验");
            }
            return attemptResponse(existing.attemptId(), studentUserId, assessment, repository.listQuestions(assessmentId));
        }

        List<AssessmentRepository.QuestionRow> questions = repository.listQuestions(assessmentId);
        if (questions.isEmpty()) throw new BizException(409, "测验尚未配置题目");
        Map<Long, AssessmentSubmitRequest.Answer> submitted = validateAnswers(request.getAnswers(), questions);

        List<GradedAnswer> gradedAnswers = new ArrayList<>();
        int earnedScore = 0;
        for (AssessmentRepository.QuestionRow question : questions) {
            AssessmentSubmitRequest.Answer answer = submitted.get(question.id());
            GradedAnswer graded = grade(question, answer);
            gradedAnswers.add(graded);
            earnedScore += graded.earnedScore();
        }

        Long attemptId = IdGenerator.nextId();
        repository.insertAttempt(attemptId, assessmentId, studentUserId, idempotencyKey,
            repository.nextAttemptNo(studentUserId, assessmentId), assessment.totalScore(), earnedScore,
            request.getDurationSeconds());
        for (GradedAnswer answer : gradedAnswers) {
            repository.insertAnswer(IdGenerator.nextId(), attemptId, answer.question().id(),
                write(objectMapper, answer.selectedValues()), answer.correct(), answer.earnedScore(), answer.errorType(),
                answer.durationSeconds());
            repository.updateMastery(IdGenerator.nextId(), studentUserId, courseId,
                answer.question().knowledgePointId(), answer.correct());
        }

        List<String> weakKnowledgePoints = gradedAnswers.stream()
            .filter(answer -> !answer.correct())
            .map(answer -> answer.question().knowledgePointName())
            .distinct()
            .toList();
        int profileVersion = studentProfileService.applyAssessment(studentUserId, assessment.title(), earnedScore,
            assessment.totalScore(), weakKnowledgePoints);
        learningPathService.regenerateAfterAssessment(studentUserId, courseId, attemptId, profileVersion);
        return attemptResponse(attemptId, studentUserId, assessment, questions);
    }

    private Map<Long, AssessmentSubmitRequest.Answer> validateAnswers(
        List<AssessmentSubmitRequest.Answer> answers, List<AssessmentRepository.QuestionRow> questions) {
        Set<Long> questionIds = questions.stream().map(AssessmentRepository.QuestionRow::id)
            .collect(java.util.stream.Collectors.toSet());
        Map<Long, AssessmentSubmitRequest.Answer> submitted = new LinkedHashMap<>();
        for (AssessmentSubmitRequest.Answer answer : answers) {
            if (!questionIds.contains(answer.getQuestionId())) {
                throw new BizException(400, "答案中包含不属于当前测验的题目");
            }
            if (submitted.put(answer.getQuestionId(), answer) != null) {
                throw new BizException(400, "同一题目不能重复提交");
            }
        }
        return submitted;
    }

    private GradedAnswer grade(AssessmentRepository.QuestionRow question, AssessmentSubmitRequest.Answer answer) {
        List<String> selected = answer == null ? List.of() : normalizeValues(answer.getSelectedValues());
        int durationSeconds = answer == null ? 0 : answer.getDurationSeconds();
        List<AssessmentResponse.Option> options = parseOptions(question.optionsJson());
        Set<String> allowedValues = options.stream().map(AssessmentResponse.Option::value)
            .map(this::normalizeValue).collect(java.util.stream.Collectors.toSet());
        if (!allowedValues.containsAll(selected)) throw new BizException(400, "答案中包含无效选项");
        if (("SINGLE_CHOICE".equals(question.questionType()) || "TRUE_FALSE".equals(question.questionType()))
            && selected.size() > 1) {
            throw new BizException(400, "单选题或判断题只能选择一个答案");
        }
        List<String> correctValues = normalizeValues(parseStringList(question.correctAnswerJson()));
        boolean correct = new LinkedHashSet<>(selected).equals(new LinkedHashSet<>(correctValues));
        int score = correct ? question.score() : 0;
        return new GradedAnswer(question, selected, correctValues, correct, score,
            errorType(question.questionType(), selected, correctValues, correct), durationSeconds);
    }

    private String errorType(String questionType, List<String> selected, List<String> correct, boolean isCorrect) {
        if (isCorrect) return null;
        if (selected.isEmpty()) return "UNANSWERED";
        if ("MULTIPLE_CHOICE".equals(questionType)) {
            Set<String> selectedSet = new LinkedHashSet<>(selected);
            Set<String> correctSet = new LinkedHashSet<>(correct);
            if (correctSet.containsAll(selectedSet)) return "PARTIAL_SELECTION";
            if (selectedSet.stream().anyMatch(correctSet::contains)) return "MIXED_SELECTION";
        }
        return "WRONG_OPTION";
    }

    private AssessmentAttemptResponse attemptResponse(Long attemptId, Long studentUserId,
                                                       AssessmentRepository.AssessmentRow assessment,
                                                       List<AssessmentRepository.QuestionRow> questions) {
        AssessmentRepository.AttemptRow attempt = repository.getAttempt(attemptId, studentUserId);
        if (attempt == null) throw new BizException(404, "测验作答记录不存在");
        Map<Long, AssessmentRepository.AnswerRow> answerByQuestion = repository.listAnswers(attemptId).stream()
            .collect(java.util.stream.Collectors.toMap(AssessmentRepository.AnswerRow::questionId, row -> row));
        List<AssessmentAttemptResponse.AnswerResult> answerResults = questions.stream().map(question -> {
            AssessmentRepository.AnswerRow answer = answerByQuestion.get(question.id());
            return new AssessmentAttemptResponse.AnswerResult(
                question.id(), question.prompt(), question.questionType(), question.knowledgePointName(),
                parseStringList(answer.answerJson()), parseStringList(question.correctAnswerJson()), answer.correct(),
                answer.earnedScore(), question.score(), answer.errorType(), question.analysis(), answer.durationSeconds());
        }).toList();
        List<Long> pointIds = questions.stream().map(AssessmentRepository.QuestionRow::knowledgePointId).distinct().toList();
        List<AssessmentAttemptResponse.KnowledgeDiagnosis> diagnoses = repository
            .listMastery(studentUserId, assessment.courseId(), pointIds).stream()
            .map(row -> new AssessmentAttemptResponse.KnowledgeDiagnosis(row.knowledgePointId(),
                row.knowledgePointName(), row.masteryScore(), row.attemptCount(), row.correctCount()))
            .toList();
        return new AssessmentAttemptResponse(attempt.id(), attempt.assessmentId(), attempt.attemptNo(),
            attempt.totalScore(), attempt.earnedScore(), attempt.durationSeconds(),
            report(assessment.title(), attempt.earnedScore(), attempt.totalScore(), diagnoses), true,
            attempt.submittedAt(), answerResults, diagnoses);
    }

    private String report(String title, int earnedScore, int totalScore,
                          List<AssessmentAttemptResponse.KnowledgeDiagnosis> diagnoses) {
        List<String> strong = diagnoses.stream().filter(item -> item.masteryScore().compareTo(new BigDecimal("80")) >= 0)
            .map(AssessmentAttemptResponse.KnowledgeDiagnosis::knowledgePointName).toList();
        List<String> weak = diagnoses.stream().filter(item -> item.masteryScore().compareTo(new BigDecimal("60")) < 0)
            .map(AssessmentAttemptResponse.KnowledgeDiagnosis::knowledgePointName).toList();
        StringBuilder report = new StringBuilder("《").append(title).append("》得分 ")
            .append(earnedScore).append('/').append(totalScore).append("。");
        if (!strong.isEmpty()) report.append("表现较好：").append(String.join("、", strong)).append("。");
        if (!weak.isEmpty()) report.append("建议优先复习：").append(String.join("、", weak)).append("。");
        if (strong.isEmpty() && weak.isEmpty()) report.append("各知识点掌握度居中，建议结合解析继续巩固。");
        return report.toString();
    }

    private AssessmentResponse response(AssessmentRepository.AssessmentRow assessment,
                                        List<AssessmentRepository.QuestionRow> questions) {
        List<AssessmentResponse.Question> responseQuestions = questions.stream().map(question ->
            new AssessmentResponse.Question(question.id(), question.knowledgePointId(),
                question.knowledgePointName(), question.questionType(), question.prompt(),
                parseOptions(question.optionsJson()), question.score())).toList();
        return new AssessmentResponse(assessment.id(), assessment.courseId(), assessment.title(),
            assessment.description(), assessment.totalScore(), assessment.durationMinutes(),
            assessment.questionCount(), responseQuestions);
    }

    private List<String> normalizeValues(List<String> values) {
        List<String> normalized = values.stream().map(this::normalizeValue).sorted(Comparator.naturalOrder()).toList();
        if (new LinkedHashSet<>(normalized).size() != normalized.size()) {
            throw new BizException(400, "同一选项不能重复提交");
        }
        return normalized;
    }

    private String normalizeValue(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> parseStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new BizException(500, "测验答案数据格式错误");
        }
    }

    private List<AssessmentResponse.Option> parseOptions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new BizException(500, "测验选项数据格式错误");
        }
    }

    private record GradedAnswer(AssessmentRepository.QuestionRow question, List<String> selectedValues,
                                List<String> correctValues, boolean correct, int earnedScore,
                                String errorType, int durationSeconds) {
    }
}
