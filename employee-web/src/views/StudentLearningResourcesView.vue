<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  ArrowLeft,
  BookOpenCheck,
  BrainCircuit,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Code2,
  FileText,
  GitFork,
  LoaderCircle,
  Map,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
  Sparkles,
} from 'lucide-vue-next';
import {
  employeeApi,
  type CourseResponse,
  type GeneratedLearningResourceResponse,
  type LearningResourceFeedbackResponse,
  type LearningResourceFeedbackType,
  type LearningResourceTaskResponse,
  type LearningResourceType,
} from '@/api';
import AppButton from '@/components/ui/AppButton.vue';
import AppDialog from '@/components/ui/AppDialog.vue';
import AppEmptyState from '@/components/ui/AppEmptyState.vue';
import AppErrorState from '@/components/ui/AppErrorState.vue';
import AppLoadingState from '@/components/ui/AppLoadingState.vue';
import GeneratedResourceContent from '@/components/course/GeneratedResourceContent.vue';
import { createClientId } from '@/utils/request-id';
import agentTutorHero from '@/assets/agent-tutor-hero-3d.webp';

const route = useRoute();
const router = useRouter();
const course = ref<CourseResponse | null>(null);
const tasks = ref<LearningResourceTaskResponse[]>([]);
const resources = ref<GeneratedLearningResourceResponse[]>([]);
const activeTaskId = ref('');
const selectedResource = ref<GeneratedLearningResourceResponse | null>(null);
const resourceFeedback = ref<LearningResourceFeedbackResponse | null>(null);
const feedbackType = ref<LearningResourceFeedbackType | ''>('');
const feedbackComment = ref('');
const selectedPointId = ref('');
const requestMessage = ref('');
const resourceType = ref<'ALL' | LearningResourceType>('ALL');
const loading = ref(false);
const submitting = ref(false);
const retryingTaskId = ref('');
const savingFeedback = ref(false);
const error = ref('');
const notice = ref('');
const selectedStepCode = ref('');
let pollTimer: number | undefined;
let taskStream: EventSource | undefined;

const courseId = computed(() => String(route.params.courseId ?? ''));
const knowledgePoints = computed(() => course.value?.chapters.flatMap((chapter) => (
  chapter.knowledgePoints.map((point) => ({ ...point, chapterName: chapter.chapterName }))
)) ?? []);
const activeTask = computed(() => tasks.value.find((task) => String(task.taskId) === activeTaskId.value) ?? tasks.value[0] ?? null);
const filteredResources = computed(() => resourceType.value === 'ALL'
  ? resources.value
  : resources.value.filter((resource) => resource.resourceType === resourceType.value));
const activeTraceStep = computed(() => {
  const steps = activeTask.value?.steps ?? [];
  return steps.find((step) => step.roleCode === selectedStepCode.value)
    ?? steps.find((step) => step.status === 'RUNNING')
    ?? [...steps].reverse().find((step) => step.status === 'SUCCEEDED' || step.status === 'FAILED')
    ?? null;
});
const roleDefinitions = [
  { code: 'PROFILE_ANALYST', name: '画像分析', icon: BrainCircuit },
  { code: 'PATH_PLANNER', name: '路径规划', icon: GitFork },
  { code: 'CONTENT_GENERATOR', name: '内容生成', icon: FileText },
  { code: 'PRACTICE_GENERATOR', name: '练习生成', icon: Code2 },
  { code: 'FACT_CHECKER', name: '事实校验', icon: ShieldCheck },
];
const resourceTypes: Array<{ value: 'ALL' | LearningResourceType; label: string }> = [
  { value: 'ALL', label: '全部资源' },
  { value: 'EXPLANATION', label: '讲解文档' },
  { value: 'MIND_MAP', label: '思维导图' },
  { value: 'EXERCISE', label: '练习题' },
  { value: 'CODE_LAB', label: '代码实操' },
  { value: 'EXTENDED_READING', label: '拓展阅读' },
];
const feedbackOptions: Array<{ value: LearningResourceFeedbackType; label: string }> = [
  { value: 'HELPFUL', label: '有帮助' },
  { value: 'TOO_DIFFICULT', label: '太难' },
  { value: 'TOO_EASY', label: '太简单' },
  { value: 'NOT_RELEVANT', label: '不相关' },
];

