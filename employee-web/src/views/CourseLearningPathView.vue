<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  ArrowLeft,
  BookOpenCheck,
  Check,
  CheckCircle2,
  Clock3,
  FileText,
  LockKeyhole,
  Map,
  PlayCircle,
  RefreshCcw,
  Sparkles,
  Target,
} from 'lucide-vue-next';
import {
  employeeApi,
  type EntityId,
  type LearningPathNodeResponse,
  type LearningPathNodeStatus,
  type LearningPathRecommendationResponse,
  type LearningPathResponse,
} from '@/api';
import AppErrorState from '@/components/ui/AppErrorState.vue';
import AppLoadingState from '@/components/ui/AppLoadingState.vue';
import learningPathHero from '@/assets/learning-path-hero-3d.webp';
import learningStatCompleted from '@/assets/learning-stat-completed.webp';
import learningStatFocus from '@/assets/learning-stat-focus.webp';
import learningStatTime from '@/assets/learning-stat-time.webp';

type LearningPathTab = 'today' | 'path' | 'recommendations';

const route = useRoute();
const router = useRouter();
const learningPath = ref<LearningPathResponse | null>(null);
const activeTab = ref<LearningPathTab>('today');
const loading = ref(false);
const completingNodeId = ref('');
const recentlyCompletedNodeId = ref('');
const error = ref('');
const actionNotice = ref('');

const courseId = computed(() => String(route.params.courseId ?? ''));
const currentNode = computed(() => learningPath.value?.nodes.find((node) => node.status === 'IN_PROGRESS') ?? null);
const completedCount = computed(() => learningPath.value?.nodes.filter((node) => node.status === 'COMPLETED').length ?? 0);
const totalMinutes = computed(() => learningPath.value?.nodes.reduce((total, node) => total + node.estimatedMinutes, 0) ?? 0);
const activeRecommendations = computed(() => learningPath.value?.recommendations.filter((item) => item.status === 'ACTIVE') ?? []);
const visibleTodayNodes = computed(() => {
  if (!learningPath.value || !recentlyCompletedNodeId.value) return learningPath.value?.todayNodes ?? [];
  const completedNode = learningPath.value.nodes.find((node) => String(node.nodeId) === recentlyCompletedNodeId.value);
  if (!completedNode || completedNode.status !== 'COMPLETED') return learningPath.value.todayNodes;
  return [
    completedNode,
    ...learningPath.value.todayNodes
      .filter((node) => String(node.nodeId) !== recentlyCompletedNodeId.value)
      .slice(0, 2),
  ];
});

watch(courseId, loadLearningPath, { immediate: true });

async function loadLearningPath() {
  if (!courseId.value) {
    error.value = '课程编号无效';
    return;
  }
  loading.value = true;
  recentlyCompletedNodeId.value = '';
  error.value = '';
  actionNotice.value = '';
  try {
    learningPath.value = await employeeApi.courses.learningPath(courseId.value);
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '学习路径加载失败';
  } finally {
    loading.value = false;
  }
}

async function completeNode(node: LearningPathNodeResponse) {
  if (!learningPath.value || node.status !== 'IN_PROGRESS') return;
  completingNodeId.value = String(node.nodeId);
  error.value = '';
  actionNotice.value = '';
  try {
    learningPath.value = await employeeApi.courses.completeLearningPathNode(learningPath.value.courseId, node.nodeId);
    recentlyCompletedNodeId.value = String(node.nodeId);
    actionNotice.value = `“${node.knowledgePointName}”已完成，下一学习节点已解锁。`;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '学习进度更新失败';
  } finally {
    completingNodeId.value = '';
  }
}

function openCourse() {
  if (!learningPath.value) return;
  void router.push({
    name: 'employee-courses',
    query: { courseId: String(learningPath.value.courseId) },
  });
}

function openNodeResource(node: LearningPathNodeResponse) {
  if (!learningPath.value || node.status === 'LOCKED') return;
  void router.push({
    name: 'employee-courses',
    query: {
      courseId: String(learningPath.value.courseId),
      chapterId: String(node.chapterId),
    },
  });
}

function openRecommendation(recommendation: LearningPathRecommendationResponse) {
  const node = learningPath.value?.nodes.find((item) => String(item.nodeId) === String(recommendation.nodeId));
  if (node) openNodeResource(node);
}

function openLearningResources() {
  if (!learningPath.value) return;
  void router.push({
    name: 'student-learning-resources',
    params: { courseId: String(learningPath.value.courseId) },
    query: currentNode.value ? { pointId: String(currentNode.value.knowledgePointId) } : {},
  });
}

function statusLabel(status: LearningPathNodeStatus) {
  if (status === 'COMPLETED') return '已完成';
  if (status === 'IN_PROGRESS') return '当前学习';
  if (status === 'PENDING') return '待学习';
  return '未解锁';
}

function masteryLabel(score?: number | null) {
  if (score == null) return '待评估';
  return `${Number(score).toFixed(0)}%`;
}

