package com.workforcehub.learningresource.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.course.service.CourseResourceAccessService;
import com.workforcehub.course.service.EducationRoleService;
import com.workforcehub.learningresource.dto.CreateLearningResourceTaskRequest;
import com.workforcehub.learningresource.dto.GeneratedLearningResourceResponse;
import com.workforcehub.learningresource.dto.LearningResourceReviewRequest;
import com.workforcehub.learningresource.dto.LearningResourceFeedbackRequest;
import com.workforcehub.learningresource.dto.LearningResourceFeedbackResponse;
import com.workforcehub.learningresource.dto.LearningResourcePushRequest;
import com.workforcehub.learningresource.dto.LearningResourcePushResponse;
import com.workforcehub.learningresource.dto.LearningResourceTaskResponse;
import com.workforcehub.learningresource.dto.LearningResourceUpdateRequest;
import com.workforcehub.learningresource.repository.LearningResourceRepository;
import com.workforcehub.profile.service.StudentProfileService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

@Service
public class LearningResourceService {
    private static final Map<String, String> ROLE_NAMES = Map.of(
        "PROFILE_ANALYST", "画像分析智能体",
        "PATH_PLANNER", "路径规划智能体",
        "CONTENT_GENERATOR", "内容生成智能体",
        "PRACTICE_GENERATOR", "练习生成智能体",
        "FACT_CHECKER", "事实校验智能体"
    );
    private static final Map<String, String> RESOURCE_TYPE_NAMES = Map.of(
        "EXPLANATION", "讲解文档",
        "MIND_MAP", "思维导图",
        "EXERCISE", "练习题",
        "CODE_LAB", "代码实操",
        "EXTENDED_READING", "拓展阅读"
    );
    private static final Map<String, String> FEEDBACK_TYPE_NAMES = Map.of(
        "HELPFUL", "有帮助",
        "TOO_DIFFICULT", "太难",
        "TOO_EASY", "太简单",
        "NOT_RELEVANT", "不相关"
    );
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile(
        "(?<!\\d)(?:(?:手机号|手机|电话)\\s*[:：]?\\s*)?1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");
    private static final Pattern STUDENT_NUMBER = Pattern.compile(
        "(?i)(学号|student\\s*id)\\s*[:：]?\\s*[A-Z0-9-]{5,20}");
    private static final Pattern SECRET = Pattern.compile(
        "(?i)(password|token|api[-_ ]?key|密码|密钥)\\s*[:=：]\\s*\\S+");

    private final LearningResourceRepository repository;
    private final EducationRoleService educationRoleService;
    private final CourseResourceAccessService courseResourceAccessService;
    private final StudentProfileService studentProfileService;
    private final LearningContentSafetyPolicyService contentSafetyPolicyService;
    private final LearningResourceGenerationService generationService;
    private final Executor agentExecutor;
    private final Executor bizExecutor;
    private final ObjectMapper objectMapper;

