package com.workforcehub.learningpath.service;

import com.workforcehub.assessment.service.StudentMasteryQueryService;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.course.dto.CourseResponse;
import com.workforcehub.course.service.CourseStudentQueryService;
import com.workforcehub.course.service.EducationRoleService;
import com.workforcehub.learningpath.dto.LearningPathResponse;
import com.workforcehub.learningpath.repository.LearningPathRepository;
import com.workforcehub.profile.service.StudentProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

@Service
public class LearningPathService {
    private static final BigDecimal COMPLETION_MASTERY = new BigDecimal("80");
    private static final String COMPLETION_CONDITION = "测验掌握度达到80%，或完成课程文章后手动标记完成";

    private final EducationRoleService educationRoleService;
    private final CourseStudentQueryService courseStudentQueryService;
    private final StudentProfileService studentProfileService;
    private final StudentMasteryQueryService studentMasteryQueryService;
    private final LearningPathRepository repository;

    public LearningPathService(EducationRoleService educationRoleService,
                               CourseStudentQueryService courseStudentQueryService,
                               StudentProfileService studentProfileService,
                               StudentMasteryQueryService studentMasteryQueryService,
                               LearningPathRepository repository) {
        this.educationRoleService = educationRoleService;
        this.courseStudentQueryService = courseStudentQueryService;
        this.studentProfileService = studentProfileService;
        this.studentMasteryQueryService = studentMasteryQueryService;
        this.repository = repository;
    }

    @Transactional(rollbackFor = Exception.class)
    public LearningPathResponse current(Long courseId) {
        Long studentUserId = educationRoleService.requireCurrentRole("STUDENT");
        CourseResponse course = courseStudentQueryService.getPublishedCourse(courseId);
        LearningPathRepository.PathRow active = repository.getActivePath(studentUserId, courseId);
        if (active == null) {
            int profileVersion = studentProfileService.currentVersion(studentUserId);
            active = generate(studentUserId, course, profileVersion, null,
                "根据课程知识依赖、当前画像和已有掌握度生成首版学习路径", false);
        }
        return response(active, course.getCourseName());
    }

