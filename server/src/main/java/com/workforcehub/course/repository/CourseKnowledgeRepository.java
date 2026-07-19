package com.workforcehub.course.repository;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.course.dto.CourseKnowledgeDocumentResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.sql.SQLException;

@Repository
public class CourseKnowledgeRepository {
    private final JdbcTemplate jdbcTemplate;

    public CourseKnowledgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lockCourse(Long courseId) {
        List<Long> ids = jdbcTemplate.queryForList(
            "SELECT id FROM edu_course WHERE id = ? AND is_deleted = 0 FOR UPDATE", Long.class, courseId);
        if (ids.isEmpty()) throw new BizException(404, "课程不存在");
    }

    public Long findSpaceId(Long courseId) {
        return jdbcTemplate.query("SELECT kb_space_id FROM edu_course_knowledge_space WHERE course_id = ?",
            rs -> rs.next() ? rs.getLong("kb_space_id") : null, courseId);
    }

    public void bindSpace(Long courseId, Long spaceId, Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO edu_course_knowledge_space (course_id, kb_space_id, created_by)
            VALUES (?, ?, ?)
            """, courseId, spaceId, operatorId);
    }

    public Long findBindingId(Long courseId, Long attachmentRefId) {
        return jdbcTemplate.query("""
            SELECT id FROM edu_course_knowledge_document
            WHERE course_id = ? AND attachment_ref_id = ?
            """, rs -> rs.next() ? rs.getLong("id") : null, courseId, attachmentRefId);
    }

    public void validateRelations(Long courseId, Long chapterId, List<Long> knowledgePointIds) {
        if (chapterId != null) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edu_course_chapter WHERE id = ? AND course_id = ?",
                Integer.class, chapterId, courseId);
            if (count == null || count == 0) throw new BizException(400, "章节不属于当前课程");
        }
        if (knowledgePointIds.isEmpty()) return;
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM edu_knowledge_point
            WHERE course_id = ? AND id IN (%s)
            """.formatted(String.join(",", java.util.Collections.nCopies(knowledgePointIds.size(), "?"))),
            Integer.class, parameters(courseId, knowledgePointIds));
        if (count == null || count != knowledgePointIds.size()) {
            throw new BizException(400, "知识点不属于当前课程");
        }
    }

    public void bindDocument(Long bindingId, Long courseId, Long documentId, Long attachmentRefId,
                             Long chapterId, List<Long> knowledgePointIds, Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO edu_course_knowledge_document
                (id, course_id, kb_document_id, attachment_ref_id, chapter_id, created_by)
            VALUES (?, ?, ?, ?, ?, ?)
            """, bindingId, courseId, documentId, attachmentRefId, chapterId, operatorId);
        for (Long knowledgePointId : knowledgePointIds) {
            jdbcTemplate.update("""
                INSERT INTO edu_course_knowledge_document_point (document_binding_id, knowledge_point_id)
                VALUES (?, ?)
                """, bindingId, knowledgePointId);
        }
    }

    public Map<Long, Relation> listRelations(Long courseId) {
        Map<Long, Relation> relations = new LinkedHashMap<>();
        jdbcTemplate.query("""
            SELECT binding.kb_document_id, binding.chapter_id, chapter.chapter_name,
                   point.id knowledge_point_id, point.point_name
            FROM edu_course_knowledge_document binding
            LEFT JOIN edu_course_chapter chapter ON chapter.id = binding.chapter_id
            LEFT JOIN edu_course_knowledge_document_point relation ON relation.document_binding_id = binding.id
            LEFT JOIN edu_knowledge_point point ON point.id = relation.knowledge_point_id
            WHERE binding.course_id = ?
            ORDER BY binding.created_at DESC, point.sort_order, point.id
            """, rs -> {
            try {
                Relation relation = relations.computeIfAbsent(rs.getLong("kb_document_id"), id -> {
                    try {
                        return new Relation(rs.getObject("chapter_id", Long.class), rs.getString("chapter_name"),
                            new ArrayList<>());
                    } catch (SQLException exception) {
                        throw new IllegalStateException(exception);
                    }
                });
                Long pointId = rs.getObject("knowledge_point_id", Long.class);
                if (pointId != null) {
                    CourseKnowledgeDocumentResponse.KnowledgePoint point = new CourseKnowledgeDocumentResponse.KnowledgePoint();
                    point.setKnowledgePointId(pointId);
                    point.setPointName(rs.getString("point_name"));
                    relation.knowledgePoints().add(point);
                }
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }, courseId);
        return relations;
    }

    public void deleteCourseBindings(Long courseId) {
        jdbcTemplate.update("""
            DELETE FROM edu_course_knowledge_document_point
            WHERE document_binding_id IN (
                SELECT id FROM edu_course_knowledge_document WHERE course_id = ?
            )
            """, courseId);
        jdbcTemplate.update("DELETE FROM edu_course_knowledge_document WHERE course_id = ?", courseId);
        jdbcTemplate.update("DELETE FROM edu_course_knowledge_space WHERE course_id = ?", courseId);
    }

    private Object[] parameters(Long courseId, List<Long> ids) {
        List<Object> parameters = new ArrayList<>();
        parameters.add(courseId);
        parameters.addAll(ids);
        return parameters.toArray();
    }

    public record Relation(Long chapterId, String chapterName,
                           List<CourseKnowledgeDocumentResponse.KnowledgePoint> knowledgePoints) {
    }
}
