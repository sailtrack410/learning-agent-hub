package com.workforcehub.course.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.course.dto.CourseKnowledgeDocumentRequest;
import com.workforcehub.course.dto.CourseKnowledgeDocumentResponse;
import com.workforcehub.course.dto.CourseResponse;
import com.workforcehub.course.repository.CourseKnowledgeRepository;
import com.workforcehub.course.repository.CourseRepository;
import com.workforcehub.ragflow.dto.RagflowDocumentResponse;
import com.workforcehub.ragflow.service.RagflowCourseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class CourseKnowledgeService {
    private final CourseResourceAccessService courseResourceAccessService;
    private final CourseRepository courseRepository;
    private final CourseKnowledgeRepository courseKnowledgeRepository;
    private final RagflowCourseService ragflowCourseService;

    public CourseKnowledgeService(CourseResourceAccessService courseResourceAccessService,
                                  CourseRepository courseRepository,
                                  CourseKnowledgeRepository courseKnowledgeRepository,
                                  RagflowCourseService ragflowCourseService) {
        this.courseResourceAccessService = courseResourceAccessService;
        this.courseRepository = courseRepository;
        this.courseKnowledgeRepository = courseKnowledgeRepository;
        this.ragflowCourseService = ragflowCourseService;
    }

    public List<CourseKnowledgeDocumentResponse> listDocuments(Long courseId) {
        Long userId = CurrentUserHolder.requireUserId();
        courseResourceAccessService.requireTeacherCourse(userId, courseId);
        Long spaceId = courseKnowledgeRepository.findSpaceId(courseId);
        if (spaceId == null) return List.of();
        return responses(courseId, ragflowCourseService.listDocuments(spaceId));
    }

    @Transactional(rollbackFor = Exception.class)
    public CourseKnowledgeDocumentResponse addDocument(Long courseId, CourseKnowledgeDocumentRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        courseResourceAccessService.requireCourseResource(userId, courseId, request.getAttachmentRefId());
        List<Long> knowledgePointIds = normalizedPointIds(request.getKnowledgePointIds());
        courseKnowledgeRepository.validateRelations(courseId, request.getChapterId(), knowledgePointIds);
        Long existingBindingId = courseKnowledgeRepository.findBindingId(courseId, request.getAttachmentRefId());
        if (existingBindingId != null) {
            return listDocuments(courseId).stream()
                .filter(item -> request.getAttachmentRefId().equals(item.getAttachmentRefId()))
                .findFirst()
                .orElseThrow(() -> new BizException(409, "课程资料已入库"));
        }
        courseKnowledgeRepository.lockCourse(courseId);
        existingBindingId = courseKnowledgeRepository.findBindingId(courseId, request.getAttachmentRefId());
        if (existingBindingId != null) {
            return listDocuments(courseId).stream()
                .filter(item -> request.getAttachmentRefId().equals(item.getAttachmentRefId()))
                .findFirst()
                .orElseThrow(() -> new BizException(409, "课程资料已入库"));
        }
        Long spaceId = courseKnowledgeRepository.findSpaceId(courseId);
        if (spaceId == null) {
            CourseResponse course = courseRepository.getTeacherCourse(courseId, userId);
            spaceId = ragflowCourseService.createSpace(courseId, course.getCourseName(), userId);
            courseKnowledgeRepository.bindSpace(courseId, spaceId, userId);
        }
        RagflowDocumentResponse document = ragflowCourseService.addDocument(
            courseId, spaceId, request.getAttachmentRefId(), userId);
        courseKnowledgeRepository.bindDocument(IdGenerator.nextId(), courseId, document.getDocumentId(),
            request.getAttachmentRefId(), request.getChapterId(), knowledgePointIds, userId);
        return responses(courseId, List.of(document)).get(0);
    }

    public CourseKnowledgeDocumentResponse retry(Long courseId, Long documentId) {
        Long userId = CurrentUserHolder.requireUserId();
        courseResourceAccessService.requireTeacherCourse(userId, courseId);
        Long spaceId = courseKnowledgeRepository.findSpaceId(courseId);
        if (spaceId == null || !courseKnowledgeRepository.listRelations(courseId).containsKey(documentId)) {
            throw new BizException(404, "课程资料不存在");
        }
        return responses(courseId, List.of(ragflowCourseService.reingestDocument(spaceId, documentId, userId))).get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCourseKnowledge(Long courseId, Long operatorId) {
        Long spaceId = courseKnowledgeRepository.findSpaceId(courseId);
        if (spaceId == null) return;
        ragflowCourseService.deleteSpace(spaceId, operatorId);
        courseKnowledgeRepository.deleteCourseBindings(courseId);
    }

    private List<CourseKnowledgeDocumentResponse> responses(Long courseId, List<RagflowDocumentResponse> documents) {
        Map<Long, CourseKnowledgeRepository.Relation> relations = courseKnowledgeRepository.listRelations(courseId);
        return documents.stream().filter(document -> relations.containsKey(document.getDocumentId())).map(document -> {
            CourseKnowledgeRepository.Relation relation = relations.get(document.getDocumentId());
            CourseKnowledgeDocumentResponse response = new CourseKnowledgeDocumentResponse();
            response.setDocumentId(document.getDocumentId());
            response.setAttachmentRefId(document.getAttachmentRefId());
            response.setTitle(document.getTitle());
            response.setMimeType(document.getMimeType());
            response.setFileSize(document.getFileSize());
            response.setParserTypeName(document.getParserTypeName());
            response.setIngestStatus(document.getIngestStatus());
            response.setIngestStatusName(document.getIngestStatusName());
            response.setIngestProgressPercent(document.getIngestProgressPercent());
            response.setIngestStage(document.getIngestStage());
            response.setIngestStageName(document.getIngestStageName());
            response.setVectorStatus(document.getVectorStatus());
            response.setVectorStatusName(document.getVectorStatusName());
            response.setChunkCount(document.getChunkCount());
            response.setErrorMessage(document.getErrorMessage());
            response.setChapterId(relation.chapterId());
            response.setChapterName(relation.chapterName());
            response.setKnowledgePoints(relation.knowledgePoints());
            response.setCreatedAt(document.getCreatedAt());
            response.setUpdatedAt(document.getUpdatedAt());
            return response;
        }).toList();
    }

    private List<Long> normalizedPointIds(List<Long> ids) {
        if (ids == null) return List.of();
        if (ids.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BizException(400, "知识点不能为空");
        }
        return List.copyOf(new LinkedHashSet<>(ids));
    }
}
