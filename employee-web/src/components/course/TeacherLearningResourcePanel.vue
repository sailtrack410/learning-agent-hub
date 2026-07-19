<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import {
  Check,
  ChevronRight,
  Clock3,
  Eye,
  FileCheck2,
  FilePenLine,
  Filter,
  LoaderCircle,
  RefreshCw,
  Rocket,
  RotateCcw,
  Search,
  Send,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  Undo2,
  UserRound,
  UsersRound,
  X,
} from 'lucide-vue-next';
import {
  employeeApi,
  type CourseLearningOverviewResponse,
  type CourseResponse,
  type GeneratedLearningResourceResponse,
  type LearningResourcePublicationStatus,
  type LearningResourceTaskResponse,
  type LearningResourceType,
} from '@/api';
import AppButton from '@/components/ui/AppButton.vue';
import AppDialog from '@/components/ui/AppDialog.vue';
import AppEmptyState from '@/components/ui/AppEmptyState.vue';
import AppLoadingState from '@/components/ui/AppLoadingState.vue';
import GeneratedResourceContent from './GeneratedResourceContent.vue';

const props = defineProps<{
  course: CourseResponse;
}>();

const tasks = ref<LearningResourceTaskResponse[]>([]);
const resources = ref<GeneratedLearningResourceResponse[]>([]);
const loading = ref(false);
const actionResourceId = ref('');
const error = ref('');
const notice = ref('');
const statusFilter = ref<'ALL' | LearningResourcePublicationStatus>('ALL');
const typeFilter = ref<'ALL' | LearningResourceType>('ALL');
const pointFilter = ref('');
const selectedResource = ref<GeneratedLearningResourceResponse | null>(null);
const editTarget = ref<GeneratedLearningResourceResponse | null>(null);
const rejectTarget = ref<GeneratedLearningResourceResponse | null>(null);
const pushTarget = ref<GeneratedLearningResourceResponse | null>(null);
const pushOverview = ref<CourseLearningOverviewResponse | null>(null);
const pushTargetType = ref<'STUDENT' | 'CLASS'>('STUDENT');
const pushTargetId = ref('');
const pushKeyword = ref('');
const pushLoading = ref(false);
const editForm = ref({ title: '', summary: '', content: '' });
const rejectNote = ref('');
let pollTimer: number | undefined;

const knowledgePoints = computed(() => props.course.chapters.flatMap((chapter) => chapter.knowledgePoints));
const pendingReviewCount = computed(() => resources.value.filter((item) => item.publicationStatus === 'DRAFT').length);
const runningTaskCount = computed(() => tasks.value.filter((item) => item.status === 'PENDING' || item.status === 'RUNNING').length);
const publishedCount = computed(() => resources.value.filter((item) => item.publicationStatus === 'PUBLISHED').length);
const hasRunningTask = computed(() => runningTaskCount.value > 0);
const pushClasses = computed(() => pushOverview.value?.classes.filter((item) => item.classId != null) ?? []);
const pushStudents = computed(() => {
  const keyword = pushKeyword.value.trim().toLowerCase();
  const students = pushOverview.value?.students ?? [];
  if (!keyword) return students;
  return students.filter((student) => `${student.studentName} ${student.studentNo} ${student.className ?? ''}`.toLowerCase().includes(keyword));
});

watch(() => props.course.courseId, loadData, { immediate: true });
watch([statusFilter, typeFilter, pointFilter], loadResources);
watch(hasRunningTask, (running) => {
  if (running) startPolling();
  else stopPolling();
});
onBeforeUnmount(stopPolling);

async function loadData() {
  loading.value = true;
  error.value = '';
  try {
    await Promise.all([loadTasks(), loadResources()]);
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '资源治理数据加载失败';
  } finally {
    loading.value = false;
  }
}

async function loadTasks() {
  tasks.value = await employeeApi.teacherLearningResources.tasks({ courseId: props.course.courseId });
}

async function loadResources() {
  resources.value = await employeeApi.teacherLearningResources.list({
    courseId: props.course.courseId,
    knowledgePointId: pointFilter.value || undefined,
    resourceType: typeFilter.value === 'ALL' ? undefined : typeFilter.value,
    status: statusFilter.value === 'ALL' ? undefined : statusFilter.value,
  });
}

async function refresh() {
  error.value = '';
  try {
    await Promise.all([loadTasks(), loadResources()]);
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '刷新失败';
  }
}