    @Transactional(rollbackFor = Exception.class)
    public LearningPathResponse completeNode(Long courseId, Long nodeId) {
        Long studentUserId = educationRoleService.requireCurrentRole("STUDENT");
        CourseResponse course = courseStudentQueryService.getPublishedCourse(courseId);
        LearningPathRepository.NodeAccessRow node = repository.lockActiveNode(studentUserId, courseId, nodeId);
        if (node == null) throw new BizException(404, "学习路径节点不存在");
        if ("COMPLETED".equals(node.status())) {
            return response(repository.getActivePath(studentUserId, courseId), course.getCourseName());
        }
        if (!"IN_PROGRESS".equals(node.status())) {
            throw new BizException(409, "请按学习路径顺序完成当前节点");
        }
        repository.completeNode(node.pathId(), node.nodeId());
        repository.upsertProgress(IdGenerator.nextId(), studentUserId, courseId, node.knowledgePointId(),
            node.nodeId(), "COMPLETED", 100, "MANUAL");
        LearningPathRepository.NodeAccessRow next = repository.activateNextNode(node.pathId());
        if (next != null) {
            repository.upsertProgress(IdGenerator.nextId(), studentUserId, courseId, next.knowledgePointId(),
                next.nodeId(), "IN_PROGRESS", 0, null);
        }
        return response(repository.getActivePath(studentUserId, courseId), course.getCourseName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void regenerateAfterAssessment(Long studentUserId, Long courseId, Long assessmentAttemptId,
                                          int profileVersionNo) {
        educationRoleService.requireCurrentRole("STUDENT");
        CourseResponse course = courseStudentQueryService.getPublishedCourse(courseId);
        generate(studentUserId, course, profileVersionNo, assessmentAttemptId,
            "完成课程测验后，根据最新掌握度重排薄弱知识点和推荐资源", true);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCoursePaths(Long courseId) {
        repository.deleteCoursePaths(courseId);
    }

    private LearningPathRepository.PathRow generate(Long studentUserId, CourseResponse course,
                                                     int profileVersionNo, Long assessmentAttemptId,
                                                     String generationReason, boolean replaceActive) {
        List<PointSpec> points = flatten(course);
        if (points.isEmpty()) throw new BizException(409, "课程尚未配置知识点");
        Map<Long, BigDecimal> mastery = studentMasteryQueryService.courseMastery(studentUserId, course.getCourseId());
        Map<Long, LearningPathRepository.ProgressRow> progress = repository.listProgress(
            studentUserId, course.getCourseId());
        List<PointSpec> ordered = topologicalOrder(points, course.getDependencies(), mastery);

        if (replaceActive) repository.supersedeActive(studentUserId, course.getCourseId());
        int versionNo = repository.nextVersionNo(studentUserId, course.getCourseId());
        Long pathId = IdGenerator.nextId();
        if (replaceActive) {
            repository.insertPath(pathId, studentUserId, course.getCourseId(), profileVersionNo,
                assessmentAttemptId, versionNo, generationReason);
        } else {
            repository.insertPathIfAbsent(pathId, studentUserId, course.getCourseId(), profileVersionNo,
                versionNo, generationReason);
            LearningPathRepository.PathRow active = repository.getActivePath(studentUserId, course.getCourseId());
            if (active == null) throw new BizException(500, "学习路径初始化失败");
            if (!pathId.equals(active.pathId())) return active;
        }

        Map<Long, Set<Long>> prerequisites = prerequisites(points, course.getDependencies());
        Set<Long> completedPoints = new HashSet<>();
        boolean currentAssigned = false;
        int sequence = 0;
        for (PointSpec point : ordered) {
            BigDecimal score = mastery.get(point.knowledgePointId());
            LearningPathRepository.ProgressRow existing = progress.get(point.knowledgePointId());
            boolean manualCompleted = existing != null && "COMPLETED".equals(existing.status())
                && "MANUAL".equals(existing.completionSource());
            boolean assessmentCompleted = score != null && score.compareTo(COMPLETION_MASTERY) >= 0;
            String status;
            String completionSource = null;
            if (manualCompleted || assessmentCompleted) {
                status = "COMPLETED";
                completionSource = manualCompleted ? "MANUAL" : "ASSESSMENT";
                completedPoints.add(point.knowledgePointId());
            } else {
                boolean prerequisiteCompleted = completedPoints.containsAll(
                    prerequisites.getOrDefault(point.knowledgePointId(), Set.of()));
                if (!currentAssigned && prerequisiteCompleted) {
                    status = "IN_PROGRESS";
                    currentAssigned = true;
                } else {
                    status = prerequisiteCompleted ? "PENDING" : "LOCKED";
                }
            }
            int estimatedMinutes = estimatedMinutes(score);
            String reason = recommendationReason(point, score, prerequisites, points);
            Long nodeId = IdGenerator.nextId();
            int sequenceNo = ++sequence;
            repository.insertNode(nodeId, pathId, point.knowledgePointId(), point.chapterId(), sequenceNo,
                learningObjective(point), "COURSE_ARTICLE", point.chapterId(), point.chapterName(),
                estimatedMinutes, COMPLETION_CONDITION, status, score, reason);
            repository.insertRecommendation(IdGenerator.nextId(), pathId, nodeId, studentUserId,
                course.getCourseId(), point.knowledgePointId(), "COURSE_ARTICLE", point.chapterId(),
                point.chapterName(), sequenceNo, reason, "COMPLETED".equals(status) ? "COMPLETED" : "ACTIVE");
            int progressPercent = "COMPLETED".equals(status) ? 100
                : score == null ? 0 : Math.max(0, Math.min(79, score.intValue()));
            repository.upsertProgress(IdGenerator.nextId(), studentUserId, course.getCourseId(),
                point.knowledgePointId(), nodeId, status, progressPercent, completionSource);
        }
        LearningPathRepository.PathRow active = repository.getActivePath(studentUserId, course.getCourseId());
        if (active == null) throw new BizException(500, "学习路径生成失败");
        return active;
    }

    private LearningPathResponse response(LearningPathRepository.PathRow path, String courseName) {
        if (path == null) throw new BizException(404, "学习路径不存在");
        List<LearningPathResponse.Node> nodes = repository.listNodes(path.pathId()).stream().map(row ->
            new LearningPathResponse.Node(row.nodeId(), row.sequenceNo(), row.knowledgePointId(),
                row.knowledgePointName(), row.chapterId(), row.chapterName(), row.learningObjective(),
                row.resourceType(), row.resourceRefId(), row.resourceTitle(), row.estimatedMinutes(),
                row.completionCondition(), row.nodeStatus(), row.masteryScore(), row.recommendationReason()))
            .toList();
        List<LearningPathResponse.Recommendation> recommendations = repository.listRecommendations(path.pathId())
            .stream().map(row -> new LearningPathResponse.Recommendation(row.recommendationId(), row.nodeId(),
                row.knowledgePointId(), row.knowledgePointName(), row.resourceType(), row.resourceRefId(),
                row.resourceTitle(), row.priorityNo(), row.reason(), row.status())).toList();
        long completed = nodes.stream().filter(node -> "COMPLETED".equals(node.status())).count();
        int progressPercent = nodes.isEmpty() ? 0 : Math.round(completed * 100F / nodes.size());
        Long currentNodeId = nodes.stream().filter(node -> "IN_PROGRESS".equals(node.status()))
            .map(LearningPathResponse.Node::nodeId).findFirst().orElse(null);
        List<LearningPathResponse.Node> today = nodes.stream()
            .filter(node -> "IN_PROGRESS".equals(node.status()) || "PENDING".equals(node.status()))
            .limit(3).toList();
        return new LearningPathResponse(path.pathId(), path.courseId(), courseName, path.versionNo(),
            path.profileVersionNo(), path.sourceAssessmentAttemptId(), path.generationReason(),
            progressPercent, currentNodeId, path.createdAt(), today, nodes, recommendations);
    }

    private List<PointSpec> flatten(CourseResponse course) {
        List<PointSpec> points = new ArrayList<>();
        for (CourseResponse.Chapter chapter : course.getChapters()) {
            for (CourseResponse.KnowledgePoint point : chapter.getKnowledgePoints()) {
                points.add(new PointSpec(point.getKnowledgePointId(), point.getPointCode(), point.getPointName(),
                    point.getSummary(), point.getSortOrder(), chapter.getChapterId(), chapter.getChapterNo(),
                    chapter.getChapterName()));
            }
        }
        return points;
    }

    private List<PointSpec> topologicalOrder(List<PointSpec> points,
                                             List<CourseResponse.Dependency> dependencies,
                                             Map<Long, BigDecimal> mastery) {
        Map<String, PointSpec> byCode = new LinkedHashMap<>();
        Map<Long, Integer> incoming = new HashMap<>();
        Map<Long, List<Long>> outgoing = new HashMap<>();
        for (PointSpec point : points) {
            byCode.put(point.pointCode(), point);
            incoming.put(point.knowledgePointId(), 0);
        }
        for (CourseResponse.Dependency dependency : dependencies) {
            PointSpec prerequisite = byCode.get(dependency.getPrerequisitePointCode());
            PointSpec dependent = byCode.get(dependency.getDependentPointCode());
            if (prerequisite == null || dependent == null) continue;
            outgoing.computeIfAbsent(prerequisite.knowledgePointId(), key -> new ArrayList<>())
                .add(dependent.knowledgePointId());
            incoming.computeIfPresent(dependent.knowledgePointId(), (key, value) -> value + 1);
        }
        Map<Long, PointSpec> byId = points.stream().collect(java.util.stream.Collectors.toMap(
            PointSpec::knowledgePointId, item -> item));
        Comparator<PointSpec> comparator = Comparator
            .comparing((PointSpec point) -> mastery.getOrDefault(point.knowledgePointId(), BigDecimal.ZERO))
            .thenComparing(PointSpec::chapterNo)
            .thenComparing(PointSpec::sortOrder)
            .thenComparing(PointSpec::knowledgePointId);
        PriorityQueue<PointSpec> available = new PriorityQueue<>(comparator);
        points.stream().filter(point -> incoming.get(point.knowledgePointId()) == 0).forEach(available::add);
        List<PointSpec> ordered = new ArrayList<>();
        while (!available.isEmpty()) {
            PointSpec point = available.poll();
            ordered.add(point);
            for (Long nextId : outgoing.getOrDefault(point.knowledgePointId(), List.of())) {
                int remaining = incoming.computeIfPresent(nextId, (key, value) -> value - 1);
                if (remaining == 0) available.add(byId.get(nextId));
            }
        }
        if (ordered.size() != points.size()) throw new BizException(500, "课程知识依赖存在环路");
        return ordered;
    }

    private Map<Long, Set<Long>> prerequisites(List<PointSpec> points,
                                                List<CourseResponse.Dependency> dependencies) {
        Map<String, PointSpec> byCode = points.stream().collect(java.util.stream.Collectors.toMap(
            PointSpec::pointCode, item -> item));
        Map<Long, Set<Long>> result = new HashMap<>();
        for (CourseResponse.Dependency dependency : dependencies) {
            PointSpec prerequisite = byCode.get(dependency.getPrerequisitePointCode());
            PointSpec dependent = byCode.get(dependency.getDependentPointCode());
            if (prerequisite != null && dependent != null) {
                result.computeIfAbsent(dependent.knowledgePointId(), key -> new LinkedHashSet<>())
                    .add(prerequisite.knowledgePointId());
            }
        }
        return result;
    }

    private String recommendationReason(PointSpec point, BigDecimal mastery,
                                        Map<Long, Set<Long>> prerequisites, List<PointSpec> points) {
        String reason;
        if (mastery == null) {
            reason = "尚无该知识点评估记录，建议先通过课程文章建立基础";
        } else if (mastery.compareTo(new BigDecimal("60")) < 0) {
            reason = "当前掌握度为 " + mastery.intValue() + "%，属于薄弱知识点，需优先巩固";
        } else if (mastery.compareTo(COMPLETION_MASTERY) < 0) {
            reason = "当前掌握度为 " + mastery.intValue() + "%，建议通过课程文章和练习继续巩固";
        } else {
            reason = "当前掌握度为 " + mastery.intValue() + "%，已达到完成条件，可快速复习后进入后续知识点";
        }
        Set<Long> prerequisiteIds = prerequisites.getOrDefault(point.knowledgePointId(), Set.of());
        if (!prerequisiteIds.isEmpty()) {
            Map<Long, String> names = points.stream().collect(java.util.stream.Collectors.toMap(
                PointSpec::knowledgePointId, PointSpec::pointName));
            String prerequisiteNames = prerequisiteIds.stream().map(names::get).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining("、"));
            if (!prerequisiteNames.isBlank()) reason += "；建议在完成“" + prerequisiteNames + "”后学习";
        }
        return reason + "。";
    }

    private String learningObjective(PointSpec point) {
        return point.summary() == null || point.summary().isBlank()
            ? "理解并能应用“" + point.pointName() + "”"
            : point.summary();
    }

    private int estimatedMinutes(BigDecimal mastery) {
        if (mastery == null) return 35;
        if (mastery.compareTo(COMPLETION_MASTERY) >= 0) return 15;
        if (mastery.compareTo(new BigDecimal("60")) >= 0) return 25;
        return 40;
    }

    private record PointSpec(Long knowledgePointId, String pointCode, String pointName, String summary,
                             int sortOrder, Long chapterId, int chapterNo, String chapterName) {
    }
}
