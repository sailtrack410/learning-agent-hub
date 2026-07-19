package com.workforcehub.assessment;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleAssessmentApiIntegrationTest extends LocalIntegrationTestSupport {
    private static final long COURSE_ID = 8100001L;
    private static final long ASSESSMENT_ID = 8500001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedAssessment() {
        jdbcTemplate.update("""
            INSERT INTO edu_course
                (id, course_code, course_name, summary, total_hours, status, is_deleted)
            VALUES (?, 'JAVA_WEB_TEST', 'Java Web 应用开发', '测试课程', 48, 'PUBLISHED', 0)
            """, COURSE_ID);
        jdbcTemplate.update("""
            INSERT INTO edu_course_chapter
                (id, course_id, chapter_no, chapter_name, content_type, content_json, status)
            VALUES (8110001, ?, 1, 'Web 基础', 'ARTICLE', '{"type":"doc","content":[]}', 'PUBLISHED')
            """, COURSE_ID);
        jdbcTemplate.batchUpdate(
            "INSERT INTO edu_knowledge_point (id, course_id, chapter_id, point_code, point_name, sort_order) VALUES (?, ?, 8110001, ?, ?, ?)",
            java.util.List.of(
                new Object[]{8120001L, COURSE_ID, "HTTP", "HTTP 请求与响应", 1},
                new Object[]{8120002L, COURSE_ID, "SERVLET", "Servlet 生命周期", 2},
                new Object[]{8120003L, COURSE_ID, "SPRING_BOOT", "Spring Boot 自动配置", 3}
            ));
        jdbcTemplate.update("""
            INSERT INTO edu_assessment
                (id, course_id, title, description, status, total_score, duration_minutes)
            VALUES (?, ?, 'Java Web 基础诊断', '三类题型测验', 'PUBLISHED', 100, 20)
            """, ASSESSMENT_ID, COURSE_ID);
        jdbcTemplate.batchUpdate("""
            INSERT INTO edu_assessment_question
                (id, assessment_id, knowledge_point_id, question_type, prompt, options_json,
                 correct_answer_json, analysis, score, sort_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, java.util.List.of(
            new Object[]{8510001L, ASSESSMENT_ID, 8120001L, "SINGLE_CHOICE", "创建资源的状态码？",
                "[{\"value\":\"A\",\"label\":\"200\"},{\"value\":\"B\",\"label\":\"201\"}]",
                "[\"B\"]", "201 表示创建成功。", 30, 1},
            new Object[]{8510002L, ASSESSMENT_ID, 8120002L, "TRUE_FALSE", "Servlet 实例字段可保存请求用户。",
                "[{\"value\":\"TRUE\",\"label\":\"正确\"},{\"value\":\"FALSE\",\"label\":\"错误\"}]",
                "[\"FALSE\"]", "Servlet 通常被并发请求共享。", 30, 2},
            new Object[]{8510003L, ASSESSMENT_ID, 8120003L, "MULTIPLE_CHOICE", "哪些会影响自动配置？",
                "[{\"value\":\"A\",\"label\":\"类路径\"},{\"value\":\"B\",\"label\":\"属性\"},{\"value\":\"C\",\"label\":\"Bean\"}]",
                "[\"A\",\"B\",\"C\"]", "自动配置由条件决定。", 40, 3}
        ));
    }

    @Test
    void endpointsShouldRequireStudentAndHideCorrectAnswersBeforeSubmission() throws Exception {
        Cookie teacher = login("lisi.manager");
        Cookie student = login("zhangsan.employee");

        mockMvc.perform(get("/api/employee/courses/{courseId}/assessments", COURSE_ID))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/employee/courses/{courseId}/assessments", COURSE_ID).cookie(teacher))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employee/courses/{courseId}/assessments", COURSE_ID).cookie(student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].questionCount").value(3))
            .andExpect(jsonPath("$.data[0].questions.length()").value(0));

        MvcResult detail = mockMvc.perform(get(
                "/api/employee/courses/{courseId}/assessments/{assessmentId}", COURSE_ID, ASSESSMENT_ID)
                .cookie(student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.questions.length()").value(3))
            .andExpect(jsonPath("$.data.questions[0].questionType").value("SINGLE_CHOICE"))
            .andReturn();
        assertTrue(detail.getResponse().getContentAsString().indexOf("correctValues") < 0);
    }

    @Test
    void submissionShouldGradeThreeQuestionTypesUpdateMasteryAndProfileIdempotently() throws Exception {
        Cookie student = login("zhangsan.employee");
        String request = """
            {
              "idempotencyKey":"assessment-first-attempt",
              "durationSeconds":180,
              "answers":[
                {"questionId":8510001,"selectedValues":["B"],"durationSeconds":30},
                {"questionId":8510002,"selectedValues":["TRUE"],"durationSeconds":40},
                {"questionId":8510003,"selectedValues":["A","B","C"],"durationSeconds":70}
              ]
            }
            """;

        MvcResult result = submit(student, request)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.earnedScore").value(70))
            .andExpect(jsonPath("$.data.attemptNo").value(1))
            .andExpect(jsonPath("$.data.answers[0].correct").value(true))
            .andExpect(jsonPath("$.data.answers[1].errorType").value("WRONG_OPTION"))
            .andExpect(jsonPath("$.data.answers[2].correct").value(true))
            .andExpect(jsonPath("$.data.diagnoses.length()").value(3))
            .andExpect(jsonPath("$.data.profileUpdated").value(true))
            .andReturn();
        JsonNode attempt = data(result);
        String attemptId = attempt.get("attemptId").asText();

        submit(student, request)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.attemptId").value(attemptId))
            .andExpect(jsonPath("$.data.attemptNo").value(1));

        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM edu_assessment_attempt WHERE student_user_id = 2003", Integer.class));
        assertEquals(3, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM edu_assessment_answer", Integer.class));
        assertEquals(3, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM edu_student_mastery WHERE student_user_id = 2003", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM edu_student_profile_version WHERE trigger_type = 'ASSESSMENT'", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM edu_student_profile_dimension WHERE dimension_code IN ('MASTERY_LEVEL', 'ERROR_PATTERN')",
            Integer.class));
    }

    @Test
    void emptyPartialInvalidAndCrossStudentSubmissionsShouldBeHandled() throws Exception {
        Cookie student = login("zhangsan.employee");
        Cookie anotherStudent = login("wangwu.employee");

        submit(student, """
            {"idempotencyKey":"empty-attempt","durationSeconds":5,"answers":[]}
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.earnedScore").value(0))
            .andExpect(jsonPath("$.data.answers[0].errorType").value("UNANSWERED"));

        submit(student, """
            {"idempotencyKey":"partial-attempt","durationSeconds":20,"answers":[
              {"questionId":8510003,"selectedValues":["A","B"],"durationSeconds":20}
            ]}
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.answers[2].errorType").value("PARTIAL_SELECTION"));

        submit(student, """
            {"idempotencyKey":"invalid-attempt","durationSeconds":10,"answers":[
              {"questionId":8510001,"selectedValues":["Z"],"durationSeconds":10}
            ]}
            """)
            .andExpect(status().isBadRequest());

        submit(anotherStudent, """
            {"idempotencyKey":"another-student","durationSeconds":10,"answers":[
              {"questionId":8510001,"selectedValues":["B"],"durationSeconds":10}
            ]}
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.attemptNo").value(1));

        assertEquals(3, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM edu_assessment_attempt", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM edu_student_profile", Integer.class));
    }

    private org.springframework.test.web.servlet.ResultActions submit(Cookie session, String request) throws Exception {
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