function startPolling() {
  if (pollTimer) return;
  pollTimer = window.setInterval(() => void refresh(), 2000);
}

function stopPolling() {
  if (!pollTimer) return;
  window.clearInterval(pollTimer);
  pollTimer = undefined;
}

function openEdit(resource: GeneratedLearningResourceResponse) {
  editTarget.value = resource;
  editForm.value = { title: resource.title, summary: resource.summary, content: resource.content };
}

async function saveEdit() {
  if (!editTarget.value) return;
  await runAction(editTarget.value.resourceId, async () => {
    const updated = await employeeApi.teacherLearningResources.update(editTarget.value!.resourceId, {
      title: editForm.value.title.trim(),
      summary: editForm.value.summary.trim(),
      content: editForm.value.content.trim(),
    });
    replaceResource(updated);
    editTarget.value = null;
    notice.value = '资源已保存，修改后的内容需要重新审核。';
  });
}

async function approve(resource: GeneratedLearningResourceResponse) {
  await runAction(resource.resourceId, async () => {
    replaceResource(await employeeApi.teacherLearningResources.review(resource.resourceId, 'APPROVE', '引用与正文已核对'));
    notice.value = '资源已审核通过，可以发布给学生。';
  });
}

function openReject(resource: GeneratedLearningResourceResponse) {
  rejectTarget.value = resource;
  rejectNote.value = '';
}

async function reject() {
  if (!rejectTarget.value || !rejectNote.value.trim()) return;
  await runAction(rejectTarget.value.resourceId, async () => {
    replaceResource(await employeeApi.teacherLearningResources.review(
      rejectTarget.value!.resourceId,
      'REJECT',
      rejectNote.value.trim(),
    ));
    rejectTarget.value = null;
    notice.value = '资源已拒绝，不会向学生发布。';
  });
}

async function publish(resource: GeneratedLearningResourceResponse) {
  await runAction(resource.resourceId, async () => {
    replaceResource(await employeeApi.teacherLearningResources.publish(resource.resourceId));
    notice.value = '资源已发布，学生资源库已同步更新。';
  });
}

async function withdraw(resource: GeneratedLearningResourceResponse) {
  await runAction(resource.resourceId, async () => {
    replaceResource(await employeeApi.teacherLearningResources.withdraw(resource.resourceId));
    notice.value = '资源已撤回，学生端不再展示。';
  });
}

async function openPush(resource: GeneratedLearningResourceResponse) {
  pushTarget.value = resource;
  pushOverview.value = null;
  pushTargetId.value = '';
  pushKeyword.value = '';
  pushLoading.value = true;
  error.value = '';
  try {
    pushOverview.value = await employeeApi.teacherCourses.learningOverview(props.course.courseId);
    pushTargetType.value = pushClasses.value.length ? 'CLASS' : 'STUDENT';
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '推送目标加载失败';
    pushTarget.value = null;
  } finally {
    pushLoading.value = false;
  }
}

async function submitPush() {
  if (!pushTarget.value || !pushTargetId.value) return;
  const resource = pushTarget.value;
  await runAction(resource.resourceId, async () => {
    const result = await employeeApi.teacherLearningResources.push(resource.resourceId, pushTargetType.value, pushTargetId.value);
    pushTarget.value = null;
    notice.value = `已将“${resource.title}”推送给${result.targetName}`;
  });
}

async function runAction(resourceId: string | number, action: () => Promise<void>) {
  actionResourceId.value = String(resourceId);
  error.value = '';
  notice.value = '';
  try {
    await action();
    await loadResources();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '资源操作失败';
  } finally {
    actionResourceId.value = '';
  }
}

function replaceResource(resource: GeneratedLearningResourceResponse) {
  const index = resources.value.findIndex((item) => String(item.resourceId) === String(resource.resourceId));
  if (index >= 0) resources.value.splice(index, 1, resource);
}

function taskStepSummary(task: LearningResourceTaskResponse) {
  const succeeded = task.steps.filter((step) => step.status === 'SUCCEEDED').length;
  return `${succeeded} / 5 个角色完成`;
}

function formatDuration(milliseconds?: number | null) {
  if (milliseconds == null) return '—';
  return milliseconds < 1000 ? `${milliseconds} ms` : `${(milliseconds / 1000).toFixed(1)} 秒`;
}
</script>