    public LearningResourceService(LearningResourceRepository repository,
                                   EducationRoleService educationRoleService,
                                   CourseResourceAccessService courseResourceAccessService,
                                   StudentProfileService studentProfileService,
                                   LearningContentSafetyPolicyService contentSafetyPolicyService,
                                   LearningResourceGenerationService generationService,
                                   @Qualifier("agentExecutor") Executor agentExecutor,
                                   @Qualifier("bizExecutor") Executor bizExecutor,
                                   ObjectMapper objectMapper) {
        this.repository = repository;
        this.educationRoleService = educationRoleService;
        this.courseResourceAccessService = courseResourceAccessService;
        this.studentProfileService = studentProfileService;
        this.contentSafetyPolicyService = contentSafetyPolicyService;
        this.generationService = generationService;
        this.agentExecutor = agentExecutor;
        this.bizExecutor = bizExecutor;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public LearningResourceTaskResponse createTask(CreateLearningResourceTaskRequest request) {
        Long studentUserId = educationRoleService.requireCurrentRole("STUDENT");
        String idempotencyKey = request.getIdempotencyKey().trim();
        LearningResourceRepository.TaskRow existing = repository.findTaskByIdempotency(studentUserId, idempotencyKey);
        if (existing != null) {
            requireSameTarget(existing, request.getCourseId(), request.getKnowledgePointId());
            return toTask(existing, false);
        }

        LearningResourceRepository.CoursePointRow context =
            repository.getPublishedCoursePoint(request.getCourseId(), request.getKnowledgePointId());
        String sanitizedRequest = sanitizeAndValidateRequest(request.getRequestMessage());
        StudentProfileService.GenerationSnapshot profile = studentProfileService.generationSnapshot(studentUserId);
        if (profile.dimensions().size() < 6) {
            throw new BizException(409, "请先通过画像对话完成至少6个学习维度");
        }
        String profileSnapshotJson = profileSnapshot(profile);
        Long taskId = IdGenerator.nextId();
        try {
            repository.insertTask(taskId, studentUserId, context, profile.versionNo(), profileSnapshotJson,
                idempotencyKey, sanitizedRequest);
        } catch (DuplicateKeyException exception) {
            LearningResourceRepository.TaskRow duplicate = repository.findTaskByIdempotency(studentUserId, idempotencyKey);
            if (duplicate == null) throw exception;
            requireSameTarget(duplicate, request.getCourseId(), request.getKnowledgePointId());
            return toTask(duplicate, false);
        }
        schedule(taskId);
        return toTask(repository.getTask(taskId), false);
    }

    public List<LearningResourceTaskResponse> listStudentTasks(Long courseId) {
        Long studentUserId = educationRoleService.requireCurrentRole("STUDENT");
        return repository.listStudentTasks(studentUserId, courseId).stream()
            .map(row -> toTask(row, false)).toList();
    }

    public LearningResourceTaskResponse getStudentTask(Long taskId) {
        Long studentUserId = educationRoleService.requireCurrentRole("STUDENT");
        LearningResourceRepository.TaskRow task = repository.getTask(taskId);
        if (!studentUserId.equals(task.studentUserId())) throw new BizException(404, "资源生成任务不存在");
        return toTask(task, false);
    }

    public SseEmitter streamStudentTask(Long taskId) {
        Long studentUserId = educationRoleService.requireCurrentRole("STUDENT");
        LearningResourceRepository.TaskRow task = repository.getTask(taskId);
        if (!studentUserId.equals(task.studentUserId())) throw new BizException(404, "资源生成任务不存在");
        SseEmitter emitter = new SseEmitter(20 * 60 * 1000L);
        bizExecutor.execute(() -> streamTask(taskId, studentUserId, emitter));
        return emitter;
    }

    @Transactional(rollbackFor = Exception.class)
    public LearningResourceTaskResponse retryStudentTask(Long taskId) {
        Long studentUserId = educationRoleService.requireCurrentRole("STUDENT");
        LearningResourceRepository.TaskRow task = repository.lockTask(taskId);
        if (!studentUserId.equals(task.studentUserId())) throw new BizException(404, "资源生成任务不存在");
        if (!"FAILED".equals(task.status())) throw new BizException(409, "只有失败任务可以重试");
        if (repository.restartFailedTask(taskId) != 1) throw new BizException(409, "任务状态已变化，请刷新后重试");
        schedule(taskId);
        return toTask(repository.getTask(taskId), false);
    }

    public List<GeneratedLearningResourceResponse> listStudentResources(Long courseId, String resourceType) {
        Long studentUserId = educationRoleService.requireCurrentRole("STUDENT");
        return repository.listStudentResources(studentUserId, courseId, normalizedFilter(resourceType)).stream()
            .map(this::toResource).toList();
    }

    public GeneratedLearningResourceResponse getStudentResource(Long resourceId) {
        Long studentUserId = educationRoleService.requireCurrentRole("STUDENT");
        LearningResourceRepository.ResourceRow resource = repository.getResource(resourceId);
        if (!repository.canStudentAccessResource(studentUserId, resourceId)) {
            throw new BizException(404, "学习资源不存在");
        }
        return toResource(resource);
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordStudentResourceView(Long resourceId) {
        Long studentUserId = educationRoleService.requireCurrentRole("STUDENT");
        if (!repository.canStudentAccessResource(studentUserId, resourceId)) {
            throw new BizException(404, "学习资源不存在");
        }
        repository.recordResourceView(IdGenerator.nextId(), resourceId, studentUserId);
    }

    public LearningResourceFeedbackResponse getStudentResourceFeedback(Long resourceId) {
        Long studentUserId = educationRoleService.requireCurrentRole("STUDENT");
        if (!repository.canStudentAccessResource(studentUserId, resourceId)) {
            throw new BizException(404, "学习资源不存在");
        }
        return toFeedback(repository.getResourceFeedback(resourceId, studentUserId));
    }

    @Transactional(rollbackFor = Exception.class)
    public LearningResourceFeedbackResponse saveStudentResourceFeedback(
        Long resourceId, LearningResourceFeedbackRequest request) {
        Long studentUserId = educationRoleService.requireCurrentRole("STUDENT");
        LearningResourceRepository.ResourceRow resource = repository.lockResource(resourceId);
        if (!repository.canStudentAccessResource(studentUserId, resourceId)) {
            throw new BizException(404, "学习资源不存在");
        }
        String feedbackType = normalizedFilter(request.getFeedbackType());
        String feedbackTypeName = FEEDBACK_TYPE_NAMES.get(feedbackType);
        if (feedbackTypeName == null) throw new BizException(400, "资源反馈类型不支持");
        String comment = sanitizeAndValidateFeedback(request.getComment());
        LearningResourceRepository.ResourceFeedbackRow existing =
            repository.getResourceFeedback(resourceId, studentUserId);
        if (existing != null && feedbackType.equals(existing.feedbackType())
            && Objects.equals(comment, existing.comment())) {
            return toFeedback(existing);
        }
        repository.upsertResourceFeedback(IdGenerator.nextId(), resourceId, studentUserId, feedbackType, comment);
        int profileVersionNo = studentProfileService.applyResourceFeedback(
            studentUserId, resource.title(), RESOURCE_TYPE_NAMES.getOrDefault(resource.resourceType(), resource.resourceType()),
            feedbackType, feedbackTypeName, comment);
        repository.updateFeedbackProfileVersion(resourceId, studentUserId, profileVersionNo);
        return toFeedback(repository.getResourceFeedback(resourceId, studentUserId));
    }

    public List<LearningResourceTaskResponse> listTeacherTasks(Long courseId, String status) {
        Long teacherUserId = educationRoleService.requireCurrentRole("TEACHER");
        return repository.listTeacherTasks(teacherUserId, courseId, normalizedFilter(status)).stream()
            .map(row -> toTask(row, true)).toList();
    }

    public LearningResourceTaskResponse getTeacherTask(Long taskId) {
        Long teacherUserId = educationRoleService.requireCurrentRole("TEACHER");
        LearningResourceRepository.TaskRow task = repository.getTask(taskId);
        courseResourceAccessService.requireTeacherCourse(teacherUserId, task.courseId());
        return toTask(task, true);
    }

    public List<GeneratedLearningResourceResponse> listTeacherResources(Long courseId, Long knowledgePointId,
                                                                        String resourceType, String status) {
        Long teacherUserId = educationRoleService.requireCurrentRole("TEACHER");
        return repository.listTeacherResources(teacherUserId, courseId, knowledgePointId,
                normalizedFilter(resourceType), normalizedFilter(status)).stream()
            .map(this::toResource).toList();
    }

    public GeneratedLearningResourceResponse getTeacherResource(Long resourceId) {
        Long teacherUserId = educationRoleService.requireCurrentRole("TEACHER");
        LearningResourceRepository.ResourceRow resource = repository.getResource(resourceId);
        courseResourceAccessService.requireTeacherCourse(teacherUserId, resource.courseId());
        return toResource(resource);
    }

    @Transactional(rollbackFor = Exception.class)
    public GeneratedLearningResourceResponse updateTeacherResource(Long resourceId,
                                                                    LearningResourceUpdateRequest request) {
        Long teacherUserId = educationRoleService.requireCurrentRole("TEACHER");
        LearningResourceRepository.ResourceRow resource = repository.getResource(resourceId);
        courseResourceAccessService.requireTeacherCourse(teacherUserId, resource.courseId());
        if (repository.updateResource(resourceId, teacherUserId, request.getTitle().trim(),
                request.getSummary().trim(), request.getContent().trim()) != 1) {
            throw new BizException(409, "已发布资源请先撤回后再修改");
        }
        return toResource(repository.getResource(resourceId));
    }

    @Transactional(rollbackFor = Exception.class)
    public GeneratedLearningResourceResponse reviewTeacherResource(Long resourceId,
                                                                    LearningResourceReviewRequest request) {
        Long teacherUserId = educationRoleService.requireCurrentRole("TEACHER");
        LearningResourceRepository.ResourceRow resource = repository.getResource(resourceId);
        courseResourceAccessService.requireTeacherCourse(teacherUserId, resource.courseId());
        String decision = request.getDecision().trim().toUpperCase(Locale.ROOT);
        String note = StringUtils.hasText(request.getNote()) ? request.getNote().trim() : null;
        if ("REJECT".equals(decision) && note == null) throw new BizException(400, "拒绝时必须填写审核意见");
        if (repository.reviewResource(resourceId, teacherUserId, decision, note) != 1) {
            throw new BizException(409, "资源状态已变化，请刷新后重试");
        }
        return toResource(repository.getResource(resourceId));
    }

    @Transactional(rollbackFor = Exception.class)
    public GeneratedLearningResourceResponse publishTeacherResource(Long resourceId) {
        Long teacherUserId = educationRoleService.requireCurrentRole("TEACHER");
        LearningResourceRepository.ResourceRow resource = repository.getResource(resourceId);
        courseResourceAccessService.requireTeacherCourse(teacherUserId, resource.courseId());
        if (repository.publishResource(resourceId, teacherUserId) != 1) {
            throw new BizException(409, "资源需先审核通过后才能发布");
        }
        return toResource(repository.getResource(resourceId));
    }

    @Transactional(rollbackFor = Exception.class)
    public GeneratedLearningResourceResponse withdrawTeacherResource(Long resourceId) {
        Long teacherUserId = educationRoleService.requireCurrentRole("TEACHER");
        LearningResourceRepository.ResourceRow resource = repository.getResource(resourceId);
        courseResourceAccessService.requireTeacherCourse(teacherUserId, resource.courseId());
        if (repository.withdrawResource(resourceId, teacherUserId) != 1) {
            throw new BizException(409, "只有已发布资源可以撤回");
        }
        return toResource(repository.getResource(resourceId));
    }

    @Transactional(rollbackFor = Exception.class)
    public LearningResourcePushResponse pushTeacherResource(Long resourceId, LearningResourcePushRequest request) {
        Long teacherUserId = educationRoleService.requireCurrentRole("TEACHER");
        LearningResourceRepository.ResourceRow resource = repository.getResource(resourceId);
        courseResourceAccessService.requireTeacherCourse(teacherUserId, resource.courseId());
        if (!"PUBLISHED".equals(resource.publicationStatus())) {
            throw new BizException(409, "只有已发布资源可以推送");
        }
        String targetType = request.getTargetType().trim().toUpperCase(Locale.ROOT);
        if (!"STUDENT".equals(targetType) && !"CLASS".equals(targetType)) {
            throw new BizException(400, "推送目标类型仅支持 STUDENT 或 CLASS");
        }
        LearningResourceRepository.PushTargetRow target = repository.getPushTarget(
            resource.courseId(), targetType, request.getTargetId());
        if (target == null) throw new BizException(404, "推送目标不存在");
        repository.upsertResourcePush(IdGenerator.nextId(), resourceId, resource.courseId(), targetType,
            target.targetId(), teacherUserId);
        LearningResourceRepository.ResourcePushRow push = repository.getResourcePush(
            resourceId, targetType, target.targetId());
        return new LearningResourcePushResponse(push.pushId(), push.resourceId(), push.courseId(),
            push.targetType(), push.targetId(), target.targetName(), push.status(), push.createdAt());
    }

    private void schedule(Long taskId) {
        Runnable execution = () -> agentExecutor.execute(() -> generationService.execute(taskId));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    execution.run();
                }
            });
            return;
        }
        execution.run();
    }

    private void streamTask(Long taskId, Long studentUserId, SseEmitter emitter) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                LearningResourceRepository.TaskRow row = repository.getTask(taskId);
                if (!studentUserId.equals(row.studentUserId())) {
                    emitter.completeWithError(new BizException(404, "资源生成任务不存在"));
                    return;
                }
                emitter.send(SseEmitter.event().name("task").data(toTask(row, false)));
                if ("SUCCEEDED".equals(row.status()) || "FAILED".equals(row.status())) {
                    emitter.complete();
                    return;
                }
                Thread.sleep(1000L);
            }
        } catch (IOException exception) {
            emitter.complete();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (Exception exception) {
            emitter.completeWithError(exception);
        }
    }

    private LearningResourceTaskResponse toTask(LearningResourceRepository.TaskRow row, boolean teacherView) {
        List<LearningResourceTaskResponse.Step> steps = repository.listSteps(row.id(), row.attemptNo()).stream()
            .map(step -> new LearningResourceTaskResponse.Step(
                step.id(), step.roleCode(), step.roleName(), step.stepOrder(), step.status(), statusName(step.status()),
                step.inputSummary(), step.outputSummary(), step.liveOutput(), step.modelName(),
                step.durationMs(), step.failureMessage(),
                step.startedAt(), step.finishedAt()
            )).toList();
        List<GeneratedLearningResourceResponse> resources = repository.listTaskResources(row.id()).stream()
            .filter(resource -> teacherView || "PUBLISHED".equals(resource.publicationStatus()))
            .map(this::toResource).toList();
        return new LearningResourceTaskResponse(
            row.id(), teacherView ? row.studentUserId() : null, row.courseId(), row.courseName(), row.chapterId(),
            row.chapterName(), row.knowledgePointId(), row.knowledgePointName(), row.profileVersionNo(),
            row.requestMessage(), row.status(), statusName(row.status()), row.progressPercent(), row.currentRoleCode(),
            row.currentRoleCode() == null ? null : ROLE_NAMES.get(row.currentRoleCode()),
            row.attemptNo(), row.modelName(), row.failureType(),
            row.failureMessage(), row.totalDurationMs(), row.startedAt(), row.finishedAt(), row.createdAt(),
            row.updatedAt(), steps, resources
        );
    }

    private GeneratedLearningResourceResponse toResource(LearningResourceRepository.ResourceRow row) {
        List<GeneratedLearningResourceResponse.Citation> citations = repository.listCitations(row.id()).stream()
            .map(item -> new GeneratedLearningResourceResponse.Citation(
                item.documentId(), item.chunkId(), item.documentTitle(), item.excerpt(), item.pageNo(),
                item.relevanceScore()
            )).toList();
        return new GeneratedLearningResourceResponse(
            row.id(), row.taskId(), row.courseId(), row.courseName(), row.chapterId(), row.chapterName(),
            row.knowledgePointId(), row.knowledgePointName(), row.resourceType(),
            RESOURCE_TYPE_NAMES.getOrDefault(row.resourceType(), row.resourceType()), row.title(), row.summary(),
            row.content(), row.contentFormat(), row.validationStatus(), validationName(row.validationStatus()),
            row.validationReason(), row.confidence(), row.publicationStatus(),
            publicationName(row.publicationStatus()), row.reviewNote(), row.reviewedAt(), row.publishedAt(),
            row.createdAt(), row.updatedAt(), citations
        );
    }

    private String profileSnapshot(StudentProfileService.GenerationSnapshot profile) {
        List<Map<String, Object>> dimensions = profile.dimensions().stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("dimensionCode", item.dimensionCode());
            value.put("conclusion", sanitize(item.conclusion()));
            value.put("confidence", item.confidence());
            return value;
        }).toList();
        try {
            return objectMapper.writeValueAsString(Map.of("versionNo", profile.versionNo(), "dimensions", dimensions));
        } catch (Exception exception) {
            throw new BizException(500, "画像快照保存失败");
        }
    }

    private String sanitizeAndValidateRequest(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim();
        LearningContentSafetyPolicyService.Policy policy = contentSafetyPolicyService.current();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (policy.promptInjectionDetectionEnabled()
            && policy.blockedMarkers().stream().anyMatch(lower::contains)) {
            throw new BizException(400, "补充学习诉求包含不安全指令，请只描述学习目标或困难");
        }
        return sanitize(normalized);
    }

    private String sanitizeAndValidateFeedback(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim();
        LearningContentSafetyPolicyService.Policy policy = contentSafetyPolicyService.current();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (policy.promptInjectionDetectionEnabled()
            && policy.blockedMarkers().stream().anyMatch(lower::contains)) {
            throw new BizException(400, "反馈说明包含不安全指令，请只描述资源使用感受");
        }
        return sanitize(normalized);
    }

    private LearningResourceFeedbackResponse toFeedback(LearningResourceRepository.ResourceFeedbackRow row) {
        if (row == null) return null;
        return new LearningResourceFeedbackResponse(
            row.feedbackId(), row.resourceId(), row.feedbackType(),
            FEEDBACK_TYPE_NAMES.getOrDefault(row.feedbackType(), row.feedbackType()), row.comment(),
            row.profileVersionNo(), row.createdAt(), row.updatedAt());
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) return value;
        String sanitized = EMAIL.matcher(value).replaceAll("[邮箱已隐藏]");
        sanitized = PHONE.matcher(sanitized).replaceAll("[手机号已隐藏]");
        sanitized = ID_CARD.matcher(sanitized).replaceAll("[身份证号已隐藏]");
        sanitized = STUDENT_NUMBER.matcher(sanitized).replaceAll("[学号已隐藏]");
        return SECRET.matcher(sanitized).replaceAll("[凭证已隐藏]");
    }

    private void requireSameTarget(LearningResourceRepository.TaskRow task, Long courseId, Long knowledgePointId) {
        if (!task.courseId().equals(courseId) || !task.knowledgePointId().equals(knowledgePointId)) {
            throw new BizException(409, "该幂等键已用于其他课程或知识点");
        }
    }

    private String normalizedFilter(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String statusName(String status) {
        if ("PENDING".equals(status)) return "等待执行";
        if ("RUNNING".equals(status)) return "正在生成";
        if ("SUCCEEDED".equals(status)) return "生成完成";
        if ("FAILED".equals(status)) return "生成失败";
        return status;
    }

    private String validationName(String status) {
        if ("PASSED".equals(status)) return "校验通过";
        if ("NEEDS_REVIEW".equals(status)) return "需要复核";
        if ("REJECTED".equals(status)) return "校验拒绝";
        return status;
    }

    private String publicationName(String status) {
        if ("DRAFT".equals(status)) return "待审核";
        if ("PUBLISHED".equals(status)) return "已发布";
        if ("WITHDRAWN".equals(status)) return "已撤回";
        if ("REJECTED".equals(status)) return "已拒绝";
        return status;
    }
}
