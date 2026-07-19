package com.workforcehub.course.service;

import com.workforcehub.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CourseResourceAccessService {
    private final JdbcTemplate jdbcTemplate;

    public CourseResourceAccessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void requireTeacherCourse(Long userId, Long courseId) {
        if (!"TEACHER".equals(findEducationRole(userId))) {
            throw new BizException(403, "仅教师可以上传课程资源");
        }
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM edu_course course
            JOIN edu_course_teacher member ON member.course_id = course.id AND member.user_id = ?
            WHERE course.id = ? AND course.is_deleted = 0
            """,
            Integer.class,
            userId,
            courseId
        );
        if (count == null || count == 0) {
            throw new BizException(404, "课程不存在");
        }
    }

    public void requireReadableResource(Long userId, Long attachmentRefId) {
        String role = findEducationRole(userId);
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_attachment_ref attachment
            JOIN edu_course course ON course.id = attachment.owner_id AND course.is_deleted = 0
            WHERE attachment.id = ?
              AND attachment.owner_type = 'COURSE_RESOURCE'
              AND attachment.is_deleted = 0
              AND (
                  (? = 'TEACHER' AND EXISTS (
                      SELECT 1 FROM edu_course_teacher member
                      WHERE member.course_id = course.id AND member.user_id = ?
                  ))
                  OR (
                      ? = 'STUDENT'
                      AND course.status = 'PUBLISHED'
                      AND (
                          course.cover_attachment_ref_id = attachment.id
                          OR EXISTS (
                              SELECT 1 FROM edu_course_chapter chapter
                              WHERE chapter.course_id = course.id
                                AND chapter.status = 'PUBLISHED'
                                AND (
                                    chapter.resource_attachment_ref_id = attachment.id
                                    OR chapter.content_json REGEXP CONCAT(
                                        '"attachmentRefId"[[:space:]]*:[[:space:]]*"?',
                                        attachment.id,
                                        '"?([,}])'
                                    )
                                )
                          )
                      )
                  )
              )
            """, Integer.class, attachmentRefId, role, userId, role);
        if (count == null || count == 0) {
            throw new BizException(403, "无权访问该课程资源");
        }
    }

    public void requireCourseResource(Long userId, Long courseId, Long attachmentRefId) {
        requireTeacherCourse(userId, courseId);
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_attachment_ref
            WHERE id = ? AND owner_type = 'COURSE_RESOURCE' AND owner_id = ?
              AND is_deleted = 0
            """, Integer.class, attachmentRefId, courseId);
        if (count == null || count == 0) {
            throw new BizException(400, "课程资源与当前课程不匹配");
        }
    }

    private String findEducationRole(Long userId) {
        return jdbcTemplate.query("SELECT education_role FROM edu_user_role WHERE user_id = ?",
            rs -> rs.next() ? rs.getString("education_role") : null,
            userId);
    }
}
