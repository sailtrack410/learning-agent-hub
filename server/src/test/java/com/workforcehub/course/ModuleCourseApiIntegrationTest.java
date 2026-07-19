package com.workforcehub.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleCourseApiIntegrationTest extends LocalIntegrationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void coursePreviewShouldAllowSameOriginFrameOnly() throws Exception {
        mockMvc.perform(get("/api/employee/courses/resources/1/preview"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }

    @Test
    void courseKnowledgeEndpointsShouldEnforceAuthenticationRoleAndMembership() throws Exception {
        Cookie ownerSession = login("lisi.manager");
        Cookie studentSession = login("zhangsan.employee");
        long courseId = createCourse(ownerSession, "KNOWLEDGE_AUTH");

        mockMvc.perform(get("/api/employee/teacher/courses/{courseId}/knowledge-documents", courseId))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/employee/teacher/courses/{courseId}/knowledge-documents", courseId)
                .cookie(studentSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("仅教师可以上传课程资源"));

        makeTeacher(2003L);
        mockMvc.perform(get("/api/employee/teacher/courses/{courseId}/knowledge-documents", courseId)
                .cookie(studentSession))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/employee/teacher/courses/{courseId}/knowledge-documents", courseId)
                .cookie(studentSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentRefId\":8200001,\"knowledgePointIds\":[]}"))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/employee/teacher/courses/{courseId}/knowledge-documents/{documentId}/retry",
                courseId, 8500001L).cookie(studentSession))
            .andExpect(status().isNotFound());
    }

    @Test
    void teacherInvitationShouldGrantCollaborationWithoutOwnership() throws Exception {
        makeTeacher(2003L);
        Cookie ownerSession = login("lisi.manager");
        Cookie collaboratorSession = login("zhangsan.employee");
        long courseId = createCourse(ownerSession, "COLLAB_JAVA");

        mockMvc.perform(get("/api/employee/teacher/courses/{courseId}", courseId)
                .cookie(collaboratorSession))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("课程不存在或无权管理"));

        MvcResult invitationResult = mockMvc.perform(post("/api/employee/teacher/courses/{courseId}/invitations", courseId)
                .cookie(ownerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.token").isString())
            .andReturn();
        String token = data(invitationResult).get("token").asText();

        mockMvc.perform(post("/api/employee/teacher/courses/invitations/accept")
                .cookie(collaboratorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.teacherRole").value("COLLABORATOR"))
            .andExpect(jsonPath("$.data.teachers.length()").value(2));

        mockMvc.perform(put("/api/employee/teacher/courses/{courseId}", courseId)
                .cookie(collaboratorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(courseJson("COLLAB_JAVA", "协作更新后的课程")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.courseName").value("协作更新后的课程"));

        mockMvc.perform(delete("/api/employee/teacher/courses/{courseId}", courseId)
                .cookie(collaboratorSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("仅课程所有者可以执行该操作"));

        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM edu_course_teacher
            WHERE course_id = ? AND user_id = 2003 AND member_role = 'COLLABORATOR'
            """, Integer.class, courseId));
    }

    @Test
    void teacherWithoutMembershipShouldNotListOrEditCourse() throws Exception {
        makeTeacher(2003L);
        Cookie ownerSession = login("lisi.manager");
        Cookie outsiderSession = login("zhangsan.employee");
        long courseId = createCourse(ownerSession, "PRIVATE_JAVA");

        mockMvc.perform(get("/api/employee/teacher/courses").cookie(outsiderSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(put("/api/employee/teacher/courses/{courseId}", courseId)
                .cookie(outsiderSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(courseJson("PRIVATE_JAVA", "越权修改")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("课程不存在"));

        assertEquals("私有课程", jdbcTemplate.queryForObject(
            "SELECT course_name FROM edu_course WHERE id = ?", String.class, courseId));
    }

    @Test
    void teacherShouldSaveArticleWithoutChangingChapterIdentity() throws Exception {
        Cookie teacherSession = login("lisi.manager");
        long courseId = createCourse(teacherSession, "ARTICLE_JAVA");
        String outline = """
            {
              "chapters":[{
                "chapterName":"Servlet 生命周期",
                "summary":"理解容器中的完整执行过程",
                "knowledgePoints":[{"pointCode":"SERVLET_LIFE","pointName":"Servlet 生命周期"}]
              }],
              "dependencies":[]
            }
            """;
        MvcResult outlineResult = mockMvc.perform(put("/api/employee/teacher/courses/{courseId}/outline", courseId)
                .cookie(teacherSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(outline))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.chapters[0].contentType").value("ARTICLE"))
            .andReturn();
        long chapterId = data(outlineResult).get("chapters").get(0).get("chapterId").asLong();

        MvcResult articleResult = mockMvc.perform(put(
                "/api/employee/teacher/courses/{courseId}/chapters/{chapterId}/article", courseId, chapterId)
                .cookie(teacherSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(java.util.Map.of(
                    "title", "Servlet 生命周期详解",
                    "summary", "初始化、服务与销毁",
                    "contentJson", "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"正文\"}]}]}",
                    "status", "PUBLISHED"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.chapters[0].chapterId").value(chapterId))
            .andExpect(jsonPath("$.data.chapters[0].chapterName").value("Servlet 生命周期详解"))
            .andExpect(jsonPath("$.data.chapters[0].status").value("PUBLISHED"))
            .andReturn();

        String retainedOutline = """
            {
              "chapters":[{
                "chapterId":%d,
                "chapterName":"Servlet 生命周期详解",
                "summary":"编排调整后的章节简介",
                "knowledgePoints":[{"pointCode":"SERVLET_LIFE","pointName":"Servlet 生命周期"}]
              }],
              "dependencies":[]
            }
            """.formatted(chapterId);
        mockMvc.perform(put("/api/employee/teacher/courses/{courseId}/outline", courseId)
                .cookie(teacherSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(retainedOutline))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.chapters[0].chapterId").value(chapterId))
            .andExpect(jsonPath("$.data.chapters[0].status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.chapters[0].contentJson").value(data(articleResult).get("chapters").get(0).get("contentJson").asText()));
    }

    @Test
    void enrollmentShouldLimitTeacherDirectoryPrivateChatAndClassGroup() throws Exception {
        Cookie teacherSession = login("lisi.manager");
        Cookie enrolledStudentSession = login("zhangsan.employee");
        Cookie otherStudentSession = login("wangwu.employee");
        long courseId = createCourse(teacherSession, "COURSE_COMMUNICATION");
        jdbcTemplate.update("UPDATE edu_course SET status = 'PUBLISHED' WHERE id = ?", courseId);

        mockMvc.perform(get("/api/employee/courses").cookie(enrolledStudentSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].enrolled").value(false));

        mockMvc.perform(post("/api/employee/courses/{courseId}/enrollment", courseId)
                .cookie(enrolledStudentSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enrolled").value(true));
        mockMvc.perform(post("/api/employee/courses/{courseId}/enrollment", courseId)
                .cookie(enrolledStudentSession))
            .andExpect(status().isOk());
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM edu_course_enrollment
            WHERE course_id = ? AND student_user_id = 2003
            """, Integer.class, courseId));

        mockMvc.perform(get("/api/employee/teacher/courses/{courseId}/learning-overview", courseId)
                .cookie(teacherSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.studentCount").value(1))
            .andExpect(jsonPath("$.data.students[0].studentUserId").value(2003))
            .andExpect(jsonPath("$.data.students[0].classId").value(111));

        MvcResult privateResult = mockMvc.perform(post(
                "/api/employee/teacher/courses/{courseId}/students/{studentUserId}/private-conversation",
                courseId, 2003L).cookie(teacherSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.conversationType").value("PRIVATE"))
            .andReturn();
        long privateConversationId = data(privateResult).get("conversationId").asLong();
        assertEquals(2, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM im_conversation_member
            WHERE conversation_id = ? AND status = 'ACTIVE'
            """, Integer.class, privateConversationId));

        mockMvc.perform(post(
                "/api/employee/teacher/courses/{courseId}/students/{studentUserId}/private-conversation",
                courseId, 2004L).cookie(teacherSession))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("学生未加入该课程"));

        MvcResult groupResult = mockMvc.perform(post(
                "/api/employee/teacher/courses/{courseId}/classes/{classId}/conversation",
                courseId, 111L).cookie(teacherSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.conversationType").value("GROUP"))
            .andReturn();
        long groupConversationId = data(groupResult).get("conversationId").asLong();
        MvcResult repeatedGroupResult = mockMvc.perform(post(
                "/api/employee/teacher/courses/{courseId}/classes/{classId}/conversation",
                courseId, 111L).cookie(teacherSession))
            .andExpect(status().isOk())
            .andReturn();
        assertEquals(groupConversationId, data(repeatedGroupResult).get("conversationId").asLong());
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM im_conversation WHERE conversation_key = ?
            """, Integer.class, "COURSE_CLASS:" + courseId + ":111"));
        assertEquals(2, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM im_conversation_member
            WHERE conversation_id = ? AND status = 'ACTIVE'
            """, Integer.class, groupConversationId));

        mockMvc.perform(post(
                "/api/employee/teacher/courses/{courseId}/classes/{classId}/conversation",
                courseId, 111L).cookie(otherStudentSession))
            .andExpect(status().isForbidden());
    }

    private long createCourse(Cookie session, String courseCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/teacher/courses")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(courseJson(courseCode, "私有课程")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.teacherRole").value("OWNER"))
            .andReturn();
        return data(result).get("courseId").asLong();
    }

    private Cookie login(String loginName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"loginName":"%s","password":"Employee@123456"}
                    """.formatted(loginName)))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session);
        return session;
    }

    private void makeTeacher(long userId) {
        jdbcTemplate.update("UPDATE edu_user_role SET education_role = 'TEACHER' WHERE user_id = ?", userId);
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("data");
    }

    private String courseJson(String courseCode, String courseName) {
        return """
            {
              "courseCode":"%s",
              "courseName":"%s",
              "summary":"课程简介",
              "totalHours":32,
              "status":"DRAFT"
            }
            """.formatted(courseCode, courseName);
    }
}