<template>
  <section class="teacher-resource-panel">
    <header class="panel-hero">
      <div><span><Sparkles :size="15" />多智能体资源治理</span><h2>生成任务与内容审核</h2><p>查看五角色执行轨迹，核对引用后再修改、审核和发布资源。</p></div>
      <AppButton variant="secondary" size="sm" :disabled="loading" @click="refresh"><RefreshCw :size="16" />刷新</AppButton>
    </header>

    <p v-if="notice" class="panel-notice"><Check :size="16" />{{ notice }}</p>
    <p v-if="error" class="panel-error">{{ error }}</p>

    <section class="resource-metrics">
      <article><span><LoaderCircle :size="18" /></span><div><strong>{{ runningTaskCount }}</strong><small>执行中任务</small></div></article>
      <article><span><ShieldAlert :size="18" /></span><div><strong>{{ pendingReviewCount }}</strong><small>待审核资源</small></div></article>
      <article><span><FileCheck2 :size="18" /></span><div><strong>{{ publishedCount }}</strong><small>已发布资源</small></div></article>
    </section>

    <section class="generation-tasks">
      <header><div><span>生成任务</span><h3>五角色执行轨迹</h3></div><small>页面关闭后任务继续执行</small></header>
      <div v-if="tasks.length" class="task-table">
        <article v-for="task in tasks" :key="String(task.taskId)">
          <span class="task-state" :class="task.status.toLowerCase()"><LoaderCircle v-if="task.status === 'RUNNING'" :size="16" /><Check v-else-if="task.status === 'SUCCEEDED'" :size="16" /><X v-else-if="task.status === 'FAILED'" :size="16" /><Clock3 v-else :size="16" /></span>
          <div><strong>{{ task.knowledgePointName }}</strong><small>学生 {{ task.studentUserId }} · 画像 V{{ task.profileVersionNo }}</small></div>
          <div class="task-bar"><span :style="{ width: `${task.progressPercent}%` }" /></div>
          <div><strong>{{ task.progressPercent }}%</strong><small>{{ taskStepSummary(task) }}</small></div>
          <div><strong>{{ task.statusName }}</strong><small>{{ task.failureMessage || formatDuration(task.totalDurationMs) }}</small></div>
        </article>
      </div>
      <AppEmptyState v-else title="暂无生成任务" description="学生发起个性化资源生成后，任务会显示在这里。" />
    </section>

    <section class="resource-governance">
      <header>
        <div><span>资源库</span><h3>审核与发布</h3></div>
        <div class="resource-filters"><Filter :size="16" />
          <select v-model="pointFilter"><option value="">全部知识点</option><option v-for="point in knowledgePoints" :key="String(point.knowledgePointId)" :value="String(point.knowledgePointId)">{{ point.pointName }}</option></select>
          <select v-model="typeFilter"><option value="ALL">全部类型</option><option value="EXPLANATION">讲解文档</option><option value="MIND_MAP">思维导图</option><option value="EXERCISE">练习题</option><option value="CODE_LAB">代码实操</option><option value="EXTENDED_READING">拓展阅读</option></select>
          <select v-model="statusFilter"><option value="ALL">全部状态</option><option value="DRAFT">待审核</option><option value="PUBLISHED">已发布</option><option value="WITHDRAWN">已撤回</option><option value="REJECTED">已拒绝</option></select>
        </div>
      </header>

      <div v-if="resources.length" class="governance-list">
        <article v-for="resource in resources" :key="String(resource.resourceId)">
          <span class="resource-type">{{ resource.resourceTypeName }}</span>
          <div class="resource-main"><small>{{ resource.knowledgePointName }} · 学生资源</small><strong>{{ resource.title }}</strong><p>{{ resource.summary }}</p></div>
          <div class="resource-validation"><span :class="resource.validationStatus.toLowerCase()"><ShieldCheck :size="15" />{{ resource.validationStatusName }}</span><small>置信度 {{ Math.round(resource.confidence * 100) }}%</small></div>
          <span class="publication-state" :class="resource.publicationStatus.toLowerCase()">{{ resource.publicationStatusName }}</span>
          <div class="resource-actions">
            <AppButton variant="ghost" size="sm" @click="selectedResource = resource"><Eye :size="15" />查看</AppButton>
            <AppButton v-if="resource.publicationStatus !== 'PUBLISHED'" variant="ghost" size="sm" @click="openEdit(resource)"><FilePenLine :size="15" />修改</AppButton>
            <AppButton v-if="resource.publicationStatus === 'DRAFT' && resource.validationStatus !== 'PASSED'" variant="secondary" size="sm" :disabled="actionResourceId === String(resource.resourceId)" @click="approve(resource)"><ShieldCheck :size="15" />通过</AppButton>
            <AppButton v-if="resource.publicationStatus === 'DRAFT'" variant="destructive" size="sm" :disabled="actionResourceId === String(resource.resourceId)" @click="openReject(resource)"><X :size="15" />拒绝</AppButton>
            <AppButton v-if="resource.publicationStatus === 'DRAFT' && resource.validationStatus === 'PASSED'" variant="primary" size="sm" :disabled="actionResourceId === String(resource.resourceId)" @click="publish(resource)"><Rocket :size="15" />发布</AppButton>
            <AppButton v-if="resource.publicationStatus === 'PUBLISHED'" variant="primary" size="sm" :disabled="pushLoading" @click="openPush(resource)"><Send :size="15" />推送</AppButton>
            <AppButton v-if="resource.publicationStatus === 'PUBLISHED'" variant="secondary" size="sm" :disabled="actionResourceId === String(resource.resourceId)" @click="withdraw(resource)"><Undo2 :size="15" />撤回</AppButton>
          </div>
          <ChevronRight :size="17" class="row-chevron" />
        </article>
      </div>
      <AppEmptyState v-else title="当前筛选下暂无资源" description="生成任务完成后，5 类资源会进入待审核列表。" />
    </section>

    <AppDialog v-if="selectedResource" wide>
      <div class="detail-dialog"><GeneratedResourceContent :resource="selectedResource" /><footer><AppButton variant="primary" @click="selectedResource = null">关闭</AppButton></footer></div>
    </AppDialog>

    <AppDialog v-if="editTarget" wide>
      <form class="edit-dialog" @submit.prevent="saveEdit">
        <header><span>教师修改</span><h2>{{ editTarget.resourceTypeName }}</h2><p>修改后需要重新审核，已发布资源必须先撤回。</p></header>
        <label><span>标题</span><input v-model="editForm.title" maxlength="256" required /></label>
        <label><span>摘要</span><textarea v-model="editForm.summary" maxlength="1000" required /></label>
        <label><span>正文</span><textarea v-model="editForm.content" class="content-editor" maxlength="100000" required /></label>
        <footer><AppButton variant="secondary" type="button" @click="editTarget = null">取消</AppButton><AppButton variant="primary" type="submit" :disabled="actionResourceId === String(editTarget.resourceId)"><FilePenLine :size="16" />保存修改</AppButton></footer>
      </form>
    </AppDialog>

    <AppDialog v-if="rejectTarget">
      <form class="reject-dialog" @submit.prevent="reject">
        <header><ShieldAlert :size="22" /><div><h2>拒绝这条资源？</h2><p>拒绝后资源不会向学生发布，仍可修改后重新审核。</p></div></header>
        <label><span>拒绝原因</span><textarea v-model="rejectNote" maxlength="1000" required placeholder="说明事实错误、引用不足或需要调整的内容" /></label>
        <footer><AppButton variant="secondary" type="button" @click="rejectTarget = null">取消</AppButton><AppButton variant="destructive" type="submit" :disabled="!rejectNote.trim() || actionResourceId === String(rejectTarget.resourceId)">确认拒绝</AppButton></footer>
      </form>
    </AppDialog>

    <AppDialog v-if="pushTarget" wide>
      <form class="push-dialog" @submit.prevent="submitPush">
        <header><span><Send :size="16" />定向推送</span><h2>{{ pushTarget.title }}</h2><p>选择一个班级或学生，重复推送会恢复原有推送关系，不会产生重复记录。</p></header>
        <AppLoadingState v-if="pushLoading" />
        <nav v-else aria-label="推送目标类型"><button type="button" :class="{ active: pushTargetType === 'CLASS' }" :disabled="!pushClasses.length" @click="pushTargetType = 'CLASS'; pushTargetId = ''"><UsersRound :size="17" />按班级</button><button type="button" :class="{ active: pushTargetType === 'STUDENT' }" @click="pushTargetType = 'STUDENT'; pushTargetId = ''"><UserRound :size="17" />按学生</button></nav>
        <div v-if="!pushLoading && pushTargetType === 'CLASS'" class="push-options">
          <label v-for="item in pushClasses" :key="String(item.classId)"><input v-model="pushTargetId" type="radio" name="push-class" :value="String(item.classId)" /><span><strong>{{ item.className }}</strong><small>{{ item.studentCount }} 人 · {{ item.riskStudentCount }} 人需关注 · 完成率 {{ Math.round(item.completionRate) }}%</small></span></label>
          <AppEmptyState v-if="!pushClasses.length" title="暂无可推送班级" description="可切换到按学生推送。" />
        </div>
        <div v-else-if="!pushLoading" class="student-picker">
          <label class="push-search"><Search :size="16" /><input v-model="pushKeyword" placeholder="搜索姓名、学号或班级" /></label>
          <div class="push-options">
            <label v-for="student in pushStudents" :key="String(student.studentUserId)"><input v-model="pushTargetId" type="radio" name="push-student" :value="String(student.studentUserId)" /><span><strong>{{ student.studentName }}</strong><small>{{ student.studentNo }} · {{ student.className || '未分班' }} · 进度 {{ Math.round(student.progressPercent) }}%</small></span><em v-if="student.riskLevel !== 'LOW'" :class="student.riskLevel.toLowerCase()">{{ student.riskLevel === 'HIGH' ? '高风险' : '需关注' }}</em></label>
            <AppEmptyState v-if="!pushStudents.length" title="未找到学生" description="请调整搜索关键词。" />
          </div>
        </div>
        <footer><AppButton variant="secondary" type="button" @click="pushTarget = null">取消</AppButton><AppButton variant="primary" type="submit" :disabled="!pushTargetId || actionResourceId === String(pushTarget.resourceId)"><Send :size="16" />确认推送</AppButton></footer>
      </form>
    </AppDialog>
  </section>