watch(courseId, loadPage, { immediate: true });
watch([activeTaskId, () => activeTask.value?.status], () => {
  connectTaskStream();
});
onBeforeUnmount(() => {
  stopPolling();
  closeTaskStream();
});

async function loadPage() {
  if (!courseId.value) {
    error.value = '课程编号无效';
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const [courseDetail, taskList, resourceList] = await Promise.all([
      employeeApi.courses.detail(courseId.value),
      employeeApi.learningResources.tasks(courseId.value),
      employeeApi.learningResources.list({ courseId: courseId.value }),
    ]);
    course.value = courseDetail;
    tasks.value = taskList;
    resources.value = resourceList;
    if (!activeTaskId.value && taskList.length) activeTaskId.value = String(taskList[0].taskId);
    const requestedPoint = Array.isArray(route.query.pointId) ? route.query.pointId[0] : route.query.pointId;
    selectedPointId.value = requestedPoint && knowledgePoints.value.some((point) => String(point.knowledgePointId) === requestedPoint)
      ? requestedPoint
      : selectedPointId.value || String(knowledgePoints.value[0]?.knowledgePointId ?? '');
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '个性化资源加载失败';
  } finally {
    loading.value = false;
  }
}

async function refreshTaskState() {
  try {
    const [taskList, resourceList] = await Promise.all([
      employeeApi.learningResources.tasks(courseId.value),
      employeeApi.learningResources.list({ courseId: courseId.value }),
    ]);
    tasks.value = taskList;
    resources.value = resourceList;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '任务状态刷新失败';
  }
}

async function createTask() {
  if (!selectedPointId.value) return;
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    const task = await employeeApi.learningResources.createTask({
      courseId: courseId.value,
      knowledgePointId: selectedPointId.value,
      idempotencyKey: createClientId('learning-resource'),
      requestMessage: requestMessage.value.trim() || undefined,
    });
    tasks.value = [task, ...tasks.value.filter((item) => String(item.taskId) !== String(task.taskId))];
    activeTaskId.value = String(task.taskId);
    requestMessage.value = '';
    notice.value = '生成任务已进入后台，离开页面不会中断。';
    connectTaskStream();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '生成任务创建失败';
  } finally {
    submitting.value = false;
  }
}

async function retryTask(task: LearningResourceTaskResponse) {
  retryingTaskId.value = String(task.taskId);
  error.value = '';
  try {
    const updated = await employeeApi.learningResources.retryTask(task.taskId);
    replaceTask(updated);
    notice.value = '失败任务已重新进入后台执行。';
    connectTaskStream();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '任务重试失败';
  } finally {
    retryingTaskId.value = '';
  }
}

async function openResource(resource: GeneratedLearningResourceResponse) {
  selectedResource.value = resource;
  resourceFeedback.value = null;
  feedbackType.value = '';
  feedbackComment.value = '';
  try {
    const [, existingFeedback] = await Promise.all([
      employeeApi.learningResources.recordView(resource.resourceId),
      employeeApi.learningResources.feedback(resource.resourceId),
    ]);
    resourceFeedback.value = existingFeedback;
    feedbackType.value = existingFeedback?.feedbackType ?? '';
    feedbackComment.value = existingFeedback?.comment ?? '';
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '资源学习记录加载失败';
  }
}

async function saveFeedback() {
  const resource = selectedResource.value;
  if (!resource || !feedbackType.value) return;
  savingFeedback.value = true;
  error.value = '';
  try {
    const savedFeedback = await employeeApi.learningResources.saveFeedback(
      resource.resourceId,
      feedbackType.value,
      feedbackComment.value.trim() || undefined,
    );
    resourceFeedback.value = savedFeedback;
    feedbackComment.value = savedFeedback.comment ?? '';
    notice.value = savedFeedback.profileVersionNo
      ? `反馈已保存，学习画像已更新到 V${savedFeedback.profileVersionNo}。`
      : '反馈已保存。';
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '资源反馈保存失败';
  } finally {
    savingFeedback.value = false;
  }
}

function closeResource() {
  selectedResource.value = null;
  resourceFeedback.value = null;
}

function replaceTask(task: LearningResourceTaskResponse) {
  const index = tasks.value.findIndex((item) => String(item.taskId) === String(task.taskId));
  if (index < 0) tasks.value = [task, ...tasks.value];
  else tasks.value.splice(index, 1, task);
}

