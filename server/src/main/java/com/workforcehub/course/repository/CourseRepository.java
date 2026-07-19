package com.workforcehub.course.repository;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.course.dto.CourseResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class CourseRepository {
    private final JdbcTemplate jdbcTemplate;

    public CourseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsCourseCode(String courseCode, Long excludedCourseId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM edu_course
            WHERE course_code = ? AND is_deleted = 0 AND (? IS NULL OR id <> ?)
            """, Long.class, courseCode, excludedCourseId, excludedCourseId);
        return count != null && count > 0;
    }

    public List<CourseResponse> listCourses(boolean publishedOnly) {
        return jdbcTemplate.query("""
            SELECT c.id, c.course_code, c.course_name, c.summary, c.learning_objectives,
                   c.prerequisites, c.teacher_name, c.cover_attachment_ref_id, c.total_hours, c.status, c.created_at, c.updated_at,
                   COUNT(DISTINCT chapter.id) chapter_count,
                   COUNT(DISTINCT point.id) knowledge_point_count
            FROM edu_course c
            LEFT JOIN edu_course_chapter chapter ON chapter.course_id = c.id
                AND (? = 0 OR chapter.status = 'PUBLISHED')
            LEFT JOIN edu_knowledge_point point ON point.chapter_id = chapter.id
            WHERE c.is_deleted = 0
              AND (? = 0 OR c.status = 'PUBLISHED')
            GROUP BY c.id, c.course_code, c.course_name, c.summary, c.learning_objectives,
                     c.prerequisites, c.teacher_name, c.cover_attachment_ref_id, c.total_hours, c.status, c.created_at, c.updated_at
            ORDER BY c.updated_at DESC, c.id DESC
            """, this::mapCourse, publishedOnly ? 1 : 0, publishedOnly ? 1 : 0);
    }

    public Set<Long> listEnrolledCourseIds(Long studentUserId) {
        return new java.util.LinkedHashSet<>(jdbcTemplate.queryForList("""
            SELECT course_id
            FROM edu_course_enrollment
            WHERE student_user_id = ? AND status = 'ENROLLED'
            """, Long.class, studentUserId));
    }

    public boolean isStudentEnrolled(Long courseId, Long studentUserId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM edu_course_enrollment
            WHERE course_id = ? AND student_user_id = ? AND status = 'ENROLLED'
            """, Long.class, courseId, studentUserId);
        return count != null && count > 0;
    }

    public void enrollStudent(Long courseId, Long studentUserId) {
        jdbcTemplate.update("""
            INSERT INTO edu_course_enrollment (course_id, student_user_id, status)
            VALUES (?, ?, 'ENROLLED')
            ON DUPLICATE KEY UPDATE status = 'ENROLLED'
            """, courseId, studentUserId);
    }

    public List<CourseResponse> listTeacherCourses(Long userId) {
        List<CourseResponse> courses = jdbcTemplate.query("""
            SELECT c.id, c.course_code, c.course_name, c.summary, c.learning_objectives,
                   c.prerequisites, c.teacher_name, c.cover_attachment_ref_id, c.total_hours, c.status, c.created_at, c.updated_at,
                   member.member_role teacher_role,
                   COUNT(DISTINCT chapter.id) chapter_count,
                   COUNT(DISTINCT point.id) knowledge_point_count
            FROM edu_course c
            JOIN edu_course_teacher member ON member.course_id = c.id AND member.user_id = ?
            LEFT JOIN edu_course_chapter chapter ON chapter.course_id = c.id
            LEFT JOIN edu_knowledge_point point ON point.chapter_id = chapter.id
            WHERE c.is_deleted = 0
            GROUP BY c.id, c.course_code, c.course_name, c.summary, c.learning_objectives,
                     c.prerequisites, c.teacher_name, c.cover_attachment_ref_id, c.total_hours, c.status, c.created_at, c.updated_at,
                     member.member_role
            ORDER BY c.updated_at DESC, c.id DESC
            """, this::mapCourse, userId);
        courses.forEach(this::loadTeachers);
        return courses;
    }

    public CourseResponse getTeacherCourse(Long courseId, Long userId) {
        List<CourseResponse> courses = jdbcTemplate.query("""
            SELECT c.id, c.course_code, c.course_name, c.summary, c.learning_objectives,
                   c.prerequisites, c.teacher_name, c.cover_attachment_ref_id, c.total_hours, c.status, c.created_at, c.updated_at,
                   member.member_role teacher_role,
                   COUNT(DISTINCT chapter.id) chapter_count,
                   COUNT(DISTINCT point.id) knowledge_point_count
            FROM edu_course c
            JOIN edu_course_teacher member ON member.course_id = c.id AND member.user_id = ?
            LEFT JOIN edu_course_chapter chapter ON chapter.course_id = c.id
            LEFT JOIN edu_knowledge_point point ON point.chapter_id = chapter.id
            WHERE c.id = ? AND c.is_deleted = 0
            GROUP BY c.id, c.course_code, c.course_name, c.summary, c.learning_objectives,
                     c.prerequisites, c.teacher_name, c.cover_attachment_ref_id, c.total_hours, c.status, c.created_at, c.updated_at,
                     member.member_role
            """, this::mapCourse, userId, courseId);
        if (courses.isEmpty()) throw new BizException(404, "课程不存在或无权管理");
        CourseResponse course = courses.get(0);
        loadOutline(course, false);
        loadTeachers(course);
        return course;
    }

    public CourseResponse getCourse(Long courseId, boolean publishedOnly) {
        List<CourseResponse> courses = jdbcTemplate.query("""
            SELECT c.id, c.course_code, c.course_name, c.summary, c.learning_objectives,
                   c.prerequisites, c.teacher_name, c.cover_attachment_ref_id, c.total_hours, c.status, c.created_at, c.updated_at,
                   COUNT(DISTINCT chapter.id) chapter_count,
                   COUNT(DISTINCT point.id) knowledge_point_count
            FROM edu_course c
            LEFT JOIN edu_course_chapter chapter ON chapter.course_id = c.id
                AND (? = 0 OR chapter.status = 'PUBLISHED')
            LEFT JOIN edu_knowledge_point point ON point.chapter_id = chapter.id
            WHERE c.id = ? AND c.is_deleted = 0
              AND (? = 0 OR c.status = 'PUBLISHED')
            GROUP BY c.id, c.course_code, c.course_name, c.summary, c.learning_objectives,
                     c.prerequisites, c.teacher_name, c.cover_attachment_ref_id, c.total_hours, c.status, c.created_at, c.updated_at
            """, this::mapCourse, publishedOnly ? 1 : 0, courseId, publishedOnly ? 1 : 0);
        if (courses.isEmpty()) throw new BizException(404, "课程不存在");
        CourseResponse course = courses.get(0);
        loadOutline(course, publishedOnly);
        return course;
    }

    public void insertCourse(Long courseId, String courseCode, String courseName, String summary,
                             String learningObjectives, String prerequisites, String teacherName,
                             Long coverAttachmentRefId, Integer totalHours, String status, Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO edu_course
                (id, course_code, course_name, summary, learning_objectives, prerequisites,
                 teacher_name, cover_attachment_ref_id, total_hours, status, created_by, updated_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, courseId, courseCode, courseName, summary, learningObjectives, prerequisites,
            teacherName, coverAttachmentRefId, totalHours, status, operatorId, operatorId);
    }

    public void insertCourseTeacher(Long memberId, Long courseId, Long userId, String memberRole, Long invitedBy) {
        jdbcTemplate.update("""
            INSERT INTO edu_course_teacher (id, course_id, user_id, member_role, invited_by)
            VALUES (?, ?, ?, ?, ?)
            """, memberId, courseId, userId, memberRole, invitedBy);
    }

    public boolean isCourseOwner(Long courseId, Long userId) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM edu_course_teacher
            WHERE course_id = ? AND user_id = ? AND member_role = 'OWNER'
            """, Integer.class, courseId, userId);
        return count != null && count > 0;
    }

    public void insertInvitation(Long invitationId, Long courseId, String tokenHash, Long invitedBy,
                                 LocalDateTime expiresAt) {
        jdbcTemplate.update("""
            INSERT INTO edu_course_invitation (id, course_id, token_hash, invited_by, expires_at)
            VALUES (?, ?, ?, ?, ?)
            """, invitationId, courseId, tokenHash, invitedBy, expiresAt);
    }

    public InvitationRow lockInvitation(String tokenHash) {
        List<InvitationRow> rows = jdbcTemplate.query("""
            SELECT id, course_id, status, invited_by, expires_at, accepted_by
            FROM edu_course_invitation
            WHERE token_hash = ?
            FOR UPDATE
            """, (rs, rowNum) -> new InvitationRow(
                rs.getLong("id"),
                rs.getLong("course_id"),
                rs.getString("status"),
                rs.getLong("invited_by"),
                rs.getTimestamp("expires_at").toLocalDateTime(),
                rs.getObject("accepted_by", Long.class)
            ), tokenHash);
        if (rows.isEmpty()) throw new BizException(404, "课程邀请无效");
        return rows.get(0);
    }

    public boolean isCourseTeacher(Long courseId, Long userId) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM edu_course_teacher WHERE course_id = ? AND user_id = ?
            """, Integer.class, courseId, userId);
        return count != null && count > 0;
    }

    public void acceptInvitation(Long invitationId, Long userId, LocalDateTime acceptedAt) {
        jdbcTemplate.update("""
            UPDATE edu_course_invitation
            SET status = 'ACCEPTED', accepted_by = ?, accepted_at = ?
            WHERE id = ? AND status = 'ACTIVE'
            """, userId, acceptedAt, invitationId);
    }

    public void updateCourse(Long courseId, String courseCode, String courseName, String summary,
                             String learningObjectives, String prerequisites, String teacherName,
                             Long coverAttachmentRefId, Integer totalHours, String status, Long operatorId) {
        int updated = jdbcTemplate.update("""
            UPDATE edu_course
            SET course_code = ?, course_name = ?, summary = ?, learning_objectives = ?, prerequisites = ?,
                teacher_name = ?, cover_attachment_ref_id = ?, total_hours = ?, status = ?, updated_by = ?
            WHERE id = ? AND is_deleted = 0
            """, courseCode, courseName, summary, learningObjectives, prerequisites,
            teacherName, coverAttachmentRefId, totalHours, status, operatorId, courseId);
        if (updated == 0) throw new BizException(404, "课程不存在");
    }

    public void deleteCourse(Long courseId) {
        jdbcTemplate.update("DELETE FROM edu_course_invitation WHERE course_id = ?", courseId);
        jdbcTemplate.update("DELETE FROM edu_course_teacher WHERE course_id = ?", courseId);
        int updated = jdbcTemplate.update("DELETE FROM edu_course WHERE id = ? AND is_deleted = 0", courseId);
        if (updated == 0) throw new BizException(404, "课程不存在");
    }

    public void clearOutline(Long courseId) {
        jdbcTemplate.update("DELETE FROM edu_knowledge_dependency WHERE course_id = ?", courseId);
        jdbcTemplate.update("DELETE FROM edu_knowledge_point WHERE course_id = ?", courseId);
        jdbcTemplate.update("DELETE FROM edu_course_chapter WHERE course_id = ?", courseId);
    }

    public List<Long> listChapterIds(Long courseId) {
        return jdbcTemplate.queryForList("SELECT id FROM edu_course_chapter WHERE course_id = ?", Long.class, courseId);
    }

    public void clearOutlineRelations(Long courseId) {
        jdbcTemplate.update("DELETE FROM edu_knowledge_dependency WHERE course_id = ?", courseId);
        jdbcTemplate.update("DELETE FROM edu_knowledge_point WHERE course_id = ?", courseId);
        jdbcTemplate.update("UPDATE edu_course_chapter SET chapter_no = chapter_no + 100000 WHERE course_id = ?", courseId);
    }

    public void deleteChapter(Long courseId, Long chapterId) {
        jdbcTemplate.update("DELETE FROM edu_course_chapter WHERE course_id = ? AND id = ?", courseId, chapterId);
    }

    public void insertChapter(Long chapterId, Long courseId, int chapterNo, String chapterName, String summary,
                              boolean existing) {
        if (existing) {
            jdbcTemplate.update("""
                UPDATE edu_course_chapter
                SET chapter_no = ?, chapter_name = ?, summary = ?
                WHERE id = ? AND course_id = ?
                """, chapterNo, chapterName, summary, chapterId, courseId);
            return;
        }
        jdbcTemplate.update("""
            INSERT INTO edu_course_chapter
                (id, course_id, chapter_no, chapter_name, summary, content_type, content_json, status)
            VALUES (?, ?, ?, ?, ?, 'ARTICLE', '{"type":"doc","content":[{"type":"paragraph"}]}', 'DRAFT')
            """, chapterId, courseId, chapterNo, chapterName, summary);
    }

    public void updateArticle(Long courseId, Long chapterId, String title, String summary,
                              String contentJson, String status) {
        int updated = jdbcTemplate.update("""
            UPDATE edu_course_chapter
            SET chapter_name = ?, summary = ?, content_type = 'ARTICLE', content_json = ?, status = ?
            WHERE id = ? AND course_id = ?
            """, title, summary, contentJson, status, chapterId, courseId);
        if (updated == 0) throw new BizException(404, "课程文章不存在");
    }

    public void insertKnowledgePoint(Long pointId, Long courseId, Long chapterId, String pointCode,
                                     String pointName, String summary, int sortOrder) {
        jdbcTemplate.update("""
            INSERT INTO edu_knowledge_point
                (id, course_id, chapter_id, point_code, point_name, summary, sort_order)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, pointId, courseId, chapterId, pointCode, pointName, summary, sortOrder);
    }

    public void insertDependency(Long dependencyId, Long courseId, Long prerequisitePointId, Long dependentPointId) {
        jdbcTemplate.update("""
            INSERT INTO edu_knowledge_dependency
                (id, course_id, prerequisite_point_id, dependent_point_id)
            VALUES (?, ?, ?, ?)
            """, dependencyId, courseId, prerequisitePointId, dependentPointId);
    }

    private void loadOutline(CourseResponse course, boolean publishedOnly) {
        Map<Long, CourseResponse.Chapter> chapters = new LinkedHashMap<>();
        jdbcTemplate.query("""
            SELECT chapter.id chapter_id, chapter.chapter_no, chapter.chapter_name, chapter.summary chapter_summary,
                   chapter.content_type, chapter.content_json, chapter.status chapter_status,
                   point.id point_id, point.point_code, point.point_name, point.summary point_summary, point.sort_order
            FROM edu_course_chapter chapter
            LEFT JOIN edu_knowledge_point point ON point.chapter_id = chapter.id
            WHERE chapter.course_id = ? AND (? = 0 OR chapter.status = 'PUBLISHED')
            ORDER BY chapter.chapter_no, point.sort_order, point.id
            """, rs -> {
            CourseResponse.Chapter chapter = chapters.computeIfAbsent(rs.getLong("chapter_id"), id -> {
                CourseResponse.Chapter item = new CourseResponse.Chapter();
                item.setChapterId(id);
                try {
                    item.setChapterNo(rs.getInt("chapter_no"));
                    item.setChapterName(rs.getString("chapter_name"));
                    item.setSummary(rs.getString("chapter_summary"));
                    item.setContentType(rs.getString("content_type"));
                    item.setContentJson(rs.getString("content_json"));
                    item.setStatus(rs.getString("chapter_status"));
                    item.setStatusName("PUBLISHED".equals(item.getStatus()) ? "已发布" : "草稿");
                } catch (SQLException exception) {
                    throw new IllegalStateException(exception);
                }
                return item;
            });
            Long pointId = rs.getObject("point_id", Long.class);
            if (pointId != null) {
                CourseResponse.KnowledgePoint point = new CourseResponse.KnowledgePoint();
                point.setKnowledgePointId(pointId);
                point.setPointCode(rs.getString("point_code"));
                point.setPointName(rs.getString("point_name"));
                point.setSummary(rs.getString("point_summary"));
                point.setSortOrder(rs.getInt("sort_order"));
                chapter.getKnowledgePoints().add(point);
            }
        }, course.getCourseId(), publishedOnly ? 1 : 0);
        course.setChapters(new ArrayList<>(chapters.values()));
        course.setDependencies(jdbcTemplate.query("""
            SELECT prerequisite.point_code prerequisite_code, dependent.point_code dependent_code
            FROM edu_knowledge_dependency dependency
            JOIN edu_knowledge_point prerequisite ON prerequisite.id = dependency.prerequisite_point_id
            JOIN edu_knowledge_point dependent ON dependent.id = dependency.dependent_point_id
            WHERE dependency.course_id = ?
            ORDER BY dependency.id
            """, (rs, rowNum) -> {
            CourseResponse.Dependency dependency = new CourseResponse.Dependency();
            dependency.setPrerequisitePointCode(rs.getString("prerequisite_code"));
            dependency.setDependentPointCode(rs.getString("dependent_code"));
            return dependency;
        }, course.getCourseId()));
    }

    private void loadTeachers(CourseResponse course) {
        course.setTeachers(jdbcTemplate.query("""
            SELECT member.user_id, user.real_name, member.member_role
            FROM edu_course_teacher member
            JOIN org_user user ON user.id = member.user_id AND user.is_deleted = 0
            WHERE member.course_id = ?
            ORDER BY CASE member.member_role WHEN 'OWNER' THEN 0 ELSE 1 END, member.joined_at, member.id
            """, (rs, rowNum) -> {
            CourseResponse.Teacher teacher = new CourseResponse.Teacher();
            teacher.setUserId(rs.getLong("user_id"));
            teacher.setTeacherName(rs.getString("real_name"));
            teacher.setMemberRole(rs.getString("member_role"));
            return teacher;
        }, course.getCourseId()));
    }

    private CourseResponse mapCourse(ResultSet rs, int rowNum) throws SQLException {
        CourseResponse response = new CourseResponse();
        response.setCourseId(rs.getLong("id"));
        response.setCourseCode(rs.getString("course_code"));
        response.setCourseName(rs.getString("course_name"));
        response.setSummary(rs.getString("summary"));
        response.setLearningObjectives(rs.getString("learning_objectives"));
        response.setPrerequisites(rs.getString("prerequisites"));
        response.setTeacherName(rs.getString("teacher_name"));
        response.setCoverAttachmentRefId(rs.getObject("cover_attachment_ref_id", Long.class));
        response.setTotalHours(rs.getInt("total_hours"));
        response.setStatus(rs.getString("status"));
        response.setStatusName("PUBLISHED".equals(response.getStatus()) ? "已发布" : "草稿");
        if (hasColumn(rs, "teacher_role")) response.setTeacherRole(rs.getString("teacher_role"));
        response.setChapterCount(rs.getInt("chapter_count"));
        response.setKnowledgePointCount(rs.getInt("knowledge_point_count"));
        response.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        response.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return response;
    }

    private boolean hasColumn(ResultSet rs, String columnName) throws SQLException {
        for (int index = 1; index <= rs.getMetaData().getColumnCount(); index++) {
            if (columnName.equalsIgnoreCase(rs.getMetaData().getColumnLabel(index))) return true;
        }
        return false;
    }

    public record InvitationRow(Long invitationId, Long courseId, String status, Long invitedBy,
                                LocalDateTime expiresAt, Long acceptedBy) {
    }
}
