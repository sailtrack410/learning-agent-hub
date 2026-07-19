<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock3,
  RotateCcw,
  Sparkles,
  XCircle,
} from 'lucide-vue-next';
import {
  employeeApi,
  type AssessmentAttemptResponse,
  type AssessmentResponse,
  type EntityId,
} from '@/api';
import AppErrorState from '@/components/ui/AppErrorState.vue';
import AppLoadingState from '@/components/ui/AppLoadingState.vue';

const props = defineProps<{
  courseId: EntityId;
  assessment: AssessmentResponse;
}>();

const detail = ref<AssessmentResponse | null>(null);
const result = ref<AssessmentAttemptResponse | null>(null);
const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const currentIndex = ref(0);
const selections = ref<Record<string, string[]>>({});
const questionDurations = ref<Record<string, number>>({});
const assessmentStartedAt = ref(0);
const questionEnteredAt = ref(0);
const idempotencyKey = ref('');

const currentQuestion = computed(() => detail.value?.questions[currentIndex.value] ?? null);
const progress = computed(() => {
  if (!detail.value?.questionCount) return 0;
  return Math.round(((currentIndex.value + 1) / detail.value.questionCount) * 100);
});
const answeredCount = computed(() => Object.values(selections.value).filter((values) => values.length > 0).length);
const scoreRate = computed(() => {
  if (!result.value?.totalScore) return 0;
  return Math.round((result.value.earnedScore / result.value.totalScore) * 100);
});

watch(
  () => [String(props.courseId), String(props.assessment.assessmentId)],
  loadAssessment,
  { immediate: true },
);

async function loadAssessment() {
  loading.value = true;
  error.value = '';
  try {
    detail.value = await employeeApi.courses.assessmentDetail(props.courseId, props.assessment.assessmentId);
    startAttempt();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '测验加载失败';
  } finally {
    loading.value = false;
  }
}

