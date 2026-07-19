package com.workforcehub.learningpath;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleLearningPathApiIntegrationTest extends LocalIntegrationTestSupport {
    private static final long COURSE_ID = 8600001L;
    private static final long ASSESSMENT_ID = 8650001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedCourseAndAssessment() {
        jdbcTemplate.update("""
            INSERT INTO edu_course
                (id, course_code, course_name, summary, total_hours, status, is_deleted)
            VALUES (?, 'PATH_TEST', '学习路径测试课程', '验证知识依赖和动态重排', 16, 'PUBLISHED', 0)
            """, COURSE_ID);
        jdbcTemplate.update("""
            INSERT INTO edu_course_chapter
                (id, course_id, chapter_no, chapter_name, summary, content_type, content_json, status)
            VALUES (8610001, ?, 1, '路径示范章节', '四个递进知识点', 'ARTICLE',
                    '{"type":"doc","content":[]}', 'PUBLISHED')
            """, COURSE_ID);
        jdbcTemplate.batchUpdate(
            "INSERT INTO edu_knowledge_point (id, course_id, chapter_id, point_code, point_name, summary, sort_order) VALUES (?, ?, 8610001, ?, ?, ?, ?)",
            java.util.List.of(
                new Object[]{8620001L, COURSE_ID, "PATH_A", "Web 基础", "理解 Web 请求和响应", 1},
                new Object[]{8620002L, COURSE_ID, "PATH_B", "Servlet 开发", "能使用 Servlet 处理请求", 2},
                new Object[]{8620003L, COURSE_ID, "PATH_C", "Spring Boot", "能搭建 Spring Boot 应用", 3},
                new Object[]{8620004L, COURSE_ID, "PATH_D", "REST API", "能设计 REST API", 4}
            ));
        jdbcTemplate.batchUpdate("""
            INSERT INTO edu_knowledge_dependency
                (id, course_id, prerequisite_point_id, dependent_point_id)
            VALUES (?, ?, ?, ?)
            """, java.util.List.of(
            new Object[]{8630001L, COURSE_ID, 8620001L, 8620002L},
            new Object[]{8630002L, COURSE_ID, 8620002L, 8620003L},
            new Object[]{8630003L, COURSE_ID, 8620003L, 8620004L}
        ));
        jdbcTemplate.update("""
            INSERT INTO edu_assessment
                (id, course_id, title, description, status, total_score, duration_minutes)
            VALUES (?, ?, '路径动态调整测验', '覆盖前两个知识点', 'PUBLISHED', 100, 10)
            """, ASSESSMENT_ID, COURSE_ID);
        jdbcTemplate.batchUpdate("""
            INSERT INTO edu_assessment_question
                (id, assessment_id, knowledge_point_id, question_type, prompt, options_json,
                 correct_answer_json, analysis, score, sort_order)
            VALUES (?, ?, ?, 'SINGLE_CHOICE', ?, ?, ?, ?, 50, ?)
            """, java.util.List.of(
            new Object[]{8660001L, ASSESSMENT_ID, 8620001L, "Web 基础正确选项？",
                "[{\"value\":\"A\",\"label\":\"正确\"},{\"value\":\"B\",\"label\":\"错误\"}]",
                "[\"A\"]", "Web 基础解析", 1},
            new Object[]{8660002L, ASSESSMENT_ID, 8620002L, "Servlet 正确选项？",
                "[{\"value\":\"A\",\"label\":\"正确\"},{\"value\":\"B\",\"label\":\"错误\"}]",
                "[\"A\"]", "Servlet 解析", 2}
        ));
    }

    @Test
    void pathShouldRequireStudentKeepUsersIsolatedAndAdvanceInOrder() throws Exception {
        Cookie teacher = login("lisi.manager");
        Cookie student = login("zhangsan.employee");
        Cookie anotherStudent = login("wangwu.employee");

        mockMvc.perform(get("/api/employee/courses/{courseId}/learning-path", COURSE_ID))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/employee/courses/{courseId}/learning-path", COURSE_ID).cookie(teacher))
            .andExpect(status().isForbidden());

        MvcResult initial = current(student)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionNo").value(1))
            .andExpect(jsonPath("$.data.nodes.length()").value(4))
            .andExpect(jsonPath("$.data.nodes[0].knowledgePointName").value("Web 基础"))
            .andExpect(jsonPath("$.data.nodes[0].status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.nodes[1].status").value("LOCKED"))
            .andExpect(jsonPath("$.data.todayNodes.length()").value(1))
            .andExpect(jsonPath("$.data.recommendations[0].reason").value(
                org.hamcrest.Matchers.containsString("尚无该知识点评估记录")))
            .andReturn();
        JsonNode initialData = data(initial);
        String studentPathId = initialData.get("pathId").asText();
        String firstNodeId = initialData.get("nodes").get(0).get("nodeId").asText();
        String thirdNodeId = initialData.get("nodes").get(2).get("nodeId").asText();

        current(anotherStudent).andExpect(status().isOk());
        String anotherPathId = jdbcTemplate.queryForObject("""
            SELECT id FROM edu_learning_path WHERE student_user_id = 2004 AND course_id = ? AND active_flag = 1
            """, String.class, COURSE_ID);
        assertNotEquals(studentPathId, anotherPathId);

        mockMvc.perform(post("/api/employee/courses/{courseId}/learning-path/nodes/{nodeId}/complete",
                COURSE_ID, thirdNodeId).cookie(student))
            .andExpect(status().isConflict());

        mockMvc.perform(post("/api/employee/courses/{courseId}/learning-path/nodes/{nodeId}/complete",
                COURSE_ID, firstNodeId).cookie(student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.progressPercent").value(25))
            .andExpect(jsonPath("$.data.nodes[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.nodes[1].status").value("IN_PROGRESS"));
    }

    @Test
    void assessmentShouldCreateNewPathVersionAndAdjustRecommendationWithoutDuplicateRegeneration() throws Exception {
        Cookie student = login("zhangsan.employee");
        MvcResult initial = current(student).andExpect(status().isOk()).andReturn();
        String initialPathId = data(initial).get("pathId").asText();
        String request = """
            {
              "idempotencyKey":"path-adjustment-attempt",
              "durationSeconds":60,
              "answers":[
                {"questionId":8660001,"selectedValues":["A"],"durationSeconds":20},
                {"questionId":8660002,"selectedValues":["B"],"durationSeconds":20}
              ]
            }
            """;

        MvcResult attempt = submitAssessment(student, request)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.earnedScore").value(50))
            .andReturn();
        String attemptId = data(attempt).get("attemptId").asText();

        current(student)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionNo").value(2))
            .andExpect(jsonPath("$.data.sourceAssessmentAttemptId").value(attemptId))
            .andExpect(jsonPath("$.data.profileVersionNo").value(1))
            .andExpect(jsonPath("$.data.nodes[0].knowledgePointName").value("Web 基础"))
            .andExpect(jsonPath("$.data.nodes[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.nodes[0].masteryScore").value(100))
            .andExpect(jsonPath("$.data.nodes[1].knowledgePointName").value("Servlet 开发"))
            .andExpect(jsonPath("$.data.nodes[1].status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.nodes[1].recommendationReason").value(
                org.hamcrest.Matchers.containsString("当前掌握度为 0%")));

        String activePathId = jdbcTemplate.queryForObject("""
            SELECT id FROM edu_learning_path WHERE student_user_id = 2003 AND course_id = ? AND active_flag = 1
            """, String.class, COURSE_ID);
        assertNotNull(activePathId);
        assertNotEquals(initialPathId, activePathId);
        assertEquals(2, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM edu_learning_path WHERE student_user_id = 2003 AND course_id = ?
            """, Integer.class, COURSE_ID));

        submitAssessment(student, request)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.attemptId").value(attemptId));
        assertEquals(2, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM edu_learning_path WHERE student_user_id = 2003 AND course_id = ?
            """, Integer.class, COURSE_ID));
    }

    @Test
    void concurrentCompletionShouldOnlyUnlockOneNextNode() throws Exception {
        Cookie student = login("zhangsan.employee");
        MvcResult initial = current(student).andExpect(status().isOk()).andReturn();
        String pathId = data(initial).get("pathId").asText();
        String firstNodeId = data(initial).get("nodes").get(0).get("nodeId").asText();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<MvcResult> completion = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试启动超时");
                return mockMvc.perform(post(
                        "/api/employee/courses/{courseId}/learning-path/nodes/{nodeId}/complete",
                        COURSE_ID, firstNodeId).cookie(student))
                    .andExpect(status().isOk())
                    .andReturn();
            };
            Future<MvcResult> first = executor.submit(completion);
            Future<MvcResult> second = executor.submit(completion);
            if (!ready.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试准备超时");
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM edu_learning_path_node
            WHERE path_id = ? AND node_status = 'IN_PROGRESS'
            """, Integer.class, pathId));
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM edu_learning_path_node
            WHERE path_id = ? AND node_status = 'COMPLETED'
            """, Integer.class, pathId));
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM edu_learning_progress
            WHERE student_user_id = 2003 AND course_id = ? AND progress_status = 'IN_PROGRESS'
            """, Integer.class, COURSE_ID));
    }

    private org.springframework.test.web.servlet.ResultActions current(Cookie session) throws Exception {
        return mockMvc.perform(get("/api/employee/courses/{courseId}/learning-path", COURSE_ID).cookie(session));
    }

    private org.springframework.test.web.servlet.ResultActions submitAssessment(Cookie session, String request)
        throws Exception {
        return mockMvc.perform(post(
                "/api/employee/courses/{courseId}/assessments/{assessmentId}/attempts", COURSE_ID, ASSESSMENT_ID)
            .cookie(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(request));
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

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("data");
    }
}
