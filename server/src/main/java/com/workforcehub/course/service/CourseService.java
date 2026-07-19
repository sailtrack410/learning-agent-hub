package com.workforcehub.course.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.course.dto.CourseOutlineSaveRequest;
import com.workforcehub.course.dto.CourseArticleSaveRequest;
import com.workforcehub.course.dto.CourseInvitationResponse;
import com.workforcehub.course.dto.CourseResponse;
import com.workforcehub.course.dto.CourseSaveRequest;
import com.workforcehub.course.repository.CourseRepository;
import com.workforcehub.learningpath.service.LearningPathService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class CourseService {
    private static final SecureRandom INVITATION_RANDOM = new SecureRandom();
    private final CourseRepository courseRepository;
    private final EducationRoleService educationRoleService;
    private final CourseResourceAccessService courseResourceAccessService;
    private final ObjectMapper objectMapper;
    private final CourseKnowledgeService courseKnowledgeService;
    private final CourseStudentQueryService courseStudentQueryService;
    private final LearningPathService learningPathService;

    public CourseService(CourseRepository courseRepository,
                         EducationRoleService educationRoleService,
                         CourseResourceAccessService courseResourceAccessService,
                         ObjectMapper objectMapper,
                         CourseKnowledgeService courseKnowledgeService,
                         CourseStudentQueryService courseStudentQueryService,
                         LearningPathService learningPathService) {
        this.courseRepository = courseRepository;
        this.educationRoleService = educationRoleService;
        this.courseResourceAccessService = courseResourceAccessService;
        this.objectMapper = objectMapper;
        this.courseKnowledgeService = courseKnowledgeService;
        this.courseStudentQueryService = courseStudentQueryService;
        this.learningPathService = learningPathService;
    }

    public List<CourseResponse> listAdminCourses() {
        return courseRepository.listCourses(false);
    }

    public CourseResponse getAdminCourse(Long courseId) {
        return courseRepository.getCourse(courseId, false);
    }

    public List<CourseResponse> listPublishedCourses() {
        return courseStudentQueryService.listPublishedCourses();
    }

    public CourseResponse getPublishedCourse(Long courseId) {
        return courseStudentQueryService.getPublishedCourse(courseId);
    }

    public CourseResponse enrollPublishedCourse(Long courseId) {
        return courseStudentQueryService.enroll(courseId);
    }

    public List<CourseResponse> listTeacherCourses() {
        Long userId = requireEducationRole("TEACHER");
        return courseRepository.listTeacherCourses(userId);
    }

    public CourseResponse getTeacherCourse(Long courseId) {
        Long userId = requireEducationRole("TEACHER");
        return courseRepository.getTeacherCourse(courseId, userId);
    }

    @Transactional
    public CourseResponse createCourse(CourseSaveRequest request) {
        String courseCode = normalizeCode(request.getCourseCode());
        validateCourse(request, courseCode, null);
        Long courseId = IdGenerator.nextId();
        courseRepository.insertCourse(courseId, courseCode, request.getCourseName().trim(), trim(request.getSummary()),
            trim(request.getLearningObjectives()), trim(request.getPrerequisites()), trim(request.getTeacherName()),
            request.getCoverAttachmentRefId(), valueOrZero(request.getTotalHours()), normalizeStatus(request.getStatus()),
            CurrentUserHolder.requireUserId());
        courseRepository.insertCourseTeacher(IdGenerator.nextId(), courseId, CurrentUserHolder.requireUserId(), "OWNER", null);
        return courseRepository.getCourse(courseId, false);
    }

    @Transactional
    public CourseResponse createTeacherCourse(CourseSaveRequest request) {
        Long userId = requireEducationRole("TEACHER");
        CourseResponse course = createCourse(request);
        return courseRepository.getTeacherCourse(course.getCourseId(), userId);
    }

    @Transactional
    public CourseResponse updateCourse(Long courseId, CourseSaveRequest request) {
        String courseCode = normalizeCode(request.getCourseCode());
        validateCourse(request, courseCode, courseId);
        courseRepository.updateCourse(courseId, courseCode, request.getCourseName().trim(), trim(request.getSummary()),
            trim(request.getLearningObjectives()), trim(request.getPrerequisites()), trim(request.getTeacherName()),
            request.getCoverAttachmentRefId(), valueOrZero(request.getTotalHours()), normalizeStatus(request.getStatus()),
            CurrentUserHolder.requireUserId());
        return courseRepository.getCourse(courseId, false);
    }

    @Transactional
    public CourseResponse updateTeacherCourse(Long courseId, CourseSaveRequest request) {
        Long userId = requireEducationRole("TEACHER");
        courseResourceAccessService.requireTeacherCourse(userId, courseId);
        if (request.getCoverAttachmentRefId() != null) {
            courseResourceAccessService.requireCourseResource(userId, courseId,
                request.getCoverAttachmentRefId());
        }
        updateCourse(courseId, request);
        return courseRepository.getTeacherCourse(courseId, userId);
    }

    @Transactional
    public CourseResponse saveOutline(Long courseId, CourseOutlineSaveRequest request) {
        courseRepository.getCourse(courseId, false);
        Map<String, Long> pointIds = validateOutline(request);
        Set<Long> existingChapterIds = new HashSet<>(courseRepository.listChapterIds(courseId));
        Set<Long> retainedChapterIds = new HashSet<>();
        for (CourseOutlineSaveRequest.Chapter chapter : request.getChapters()) {
            if (chapter.getChapterId() != null && !existingChapterIds.contains(chapter.getChapterId())) {
                throw new BizException(400, "章节不属于当前课程");
            }
            if (chapter.getChapterId() != null && !retainedChapterIds.add(chapter.getChapterId())) {
                throw new BizException(400, "章节不能重复");
            }
        }
        courseRepository.clearOutlineRelations(courseId);
        int chapterNo = 0;
        for (CourseOutlineSaveRequest.Chapter chapter : request.getChapters()) {
            boolean existing = chapter.getChapterId() != null;
            Long chapterId = existing ? chapter.getChapterId() : IdGenerator.nextId();
            retainedChapterIds.add(chapterId);
            courseRepository.insertChapter(chapterId, courseId, ++chapterNo, chapter.getChapterName().trim(),
                trim(chapter.getSummary()), existing);
            int sortOrder = 0;
            for (CourseOutlineSaveRequest.KnowledgePoint point : chapter.getKnowledgePoints()) {
                Long pointId = pointIds.get(normalizeCode(point.getPointCode()));
                courseRepository.insertKnowledgePoint(pointId, courseId, chapterId, normalizeCode(point.getPointCode()),
                    point.getPointName().trim(), trim(point.getSummary()), ++sortOrder);
            }
        }
        for (CourseOutlineSaveRequest.Dependency dependency : request.getDependencies()) {
            courseRepository.insertDependency(IdGenerator.nextId(), courseId,
                pointIds.get(normalizeCode(dependency.getPrerequisitePointCode())),
                pointIds.get(normalizeCode(dependency.getDependentPointCode())));
        }
        existingChapterIds.stream().filter(chapterId -> !retainedChapterIds.contains(chapterId))
            .forEach(chapterId -> courseRepository.deleteChapter(courseId, chapterId));
        return courseRepository.getCourse(courseId, false);
    }

    @Transactional
    public CourseResponse saveTeacherOutline(Long courseId, CourseOutlineSaveRequest request) {
        Long userId = requireEducationRole("TEACHER");
        courseResourceAccessService.requireTeacherCourse(userId, courseId);
        saveOutline(courseId, request);
        return courseRepository.getTeacherCourse(courseId, userId);
    }

    @Transactional
    public CourseResponse saveTeacherArticle(Long courseId, Long chapterId, CourseArticleSaveRequest request) {
        Long userId = requireEducationRole("TEACHER");
        courseResourceAccessService.requireTeacherCourse(userId, courseId);
        String status = normalizeStatus(request.getStatus());
        validateArticle(request.getContentJson(), status);
        validateArticleResources(userId, courseId, request.getContentJson());
        courseRepository.updateArticle(courseId, chapterId, request.getTitle().trim(), trim(request.getSummary()),
            request.getContentJson().trim(), status);
        return courseRepository.getTeacherCourse(courseId, userId);
    }

    @Transactional
    public void deleteCourse(Long courseId) {
        Long operatorId = CurrentUserHolder.requireUserId();
        courseRepository.getCourse(courseId, false);
        learningPathService.deleteCoursePaths(courseId);
        courseKnowledgeService.deleteCourseKnowledge(courseId, operatorId);
        courseRepository.clearOutline(courseId);
        courseRepository.deleteCourse(courseId);
    }

    @Transactional
    public void deleteTeacherCourse(Long courseId) {
        Long userId = requireEducationRole("TEACHER");
        requireCourseOwner(courseId, userId);
        deleteCourse(courseId);
    }

    @Transactional
    public CourseInvitationResponse createTeacherInvitation(Long courseId) {
        Long userId = requireEducationRole("TEACHER");
        requireCourseOwner(courseId, userId);
        String token = newInvitationToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        courseRepository.insertInvitation(IdGenerator.nextId(), courseId, hashToken(token), userId, expiresAt);
        return new CourseInvitationResponse(token, expiresAt);
    }

    @Transactional
    public CourseResponse acceptTeacherInvitation(String token) {
        Long userId = requireEducationRole("TEACHER");
        CourseRepository.InvitationRow invitation = courseRepository.lockInvitation(hashToken(token.trim()));
        if (!"ACTIVE".equals(invitation.status())) {
            if (userId.equals(invitation.acceptedBy())) {
                return courseRepository.getTeacherCourse(invitation.courseId(), userId);
            }
            throw new BizException(409, "课程邀请已被使用");
        }
        LocalDateTime acceptedAt = LocalDateTime.now();
        if (!invitation.expiresAt().isAfter(acceptedAt)) {
            throw new BizException(410, "课程邀请已过期");
        }
        if (courseRepository.isCourseTeacher(invitation.courseId(), userId)) {
            throw new BizException(409, "当前教师已在管理该课程");
        }
        courseRepository.insertCourseTeacher(IdGenerator.nextId(), invitation.courseId(), userId, "COLLABORATOR",
            invitation.invitedBy());
        courseRepository.acceptInvitation(invitation.invitationId(), userId, acceptedAt);
        return courseRepository.getTeacherCourse(invitation.courseId(), userId);
    }

    private Long requireEducationRole(String requiredRole) {
        return educationRoleService.requireCurrentRole(requiredRole);
    }

    private void requireCourseOwner(Long courseId, Long userId) {
        if (!courseRepository.isCourseOwner(courseId, userId)) {
            throw new BizException(403, "仅课程所有者可以执行该操作");
        }
    }

    private String newInvitationToken() {
        byte[] bytes = new byte[32];
        INVITATION_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void validateCourse(CourseSaveRequest request, String courseCode, Long excludedCourseId) {
        if (courseRepository.existsCourseCode(courseCode, excludedCourseId)) {
            throw new BizException(409, "课程编码已存在");
        }
        normalizeStatus(request.getStatus());
    }

    private Map<String, Long> validateOutline(CourseOutlineSaveRequest request) {
        Map<String, Long> pointIds = new HashMap<>();
        for (CourseOutlineSaveRequest.Chapter chapter : request.getChapters()) {
            for (CourseOutlineSaveRequest.KnowledgePoint point : chapter.getKnowledgePoints()) {
                String code = normalizeCode(point.getPointCode());
                if (pointIds.putIfAbsent(code, IdGenerator.nextId()) != null) {
                    throw new BizException(400, "知识点编码不能重复: " + code);
                }
            }
        }
        Set<String> dependencyKeys = new HashSet<>();
        for (CourseOutlineSaveRequest.Dependency dependency : request.getDependencies()) {
            String prerequisiteCode = normalizeCode(dependency.getPrerequisitePointCode());
            String dependentCode = normalizeCode(dependency.getDependentPointCode());
            if (!pointIds.containsKey(prerequisiteCode) || !pointIds.containsKey(dependentCode)) {
                throw new BizException(400, "依赖关系引用了不存在的知识点");
            }
            if (prerequisiteCode.equals(dependentCode)) {
                throw new BizException(400, "知识点不能依赖自身");
            }
            if (!dependencyKeys.add(prerequisiteCode + "->" + dependentCode)) {
                throw new BizException(400, "知识点依赖不能重复");
            }
        }
        ensureAcyclicDependencies(pointIds.keySet(), request.getDependencies());
        return pointIds;
    }

    private void ensureAcyclicDependencies(Set<String> pointCodes,
                                            List<CourseOutlineSaveRequest.Dependency> dependencies) {
        Map<String, List<String>> graph = new HashMap<>();
        pointCodes.forEach(code -> graph.put(code, new java.util.ArrayList<>()));
        dependencies.forEach(dependency -> graph.get(normalizeCode(dependency.getPrerequisitePointCode()))
            .add(normalizeCode(dependency.getDependentPointCode())));
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String pointCode : pointCodes) {
            if (hasCycle(pointCode, graph, visiting, visited)) {
                throw new BizException(400, "知识点依赖不能形成环");
            }
        }
    }

    private boolean hasCycle(String pointCode, Map<String, List<String>> graph,
                             Set<String> visiting, Set<String> visited) {
        if (visiting.contains(pointCode)) return true;
        if (!visited.add(pointCode)) return false;
        visiting.add(pointCode);
        for (String dependentCode : graph.get(pointCode)) {
            if (hasCycle(dependentCode, graph, visiting, visited)) return true;
        }
        visiting.remove(pointCode);
        return false;
    }

    private String normalizeStatus(String status) {
        String normalized = status == null || status.isBlank() ? "DRAFT" : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DRAFT", "PUBLISHED").contains(normalized)) throw new BizException(400, "课程状态不支持");
        return normalized;
    }

    private void validateArticle(String contentJson, String status) {
        if (!"PUBLISHED".equals(status)) return;
        JsonNode document = parseArticle(contentJson);
        if (!hasArticleContent(document)) {
            throw new BizException(400, "发布文章前请填写正文");
        }
    }

    private void validateArticleResources(Long userId, Long courseId, String contentJson) {
        collectArticleResourceIds(parseArticle(contentJson), new HashSet<>()).forEach(attachmentRefId ->
            courseResourceAccessService.requireCourseResource(userId, courseId, attachmentRefId));
    }

    private JsonNode parseArticle(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) return objectMapper.createObjectNode();
        try {
            JsonNode document = objectMapper.readTree(contentJson);
            if (!"doc".equals(document.path("type").asText())) throw new BizException(400, "文章内容格式不正确");
            return document;
        } catch (JsonProcessingException exception) {
            throw new BizException(400, "文章内容格式不正确");
        }
    }

    private Set<Long> collectArticleResourceIds(JsonNode node, Set<Long> attachmentRefIds) {
        String nodeType = node.path("type").asText();
        if (Set.of("image", "video", "attachment").contains(nodeType)) {
            JsonNode attachmentRefId = node.path("attrs").path("attachmentRefId");
            try {
                attachmentRefIds.add(Long.valueOf(attachmentRefId.asText()));
            } catch (NumberFormatException exception) {
                throw new BizException(400, "文章资源缺少附件标识");
            }
        }
        node.path("content").forEach(child -> collectArticleResourceIds(child, attachmentRefIds));
        return attachmentRefIds;
    }

    private boolean hasArticleContent(JsonNode node) {
        if ("text".equals(node.path("type").asText()) && !node.path("text").asText().isBlank()) return true;
        if (Set.of("image", "video", "attachment").contains(node.path("type").asText())) return true;
        for (JsonNode child : node.path("content")) {
            if (hasArticleContent(child)) return true;
        }
        return false;
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
