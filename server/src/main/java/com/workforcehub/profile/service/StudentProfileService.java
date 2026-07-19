package com.workforcehub.profile.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.course.service.EducationRoleService;
import com.workforcehub.profile.dto.StudentProfileResponse;
import com.workforcehub.profile.repository.StudentProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentProfileService {
    private static final BigDecimal DIALOGUE_CONFIDENCE = new BigDecimal("0.7500");
    private static final BigDecimal CORRECTION_CONFIDENCE = new BigDecimal("0.9500");
    private static final BigDecimal ASSESSMENT_CONFIDENCE = new BigDecimal("0.9000");
    private static final BigDecimal RESOURCE_FEEDBACK_CONFIDENCE = new BigDecimal("0.8000");
    private static final List<DimensionDefinition> DEFINITIONS = List.of(
        new DimensionDefinition("KNOWLEDGE_FOUNDATION", "知识基础", "请介绍你已经学过的 Java、数据库和 Web 基础，以及最有把握和最薄弱的部分。"),
        new DimensionDefinition("LEARNING_GOAL", "学习目标", "你希望通过《Java Web 应用开发》完成什么目标？可以描述想做出的项目或期望达到的能力。"),
        new DimensionDefinition("MASTERY_LEVEL", "掌握程度", "目前对 Servlet、Spring Boot、REST、MyBatis 等内容，你分别掌握到什么程度？"),
        new DimensionDefinition("ERROR_PATTERN", "易错点", "学习或编程时你最常遇到哪些错误？通常在哪类问题上反复卡住？"),
        new DimensionDefinition("COGNITIVE_STYLE", "认知风格", "你更容易通过原理推导、图示类比、完整案例还是分步骤操作来理解新知识？"),
        new DimensionDefinition("RESOURCE_PREFERENCE", "资源偏好", "你更偏好讲义、短视频、代码示例、练习题还是项目任务？请说明原因。"),
        new DimensionDefinition("LEARNING_PACE", "学习节奏", "你每周可投入多少时间，习惯一次集中学习还是每天短时学习？"),
        new DimensionDefinition("PRACTICE_ABILITY", "实践能力", "请描述你独立完成过的 Java 项目、调试经验，以及目前能独立完成到哪一步。")
    );
    private static final Map<String, DimensionDefinition> DEFINITION_BY_CODE = definitionMap();

    private final EducationRoleService educationRoleService;
    private final StudentProfileRepository repository;

    public StudentProfileService(EducationRoleService educationRoleService, StudentProfileRepository repository) {
        this.educationRoleService = educationRoleService;
        this.repository = repository;
    }

    @Transactional(rollbackFor = Exception.class)
    public StudentProfileResponse current() {
        Long userId = educationRoleService.requireCurrentRole("STUDENT");
        StudentProfileRepository.ProfileRow profile = requireProfile(userId);
        initializeQuestion(profile.id());
        return response(profile.id(), profile.currentVersion());
    }

    @Transactional(rollbackFor = Exception.class)
    public StudentProfileResponse answer(String message) {
        Long userId = educationRoleService.requireCurrentRole("STUDENT");
        StudentProfileRepository.ProfileRow profile = requireProfile(userId);
        List<StudentProfileRepository.DimensionRow> dimensions = repository.listDimensions(profile.id());
        DimensionDefinition definition = nextDefinition(dimensions);
        if (definition == null) {
            throw new BizException(409, "画像对话已完成，请使用纠正功能更新结论");
        }
        String conclusion = message.trim();
        int versionNo = profile.currentVersion() + 1;
        Long versionId = IdGenerator.nextId();
        String evidence = "学生在画像对话中自述：" + conclusion;
        repository.insertMessage(IdGenerator.nextId(), profile.id(), "USER", definition.code(), conclusion);
        repository.upsertDimension(IdGenerator.nextId(), profile.id(), definition.code(), conclusion,
            DIALOGUE_CONFIDENCE, evidence);
        repository.insertVersion(versionId, profile.id(), versionNo, "DIALOGUE",
            "通过画像对话更新“" + definition.name() + "”");
        repository.insertEvidence(IdGenerator.nextId(), profile.id(), versionId, definition.code(),
            "STUDENT_DIALOGUE", conclusion);
        repository.updateCurrentVersion(profile.id(), versionNo);
        List<StudentProfileRepository.DimensionRow> updated = repository.listDimensions(profile.id());
        DimensionDefinition next = nextDefinition(updated);
        repository.insertMessage(IdGenerator.nextId(), profile.id(), "ASSISTANT",
            next == null ? null : next.code(), next == null ? "画像采集已完成，你可以随时纠正任一维度。" : next.question());
        return response(profile.id(), versionNo);
    }

    @Transactional(rollbackFor = Exception.class)
    public StudentProfileResponse correct(String dimensionCode, String conclusion) {
        Long userId = educationRoleService.requireCurrentRole("STUDENT");
        DimensionDefinition definition = DEFINITION_BY_CODE.get(normalizedCode(dimensionCode));
        if (definition == null) throw new BizException(400, "画像维度不存在");
        StudentProfileRepository.ProfileRow profile = requireProfile(userId);
        boolean exists = repository.listDimensions(profile.id()).stream()
            .anyMatch(item -> definition.code().equals(item.dimensionCode()));
        if (!exists) throw new BizException(409, "请先通过画像对话完成该维度");
        String corrected = conclusion.trim();
        int versionNo = profile.currentVersion() + 1;
        Long versionId = IdGenerator.nextId();
        String evidence = "学生主动纠正画像结论：" + corrected;
        repository.upsertDimension(IdGenerator.nextId(), profile.id(), definition.code(), corrected,
            CORRECTION_CONFIDENCE, evidence);
        repository.insertVersion(versionId, profile.id(), versionNo, "CORRECTION",
            "学生纠正“" + definition.name() + "”结论");
        repository.insertEvidence(IdGenerator.nextId(), profile.id(), versionId, definition.code(),
            "STUDENT_CORRECTION", corrected);
        repository.updateCurrentVersion(profile.id(), versionNo);
        return response(profile.id(), versionNo);
    }

    @Transactional(rollbackFor = Exception.class)
    public int applyAssessment(Long studentUserId, String assessmentTitle, int earnedScore, int totalScore,
                               List<String> weakKnowledgePoints) {
        StudentProfileRepository.ProfileRow profile = requireProfile(studentUserId);
        int versionNo = profile.currentVersion() + 1;
        Long versionId = IdGenerator.nextId();
        int percentage = totalScore == 0 ? 0 : Math.round(earnedScore * 100F / totalScore);
        String weakSummary = weakKnowledgePoints.isEmpty()
            ? "本次未发现明显薄弱知识点"
            : "待加强知识点：" + String.join("、", weakKnowledgePoints);
        String masteryConclusion = "最近一次《" + assessmentTitle + "》得分率为 " + percentage + "%，" + weakSummary + "。";
        String errorConclusion = weakKnowledgePoints.isEmpty()
            ? "本次测验未发现明显易错知识点。"
            : "本次错误主要集中在" + String.join("、", weakKnowledgePoints) + "。";
        String evidence = "测验《" + assessmentTitle + "》得分 " + earnedScore + "/" + totalScore + "；" + weakSummary;

        repository.upsertDimension(IdGenerator.nextId(), profile.id(), "MASTERY_LEVEL", masteryConclusion,
            ASSESSMENT_CONFIDENCE, evidence);
        repository.upsertDimension(IdGenerator.nextId(), profile.id(), "ERROR_PATTERN", errorConclusion,
            ASSESSMENT_CONFIDENCE, evidence);
        repository.insertVersion(versionId, profile.id(), versionNo, "ASSESSMENT",
            "完成《" + assessmentTitle + "》后更新掌握程度和易错点");
        repository.insertEvidence(IdGenerator.nextId(), profile.id(), versionId, "MASTERY_LEVEL",
            "ASSESSMENT_RESULT", evidence);
        repository.insertEvidence(IdGenerator.nextId(), profile.id(), versionId, "ERROR_PATTERN",
            "ASSESSMENT_RESULT", evidence);
        repository.updateCurrentVersion(profile.id(), versionNo);
        return versionNo;
    }

    @Transactional(rollbackFor = Exception.class)
    public int applyResourceFeedback(Long studentUserId, String resourceTitle, String resourceTypeName,
                                     String feedbackType, String feedbackTypeName, String comment) {
        StudentProfileRepository.ProfileRow profile = requireProfile(studentUserId);
        int versionNo = profile.currentVersion() + 1;
        Long versionId = IdGenerator.nextId();
        String conclusion = resourceFeedbackConclusion(resourceTitle, resourceTypeName, feedbackType);
        String evidence = "对资源《" + resourceTitle + "》（" + resourceTypeName + "）反馈“" + feedbackTypeName
            + "”" + (comment == null ? "。" : "；补充说明：" + comment);
        repository.upsertDimension(IdGenerator.nextId(), profile.id(), "RESOURCE_PREFERENCE", conclusion,
            RESOURCE_FEEDBACK_CONFIDENCE, evidence);
        repository.insertVersion(versionId, profile.id(), versionNo, "RESOURCE_FEEDBACK",
            "根据学习资源反馈更新资源偏好");
        repository.insertEvidence(IdGenerator.nextId(), profile.id(), versionId, "RESOURCE_PREFERENCE",
            "RESOURCE_FEEDBACK", evidence);
        repository.updateCurrentVersion(profile.id(), versionNo);
        return versionNo;
    }

    @Transactional(rollbackFor = Exception.class)
    public int currentVersion(Long studentUserId) {
        return requireProfile(studentUserId).currentVersion();
    }

    @Transactional(rollbackFor = Exception.class)
    public GenerationSnapshot generationSnapshot(Long studentUserId) {
        StudentProfileRepository.ProfileRow profile = requireProfile(studentUserId);
        List<GenerationDimension> dimensions = repository.listDimensions(profile.id()).stream()
            .map(item -> new GenerationDimension(
                item.dimensionCode(), item.conclusion(), item.confidence(), item.evidenceSummary()))
            .toList();
        return new GenerationSnapshot(profile.currentVersion(), dimensions);
    }

    private StudentProfileRepository.ProfileRow requireProfile(Long userId) {
        repository.ensureProfile(IdGenerator.nextId(), userId);
        StudentProfileRepository.ProfileRow profile = repository.lockProfile(userId);
        if (profile == null) throw new BizException(500, "学生画像初始化失败");
        return profile;
    }

    private void initializeQuestion(Long profileId) {
        if (repository.listMessages(profileId).isEmpty()) {
            DimensionDefinition first = DEFINITIONS.get(0);
            repository.insertMessage(IdGenerator.nextId(), profileId, "ASSISTANT", first.code(), first.question());
        }
    }

    private StudentProfileResponse response(Long profileId, int versionNo) {
        List<StudentProfileRepository.DimensionRow> rows = repository.listDimensions(profileId);
        DimensionDefinition next = nextDefinition(rows);
        List<StudentProfileResponse.Dimension> dimensions = rows.stream().map(row -> {
            DimensionDefinition definition = DEFINITION_BY_CODE.get(row.dimensionCode());
            return new StudentProfileResponse.Dimension(row.dimensionCode(), definition.name(), row.conclusion(),
                row.confidence(), row.evidenceSummary(), row.updatedAt());
        }).toList();
        List<StudentProfileResponse.Message> messages = repository.listMessages(profileId).stream()
            .map(row -> new StudentProfileResponse.Message(row.id(), row.role(), row.dimensionCode(),
                row.content(), row.createdAt())).toList();
        return new StudentProfileResponse(profileId, versionNo, dimensions.size(), DEFINITIONS.size(),
            next == null, next == null ? null : next.code(), next == null ? null : next.question(),
            dimensions, messages);
    }

    private DimensionDefinition nextDefinition(List<StudentProfileRepository.DimensionRow> dimensions) {
        java.util.Set<String> completed = dimensions.stream()
            .map(StudentProfileRepository.DimensionRow::dimensionCode).collect(java.util.stream.Collectors.toSet());
        return DEFINITIONS.stream().filter(item -> !completed.contains(item.code())).findFirst().orElse(null);
    }

    private String normalizedCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String resourceFeedbackConclusion(String resourceTitle, String resourceTypeName, String feedbackType) {
        return switch (feedbackType) {
            case "HELPFUL" -> "近期反馈显示《" + resourceTitle + "》这类" + resourceTypeName + "有帮助，可优先推荐同类资源。";
            case "TOO_DIFFICULT" -> "近期反馈显示《" + resourceTitle + "》难度偏高，后续应优先推荐基础讲解和分步案例。";
            case "TOO_EASY" -> "近期反馈显示《" + resourceTitle + "》难度偏低，后续可提高练习和代码实操难度。";
            case "NOT_RELEVANT" -> "近期反馈显示《" + resourceTitle + "》与当前需要不够相关，后续应提高知识点匹配度。";
            default -> throw new BizException(400, "资源反馈类型不支持");
        };
    }

    private static Map<String, DimensionDefinition> definitionMap() {
        Map<String, DimensionDefinition> definitions = new LinkedHashMap<>();
        for (DimensionDefinition definition : DEFINITIONS) definitions.put(definition.code(), definition);
        return Map.copyOf(definitions);
    }

    private record DimensionDefinition(String code, String name, String question) {
    }

    public record GenerationSnapshot(int versionNo, List<GenerationDimension> dimensions) {
    }

    public record GenerationDimension(String dimensionCode, String conclusion,
                                      BigDecimal confidence, String evidenceSummary) {
    }
}