function startAttempt() {
  result.value = null;
  currentIndex.value = 0;
  selections.value = {};
  questionDurations.value = {};
  assessmentStartedAt.value = Date.now();
  questionEnteredAt.value = Date.now();
  idempotencyKey.value = globalThis.crypto?.randomUUID?.()
    ?? `assessment-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function questionTypeName(type: AssessmentResponse['questions'][number]['questionType']) {
  if (type === 'MULTIPLE_CHOICE') return '多选题';
  if (type === 'TRUE_FALSE') return '判断题';
  return '单选题';
}

function selected(questionId: EntityId, value: string) {
  return (selections.value[String(questionId)] ?? []).includes(value);
}

function selectOption(question: AssessmentResponse['questions'][number], value: string) {
  const key = String(question.questionId);
  if (question.questionType === 'MULTIPLE_CHOICE') {
    const values = selections.value[key] ?? [];
    selections.value = {
      ...selections.value,
      [key]: values.includes(value) ? values.filter((item) => item !== value) : [...values, value],
    };
    return;
  }
  selections.value = { ...selections.value, [key]: [value] };
}

function recordQuestionDuration() {
  const question = currentQuestion.value;
  if (!question || !questionEnteredAt.value) return;
  const key = String(question.questionId);
  const elapsed = Math.max(0, Math.round((Date.now() - questionEnteredAt.value) / 1000));
  questionDurations.value = {
    ...questionDurations.value,
    [key]: (questionDurations.value[key] ?? 0) + elapsed,
  };
}

function moveTo(index: number) {
  if (!detail.value || index < 0 || index >= detail.value.questions.length) return;
  recordQuestionDuration();
  currentIndex.value = index;
  questionEnteredAt.value = Date.now();
}

async function submit() {
  if (!detail.value) return;
  recordQuestionDuration();
  submitting.value = true;
  error.value = '';
  try {
    const durationSeconds = Math.max(0, Math.round((Date.now() - assessmentStartedAt.value) / 1000));
    result.value = await employeeApi.courses.submitAssessment(props.courseId, detail.value.assessmentId, {
      idempotencyKey: idempotencyKey.value,
      durationSeconds,
      answers: detail.value.questions.map((question) => ({
        questionId: question.questionId,
        selectedValues: selections.value[String(question.questionId)] ?? [],
        durationSeconds: questionDurations.value[String(question.questionId)] ?? 0,
      })),
    });
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '测验提交失败';
    questionEnteredAt.value = Date.now();
  } finally {
    submitting.value = false;
  }
}

function optionLabel(questionId: EntityId, value: string) {
  const question = detail.value?.questions.find((item) => String(item.questionId) === String(questionId));
  return question?.options.find((option) => option.value === value)?.label ?? value;
}

function errorTypeName(errorType?: string | null) {
  if (errorType === 'UNANSWERED') return '未作答';
  if (errorType === 'PARTIAL_SELECTION') return '选项不全';
  if (errorType === 'MIXED_SELECTION') return '混入错误选项';
  if (errorType === 'WRONG_OPTION') return '选项错误';
  return '作答错误';
}
</script>

<template>
  <AppLoadingState v-if="loading" />
  <AppErrorState
    v-else-if="error && !detail"
    title="测验加载失败"
    :description="error"
    action-label="重试"
    @retry="loadAssessment"
  />

  <section v-else-if="detail && !result" class="assessment-shell">
    <header class="assessment-intro">
      <div>
        <span class="assessment-kicker">课程诊断</span>
        <h2>{{ detail.title }}</h2>
        <p>{{ detail.description }}</p>
      </div>
      <div class="assessment-facts">
        <span><Clock3 :size="16" />{{ detail.durationMinutes }} 分钟</span>
        <span>{{ detail.questionCount }} 题</span>
        <strong>{{ detail.totalScore }} 分</strong>
      </div>
    </header>

    <div class="assessment-progress" aria-label="答题进度">
      <span :style="{ width: `${progress}%` }" />
    </div>

    <article v-if="currentQuestion" class="question-card">
      <header>
        <div>
          <span class="question-index">{{ String(currentIndex + 1).padStart(2, '0') }}</span>
          <span class="question-type">{{ questionTypeName(currentQuestion.questionType) }}</span>
          <span class="question-point">{{ currentQuestion.knowledgePointName }}</span>
        </div>
        <strong>{{ currentQuestion.score }} 分</strong>
      </header>
      <h3>{{ currentQuestion.prompt }}</h3>
      <p v-if="currentQuestion.questionType === 'MULTIPLE_CHOICE'" class="choice-hint">本题可选择多个答案</p>
      <div class="choice-list">
        <button
          v-for="option in currentQuestion.options"
          :key="option.value"
          type="button"
          class="choice"
          :class="{ selected: selected(currentQuestion.questionId, option.value) }"
          @click="selectOption(currentQuestion, option.value)"
        >
          <span class="choice-code">{{ option.value }}</span>
          <span>{{ option.label }}</span>
          <Check v-if="selected(currentQuestion.questionId, option.value)" :size="18" />
        </button>
      </div>
    </article>

    <p v-if="error" class="assessment-error">{{ error }}</p>
    <footer class="assessment-actions">
      <span>已作答 {{ answeredCount }} / {{ detail.questionCount }}</span>
      <div>
        <button type="button" class="secondary-action" :disabled="currentIndex === 0" @click="moveTo(currentIndex - 1)">
          <ChevronLeft :size="18" />上一题
        </button>
        <button
          v-if="currentIndex < detail.questions.length - 1"
          type="button"
          class="primary-action"
          @click="moveTo(currentIndex + 1)"
        >
          下一题<ChevronRight :size="18" />
        </button>
        <button v-else type="button" class="primary-action" :disabled="submitting" @click="submit">
          {{ submitting ? '正在评分…' : '提交测验' }}<CheckCircle2 :size="18" />
        </button>
      </div>
    </footer>
  </section>

  <section v-else-if="detail && result" class="result-shell">
    <header class="result-hero">
      <div class="score-orbit">
        <strong>{{ result.earnedScore }}</strong>
        <span>/ {{ result.totalScore }}</span>
      </div>
      <div>
        <span class="assessment-kicker">第 {{ result.attemptNo }} 次作答</span>
        <h2>{{ scoreRate >= 80 ? '掌握得很好' : scoreRate >= 60 ? '基础已建立' : '先从薄弱点开始巩固' }}</h2>
        <p>{{ result.report }}</p>
        <span v-if="result.profileUpdated" class="profile-sync"><Sparkles :size="16" />掌握程度与易错点已写入学习画像</span>
      </div>
      <button type="button" class="secondary-action" @click="startAttempt"><RotateCcw :size="17" />再测一次</button>
    </header>

    <section class="diagnosis-board">
      <header><div><span class="assessment-kicker">知识点诊断</span><h3>掌握度</h3></div><small>结合历次作答累计</small></header>
      <div class="diagnosis-list">
        <article v-for="item in result.diagnoses" :key="String(item.knowledgePointId)">
          <div><strong>{{ item.knowledgePointName }}</strong><span>{{ Number(item.masteryScore).toFixed(0) }}%</span></div>
          <div class="mastery-track"><span :style="{ width: `${Math.max(0, Math.min(100, Number(item.masteryScore)))}%` }" /></div>
          <small>{{ item.correctCount }} / {{ item.attemptCount }} 次答对</small>
        </article>
      </div>
    </section>

    <section class="answer-review">
      <header><span class="assessment-kicker">逐题解析</span><h3>作答回顾</h3></header>
      <article v-for="(answer, index) in result.answers" :key="String(answer.questionId)" :class="{ correct: answer.correct }">
        <header>
          <span>{{ index + 1 }}</span>
          <div><strong>{{ answer.prompt }}</strong><small>{{ answer.knowledgePointName }}</small></div>
          <CheckCircle2 v-if="answer.correct" :size="22" />
          <XCircle v-else :size="22" />
        </header>
        <div class="answer-comparison">
          <p><span>你的答案</span><strong>{{ answer.selectedValues.length ? answer.selectedValues.map((value) => optionLabel(answer.questionId, value)).join('、') : '未作答' }}</strong></p>
          <p><span>正确答案</span><strong>{{ answer.correctValues.map((value) => optionLabel(answer.questionId, value)).join('、') }}</strong></p>
          <p v-if="!answer.correct"><span>错误类型</span><strong>{{ errorTypeName(answer.errorType) }}</strong></p>
        </div>
        <p class="answer-analysis">{{ answer.analysis }}</p>
      </article>
    </section>
  </section>
</template>

<style scoped>
.assessment-shell, .result-shell { display: grid; gap: var(--space-20); }
.assessment-intro { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-24); padding-bottom: var(--space-20); border-bottom: 1px solid var(--border-subtle); }
.assessment-intro h2, .result-hero h2 { margin: var(--space-6) 0; font-family: var(--font-family-display); font-size: var(--text-title); }
.assessment-intro p, .result-hero p { max-width: 48rem; margin: 0; color: var(--text-secondary); line-height: 1.65; }
.assessment-kicker { color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; letter-spacing: .1em; }
.assessment-facts { display: flex; align-items: center; gap: var(--space-10); flex-wrap: wrap; }
.assessment-facts span, .assessment-facts strong { display: inline-flex; align-items: center; gap: var(--space-6); padding: var(--space-8) var(--space-12); border-radius: var(--radius-10); background: var(--surface-muted); color: var(--text-secondary); font-size: var(--text-caption); }
.assessment-facts strong { background: var(--surface-accent); color: var(--text-accent); }
.assessment-progress { height: .35rem; overflow: hidden; border-radius: var(--radius-16); background: var(--surface-muted); }
.assessment-progress > span { display: block; height: 100%; border-radius: inherit; background: var(--primary-500); transition: width .24s ease; }
.question-card { min-height: 22rem; padding: clamp(var(--space-20), 4vw, var(--space-32)); border: 1px solid var(--border-default); border-radius: var(--radius-16); background: var(--surface-panel-muted); }
.question-card > header { display: flex; align-items: center; justify-content: space-between; gap: var(--space-16); }
.question-card > header > div { display: flex; align-items: center; gap: var(--space-8); flex-wrap: wrap; }
.question-index { display: grid; width: 2.4rem; height: 2.4rem; place-items: center; border-radius: var(--radius-10); background: var(--surface-accent); color: var(--text-accent); font-weight: 900; }
.question-type, .question-point { padding: var(--space-6) var(--space-10); border-radius: var(--radius-16); background: var(--surface-muted); color: var(--text-tertiary); font-size: var(--text-caption); }
.question-card h3 { max-width: 52rem; margin: var(--space-24) 0 var(--space-8); font-size: var(--text-subtitle); line-height: 1.55; }
.choice-hint { margin: 0 0 var(--space-16); color: var(--text-tertiary); font-size: var(--text-caption); }
.choice-list { display: grid; gap: var(--space-10); margin-top: var(--space-20); }
.choice { display: grid; grid-template-columns: 2rem minmax(0, 1fr) auto; align-items: center; gap: var(--space-12); width: 100%; padding: var(--space-14) var(--space-16); border: 1px solid var(--border-default); border-radius: var(--radius-12); background: var(--surface-panel); color: var(--text-primary); text-align: left; transition: border-color .16s ease, background .16s ease, transform .16s ease; }
.choice:hover { transform: translateX(.2rem); border-color: var(--border-accent); }
.choice.selected { border-color: var(--border-accent); background: var(--surface-accent); color: var(--text-accent); }
.choice-code { display: grid; width: 2rem; height: 2rem; place-items: center; border-radius: var(--radius-8); background: var(--surface-muted); font-weight: 800; }
.assessment-actions { display: flex; align-items: center; justify-content: space-between; gap: var(--space-16); }
.assessment-actions > span { color: var(--text-tertiary); font-size: var(--text-caption); }
.assessment-actions > div { display: flex; gap: var(--space-10); }
.primary-action, .secondary-action { display: inline-flex; min-height: 2.7rem; align-items: center; justify-content: center; gap: var(--space-6); padding: 0 var(--space-16); border-radius: var(--radius-10); font-weight: 700; }
.primary-action { border: 1px solid var(--primary-600); background: var(--primary-600); color: var(--text-inverse); }
.secondary-action { border: 1px solid var(--border-default); background: var(--surface-panel); color: var(--text-primary); }
.primary-action:disabled, .secondary-action:disabled { cursor: not-allowed; opacity: .5; }
.assessment-error { margin: 0; padding: var(--space-10) var(--space-12); border-radius: var(--radius-10); background: var(--surface-danger); color: var(--text-danger); }
.result-shell { gap: var(--space-24); }
.result-hero { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: var(--space-24); padding: var(--space-24); border: 1px solid var(--border-accent); border-radius: var(--radius-16); background: var(--surface-accent); }
.score-orbit { display: grid; width: 7rem; height: 7rem; place-content: center; justify-items: center; border-radius: 50%; background: var(--surface-panel); box-shadow: var(--shadow-md); }
.score-orbit strong { color: var(--text-accent); font-family: var(--font-family-display); font-size: 2.4rem; line-height: 1; }
.score-orbit span { margin-top: var(--space-4); color: var(--text-tertiary); }
.profile-sync { display: inline-flex; align-items: center; gap: var(--space-6); margin-top: var(--space-12); color: var(--success-800); font-size: var(--text-caption); font-weight: 700; }
.diagnosis-board, .answer-review { display: grid; gap: var(--space-16); }
.diagnosis-board > header { display: flex; align-items: end; justify-content: space-between; gap: var(--space-16); }
.diagnosis-board h3, .answer-review h3 { margin: var(--space-4) 0 0; font-size: var(--text-subtitle); }
.diagnosis-board > header small { color: var(--text-tertiary); }
.diagnosis-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr)); gap: var(--space-12); }
.diagnosis-list article { padding: var(--space-16); border: 1px solid var(--border-default); border-radius: var(--radius-12); background: var(--surface-panel-muted); }
.diagnosis-list article > div:first-child { display: flex; justify-content: space-between; gap: var(--space-10); }
.diagnosis-list article > div:first-child span { color: var(--text-accent); font-weight: 800; }
.diagnosis-list small { color: var(--text-tertiary); }
.mastery-track { height: .45rem; margin: var(--space-12) 0 var(--space-8); overflow: hidden; border-radius: var(--radius-16); background: var(--surface-muted); }
.mastery-track span { display: block; height: 100%; border-radius: inherit; background: var(--primary-500); }
.answer-review > article { padding: var(--space-18); border: 1px solid var(--danger-100); border-radius: var(--radius-12); background: var(--surface-panel); }
.answer-review > article.correct { border-color: var(--success-100); }
.answer-review > article > header { display: grid; grid-template-columns: 2rem minmax(0, 1fr) auto; align-items: start; gap: var(--space-10); }
.answer-review > article > header > span { display: grid; width: 2rem; height: 2rem; place-items: center; border-radius: var(--radius-8); background: var(--surface-muted); font-weight: 800; }
.answer-review > article > header > div { display: grid; gap: var(--space-4); }
.answer-review > article > header small { color: var(--text-tertiary); }
.answer-review > article > header > svg { color: var(--danger-500); }
.answer-review > article.correct > header > svg { color: var(--success-500); }
.answer-comparison { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-10); margin: var(--space-16) 0; }
.answer-comparison p { display: grid; gap: var(--space-4); margin: 0; padding: var(--space-12); border-radius: var(--radius-10); background: var(--surface-muted); }
.answer-comparison span { color: var(--text-tertiary); font-size: var(--text-caption); }
.answer-analysis { margin: 0; padding-top: var(--space-14); border-top: 1px solid var(--border-subtle); color: var(--text-secondary); line-height: 1.65; }
@media (max-width: 760px) {
  .assessment-intro, .assessment-actions { align-items: stretch; flex-direction: column; }
  .assessment-actions > div { display: grid; grid-template-columns: 1fr 1fr; }
  .result-hero { grid-template-columns: 1fr; justify-items: start; }
  .score-orbit { width: 5.5rem; height: 5.5rem; }
  .answer-comparison { grid-template-columns: 1fr; }
}
@media (max-width: 480px) {
  .question-card { padding: var(--space-16); }
  .assessment-actions > div { grid-template-columns: 1fr; }
  .assessment-actions button { width: 100%; }
  .choice { grid-template-columns: 1.8rem minmax(0, 1fr) auto; padding: var(--space-12); }
}
</style>