function startPolling() {
  if (pollTimer) return;
  pollTimer = window.setInterval(() => void refreshTaskState(), 1800);
}

function connectTaskStream() {
  closeTaskStream();
  stopPolling();
  const task = activeTask.value;
  if (!task || (task.status !== 'PENDING' && task.status !== 'RUNNING')) return;
  const stream = new EventSource(employeeApi.learningResources.taskEventsUrl(task.taskId));
  taskStream = stream;
  stream.addEventListener('task', (event) => {
    try {
      const updated = JSON.parse((event as MessageEvent<string>).data) as LearningResourceTaskResponse;
      replaceTask(updated);
      if (updated.status === 'SUCCEEDED' || updated.status === 'FAILED') {
        closeTaskStream();
        void refreshTaskState();
      }
    } catch {
      closeTaskStream();
      startPolling();
    }
  });
  stream.onerror = () => {
    closeTaskStream();
    startPolling();
  };
}

function closeTaskStream() {
  taskStream?.close();
  taskStream = undefined;
}

function stopPolling() {
  if (!pollTimer) return;
  window.clearInterval(pollTimer);
  pollTimer = undefined;
}

function roleState(task: LearningResourceTaskResponse, roleCode: string) {
  const step = task.steps.find((item) => item.roleCode === roleCode);
  if (step) return step.status;
  const currentIndex = roleDefinitions.findIndex((role) => role.code === task.currentRoleCode);
  const roleIndex = roleDefinitions.findIndex((role) => role.code === roleCode);
  if (task.status === 'SUCCEEDED') return 'SUCCEEDED';
  if (task.status === 'FAILED' && roleIndex > currentIndex) return 'PENDING';
  return roleIndex === currentIndex ? 'RUNNING' : 'PENDING';
}

function formatDuration(milliseconds?: number | null) {
  if (milliseconds == null) return '等待执行';
  if (milliseconds < 1000) return `${milliseconds} ms`;
  return `${(milliseconds / 1000).toFixed(1)} 秒`;
}

function resourceIcon(type: LearningResourceType) {
  if (type === 'MIND_MAP') return Map;
  if (type === 'CODE_LAB') return Code2;
  if (type === 'EXERCISE') return BookOpenCheck;
  return FileText;
}

function goBack() {
  void router.push({ name: 'employee-courses', query: { courseId: courseId.value } });
}
</script>