</template>

<style scoped>
.teacher-resource-panel { display: grid; gap: var(--space-16); }.panel-hero, .generation-tasks, .resource-governance { border: 1px solid var(--border-default); border-radius: var(--radius-16); background: var(--surface-panel); box-shadow: var(--shadow-sm); }.panel-hero { position: relative; isolation: isolate; display: flex; overflow: hidden; align-items: flex-start; justify-content: space-between; gap: var(--space-16); padding: var(--space-24); background: linear-gradient(135deg, var(--surface-panel), var(--surface-accent)); }.panel-hero::after { position: absolute; z-index: -1; top: -6rem; right: 9rem; width: 14rem; height: 14rem; border: 1px solid var(--border-accent); border-radius: 50%; content: ''; opacity: .4; }.panel-hero span, .generation-tasks > header span, .resource-governance > header span { display: inline-flex; align-items: center; gap: var(--space-6); color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; letter-spacing: .08em; }.panel-hero h2 { margin: var(--space-5) 0; font-family: var(--font-family-display); }.panel-hero p { max-width: 48rem; margin: 0; color: var(--text-secondary); line-height: 1.6; }.panel-notice, .panel-error { margin: 0; padding: var(--space-10) var(--space-14); border-radius: var(--radius-10); }.panel-notice { display: flex; align-items: center; gap: var(--space-7); background: var(--success-surface); color: var(--success-600); }.panel-error { background: var(--danger-surface); color: var(--danger-600); }
.resource-metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--space-10); }.resource-metrics article { position: relative; display: flex; overflow: hidden; align-items: center; gap: var(--space-12); padding: var(--space-16); border: 1px solid var(--border-default); border-radius: var(--radius-12); background: linear-gradient(145deg, var(--surface-panel), var(--surface-muted)); box-shadow: var(--shadow-sm); }.resource-metrics article:nth-child(2) { border-color: color-mix(in srgb, var(--warning-400) 44%, var(--border-default)); }.resource-metrics article::after { position: absolute; right: -2rem; bottom: -2rem; width: 5rem; height: 5rem; border: 1px solid var(--border-accent); border-radius: 50%; content: ''; opacity: .25; }.resource-metrics article > span { display: grid; width: 2.5rem; height: 2.5rem; place-items: center; border-radius: var(--radius-9); background: var(--surface-accent); color: var(--text-accent); }.resource-metrics article:nth-child(2) > span { background: var(--warning-surface); color: var(--warning-700); }.resource-metrics div { display: grid; }.resource-metrics strong { font-family: var(--font-family-display); font-size: var(--text-title-sm); }.resource-metrics small { color: var(--text-tertiary); }
.generation-tasks, .resource-governance { padding: var(--space-20); }.generation-tasks > header, .resource-governance > header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-14); margin-bottom: var(--space-14); padding-bottom: var(--space-14); border-bottom: 1px solid var(--border-subtle); }.generation-tasks h3, .resource-governance h3 { margin: var(--space-4) 0 0; font-family: var(--font-family-display); }.generation-tasks > header > small { color: var(--text-tertiary); }
.task-table { display: grid; gap: var(--space-8); }.task-table > article { display: grid; grid-template-columns: auto minmax(12rem, 1.3fr) minmax(8rem, 1fr) minmax(7rem, .65fr) minmax(10rem, 1fr); align-items: center; gap: var(--space-12); padding: var(--space-12); border: 1px solid var(--border-subtle); border-radius: var(--radius-10); background: var(--surface-muted); }.task-table > article > div { display: grid; gap: var(--space-3); }.task-table small { overflow: hidden; color: var(--text-tertiary); text-overflow: ellipsis; white-space: nowrap; }.task-state { display: grid; width: 2.1rem; height: 2.1rem; place-items: center; border-radius: 50%; background: var(--surface-panel); color: var(--text-tertiary); }.task-state.running { background: var(--surface-accent); color: var(--text-accent); }.task-state.succeeded { background: var(--success-surface); color: var(--success-600); }.task-state.failed { background: var(--danger-surface); color: var(--danger-600); }.task-bar { height: .42rem; overflow: hidden; border-radius: var(--radius-16); background: var(--surface-panel); }.task-bar span { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--primary-500), var(--success-500)); }
.resource-filters { display: flex; align-items: center; gap: var(--space-7); }.resource-filters select { min-height: 2.35rem; padding: 0 var(--space-9); border: 1px solid var(--border-default); border-radius: var(--radius-8); background: var(--surface-muted); color: var(--text-primary); }.governance-list { display: grid; gap: var(--space-8); }.governance-list > article { display: grid; grid-template-columns: 6.5rem minmax(14rem, 1fr) 9rem 6rem auto auto; align-items: center; gap: var(--space-12); padding: var(--space-14); border: 1px solid var(--border-subtle); border-radius: var(--radius-10); background: color-mix(in srgb, var(--surface-panel) 80%, var(--surface-muted)); transition: border-color var(--transition-base), box-shadow var(--transition-base); }.governance-list > article:hover { border-color: var(--border-accent); box-shadow: var(--shadow-sm); }.resource-type, .publication-state { justify-self: start; padding: var(--space-6) var(--space-8); border-radius: var(--radius-8); background: var(--surface-muted); color: var(--text-secondary); font-size: var(--text-caption); font-weight: 800; }.resource-type { background: var(--surface-accent); color: var(--text-accent); }.publication-state.published { background: var(--success-surface); color: var(--success-600); }.publication-state.rejected { background: var(--danger-surface); color: var(--danger-600); }.publication-state.withdrawn { background: var(--warning-surface); color: var(--warning-700); }.resource-main { display: grid; gap: var(--space-4); min-width: 0; }.resource-main small { color: var(--text-accent); }.resource-main p { overflow: hidden; margin: 0; color: var(--text-secondary); text-overflow: ellipsis; white-space: nowrap; }.resource-validation { display: grid; gap: var(--space-4); }.resource-validation > span { display: inline-flex; align-items: center; gap: var(--space-5); color: var(--text-secondary); font-size: var(--text-caption); font-weight: 800; }.resource-validation > span.passed { color: var(--success-600); }.resource-validation > span.needs_review { color: var(--warning-700); }.resource-validation > span.rejected { color: var(--danger-600); }.resource-validation small { color: var(--text-tertiary); }.resource-actions { display: flex; justify-content: flex-end; gap: var(--space-5); }.row-chevron { color: var(--text-tertiary); }
.detail-dialog, .edit-dialog, .reject-dialog, .push-dialog { display: grid; gap: var(--space-18); min-width: 0; max-width: 100%; }.detail-dialog footer, .edit-dialog footer, .reject-dialog footer, .push-dialog footer { display: flex; justify-content: flex-end; gap: var(--space-8); padding-top: var(--space-14); border-top: 1px solid var(--border-subtle); }.edit-dialog header span, .push-dialog header > span { display: inline-flex; align-items: center; gap: var(--space-6); color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; }.edit-dialog h2, .reject-dialog h2, .push-dialog h2 { margin: var(--space-4) 0; }.edit-dialog header p, .reject-dialog p, .push-dialog header p { margin: 0; color: var(--text-secondary); }.edit-dialog label, .reject-dialog label { display: grid; gap: var(--space-7); color: var(--text-secondary); font-size: var(--text-caption); font-weight: 700; }.edit-dialog input, .edit-dialog textarea, .reject-dialog textarea { width: 100%; padding: var(--space-10); border: 1px solid var(--border-default); border-radius: var(--radius-8); background: var(--surface-panel); color: var(--text-primary); }.edit-dialog textarea, .reject-dialog textarea { min-height: 5rem; resize: vertical; }.edit-dialog .content-editor { min-height: 18rem; font-family: var(--font-family-mono); line-height: 1.6; }.reject-dialog header { display: flex; align-items: flex-start; gap: var(--space-12); color: var(--danger-600); }
.push-dialog > nav { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-8); }.push-dialog > nav button { display: flex; align-items: center; justify-content: center; gap: var(--space-7); min-height: 2.7rem; border: 1px solid var(--border-default); border-radius: var(--radius-9); background: var(--surface-panel); color: var(--text-secondary); }.push-dialog > nav button.active { border-color: var(--border-accent); background: var(--surface-accent); color: var(--text-accent); font-weight: 800; }.push-dialog > nav button:disabled { opacity: .45; }.student-picker { display: grid; gap: var(--space-10); }.push-search { display: flex; align-items: center; gap: var(--space-8); padding: 0 var(--space-10); border: 1px solid var(--border-default); border-radius: var(--radius-9); color: var(--text-tertiary); }.push-search input { width: 100%; min-height: 2.6rem; border: 0; outline: 0; background: transparent; color: var(--text-primary); }.push-options { display: grid; max-height: 22rem; overflow: auto; border: 1px solid var(--border-default); border-radius: var(--radius-10); }.push-options > label { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: var(--space-10); padding: var(--space-12); border-bottom: 1px solid var(--border-subtle); cursor: pointer; }.push-options > label:last-of-type { border-bottom: 0; }.push-options > label:has(input:checked) { background: var(--surface-accent); }.push-options input { accent-color: var(--primary-500); }.push-options label > span { display: grid; gap: var(--space-3); min-width: 0; }.push-options small { overflow: hidden; color: var(--text-tertiary); text-overflow: ellipsis; white-space: nowrap; }.push-options em { padding: var(--space-4) var(--space-7); border-radius: var(--radius-7); background: var(--warning-surface); color: var(--warning-700); font-size: var(--text-caption); font-style: normal; font-weight: 800; }.push-options em.high { background: var(--danger-surface); color: var(--danger-600); }
@media (max-width: 1100px) { .task-table > article { grid-template-columns: auto minmax(12rem, 1fr) minmax(8rem, 1fr); }.task-table > article > div:nth-last-child(-n + 2) { display: none; }.governance-list > article { grid-template-columns: 6rem minmax(12rem, 1fr) 8rem auto; }.resource-validation, .row-chevron { display: none; }.resource-actions { grid-column: 2 / -1; justify-content: flex-start; } }
@media (max-width: 700px) { .panel-hero, .generation-tasks > header, .resource-governance > header { align-items: stretch; flex-direction: column; }.resource-metrics { grid-template-columns: 1fr; }.resource-filters { align-items: stretch; flex-direction: column; }.resource-filters > svg { display: none; }.task-table > article { grid-template-columns: auto minmax(0, 1fr); }.task-table .task-bar { grid-column: 1 / -1; }.governance-list > article { grid-template-columns: 1fr auto; }.resource-type { grid-column: 1; }.resource-main { grid-column: 1 / -1; }.publication-state { grid-column: 2; grid-row: 1; }.resource-actions { grid-column: 1 / -1; flex-wrap: wrap; }.generation-tasks, .resource-governance, .panel-hero { padding: var(--space-16); }.push-options > label { grid-template-columns: auto minmax(0, 1fr); }.push-options em { grid-column: 2; justify-self: start; } }
</style>
