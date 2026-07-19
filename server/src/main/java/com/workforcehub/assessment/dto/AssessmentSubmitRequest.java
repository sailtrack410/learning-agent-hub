package com.workforcehub.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class AssessmentSubmitRequest {
    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键不能超过64个字符")
    private String idempotencyKey;

    @NotNull(message = "答案不能为空")
    @Size(max = 100, message = "答案数量不能超过100")
    @Valid
    private List<Answer> answers = new ArrayList<>();

    @NotNull(message = "总用时不能为空")
    @Min(value = 0, message = "总用时不能小于0秒")
    @Max(value = 86400, message = "总用时不能超过86400秒")
    private Integer durationSeconds;

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public List<Answer> getAnswers() {
        return answers;
    }

    public void setAnswers(List<Answer> answers) {
        this.answers = answers;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public static class Answer {
        @NotNull(message = "题目ID不能为空")
        private Long questionId;

        @NotNull(message = "选项答案不能为空")
        @Size(max = 20, message = "单题选项不能超过20个")
        private List<@NotBlank(message = "选项值不能为空") String> selectedValues = new ArrayList<>();

        @NotNull(message = "单题用时不能为空")
        @Min(value = 0, message = "单题用时不能小于0秒")
        @Max(value = 86400, message = "单题用时不能超过86400秒")
        private Integer durationSeconds;

        public Long getQuestionId() {
            return questionId;
        }

        public void setQuestionId(Long questionId) {
            this.questionId = questionId;
        }

        public List<String> getSelectedValues() {
            return selectedValues;
        }

        public void setSelectedValues(List<String> selectedValues) {
            this.selectedValues = selectedValues;
        }

        public Integer getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(Integer durationSeconds) {
            this.durationSeconds = durationSeconds;
        }
    }
}