<template>
  <main class="student-resource-page">
    <AppLoadingState v-if="loading && !course" />
    <AppErrorState v-else-if="error && !course" title="个性化资源加载失败" :description="error" action-label="重新加载" @retry="loadPage" />

    <template v-else-if="course">
      <button type="button" class="back-link" @click="goBack"><ArrowLeft :size="18" />返回课程</button>

      <header class="resource-hero">
        <div class="resource-hero-copy">
          <span><Sparkles :size="15" />多智能体个性化资源</span>
          <h1>{{ course.courseName }}</h1>
          <p>从薄弱知识点出发，依次完成画像分析、路径规划、内容生成、练习生成与事实校验。</p>
        </div>
        <div class="resource-hero-visual" aria-hidden="true"><img :src="agentTutorHero" alt="" /><div class="hero-metric"><strong>{{ resources.length }}</strong><span>已发布资源</span></div></div>
      </header>

      <p v-if="notice" class="page-notice"><CheckCircle2 :size="17" />{{ notice }}</p>
      <p v-if="error" class="page-error">{{ error }}</p>

      <section class="generation-console">
        <header>
          <div><span class="section-kicker">生成新资源</span><h2>选择本次学习重点</h2></div>
          <span class="background-note"><Clock3 :size="15" />后台持续执行</span>
        </header>
        <form class="generation-form" @submit.prevent="createTask">
          <label>
            <span>薄弱知识点</span>
            <select v-model="selectedPointId" required>
              <option v-for="point in knowledgePoints" :key="String(point.knowledgePointId)" :value="String(point.knowledgePointId)">
                {{ point.chapterName }} · {{ point.pointName }}
              </option>
            </select>
          </label>
          <label class="request-field">
            <span>补充学习诉求 <small>选填</small></span>
            <textarea v-model="requestMessage" maxlength="500" placeholder="例如：我希望先看完整案例，再通过代码任务巩固。" />
            <small>{{ requestMessage.length }} / 500</small>
          </label>
          <AppButton variant="primary" size="lg" type="submit" :disabled="submitting || !selectedPointId">
            <LoaderCircle v-if="submitting" :size="18" class="spin" /><Sparkles v-else :size="18" />
            {{ submitting ? '正在创建' : '开始生成 5 类资源' }}
          </AppButton>
        </form>
      </section>

      <section v-if="tasks.length" class="task-workspace">
        <aside class="task-list">
          <header><strong>生成记录</strong><button type="button" aria-label="刷新任务" @click="refreshTaskState"><RefreshCw :size="16" /></button></header>
          <button v-for="task in tasks" :key="String(task.taskId)" type="button" :class="{ active: String(task.taskId) === String(activeTask?.taskId) }" @click="activeTaskId = String(task.taskId)">
            <span class="task-dot" :class="task.status.toLowerCase()" />
            <span><strong>{{ task.knowledgePointName }}</strong><small>{{ task.statusName }} · 第 {{ task.attemptNo }} 次</small></span>
            <ChevronRight :size="16" />
          </button>
        </aside>

        <article v-if="activeTask" class="task-detail">
          <header>
            <div><span class="section-kicker">画像 V{{ activeTask.profileVersionNo }}</span><h2>{{ activeTask.knowledgePointName }}</h2><p>{{ activeTask.requestMessage || '按当前学习画像生成资源' }}</p></div>
            <span class="task-status" :class="activeTask.status.toLowerCase()">{{ activeTask.statusName }}</span>
          </header>
          <div class="task-progress"><span :style="{ width: `${activeTask.progressPercent}%` }" /><b>{{ activeTask.progressPercent }}%</b></div>
          <div class="agent-dag" aria-label="多智能体执行顺序">
            <button v-for="role in roleDefinitions" :key="role.code" type="button" :class="[roleState(activeTask, role.code).toLowerCase(), { active: activeTraceStep?.roleCode === role.code }]" @click="selectedStepCode = role.code">
                <span><component :is="role.icon" :size="18" /></span>
                <div><strong>{{ role.name }}</strong><small>{{ activeTask.steps.find((item) => item.roleCode === role.code)?.statusName || (roleState(activeTask, role.code) === 'RUNNING' ? '正在执行' : '等待执行') }}</small></div>
                <em>{{ formatDuration(activeTask.steps.find((item) => item.roleCode === role.code)?.durationMs) }}</em>
            </button>
          </div>
          <section v-if="activeTraceStep" class="agent-trace" aria-live="polite">
            <header><div><span>{{ activeTraceStep.roleName }}</span><strong>{{ activeTraceStep.statusName }}</strong></div><small>{{ activeTraceStep.modelName || '等待模型' }} · {{ formatDuration(activeTraceStep.durationMs) }}</small></header>
            <div v-if="activeTraceStep.inputSummary"><span>输入摘要</span><p>{{ activeTraceStep.inputSummary }}</p></div>
            <div v-if="activeTraceStep.liveOutput" class="live-output"><span>实时生成内容 · 完成前未经事实校验</span><pre>{{ activeTraceStep.liveOutput }}</pre></div>
            <div v-if="activeTraceStep.outputSummary"><span>输出摘要</span><pre>{{ activeTraceStep.outputSummary }}</pre></div>
            <p v-if="activeTraceStep.status === 'RUNNING' && !activeTraceStep.liveOutput" class="trace-waiting"><LoaderCircle :size="15" class="spin" />模型正在准备首段内容</p>
            <p v-if="activeTraceStep.failureMessage" class="trace-failure">{{ activeTraceStep.failureMessage }}</p>
          </section>
          <aside v-if="activeTask.status === 'FAILED'" class="failure-box">
            <div><strong>{{ activeTask.failureMessage || '资源生成失败' }}</strong><small>错误编号：{{ activeTask.failureType || 'GENERATION_FAILED' }}</small></div>
            <AppButton variant="secondary" size="sm" :disabled="retryingTaskId === String(activeTask.taskId)" @click="retryTask(activeTask)"><RotateCcw :size="15" />重新执行</AppButton>
          </aside>
          <aside v-else-if="activeTask.status === 'SUCCEEDED' && !activeTask.resources.length" class="review-waiting">
            <ShieldCheck :size="20" /><span><strong>生成完成，等待教师审核发布</strong><small>页面刷新或离开后仍可在这里查看最新状态。</small></span>
          </aside>
        </article>
      </section>

      <section class="resource-library">
        <header>
          <div><span class="section-kicker">资源库</span><h2>已发布的个性化学习资源</h2></div>
          <nav aria-label="资源类型筛选">
            <button v-for="type in resourceTypes" :key="type.value" type="button" :class="{ active: resourceType === type.value }" @click="resourceType = type.value">{{ type.label }}</button>
          </nav>
        </header>
        <div v-if="filteredResources.length" class="resource-grid">
          <button v-for="resource in filteredResources" :key="String(resource.resourceId)" type="button" class="resource-card" @click="openResource(resource)">
            <span class="resource-icon"><component :is="resourceIcon(resource.resourceType)" :size="22" /></span>
            <span class="resource-copy"><small>{{ resource.resourceTypeName }} · {{ resource.chapterName }}</small><strong>{{ resource.title }}</strong><p>{{ resource.summary }}</p></span>
            <span class="resource-foot"><span><ShieldCheck :size="14" />{{ resource.validationStatusName }} {{ Math.round(resource.confidence * 100) }}%</span><ChevronRight :size="17" /></span>
          </button>
        </div>
        <AppEmptyState v-else title="暂无已发布资源" description="生成任务完成并经教师审核发布后，资源会出现在这里。" />
      </section>
    </template>

    <AppDialog v-if="selectedResource" wide>
      <div class="resource-dialog">
        <GeneratedResourceContent :resource="selectedResource" />
        <section class="resource-feedback">
          <header><strong>这份资源对你有帮助吗？</strong><small>反馈会用于调整资源偏好和后续推荐。</small></header>
          <div class="feedback-options">
            <button v-for="option in feedbackOptions" :key="option.value" type="button" :class="{ active: feedbackType === option.value }" @click="feedbackType = option.value">{{ option.label }}</button>
          </div>
          <label>
            <span>补充说明 <small>选填</small></span>
            <textarea v-model="feedbackComment" maxlength="500" rows="3" placeholder="例如：希望先给一个更基础的完整示例。" />
            <small>{{ feedbackComment.length }} / 500</small>
          </label>
          <p v-if="resourceFeedback?.profileVersionNo">上次反馈已写入画像 V{{ resourceFeedback.profileVersionNo }}</p>
        </section>
        <footer><AppButton variant="secondary" @click="closeResource">关闭</AppButton><AppButton variant="primary" :disabled="!feedbackType || savingFeedback" @click="saveFeedback">{{ savingFeedback ? '正在保存' : '保存反馈' }}</AppButton></footer>
      </div>
    </AppDialog>
  </main>
