<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ArrowRight } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type {
  AdminAgentTaskMetricsResponse,
  StorageSummaryResponse,
} from '@/api/types';
import { errorMessage, formatBytes } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminHeroBanner from '@/components/AdminHeroBanner.vue';
import AdminMetricStrip from '@/components/AdminMetricStrip.vue';
import AdminSkeleton from '@/components/AdminSkeleton.vue';
import dashboardHero from '@/assets/education/admin-dashboard-hero.webp';
import taskQueueIcon from '@/assets/education/icons/admin-task-queue.webp';
import knowledgeBaseIcon from '@/assets/education/icons/admin-knowledge-base.webp';
import fileSpaceIcon from '@/assets/education/icons/admin-file-space.webp';
import pendingConfirmIcon from '@/assets/education/icons/admin-pending-confirm.webp';
import failedTaskIcon from '@/assets/education/icons/admin-failed-task.webp';
import runningTaskIcon from '@/assets/education/icons/admin-running-task.webp';
import storageQuotaIcon from '@/assets/education/icons/admin-storage-quota.webp';

const toast = useToast();
const loading = ref(true);
const taskMetrics = ref<AdminAgentTaskMetricsResponse | null>(null);
const storage = ref<StorageSummaryResponse | null>(null);

async function load() {
  loading.value = true;
  try {
    const [tm, st] = await Promise.all([
      adminApi.agentTasks.metrics(),
      adminApi.files.storageSummary(),
    ]);
    taskMetrics.value = tm;
    storage.value = st;
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

onMounted(load);

const taskCards = computed(() => {
  const v = taskMetrics.value;
  return [
    { label: '任务总数', value: v?.totalTaskCount ?? 0, icon: taskQueueIcon, hint: '全部智能任务' },
    { label: '运行中', value: v?.runningTaskCount ?? 0, icon: runningTaskIcon, hint: '正在执行', tone: 'success' as const },
    { label: '待确认', value: v?.waitingHumanConfirmTaskCount ?? 0, icon: pendingConfirmIcon, hint: '等待人工处理', tone: 'warning' as const },
    { label: '失败', value: v?.failedTaskCount ?? 0, icon: failedTaskIcon, hint: '需要定位原因', tone: 'danger' as const },
  ];
});

const storageCards = computed(() => {
  const v = storage.value;
  return [
    { label: '已用空间', value: formatBytes(v?.orgPoolUsedBytes ?? 0), icon: fileSpaceIcon, hint: '组织文件池' },
    { label: '配额', value: formatBytes(v?.orgPoolQuotaBytes ?? 0), icon: storageQuotaIcon, hint: '当前存储上限' },
    { label: '文件数', value: v?.fileObjectCount ?? 0, icon: knowledgeBaseIcon, hint: '已登记文件对象' },
    { label: '回收站', value: v?.recycleBinCount ?? 0, hint: '待清理对象' },
  ];
});

const exceptionActions = computed(() => [
  {
    label: '待人工确认',
    value: taskMetrics.value?.waitingHumanConfirmTaskCount ?? 0,
    hint: '进入任务列表完成确认',
    to: { path: '/agent-tasks', query: { status: 'WAITING_HUMAN_CONFIRM' } },
    icon: pendingConfirmIcon,
  },
  {
    label: '执行失败',
    value: taskMetrics.value?.failedTaskCount ?? 0,
    hint: '定位失败任务与原因',
    to: { path: '/agent-tasks', query: { status: 'FAILED' } },
    icon: failedTaskIcon,
  },
  {
    label: '课程知识库',
    value: '管理',
    hint: '维护课程资料与检索配置',
    to: '/ragflow',
    icon: knowledgeBaseIcon,
  },
]);
</script>

<template>
  <AdminSkeleton v-if="loading" :lines="4" />
  <main v-else class="page-grid">
    <AdminHeroBanner
      eyebrow="教育运营中心"
      title="教学智能、知识与风险统一运营"
      description="集中查看 AI 任务、课程知识库和文件存储的实时状态。"
      :image="dashboardHero"
    >
      <template #metrics>
        <div class="hero-metric"><strong>{{ taskMetrics?.totalTaskCount ?? 0 }}</strong><span>AI 任务</span></div>
        <div class="hero-metric"><strong>{{ taskMetrics?.waitingHumanConfirmTaskCount ?? 0 }}</strong><span>待确认</span></div>
        <div class="hero-metric"><strong>{{ formatBytes(storage?.orgPoolUsedBytes ?? 0) }}</strong><span>已用空间</span></div>
      </template>
    </AdminHeroBanner>

    <AdminPanel :span="12" title="待处理入口" subtitle="优先处理异常任务和课程资料治理">
      <div class="action-grid">
        <RouterLink v-for="action in exceptionActions" :key="action.label" :to="action.to" class="action-card">
          <img :src="action.icon" alt="" aria-hidden="true" />
          <span>{{ action.label }}</span>
          <strong>{{ action.value }}</strong>
          <small>{{ action.hint }}</small>
          <ArrowRight class="action-arrow" :size="18" />
        </RouterLink>
      </div>
    </AdminPanel>

    <AdminPanel :span="12" title="AI 任务概览">
      <AdminMetricStrip :cards="taskCards" />
    </AdminPanel>

    <AdminPanel :span="12" title="课程资源存储">
      <AdminMetricStrip :cards="storageCards" />
      <RouterLink to="/files" class="panel-link">进入文件空间 <ArrowRight :size="16" /></RouterLink>
    </AdminPanel>
  </main>
</template>

<style scoped>
.action-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-base);
}

.action-card {
  position: relative;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: var(--space-sm) var(--space-md);
  min-height: 112px;
  padding: var(--space-base);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel-strong);
  color: var(--ink);
  text-decoration: none;
  transition: border-color 200ms ease, box-shadow 200ms ease;
}

.action-card:hover { border-color: var(--green-light); box-shadow: var(--shadow); }
.action-card:focus-visible { outline: none; box-shadow: 0 0 0 4px var(--focus-ring); }
.action-card > img { width: 48px; height: 48px; object-fit: contain; filter: drop-shadow(0 7px 10px var(--metric-icon-shadow)); }
.action-card span { font-size: var(--text-sm); font-weight: 700; }
.action-card strong { font-size: var(--text-xl); }
.action-card small { grid-column: 2 / 4; color: var(--muted); font-size: var(--text-xs); }
.action-arrow { color: var(--muted); }

.panel-link { color: var(--muted); font-size: var(--text-sm); font-weight: 700; }
.panel-link:focus-visible { outline: none; box-shadow: 0 0 0 4px var(--focus-ring); }

.panel-link {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-xs);
  min-height: 44px;
  margin-top: var(--space-sm);
  color: var(--green-dark);
  text-decoration: none;
}

@media (max-width: 820px) {
  .action-grid { grid-template-columns: 1fr; }
}
</style>