function masteryPercent(score?: number | null) {
  if (score == null) return 0;
  return Math.min(100, Math.max(0, Number(score)));
}

function formatCreatedAt(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

function selectTab(tab: LearningPathTab) {
  activeTab.value = tab;
  actionNotice.value = '';
}
</script>

<template>
  <main class="learning-path-page">
    <AppLoadingState v-if="loading && !learningPath" />
    <AppErrorState
      v-else-if="error && !learningPath"
      title="学习路径加载失败"
      :description="error"
      action-label="重新加载"
      @retry="loadLearningPath"
    />

    <template v-else-if="learningPath">
      <button type="button" class="back-link" @click="openCourse">
        <ArrowLeft :size="18" />返回课程
      </button>

      <header class="path-hero">
        <div class="hero-copy">
          <span class="path-kicker"><Sparkles :size="15" />个性化学习路径</span>
          <h1>{{ learningPath.courseName }}</h1>
          <p>{{ learningPath.generationReason }}</p>
          <div class="version-line">
            <span>路径 V{{ learningPath.versionNo }}</span>
            <span>画像 V{{ learningPath.profileVersionNo }}</span>
            <span>{{ formatCreatedAt(learningPath.createdAt) }} 更新</span>
          </div>
          <button type="button" class="resource-generation-button" @click="openLearningResources">
            <Sparkles :size="17" />为当前薄弱点生成资源
          </button>
        </div>
        <div class="hero-visual">
          <img :src="learningPathHero" alt="" aria-hidden="true" />
          <div class="progress-orbit" :style="{ '--path-progress': `${learningPath.progressPercent * 3.6}deg` }">
            <div>
              <strong>{{ learningPath.progressPercent }}%</strong>
              <span>课程进度</span>
            </div>
          </div>
        </div>
      </header>

      <section class="path-summary" aria-label="学习路径概览">
        <article class="summary-card summary-card-completed">
          <span class="summary-visual" aria-hidden="true"><img :src="learningStatCompleted" alt="" /></span>
          <div class="summary-copy"><small>已完成知识点</small><strong>{{ completedCount }} <em>/ {{ learningPath.nodes.length }}</em></strong></div>
        </article>
        <article class="summary-card summary-card-time">
          <span class="summary-visual" aria-hidden="true"><img :src="learningStatTime" alt="" /></span>
          <div class="summary-copy"><small>预计路径总时长</small><strong>{{ totalMinutes }} <em>分钟</em></strong></div>
        </article>
        <article class="summary-card summary-card-focus">
          <span class="summary-visual" aria-hidden="true"><img :src="learningStatFocus" alt="" /></span>
          <div class="summary-copy"><small>当前学习重点</small><strong class="focus-value">{{ currentNode?.knowledgePointName || '全部完成' }}</strong></div>
        </article>
      </section>

      <nav class="path-tabs" aria-label="学习路径视图">
        <button type="button" :class="{ active: activeTab === 'today' }" @click="selectTab('today')">
          <PlayCircle :size="18" /><span><strong>今日学习</strong><small>{{ visibleTodayNodes.length }} 个安排</small></span>
        </button>
        <button type="button" :class="{ active: activeTab === 'path' }" @click="selectTab('path')">
          <Map :size="18" /><span><strong>学习路径</strong><small>{{ learningPath.nodes.length }} 个节点</small></span>
        </button>
        <button type="button" :class="{ active: activeTab === 'recommendations' }" @click="selectTab('recommendations')">
          <BookOpenCheck :size="18" /><span><strong>推荐资源</strong><small>{{ activeRecommendations.length }} 项推荐</small></span>
        </button>
      </nav>

      <p v-if="actionNotice" class="action-notice"><CheckCircle2 :size="18" />{{ actionNotice }}</p>
      <p v-if="error" class="path-error">{{ error }}</p>

      <section v-if="activeTab === 'today'" class="today-board" data-testid="learning-path-today">
        <header class="section-heading">
          <div><span class="path-kicker">今日安排</span><h2>沿着当前节奏继续</h2></div>
          <button type="button" class="refresh-button" :disabled="loading" @click="loadLearningPath">
            <RefreshCcw :size="16" />{{ loading ? '刷新中' : '刷新路径' }}
          </button>
        </header>

        <div v-if="visibleTodayNodes.length" class="today-list">
          <article
            v-for="node in visibleTodayNodes"
            :key="String(node.nodeId)"
            class="today-card"
            :class="`status-${node.status.toLowerCase()}`"
            :style="{ '--mastery-progress': `${masteryPercent(node.masteryScore)}%` }"
          >
            <span class="today-index"><small>STEP</small><strong>{{ String(node.sequenceNo).padStart(2, '0') }}</strong></span>
            <div class="today-main">
              <div class="node-title-line">
                <span class="node-status" :class="`status-${node.status.toLowerCase()}`">{{ statusLabel(node.status) }}</span>
                <small class="chapter-label">{{ node.chapterName }}</small>
              </div>
              <h3>{{ node.knowledgePointName }}</h3>
              <p>{{ node.learningObjective }}</p>
            </div>
            <div class="learning-metrics">
              <span class="metric-chip"><Clock3 :size="16" /><span><small>预计时长</small><strong>{{ node.estimatedMinutes }} 分钟</strong></span></span>
              <div class="mastery-meter">
                <div class="mastery-head"><span><Target :size="15" />当前掌握度</span><strong>{{ masteryLabel(node.masteryScore) }}</strong></div>
                <div class="mastery-track" aria-hidden="true"><span></span></div>
              </div>
            </div>
            <aside class="reason-box"><Sparkles :size="16" /><span><strong>为什么推荐</strong>{{ node.recommendationReason }}</span></aside>
            <div class="today-actions">
              <button type="button" class="secondary-button" :disabled="node.status === 'LOCKED'" @click="openNodeResource(node)">
                <FileText :size="17" />学习文章
              </button>
              <button
                v-if="node.status === 'IN_PROGRESS'"
                type="button"
                class="primary-button"
                :disabled="completingNodeId === String(node.nodeId)"
                @click="completeNode(node)"
              >
                <Check :size="17" />{{ completingNodeId === String(node.nodeId) ? '更新中' : '标记完成' }}
              </button>
            </div>
          </article>
        </div>
        <div v-else class="path-empty">
          <CheckCircle2 :size="34" /><h3>今日路径已完成</h3><p>可以复习已完成节点，或通过课程测验更新掌握度。</p>
        </div>
      </section>

      <section v-else-if="activeTab === 'path'" class="full-path" data-testid="learning-path-all">
        <header class="section-heading">
          <div><span class="path-kicker">完整路径</span><h2>知识依赖与学习顺序</h2></div>
          <p>节点按课程知识依赖、画像与掌握度排列。</p>
        </header>

        <ol class="path-timeline">
          <li v-for="node in learningPath.nodes" :key="String(node.nodeId)" :class="`status-${node.status.toLowerCase()}`">
            <span class="timeline-marker">
              <Check v-if="node.status === 'COMPLETED'" :size="18" />
              <PlayCircle v-else-if="node.status === 'IN_PROGRESS'" :size="18" />
              <LockKeyhole v-else-if="node.status === 'LOCKED'" :size="16" />
              <span v-else>{{ node.sequenceNo }}</span>
            </span>
            <article class="path-node-card">
              <header>
                <div><small>第 {{ node.sequenceNo }} 步 · {{ node.chapterName }}</small><h3>{{ node.knowledgePointName }}</h3></div>
                <span class="node-status" :class="`status-${node.status.toLowerCase()}`">{{ statusLabel(node.status) }}</span>
              </header>
              <p>{{ node.learningObjective }}</p>
              <dl>
                <div><dt>推荐资源</dt><dd>{{ node.resourceTitle }}</dd></div>
                <div><dt>预计时长</dt><dd>{{ node.estimatedMinutes }} 分钟</dd></div>
                <div><dt>当前掌握度</dt><dd>{{ masteryLabel(node.masteryScore) }}</dd></div>
                <div><dt>完成条件</dt><dd>{{ node.completionCondition }}</dd></div>
              </dl>
              <aside class="reason-box"><Sparkles :size="16" /><span><strong>为什么这样安排</strong>{{ node.recommendationReason }}</span></aside>
              <footer>
                <button type="button" class="secondary-button" :disabled="node.status === 'LOCKED'" @click="openNodeResource(node)">
                  <FileText :size="17" />打开学习资源
                </button>
                <button
                  v-if="node.status === 'IN_PROGRESS'"
                  type="button"
                  class="primary-button"
                  :disabled="completingNodeId === String(node.nodeId)"
                  @click="completeNode(node)"
                >
                  <Check :size="17" />{{ completingNodeId === String(node.nodeId) ? '更新中' : '完成当前节点' }}
                </button>
              </footer>
            </article>
          </li>
        </ol>
      </section>

      <section v-else class="recommendation-board" data-testid="learning-path-recommendations">
        <header class="section-heading">
          <div><span class="path-kicker">资源推荐</span><h2>与当前薄弱点直接相关</h2></div>
          <p>每条推荐都保留知识点、路径节点和推荐原因。</p>
        </header>

        <div v-if="activeRecommendations.length" class="recommendation-grid">
          <article v-for="recommendation in activeRecommendations" :key="String(recommendation.recommendationId)" class="recommendation-card">
            <header>
              <span class="priority-number">{{ String(recommendation.priorityNo).padStart(2, '0') }}</span>
              <span :class="['recommendation-status', recommendation.status.toLowerCase()]">{{ recommendation.status === 'COMPLETED' ? '已学习' : '推荐学习' }}</span>
            </header>
            <small class="knowledge-chip">{{ recommendation.knowledgePointName }}</small>
            <h3>{{ recommendation.resourceTitle }}</h3>
            <p>{{ recommendation.reason }}</p>
            <footer class="recommendation-footer">
              <span><FileText :size="15" />课程文章</span>
              <button type="button" class="secondary-button" @click="openRecommendation(recommendation)">查看资源</button>
            </footer>
          </article>
        </div>
        <div v-else class="path-empty"><BookOpenCheck :size="34" /><h3>暂无推荐资源</h3><p>完成画像或课程测验后会生成新的学习建议。</p></div>
      </section>
    </template>
  </main>
</template>

<style scoped>
.learning-path-page { box-sizing: border-box; width: 100%; height: 100%; min-height: 0; overflow-x: hidden; overflow-y: auto; padding: var(--layout-content-pad); background: var(--surface-canvas); color: var(--text-primary); }
.back-link { display: inline-flex; align-items: center; gap: var(--space-6); margin-bottom: var(--space-18); padding: 0; border: 0; background: transparent; color: var(--text-accent); font-weight: 700; }
.path-hero { position: relative; isolation: isolate; display: grid; grid-template-columns: minmax(0, 1fr) minmax(20rem, 26rem); align-items: center; gap: var(--space-32); overflow: visible; min-height: 21rem; padding: clamp(var(--space-24), 4vw, var(--space-40)); border: 1px solid var(--border-accent); border-radius: var(--radius-16); background: linear-gradient(135deg, var(--surface-panel), var(--surface-accent)); box-shadow: var(--shadow-sm); }
.path-hero::after { position: absolute; right: 1rem; bottom: 1rem; z-index: -1; width: 18rem; height: 18rem; border: 1px solid var(--border-accent); border-radius: 50%; background: radial-gradient(circle, var(--surface-accent), transparent 68%); content: ''; opacity: .72; }
.hero-copy { position: relative; z-index: 1; }
.path-kicker { display: inline-flex; align-items: center; gap: var(--space-6); color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; letter-spacing: .08em; }
.hero-copy h1 { max-width: 48rem; margin: var(--space-8) 0; font-family: var(--font-family-display); font-size: clamp(var(--text-title), 4vw, var(--font-size-40)); line-height: 1.15; }
.hero-copy > p { max-width: 52rem; margin: 0; color: var(--text-secondary); line-height: 1.7; }
.version-line { display: flex; flex-wrap: wrap; gap: var(--space-8); margin-top: var(--space-18); }
.version-line span { padding: var(--space-6) var(--space-10); border: 1px solid var(--border-subtle); border-radius: var(--radius-16); background: var(--surface-panel); color: var(--text-tertiary); font-size: var(--text-caption); }
.resource-generation-button { display: inline-flex; align-items: center; gap: var(--space-7); margin-top: var(--space-16); min-height: 2.6rem; padding: 0 var(--space-14); border: 1px solid var(--border-accent); border-radius: var(--radius-10); background: var(--surface-panel); color: var(--text-accent); font-weight: 800; }
.hero-visual { position: relative; align-self: stretch; min-height: 17rem; pointer-events: none; }
.hero-visual img { position: absolute; top: -6.5rem; right: -1.5rem; z-index: 1; width: min(21rem, 34vw); max-width: none; filter: drop-shadow(0 1.75rem 2.4rem color-mix(in srgb, var(--text-primary) 20%, transparent)); transform: rotate(1.5deg); transform-origin: 55% 65%; animation: hero-float 7s ease-in-out infinite; user-select: none; }
.progress-orbit { position: absolute; right: 11.5rem; bottom: 1.5rem; z-index: 2; display: grid; width: 8.5rem; height: 8.5rem; place-items: center; border: .35rem solid var(--surface-panel); border-radius: 50%; background: conic-gradient(var(--primary-500) var(--path-progress), var(--surface-muted) 0); box-shadow: var(--shadow-md); }
.progress-orbit::before { position: absolute; width: 7rem; height: 7rem; border-radius: inherit; background: var(--surface-panel); content: ''; }
.progress-orbit > div { position: relative; display: grid; justify-items: center; }
.progress-orbit strong { color: var(--text-accent); font-family: var(--font-family-display); font-size: var(--text-headline); }
.progress-orbit span { color: var(--text-tertiary); font-size: var(--text-caption); }
.path-summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-12); margin-top: var(--space-16); }
.summary-card { position: relative; display: flex; min-width: 0; min-height: 6.5rem; align-items: center; gap: var(--space-16); overflow: hidden; padding: var(--space-18); border: 1px solid var(--border-default); border-radius: var(--radius-16); background: var(--surface-panel); box-shadow: var(--shadow-sm); }
.summary-card::after { position: absolute; right: -2.5rem; bottom: -3.5rem; width: 8rem; height: 8rem; border: 1px solid var(--border-subtle); border-radius: 50%; background: radial-gradient(circle, var(--surface-accent), transparent 70%); content: ''; opacity: .72; }
.summary-card-completed { border-color: color-mix(in srgb, var(--success-500) 28%, var(--border-default)); }
.summary-card-time { border-color: color-mix(in srgb, var(--primary-500) 25%, var(--border-default)); }
.summary-card-focus { border-color: var(--border-accent); background: linear-gradient(135deg, var(--surface-panel), var(--surface-accent)); }
.summary-visual { position: relative; z-index: 1; display: grid; width: 4rem; height: 4rem; flex: 0 0 auto; place-items: center; }
.summary-visual img { display: block; width: 100%; height: 100%; object-fit: contain; filter: drop-shadow(0 .45rem .65rem color-mix(in srgb, var(--text-primary) 14%, transparent)); }
.summary-copy { position: relative; z-index: 1; min-width: 0; display: grid; gap: var(--space-6); }
.summary-copy small { color: var(--text-tertiary); font-size: var(--text-caption); font-weight: 700; }
.summary-copy strong { overflow: hidden; color: var(--text-primary); font-family: var(--font-family-display); font-size: var(--text-title); line-height: 1.1; text-overflow: ellipsis; white-space: nowrap; }
.summary-copy strong em { color: var(--text-tertiary); font-family: var(--font-family-body); font-size: var(--text-body); font-style: normal; font-weight: 600; }
.summary-copy .focus-value { font-family: var(--font-family-body); font-size: var(--text-subtitle); }
.path-tabs { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-6); margin: var(--space-24) 0 var(--space-32); padding: var(--space-6); border: 1px solid var(--border-subtle); border-radius: var(--radius-16); background: var(--surface-muted); }
.path-tabs button { position: relative; display: flex; align-items: center; justify-content: center; gap: var(--space-10); min-width: 0; min-height: 4.5rem; padding: var(--space-10) var(--space-12); border: 1px solid transparent; border-radius: var(--radius-12); background: transparent; color: var(--text-tertiary); }
.path-tabs button.active { border-color: var(--border-accent); background: var(--surface-panel); color: var(--text-accent); box-shadow: var(--shadow-sm); }
.path-tabs button.active::after { position: absolute; right: 22%; bottom: -.08rem; left: 22%; height: .16rem; border-radius: var(--radius-16); background: var(--primary-500); content: ''; }
.path-tabs button > span { min-width: 0; display: grid; gap: var(--space-4); text-align: left; }
.path-tabs strong { font-size: var(--text-body); }
.path-tabs small { color: inherit; font-size: var(--text-caption); font-weight: 500; opacity: .7; }
.today-board, .full-path, .recommendation-board { position: relative; }
.section-heading { display: flex; align-items: end; justify-content: space-between; gap: var(--space-20); margin-bottom: var(--space-20); }
.section-heading h2 { margin: var(--space-4) 0 0; font-family: var(--font-family-display); font-size: clamp(var(--text-title), 2.8vw, var(--font-size-32)); line-height: 1.2; }
.section-heading > p { max-width: 30rem; margin: 0; color: var(--text-secondary); text-align: right; }
.refresh-button, .primary-button, .secondary-button { display: inline-flex; align-items: center; justify-content: center; gap: var(--space-6); min-height: 2.6rem; padding: 0 var(--space-14); border-radius: var(--radius-10); font-weight: 700; }
.refresh-button, .secondary-button { border: 1px solid var(--border-default); background: var(--surface-panel); color: var(--text-primary); box-shadow: var(--shadow-sm); }
.primary-button { border: 1px solid var(--primary-600); background: var(--primary-600); color: var(--text-inverse); box-shadow: 0 .5rem 1rem color-mix(in srgb, var(--primary-600) 22%, transparent); }
.refresh-button:hover:not(:disabled), .secondary-button:hover:not(:disabled) { border-color: var(--border-accent); background: var(--surface-accent); color: var(--text-accent); }
.primary-button:hover:not(:disabled) { background: var(--primary-700); }
.refresh-button:disabled, .primary-button:disabled, .secondary-button:disabled { cursor: not-allowed; opacity: .5; }
.action-notice, .path-error { display: flex; align-items: center; gap: var(--space-8); margin: 0 0 var(--space-16); padding: var(--space-10) var(--space-12); border-radius: var(--radius-10); }
.action-notice { background: var(--success-surface); color: color-mix(in srgb, var(--success-500) 72%, var(--text-primary)); }
.path-error { background: var(--danger-surface); color: color-mix(in srgb, var(--danger-500) 72%, var(--text-primary)); }
.today-list { display: grid; gap: var(--space-16); }
.today-card { position: relative; display: grid; grid-template-columns: auto minmax(0, 1fr) minmax(8.8rem, auto); grid-template-rows: auto auto auto; column-gap: var(--space-20); row-gap: var(--space-14); overflow: hidden; padding: var(--space-20); border: 1px solid var(--border-default); border-radius: var(--radius-16); background: var(--surface-panel); box-shadow: var(--shadow-sm); transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease; }
.today-card::before { position: absolute; top: 0; bottom: 0; left: 0; width: .3rem; background: var(--border-default); content: ''; }
.today-card.status-in_progress { padding: var(--space-24); border-color: var(--border-accent); background: linear-gradient(135deg, var(--surface-panel) 58%, var(--surface-accent)); box-shadow: var(--shadow-md); }
.today-card.status-in_progress::before { width: .42rem; background: var(--primary-500); }
.today-card.status-completed { border-color: color-mix(in srgb, var(--success-500) 35%, var(--border-default)); background: linear-gradient(135deg, var(--surface-panel), color-mix(in srgb, var(--success-surface) 62%, var(--surface-panel))); }
.today-card.status-completed::before { background: var(--success-500); }
.today-card.status-pending:hover { border-color: var(--border-accent); transform: translateY(-.12rem); }
.today-card.status-locked { background: var(--surface-muted); opacity: .76; }
.today-index { grid-column: 1; grid-row: 1 / 4; display: grid; width: 3.6rem; height: 4.4rem; align-content: center; justify-items: center; gap: var(--space-2); border: 1px solid var(--border-subtle); border-radius: var(--radius-12); background: var(--surface-muted); color: var(--text-tertiary); }
.today-index small { font-size: .58rem; font-weight: 800; letter-spacing: .12em; }
.today-index strong { font-family: var(--font-family-display); font-size: var(--text-subtitle); }
.today-card.status-in_progress .today-index { border-color: var(--primary-500); background: var(--primary-600); color: var(--text-inverse); box-shadow: 0 .7rem 1.4rem color-mix(in srgb, var(--primary-600) 20%, transparent); }
.today-card.status-completed .today-index { border-color: var(--success-500); background: var(--success-500); color: var(--text-inverse); }
.today-main { grid-column: 2; grid-row: 1; min-width: 0; }
.node-title-line { display: flex; align-items: center; gap: var(--space-8); flex-wrap: wrap; }
.chapter-label { color: var(--text-tertiary); font-weight: 600; }
.node-status { display: inline-flex; padding: var(--space-4) var(--space-8); border-radius: var(--radius-16); background: var(--surface-muted); color: var(--text-tertiary); font-size: var(--text-caption); font-weight: 700; }
.node-status.status-completed { background: var(--success-surface); color: color-mix(in srgb, var(--success-500) 72%, var(--text-primary)); }
.node-status.status-in_progress { background: var(--surface-accent); color: var(--text-accent); }
.node-status.status-locked { background: var(--surface-muted); color: var(--text-tertiary); }
.today-main h3, .path-timeline h3, .recommendation-grid h3 { margin: var(--space-8) 0 var(--space-6); font-size: var(--text-subtitle); line-height: 1.35; }
.today-card.status-in_progress .today-main h3 { font-family: var(--font-family-display); font-size: var(--text-title); }
.today-main > p, .path-timeline article > p, .recommendation-grid article > p { margin: 0; color: var(--text-secondary); line-height: 1.65; }
.learning-metrics { grid-column: 2; grid-row: 2; display: grid; grid-template-columns: minmax(9rem, auto) minmax(14rem, 20rem); align-items: stretch; gap: var(--space-10); }
.metric-chip { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-10) var(--space-12); border: 1px solid var(--border-subtle); border-radius: var(--radius-10); background: color-mix(in srgb, var(--surface-panel) 82%, transparent); color: var(--text-accent); }
.metric-chip > span { display: grid; gap: var(--space-2); }
.metric-chip small { color: var(--text-tertiary); font-size: var(--text-caption); }
.metric-chip strong { color: var(--text-primary); font-size: var(--text-body-sm); }
.mastery-meter { display: grid; align-content: center; gap: var(--space-8); padding: var(--space-10) var(--space-12); border: 1px solid var(--border-subtle); border-radius: var(--radius-10); background: color-mix(in srgb, var(--surface-panel) 82%, transparent); }
.mastery-head { display: flex; align-items: center; justify-content: space-between; gap: var(--space-12); color: var(--text-tertiary); font-size: var(--text-caption); }
.mastery-head > span { display: inline-flex; align-items: center; gap: var(--space-6); }
.mastery-head strong { color: var(--text-primary); }
.mastery-track { height: .42rem; overflow: hidden; border-radius: var(--radius-16); background: var(--surface-muted); }
.mastery-track span { display: block; width: var(--mastery-progress); height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--primary-500), var(--success-500)); }
.reason-box { grid-column: 2; grid-row: 3; display: flex; align-items: flex-start; gap: var(--space-8); padding: var(--space-12); border: 1px solid transparent; border-radius: var(--radius-10); background: var(--surface-muted); color: var(--text-secondary); line-height: 1.6; }
.today-card.status-in_progress .reason-box { border-color: color-mix(in srgb, var(--primary-500) 20%, transparent); background: color-mix(in srgb, var(--surface-accent) 70%, var(--surface-panel)); }
.reason-box > svg { flex: 0 0 auto; margin-top: .2rem; color: var(--text-accent); }
.reason-box span { display: grid; gap: var(--space-4); }
.reason-box strong { color: var(--text-primary); font-size: var(--text-caption); }
.today-actions { display: contents; }
.today-actions .secondary-button { grid-column: 3; grid-row: 2; }
.today-actions .primary-button { grid-column: 3; grid-row: 3; }
.today-actions .primary-button, .today-actions .secondary-button { box-sizing: border-box; width: 100%; min-height: 3rem; height: auto; padding-block: 0; }
.full-path { max-width: 72rem; margin: 0 auto; }
.path-timeline { margin: 0; padding: 0; list-style: none; }
.path-timeline > li { position: relative; display: grid; grid-template-columns: 3.25rem minmax(0, 1fr); gap: var(--space-16); padding-bottom: var(--space-20); }
.path-timeline > li:not(:last-child)::before { position: absolute; top: 3rem; bottom: 0; left: 1.55rem; width: .14rem; background: linear-gradient(var(--border-accent), var(--border-default)); content: ''; }
.timeline-marker { position: relative; z-index: 1; display: grid; width: 3.15rem; height: 3.15rem; place-items: center; border: .38rem solid var(--surface-canvas); border-radius: 50%; background: var(--surface-muted); color: var(--text-tertiary); font-weight: 800; box-shadow: var(--shadow-sm); }
.path-timeline li.status-completed .timeline-marker { background: var(--success-500); color: var(--text-inverse); }
.path-timeline li.status-in_progress .timeline-marker { background: var(--primary-500); color: var(--text-inverse); box-shadow: 0 0 0 .25rem color-mix(in srgb, var(--primary-500) 24%, transparent); }
.path-timeline li.status-locked .timeline-marker { color: var(--text-tertiary); }
.path-node-card { position: relative; overflow: hidden; padding: var(--space-20); border: 1px solid var(--border-default); border-radius: var(--radius-16); background: var(--surface-panel); box-shadow: var(--shadow-sm); }
.path-node-card::before { position: absolute; top: 0; right: 0; left: 0; height: .22rem; background: var(--border-default); content: ''; }
.path-timeline li.status-completed .path-node-card { border-color: color-mix(in srgb, var(--success-500) 25%, var(--border-default)); background: linear-gradient(135deg, var(--surface-panel), color-mix(in srgb, var(--success-surface) 58%, var(--surface-panel))); }
.path-timeline li.status-completed .path-node-card::before { background: var(--success-500); }
.path-timeline li.status-in_progress .path-node-card { border-color: var(--border-accent); background: linear-gradient(135deg, var(--surface-panel) 62%, var(--surface-accent)); box-shadow: var(--shadow-md); }
.path-timeline li.status-in_progress .path-node-card::before { background: var(--primary-500); }
.path-timeline li.status-locked .path-node-card { background: var(--surface-muted); opacity: .76; }
.path-timeline article > header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-16); }
.path-timeline article > header small { color: var(--text-tertiary); }
.path-timeline dl { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-10); margin: var(--space-16) 0 0; }
.path-timeline dl > div { min-width: 0; padding: var(--space-10) var(--space-12); border: 1px solid var(--border-subtle); border-radius: var(--radius-10); background: color-mix(in srgb, var(--surface-muted) 76%, transparent); }
.path-timeline dt { color: var(--text-tertiary); font-size: var(--text-caption); }
.path-timeline dd { overflow-wrap: anywhere; margin: var(--space-4) 0 0; color: var(--text-primary); line-height: 1.5; }
.path-timeline footer { display: flex; justify-content: flex-end; gap: var(--space-8); margin-top: var(--space-14); }
.recommendation-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(18rem, 1fr)); gap: var(--space-16); }
.recommendation-card { position: relative; display: flex; min-height: 20rem; overflow: hidden; flex-direction: column; padding: var(--space-20); border: 1px solid var(--border-default); border-radius: var(--radius-16); background: linear-gradient(155deg, var(--surface-panel) 70%, var(--surface-accent)); box-shadow: var(--shadow-sm); transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease; }
.recommendation-card::before { position: absolute; top: 0; right: 0; left: 0; height: .25rem; background: linear-gradient(90deg, var(--primary-500), var(--success-500)); content: ''; }
.recommendation-card::after { position: absolute; top: -4rem; right: -4rem; width: 9rem; height: 9rem; border: 1px solid var(--border-accent); border-radius: 50%; content: ''; opacity: .42; }
.recommendation-card:hover { border-color: var(--border-accent); box-shadow: var(--shadow-md); transform: translateY(-.2rem); }
.recommendation-grid article > header { display: flex; align-items: center; justify-content: space-between; gap: var(--space-12); }
.priority-number { color: var(--text-accent); font-family: var(--font-family-display); font-size: var(--font-size-32); font-weight: 800; line-height: 1; }
.recommendation-status { padding: var(--space-4) var(--space-8); border-radius: var(--radius-16); background: var(--surface-accent); color: var(--text-accent); font-size: var(--text-caption); }
.recommendation-status.completed { background: var(--success-surface); color: color-mix(in srgb, var(--success-500) 72%, var(--text-primary)); }
.knowledge-chip { align-self: flex-start; margin-top: var(--space-20); padding: var(--space-5) var(--space-9); border: 1px solid var(--border-accent); border-radius: var(--radius-16); background: var(--surface-panel); color: var(--text-accent); font-weight: 700; }
.recommendation-grid article > p { flex: 1; margin-bottom: var(--space-18); }
.recommendation-footer { display: flex; align-items: center; justify-content: space-between; gap: var(--space-12); padding-top: var(--space-14); border-top: 1px solid var(--border-subtle); }
.recommendation-footer > span { display: inline-flex; align-items: center; gap: var(--space-6); color: var(--text-tertiary); font-size: var(--text-caption); }
.path-empty { display: grid; justify-items: center; padding: var(--space-40); border: 1px dashed var(--border-default); border-radius: var(--radius-16); color: var(--text-tertiary); text-align: center; }
.path-empty h3 { margin: var(--space-12) 0 var(--space-4); color: var(--text-primary); }
.path-empty p { margin: 0; }
@keyframes hero-float {
  0%, 100% { transform: translate3d(0, 0, 0) rotate(1.5deg); }
  50% { transform: translate3d(0, -.7rem, 0) rotate(.25deg); }
}
@media (max-width: 900px) {
  .path-hero { grid-template-columns: minmax(0, 1fr) minmax(14rem, 16rem); }
  .hero-visual img { top: -3.5rem; right: -1rem; width: 18rem; }
  .progress-orbit { right: 9.5rem; bottom: 1rem; width: 7.5rem; height: 7.5rem; }
  .path-summary { grid-template-columns: 1fr; }
  .today-card { grid-template-columns: auto minmax(0, 1fr); grid-template-rows: auto auto auto auto; }
  .today-actions { grid-column: 2; grid-row: 4; display: flex; align-items: stretch; gap: var(--space-8); flex-direction: row; justify-content: flex-start; }
  .today-actions .primary-button, .today-actions .secondary-button { grid-column: auto; grid-row: auto; width: auto; }
}
@media (max-width: 640px) {
  .learning-path-page { padding: var(--space-12); padding-bottom: 8rem; }
  .path-hero { grid-template-columns: 1fr; gap: var(--space-12); min-height: auto; padding: var(--space-20); }
  .hero-visual { min-height: 20rem; }
  .hero-visual img { top: -1.5rem; right: -.75rem; width: 17rem; }
  .progress-orbit { right: auto; bottom: 1rem; left: -.5rem; }
  .progress-orbit { width: 7.5rem; height: 7.5rem; }
  .progress-orbit::before { width: 5.9rem; height: 5.9rem; }
  .path-tabs { grid-template-columns: 1fr; }
  .path-tabs button { min-height: 3.7rem; justify-content: flex-start; }
  .path-tabs button.active::after { right: var(--space-12); left: var(--space-12); }
  .section-heading { align-items: stretch; flex-direction: column; }
  .section-heading > p { text-align: left; }
  .today-card { grid-template-columns: 1fr; padding: var(--space-16); }
  .today-card.status-in_progress { padding: var(--space-18); }
  .today-index, .today-main, .learning-metrics, .reason-box, .today-actions { grid-column: 1; grid-row: auto; }
  .today-actions { display: grid; grid-template-columns: 1fr 1fr; grid-template-rows: auto; }
  .today-index { width: 3.2rem; height: 3.8rem; }
  .learning-metrics { grid-template-columns: 1fr; }
  .path-timeline > li { grid-template-columns: 2.4rem minmax(0, 1fr); gap: var(--space-8); }
  .timeline-marker { width: 2.4rem; height: 2.4rem; border-width: .25rem; }
  .path-timeline > li:not(:last-child)::before { top: 2.2rem; left: 1.15rem; }
  .path-timeline article { padding: var(--space-14); }
  .path-timeline article > header { align-items: flex-start; flex-direction: column; }
  .path-timeline dl { grid-template-columns: 1fr; }
  .path-timeline footer { display: grid; grid-template-columns: 1fr; }
  .recommendation-grid { grid-template-columns: 1fr; }
  .recommendation-footer { align-items: stretch; flex-direction: column; }
  .recommendation-footer .secondary-button { width: 100%; }
}
@media (max-width: 420px) {
  .today-actions { grid-template-columns: 1fr; }
  .version-line { align-items: flex-start; flex-direction: column; }
}
@media (prefers-reduced-motion: reduce) {
  .hero-visual img { animation: none; }
  .today-card, .recommendation-card { transition: none; }
  .today-card.status-pending:hover, .recommendation-card:hover { transform: none; }
}
</style>
