package com.workforcehub.learningdashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleStudentLearningDashboardApiIntegrationTest extends LocalIntegrationTestSupport {
    private static final long COURSE_ID = 8900001L;
    private static final long PATH_ID = 8910001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedLearningDashboard() {
        jdbcTemplate.update("""
            INSERT INTO edu_course
                (id, course_code, course_name, summary, teacher_name, total_hours, status, is_deleted)
            VALUES (?, 'DASHBOARD_TEST', '学习首页测试课程', '验证学习指标聚合', '课程组', 24, 'PUBLISHED', 0)
            """, COURSE_ID);
        jdbcTemplate.update("""
            INSERT INTO edu_course_chapter
                (id, course_id, chapter_no, chapter_name, summary, content_type, content_json, status)
            VALUES (8901001, ?, 1, '学习首页章节', '三项今日任务', 'ARTICLE',
                    '{"type":"doc","content":[]}', 'PUBLISHED')
            """, COURSE_ID);
        jdbcTemplate.batchUpdate(
            "INSERT INTO edu_knowledge_point (id, course_id, chapter_id, point_code, point_name, summary, sort_order) VALUES (?, ?, 8901001, ?, ?, ?, ?)",
            java.util.List.of(
                new Object[]{8902001L, COURSE_ID, "DASH_A", "已完成知识点", "完成基础学习", 1},
                new Object[]{8902002L, COURSE_ID, "DASH_B", "当前知识点", "完成当前学习", 2},
                new Object[]{8902003L, COURSE_ID, "DASH_C", "后续知识点", "完成后续学习", 3}
            ));
        jdbcTemplate.update("""
            INSERT INTO edu_learning_path
                (id, student_user_id, course_id, profile_version_no, version_no, status, active_flag, generation_reason)
            VALUES (?, 2003, ?, 2, 1, 'ACTIVE', 1, '学习首页聚合测试')
            """, PATH_ID, COURSE_ID);
        jdbcTemplate.batchUpdate("""
            INSERT INTO edu_learning_path_node
                (id, path_id, knowledge_point_id, chapter_id, sequence_no, learning_objective,
                 resource_type, resource_ref_id, resource_title, estimated_minutes,
                 completion_condition, node_status, mastery_score_snapshot, recommendation_reason)
            VALUES (?, ?, ?, 8901001, ?, ?, 'COURSE_ARTICLE', 8901001, ?, ?, '完成文章学习', ?, ?, ?)
            """, java.util.List.of(
            new Object[]{8911001L, PATH_ID, 8902001L, 1, "完成基础", "学习首页章节", 15, "COMPLETED", 80, "已达到完成条件"},
            new Object[]{8911002L, PATH_ID, 8902002L, 2, "学习当前知识点", "学习首页章节", 30, "IN_PROGRESS", 50, "当前掌握度较低，建议优先学习"},
            new Object[]{8911003L, PATH_ID, 8902003L, 3, "学习后续知识点", "学习首页章节", 25, "PENDING", null, "完成当前节点后继续学习"}
        ));
        jdbcTemplate.batchUpdate("""
            INSERT INTO edu_learning_recommendation
                (id, path_id, node_id, student_user_id, course_id, knowledge_point_id,
                 resource_type, resource_ref_id, resource_title, priority_no, reason, status)
            VALUES (?, ?, ?, 2003, ?, ?, 'COURSE_ARTICLE', 8901001, ?, ?, ?, 'ACTIVE')
            """, java.util.List.of(
            new Object[]{8912001L, PATH_ID, 8911002L, COURSE_ID, 8902002L, "当前知识点课程文章", 1, "掌握度为50%，建议优先巩固"},
            new Object[]{8912002L, PATH_ID, 8911003L, COURSE_ID, 8902003L, "后续知识点课程文章", 2, "完成前置知识点后继续"}
        ));
        jdbcTemplate.update("""
            INSERT INTO edu_assessment
                (id, course_id, title, description, status, total_score, duration_minutes)
            VALUES (8920001, ?, '待完成测验', '尚未提交', 'PUBLISHED', 100, 15)
            """, COURSE_ID);
        jdbcTemplate.batchUpdate("""
            INSERT INTO edu_student_mastery
                (id, student_user_id, course_id, knowledge_point_id, mastery_score, attempt_count, correct_count)
            VALUES (?, 2003, ?, ?, ?, 1, ?)
            """, java.util.List.of(
            new Object[]{8930001L, COURSE_ID, 8902001L, 80, 1},
            new Object[]{8930002L, COURSE_ID, 8902002L, 50, 0}
        ));
    }

    @Test
    void dashboardShouldAggregateStudentLearningStateAndEnforceRole() throws Exception {
        Cookie teacher = login("lisi.manager");
        Cookie student = login("zhangsan.employee");

        mockMvc.perform(get("/api/employee/student/learning-dashboard"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/employee/student/learning-dashboard").cookie(teacher))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employee/student/learning-dashboard").cookie(student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.courseCount").value(1))
            .andExpect(jsonPath("$.data.todayTaskCount").value(2))
            .andExpect(jsonPath("$.data.overallProgress").value(33.0))
            .andExpect(jsonPath("$.data.averageMastery").value(65.0))
            .andExpect(jsonPath("$.data.pendingAssessmentCount").value(1))
            .andExpect(jsonPath("$.data.recommendationCount").value(2))
            .andExpect(jsonPath("$.data.courses[0].currentNodeTitle").value("学习首页章节"))
            .andExpect(jsonPath("$.data.todayTasks[0].status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.todayTasks[0].recommendationReason").value(
                org.hamcrest.Matchers.containsString("优先学习")))
            .andExpect(jsonPath("$.data.recommendations.length()").value(2));
    }

    private Cookie login(String loginName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                    "loginName", loginName,
                    "password", "Employee@123456"))))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session);
        return session;
    }
}
