package com.workforcehub.profile;

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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleStudentProfileApiIntegrationTest extends LocalIntegrationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void profileEndpointsShouldRequireStudentRoleAndKeepUsersIsolated() throws Exception {
        Cookie teacher = login("lisi.manager");
        Cookie student = login("zhangsan.employee");
        Cookie anotherStudent = login("wangwu.employee");

        mockMvc.perform(get("/api/employee/student/profile"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/employee/student/profile").cookie(teacher))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/employee/student/profile").cookie(student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.completedDimensions").value(0))
            .andExpect(jsonPath("$.data.totalDimensions").value(8))
            .andExpect(jsonPath("$.data.nextDimensionCode").value("KNOWLEDGE_FOUNDATION"))
            .andExpect(jsonPath("$.data.messages.length()").value(1));

        answer(student, "我掌握 Java 基础语法和 MySQL，但对 HTTP 与 Servlet 还不熟悉。")
            .andExpect(jsonPath("$.data.completedDimensions").value(1));

        mockMvc.perform(get("/api/employee/student/profile").cookie(anotherStudent))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.completedDimensions").value(0));

        assertEquals(2, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM edu_student_profile", Integer.class));
    }

    @Test
    void dialogueShouldCreateEightDimensionsVersionsEvidenceAndAllowCorrection() throws Exception {
        Cookie student = login("zhangsan.employee");
        List<String> answers = List.of(
            "我会 Java 基础和 MySQL，HTTP 基础较弱。",
            "目标是独立完成一个可以部署的课程管理系统。",
            "Servlet 入门，Spring Boot 能完成简单接口，MyBatis 需要提示。",
            "经常混淆状态码，也容易忘记事务边界。",
            "我更适合先看完整案例，再结合流程图理解原理。",
            "偏好代码示例和项目任务，讲义用于复习。",
            "每周可以学习六小时，习惯每天学习一小时。",
            "完成过控制台项目，能够断点调试，但还不能独立部署 Web 应用。"
        );

        MvcResult finalResult = null;
        for (String answer : answers) {
            finalResult = answer(student, answer).andExpect(status().isOk()).andReturn();
        }
        assertNotNull(finalResult);
        JsonNode profile = data(finalResult);
        assertEquals(8, profile.get("completedDimensions").asInt());
        assertEquals(8, profile.get("versionNo").asInt());
        assertEquals(true, profile.get("complete").asBoolean());
        assertEquals(8, profile.get("dimensions").size());

        mockMvc.perform(put("/api/employee/student/profile/dimensions/RESOURCE_PREFERENCE")
                .cookie(student)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conclusion\":\"我更偏好短视频配合代码实操。\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionNo").value(9))
            .andExpect(jsonPath("$.data.dimensions[5].conclusion").value("我更偏好短视频配合代码实操。"))
            .andExpect(jsonPath("$.data.dimensions[5].confidence").value(0.95));

        Long profileId = jdbcTemplate.queryForObject(
            "SELECT id FROM edu_student_profile WHERE student_user_id = 2003", Long.class);
        assertNotNull(profileId);
        assertEquals(9, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM edu_student_profile_version WHERE profile_id = ?", Integer.class, profileId));
        assertEquals(9, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM edu_student_profile_evidence WHERE profile_id = ?", Integer.class, profileId));
    }

    @Test
    void answerShouldValidateContentAndRejectExtraTurnAfterCompletion() throws Exception {
        Cookie student = login("zhangsan.employee");

        mockMvc.perform(post("/api/employee/student/profile/messages")
                .cookie(student)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\" \"}"))
            .andExpect(status().isBadRequest());

        for (int index = 0; index < 8; index++) {
            answer(student, "这是第" + (index + 1) + "个维度的有效自然语言回答。")
                .andExpect(status().isOk());
        }

        answer(student, "额外回答")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("画像对话已完成，请使用纠正功能更新结论"));
    }

    private org.springframework.test.web.servlet.ResultActions answer(Cookie session, String message) throws Exception {
        return mockMvc.perform(post("/api/employee/student/profile/messages")
            .cookie(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(java.util.Map.of("message", message))));
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

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("data");
    }
}