</template>

<style scoped>
.student-resource-page { height: 100%; overflow: auto; padding: var(--layout-content-pad); background-color: var(--surface-canvas); background-image: var(--canvas-backdrop); color: var(--text-primary); }.back-link { display: inline-flex; align-items: center; gap: var(--space-6); margin-bottom: var(--space-18); padding: 0; border: 0; background: transparent; color: var(--text-accent); }
.resource-hero { position: relative; display: grid; grid-template-columns: minmax(0, 1fr) minmax(20rem, 31rem); min-height: 17rem; gap: var(--space-20); overflow: hidden; margin-bottom: var(--space-18); padding: clamp(var(--space-24), 4vw, var(--space-40)); border: 1px solid var(--border-accent); border-radius: var(--radius-20); background: var(--hero-gradient); box-shadow: var(--shadow-md); }.resource-hero::after { position: absolute; right: -5rem; bottom: -8rem; width: 23rem; height: 23rem; border: 1px solid color-mix(in srgb, var(--primary-300) 36%, transparent); border-radius: 50%; content: ''; }.resource-hero-copy { position: relative; z-index: 2; display: grid; align-content: center; justify-items: start; }.resource-hero-copy > span, .section-kicker { display: inline-flex; align-items: center; gap: var(--space-6); color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; letter-spacing: .08em; }.resource-hero h1 { margin: var(--space-8) 0; font-family: var(--font-family-display); font-size: clamp(var(--text-title), 3vw, var(--text-headline)); }.resource-hero p { max-width: 48rem; margin: 0; color: var(--text-secondary); line-height: 1.7; }.resource-hero-visual { position: relative; z-index: 1; min-height: 14rem; }.resource-hero-visual > img { position: absolute; top: -4rem; right: -4rem; width: 34rem; max-width: none; filter: drop-shadow(0 1.1rem 1.5rem color-mix(in srgb, var(--primary-800) 18%, transparent)); animation: resource-agent-float 6.2s ease-in-out infinite; }.hero-metric { position: absolute; z-index: 2; bottom: var(--space-8); left: 0; display: grid; min-width: 7.5rem; justify-items: start; padding: var(--space-12) var(--space-14); border: 1px solid color-mix(in srgb, var(--primary-300) 45%, transparent); border-radius: var(--radius-12); background: var(--surface-glass); box-shadow: var(--shadow-md); backdrop-filter: blur(.75rem); }.hero-metric strong { font-family: var(--font-family-display); font-size: 2rem; color: var(--text-accent); }.hero-metric span { color: var(--text-tertiary); font-size: var(--text-caption); }
.page-notice, .page-error { display: flex; align-items: center; gap: var(--space-8); margin: 0 0 var(--space-14); padding: var(--space-10) var(--space-14); border-radius: var(--radius-10); }.page-notice { background: var(--success-surface); color: var(--success-600); }.page-error { background: var(--danger-surface); color: var(--danger-600); }
.generation-console, .task-workspace, .resource-library { margin-bottom: var(--space-18); border: 1px solid var(--border-default); border-radius: var(--radius-16); background: linear-gradient(155deg, var(--surface-panel) 80%, color-mix(in srgb, var(--surface-accent) 44%, var(--surface-panel))); box-shadow: var(--shadow-sm); }.generation-console { padding: var(--space-22); }.generation-console > header, .resource-library > header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-16); margin-bottom: var(--space-18); }.generation-console h2, .resource-library h2, .task-detail h2 { margin: var(--space-5) 0; font-family: var(--font-family-display); }.background-note { display: inline-flex; align-items: center; gap: var(--space-6); padding: var(--space-6) var(--space-9); border-radius: 999px; background: var(--surface-muted); color: var(--text-tertiary); font-size: var(--text-caption); }
.generation-form { display: grid; grid-template-columns: minmax(14rem, .8fr) minmax(18rem, 1.5fr) auto; align-items: end; gap: var(--space-14); }.generation-form label { display: grid; gap: var(--space-7); color: var(--text-secondary); font-size: var(--text-caption); font-weight: 700; }.generation-form select, .generation-form textarea { width: 100%; border: 1px solid var(--border-default); border-radius: var(--radius-8); background: var(--surface-panel); color: var(--text-primary); }.generation-form select { min-height: 2.75rem; padding: 0 var(--space-10); }.generation-form textarea { min-height: 4.5rem; padding: var(--space-10); resize: vertical; }.request-field { position: relative; }.request-field > span small { color: var(--text-tertiary); font-weight: 400; }.request-field > small { position: absolute; right: var(--space-8); bottom: var(--space-6); color: var(--text-tertiary); font-weight: 400; }
.task-workspace { display: grid; grid-template-columns: 17rem minmax(0, 1fr); overflow: hidden; }.task-list { border-right: 1px solid var(--border-default); }.task-list > header { display: flex; align-items: center; justify-content: space-between; padding: var(--space-14); border-bottom: 1px solid var(--border-subtle); }.task-list > header button { display: grid; place-items: center; border: 0; background: transparent; color: var(--text-secondary); }.task-list > button { display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: var(--space-9); width: 100%; padding: var(--space-13); border: 0; border-bottom: 1px solid var(--border-subtle); background: transparent; color: var(--text-primary); text-align: left; }.task-list > button.active { background: var(--surface-accent); }.task-list > button > span:nth-child(2) { display: grid; gap: var(--space-4); }.task-list small { color: var(--text-tertiary); }.task-dot { width: .55rem; height: .55rem; border-radius: 50%; background: var(--text-tertiary); }.task-dot.running { background: var(--primary-500); box-shadow: 0 0 0 4px var(--surface-accent); }.task-dot.succeeded { background: var(--success-500); }.task-dot.failed { background: var(--danger-500); }
.task-detail { display: grid; gap: var(--space-18); padding: var(--space-22); }.task-detail > header { display: flex; justify-content: space-between; gap: var(--space-16); }.task-detail header p { margin: 0; color: var(--text-secondary); }.task-status { align-self: flex-start; padding: var(--space-7) var(--space-10); border-radius: var(--radius-8); background: var(--surface-muted); color: var(--text-secondary); font-size: var(--text-caption); font-weight: 800; }.task-status.running { background: var(--surface-accent); color: var(--text-accent); }.task-status.succeeded { background: var(--success-surface); color: var(--success-600); }.task-status.failed { background: var(--danger-surface); color: var(--danger-600); }
.task-progress { position: relative; height: .55rem; overflow: visible; border-radius: 999px; background: var(--surface-muted); }.task-progress > span { display: block; height: 100%; border-radius: inherit; background: var(--primary-500); transition: width .35s ease; }.task-progress b { position: absolute; right: 0; top: calc(var(--space-18) * -1); color: var(--text-accent); font-size: var(--text-caption); }
.agent-dag { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: var(--space-7); padding: var(--space-4) 0; }.agent-dag > button { display: grid; min-width: 0; justify-items: center; gap: var(--space-7); padding: var(--space-12); border: 1px solid var(--border-default); border-radius: var(--radius-10); background: var(--surface-panel); color: var(--text-primary); text-align: center; }.agent-dag > button.active { box-shadow: 0 0 0 2px var(--surface-panel), 0 0 0 4px var(--border-accent); }.agent-dag > button > span { display: grid; width: 2.2rem; height: 2.2rem; place-items: center; border-radius: var(--radius-8); background: var(--surface-muted); color: var(--text-tertiary); }.agent-dag > button > div { display: grid; gap: var(--space-3); }.agent-dag small, .agent-dag em { color: var(--text-tertiary); font-size: var(--text-caption); font-style: normal; }.agent-dag > button.running { border-color: var(--border-accent); background: var(--surface-accent); }.agent-dag > button.running > span { background: var(--primary-500); color: var(--text-inverse); }.agent-dag > button.succeeded > span { background: var(--success-surface); color: var(--success-600); }.agent-dag > button.failed > span { background: var(--danger-surface); color: var(--danger-600); }
.agent-trace { display: grid; gap: var(--space-12); padding: var(--space-16); border: 1px solid var(--border-default); border-radius: var(--radius-10); background: var(--surface-muted); }.agent-trace header { display: flex; align-items: center; justify-content: space-between; gap: var(--space-10); }.agent-trace header > div { display: flex; align-items: center; gap: var(--space-8); }.agent-trace header span, .agent-trace > div > span { color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; }.agent-trace header small { color: var(--text-tertiary); }.agent-trace > div { display: grid; gap: var(--space-6); }.agent-trace p, .agent-trace pre { margin: 0; }.agent-trace pre { max-height: 16rem; overflow: auto; padding: var(--space-12); border-radius: var(--radius-8); background: var(--surface-panel); color: var(--text-secondary); font: inherit; line-height: 1.6; white-space: pre-wrap; overflow-wrap: anywhere; }.live-output pre { border-left: 3px solid var(--primary-500); }.trace-waiting { display: flex; align-items: center; gap: var(--space-7); color: var(--text-tertiary); }.trace-failure { color: var(--danger-600); }
.failure-box, .review-waiting { display: flex; align-items: center; justify-content: space-between; gap: var(--space-12); padding: var(--space-14); border-radius: var(--radius-10); }.failure-box { background: var(--danger-surface); }.failure-box > div, .review-waiting span { display: grid; gap: var(--space-4); }.failure-box small, .review-waiting small { color: var(--text-tertiary); }.review-waiting { justify-content: flex-start; background: var(--warning-surface); color: var(--warning-700); }.review-waiting strong { color: var(--text-primary); }
.resource-library { padding: var(--space-22); }.resource-library nav { display: flex; flex-wrap: wrap; gap: var(--space-5); }.resource-library nav button { padding: var(--space-7) var(--space-10); border: 1px solid transparent; border-radius: var(--radius-8); background: transparent; color: var(--text-secondary); }.resource-library nav button.active { border-color: var(--border-accent); background: var(--surface-accent); color: var(--text-accent); }.resource-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(17rem, 1fr)); gap: var(--space-12); }.resource-card { display: grid; grid-template-columns: auto 1fr; gap: var(--space-12); padding: var(--space-16); border: 1px solid var(--border-default); border-radius: var(--radius-12); background: var(--surface-panel); color: var(--text-primary); text-align: left; transition: transform .18s ease, box-shadow .18s ease; }.resource-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-sm); }.resource-icon { display: grid; width: 2.7rem; height: 2.7rem; place-items: center; border-radius: var(--radius-10); background: var(--surface-accent); color: var(--text-accent); }.resource-copy { display: grid; gap: var(--space-5); min-width: 0; }.resource-copy small { color: var(--text-accent); font-weight: 700; }.resource-copy p { display: -webkit-box; margin: 0; overflow: hidden; color: var(--text-secondary); line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.resource-foot { grid-column: 1 / -1; display: flex; align-items: center; justify-content: space-between; padding-top: var(--space-10); border-top: 1px solid var(--border-subtle); color: var(--text-tertiary); font-size: var(--text-caption); }.resource-foot > span { display: inline-flex; align-items: center; gap: var(--space-5); }
.resource-dialog { display: grid; gap: var(--space-20); }.resource-feedback { display: grid; gap: var(--space-12); padding: var(--space-16); border: 1px solid var(--border-default); border-radius: var(--radius-12); background: var(--surface-muted); }.resource-feedback header, .resource-feedback label { display: grid; gap: var(--space-4); }.resource-feedback header small, .resource-feedback label > span, .resource-feedback label > small, .resource-feedback p { color: var(--text-tertiary); font-size: var(--text-caption); }.resource-feedback label > span { color: var(--text-secondary); font-weight: 700; }.resource-feedback textarea { box-sizing: border-box; width: 100%; padding: var(--space-10); resize: vertical; border: 1px solid var(--border-default); border-radius: var(--radius-8); background: var(--surface-panel); color: var(--text-primary); font: inherit; }.resource-feedback label > small { justify-self: end; }.resource-feedback p { margin: 0; }.feedback-options { display: flex; flex-wrap: wrap; gap: var(--space-7); }.feedback-options button { padding: var(--space-7) var(--space-11); border: 1px solid var(--border-default); border-radius: var(--radius-8); background: var(--surface-panel); color: var(--text-secondary); }.feedback-options button.active { border-color: var(--border-accent); background: var(--surface-accent); color: var(--text-accent); }.resource-dialog footer { display: flex; justify-content: flex-end; gap: var(--space-8); padding-top: var(--space-14); border-top: 1px solid var(--border-subtle); }.spin { animation: spin .8s linear infinite; }@keyframes spin { to { transform: rotate(360deg); } }@keyframes resource-agent-float { 0%, 100% { transform: translate3d(0, 0, 0) rotate(.45deg); } 50% { transform: translate3d(0, -.5rem, 0) rotate(-.35deg); } }
@media (max-width: 1000px) { .generation-form { grid-template-columns: 1fr 1fr; }.generation-form > :last-child { grid-column: 1 / -1; }.task-workspace { grid-template-columns: 1fr; }.task-list { display: flex; overflow-x: auto; border-right: 0; border-bottom: 1px solid var(--border-default); }.task-list > header { flex: 0 0 auto; }.task-list > button { min-width: 15rem; border-right: 1px solid var(--border-subtle); border-bottom: 0; }.agent-dag { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 900px) { .resource-hero { grid-template-columns: 1fr; }.resource-hero-visual { min-height: 15rem; }.resource-hero-visual > img { top: -2rem; right: -3rem; width: 30rem; } }
@media (max-width: 640px) { .student-resource-page { padding: var(--space-12); padding-bottom: 7rem; }.resource-hero { min-height: 0; padding: var(--space-20); }.resource-hero-visual { min-height: 12rem; }.resource-hero-visual > img { top: -1rem; right: -4rem; width: 25rem; }.hero-metric { bottom: 0; left: 0; }.generation-console > header, .resource-library > header, .task-detail > header, .agent-trace header { align-items: stretch; flex-direction: column; }.generation-form { grid-template-columns: 1fr; }.generation-form > :last-child { grid-column: auto; }.task-list > header { display: none; }.task-list > button { min-width: 13rem; }.task-detail, .generation-console, .resource-library { padding: var(--space-16); }.failure-box { align-items: stretch; flex-direction: column; }.agent-dag { grid-template-columns: 1fr; }.resource-grid { grid-template-columns: 1fr; } }
@media (prefers-reduced-motion: reduce) { .resource-hero-visual > img { animation: none; } }
</style>
