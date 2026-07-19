package com.workforcehub.deliverable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EducationCourseDatasetTest {
    private static final Path DATASET = Path.of("../deliverables/course-data/java-web");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> PROFILE_DIMENSIONS = Set.of(
        "KNOWLEDGE_BASE", "LEARNING_GOAL", "MASTERY", "ERROR_PATTERN",
        "COGNITIVE_STYLE", "RESOURCE_PREFERENCE", "LEARNING_PACE", "PRACTICAL_ABILITY"
    );

    @Test
    void shouldProvideThreeCompleteStudentProfileSamples() throws Exception {
        JsonNode profiles = read("student-profiles.json");
        assertEquals(3, profiles.size());
        assertEquals(Set.of("BASIC", "INTERMEDIATE", "ADVANCED"), values(profiles, "profileCode"));
        profiles.forEach(profile -> {
            assertFalse(profile.path("description").asText().isBlank());
            assertEquals(PROFILE_DIMENSIONS, values(profile.path("dimensions"), "code"));
            profile.path("dimensions").forEach(dimension -> {
                assertFalse(dimension.path("conclusion").asText().isBlank());
                assertTrue(dimension.path("confidence").asDouble() >= 0.5);
            });
        });
    }

    @Test
    void shouldProvideCommonErrorAndCorrectionResources() throws Exception {
        JsonNode errors = read("common-errors.json");
        assertTrue(errors.size() >= 10);
        assertEquals(errors.size(), values(errors, "id").size());
        errors.forEach(error -> {
            assertFalse(error.path("knowledgePoint").asText().isBlank());
            assertFalse(error.path("error").asText().isBlank());
            assertFalse(error.path("cause").asText().isBlank());
            assertFalse(error.path("correction").asText().isBlank());
        });
    }

    @Test
    void shouldProvideAtLeastTwentySourcedFactChecks() throws Exception {
        JsonNode facts = read("fact-check-cases.json");
        assertTrue(facts.size() >= 20);
        assertEquals(facts.size(), values(facts, "id").size());
        facts.forEach(fact -> {
            assertFalse(fact.path("question").asText().isBlank());
            assertFalse(fact.path("expectedFact").asText().isBlank());
            assertTrue(fact.path("source").asText().startsWith("https://"));
        });
    }

    @Test
    void shouldProvideAtLeastTenBlockingSafetyCases() throws Exception {
        JsonNode cases = read("safety-cases.json");
        assertTrue(cases.size() >= 10);
        assertEquals(cases.size(), values(cases, "id").size());
        cases.forEach(testCase -> {
            assertEquals("BLOCK", testCase.path("expectedAction").asText());
            assertFalse(testCase.path("reason").asText().isBlank());
        });
    }

    @Test
    void shouldProvideLectureExerciseAndCodeForEveryChapter() throws Exception {
        Path manifest = DATASET.resolve("manifest.csv");
        assertTrue(Files.isRegularFile(manifest), manifest + " 不存在");
        List<String> rows = Files.readAllLines(manifest).stream()
            .filter(row -> !row.isBlank())
            .toList();
        assertEquals(8, rows.size());
        assertEquals("course_code,chapter_no,chapter_name,knowledge_point_codes,resource_type,file_path,source",
            rows.get(0));

        Set<String> chapterNumbers = new HashSet<>();
        for (String row : rows.subList(1, rows.size())) {
            String[] fields = row.split(",", -1);
            assertEquals(7, fields.length, row);
            assertEquals("JAVA_WEB", fields[0]);
            assertTrue(chapterNumbers.add(fields[1]), "章节编号重复: " + fields[1]);
            assertEquals("LECTURE_EXERCISE_CODE", fields[4]);
            Path chapter = DATASET.resolve(fields[5]);
            assertTrue(Files.isRegularFile(chapter), chapter + " 不存在");
            String content = Files.readString(chapter);
            assertTrue(content.contains("## 核心讲义"), chapter + " 缺少核心讲义");
            assertTrue(content.contains("## 练习"), chapter + " 缺少练习");
            assertTrue(content.contains("## 参考答案"), chapter + " 缺少参考答案");
            assertTrue(content.contains("## 代码案例"), chapter + " 缺少代码案例");
            assertTrue(content.contains("```"), chapter + " 缺少代码块");
        }
        assertEquals(Set.of("1", "2", "3", "4", "5", "6", "7"), chapterNumbers);
    }

    private JsonNode read(String fileName) throws Exception {
        Path file = DATASET.resolve(fileName);
        assertTrue(Files.isRegularFile(file), file + " 不存在");
        return JSON.readTree(file.toFile());
    }

    private static Set<String> values(JsonNode rows, String field) {
        Set<String> values = new HashSet<>();
        rows.forEach(row -> values.add(row.path(field).asText()));
        return values;
    }
}
