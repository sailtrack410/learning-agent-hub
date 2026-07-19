<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { AlertTriangle, BarChart3, Eye, RefreshCw, ShieldCheck, UsersRound } from 'lucide-vue-next';
import { employeeApi, type CourseLearningOverviewResponse, type CourseLearningStudentSummaryResponse, type CourseResponse } from '@/api';
import AppButton from '@/components/ui/AppButton.vue';
import AppEmptyState from '@/components/ui/AppEmptyState.vue';
import AppErrorState from '@/components/ui/AppErrorState.vue';
import AppLoadingState from '@/components/ui/AppLoadingState.vue';
import learningStatCompleted from '@/assets/learning-stat-completed.webp';
import learningStatFocus from '@/assets/learning-stat-focus.webp';

const props = defineProps<{ course: CourseResponse }>();
const overview = ref<CourseLearningOverviewResponse | null>(null);
const loading = ref(false);
const error = ref('');
const studentFilter = ref<'ALL' | 'RISK'>('RISK');

const displayedStudents = computed(() => {
  const students = overview.value?.students ?? [];
  return studentFilter.value === 'RISK' ? students.filter((student) => student.riskLevel !== 'LOW') : students;
});

watch(() => props.course.courseId, loadOverview, { immediate: true });

async function loadOverview() {
  loading.value = true;
  error.value = '';
  try {
    overview.value = await employeeApi.teacherCourses.learningOverview(props.course.courseId);
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '学情数据加载失败';
  } finally {
    loading.value = false;
  }
}

function percent(value?: number | null) {
  return value == null ? '—' : `${Math.round(value)}%`;
}

function formatTime(value?: string | null) {
  if (!value) return '暂无学习记录';
  return new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function riskName(student: CourseLearningStudentSummaryResponse) {
  if (student.riskLevel === 'HIGH') return '高风险';
  if (student.riskLevel === 'MEDIUM') return '需关注';
  return '状态正常';
}
</script>

<template>
  <section class="analytics-panel">
    <header class="analytics-hero">
      <div><span><BarChart3 :size="15" />课程学习运营</span><h2>学情分析与风险识别</h2><p>汇总课程进度、掌握度、画像完成情况和资源使用，优先处理需要帮助的学生。</p></div>
      <AppButton variant="secondary" size="sm" :disabled="loading" @click="loadOverview"><RefreshCw :size="16" />刷新数据</AppButton>
    </header>

    <AppLoadingState v-if="loading && !overview" />
    <AppErrorState v-else-if="error && !overview" title="学情数据加载失败" :description="error" action-label="重新加载" @retry="loadOverview" />

    <template v-else-if="overview">
      <p v-if="error" class="analytics-error">{{ error }}</p>
      <section class="metric-grid" aria-label="课程学习指标">
        <article><span><UsersRound :size="19" /></span><div><strong>{{ overview.studentCount }}</strong><small>课程学生</small></div><em>{{ overview.activeStudentCount }} 人已有学习记录</em></article>
        <article class="risk-metric"><span><AlertTriangle :size="19" /></span><div><strong>{{ overview.riskStudentCount }}</strong><small>风险学生</small></div><em>需优先跟进</em></article>
        <article class="visual-metric"><span><img :src="learningStatCompleted" alt="" aria-hidden="true" /></span><div><strong>{{ percent(overview.completionRate) }}</strong><small>课程完成率</small></div><em>按学习路径进度计算</em></article>
        <article class="visual-metric"><span><img :src="learningStatFocus" alt="" aria-hidden="true" /></span><div><strong>{{ percent(overview.averageMastery) }}</strong><small>平均掌握度</small></div><em>来自课程测验诊断</em></article>
        <article><span><Eye :size="19" /></span><div><strong>{{ overview.resourceViewCount }}</strong><small>资源查看次数</small></div><em>{{ overview.resourceViewerCount }} 人查看 {{ overview.publishedResourceCount }} 份资源</em></article>
      </section>

      <section class="analytics-card class-section">
        <header><div><span>班级汇总</span><h3>班级学习状态</h3></div><small>未分班学生只计入课程总览，不作为班级推送目标</small></header>
        <div v-if="overview.classes.length" class="class-grid">
          <article v-for="item in overview.classes" :key="String(item.classId ?? item.className)">
            <header><strong>{{ item.className }}</strong><span>{{ item.studentCount }} 人</span></header>
            <div class="class-stats"><span><small>完成率</small><b>{{ percent(item.completionRate) }}</b></span><span><small>掌握度</small><b>{{ percent(item.averageMastery) }}</b></span><span :class="{ danger: item.riskStudentCount > 0 }"><small>风险学生</small><b>{{ item.riskStudentCount }}</b></span></div>
            <div class="completion-bar"><span :style="{ width: `${Math.max(0, Math.min(100, item.completionRate))}%` }" /></div>
          </article>
        </div>
        <AppEmptyState v-else title="暂无班级数据" description="课程学生完成组织归属后，班级统计会显示在这里。" />
      </section>

      <div class="analytics-columns">
        <section class="analytics-card student-section">
          <header><div><span>学生画像</span><h3>风险学生与学习进度</h3></div><nav aria-label="学生范围筛选"><button type="button" :class="{ active: studentFilter === 'RISK' }" @click="studentFilter = 'RISK'">风险学生</button><button type="button" :class="{ active: studentFilter === 'ALL' }" @click="studentFilter = 'ALL'">全部学生</button></nav></header>
          <div v-if="displayedStudents.length" class="student-list">
            <article v-for="student in displayedStudents" :key="String(student.studentUserId)">
              <div class="student-identity"><span>{{ student.studentName.slice(0, 1) }}</span><div><strong>{{ student.studentName }}</strong><small>{{ student.studentNo }} · {{ student.className || '未分班' }}</small></div></div>
              <span class="risk-tag" :class="student.riskLevel.toLowerCase()">{{ riskName(student) }}</span>
              <div class="student-stats"><span><small>进度</small><b>{{ percent(student.progressPercent) }}</b></span><span><small>掌握度</small><b>{{ percent(student.averageMastery) }}</b></span><span><small>画像</small><b>V{{ student.profileVersionNo }} · {{ student.profileDimensionCount }} 维</b></span></div>
              <p>{{ student.profileSummary || '画像尚未形成完整结论' }}</p>
              <footer><span v-if="student.riskReasons.length"><AlertTriangle :size="14" />{{ student.riskReasons.join('；') }}</span><span v-else><ShieldCheck :size="14" />当前学习状态正常</span><time>{{ formatTime(student.lastLearningAt) }}</time></footer>
            </article>
          </div>
          <AppEmptyState v-else title="当前没有风险学生" description="可切换到全部学生查看课程学习明细。" />
        </section>

        <section class="analytics-card weak-section">
          <header><div><span>知识点诊断</span><h3>薄弱知识点</h3></div></header>
          <div v-if="overview.weakKnowledgePoints.length" class="weak-list">
            <article v-for="(point, index) in overview.weakKnowledgePoints" :key="String(point.knowledgePointId)"><span>{{ String(index + 1).padStart(2, '0') }}</span><div><strong>{{ point.knowledgePointName }}</strong><small>{{ point.assessedStudentCount ? `${point.assessedStudentCount} 人已评估，${point.weakStudentCount} 人薄弱` : '尚无测验数据' }}</small></div><b>{{ percent(point.averageMastery) }}</b></article>
          </div>
          <AppEmptyState v-else title="暂无知识点诊断" description="学生完成课程测验后生成掌握度统计。" />
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.analytics-panel { display: grid; gap: var(--space-16); }.analytics-hero, .analytics-card { border: 1px solid var(--border-default); border-radius: var(--radius-16); background: var(--surface-panel); box-shadow: var(--shadow-sm); }.analytics-hero { position: relative; isolation: isolate; display: flex; overflow: hidden; align-items: flex-start; justify-content: space-between; gap: var(--space-16); padding: var(--space-24); background: linear-gradient(135deg, var(--surface-panel), var(--surface-accent)); }.analytics-hero::after { position: absolute; z-index: -1; top: -6rem; right: 8rem; width: 14rem; height: 14rem; border: 1px solid var(--border-accent); border-radius: 50%; content: ''; opacity: .42; }.analytics-hero > div > span, .analytics-card > header > div > span { display: inline-flex; align-items: center; gap: var(--space-6); color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; letter-spacing: .08em; }.analytics-hero h2, .analytics-card h3 { margin: var(--space-5) 0; font-family: var(--font-family-display); }.analytics-hero p { max-width: 52rem; margin: 0; color: var(--text-secondary); line-height: 1.65; }.analytics-error { margin: 0; padding: var(--space-10) var(--space-14); border-radius: var(--radius-10); background: var(--danger-surface); color: var(--danger-600); }
.metric-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: var(--space-10); }.metric-grid article { position: relative; display: grid; grid-template-columns: auto 1fr; overflow: hidden; align-items: center; gap: var(--space-10); min-width: 0; padding: var(--space-16); border: 1px solid var(--border-default); border-radius: var(--radius-12); background: linear-gradient(145deg, var(--surface-panel), color-mix(in srgb, var(--surface-muted) 72%, var(--surface-panel))); box-shadow: var(--shadow-sm); }.metric-grid article > span { display: grid; width: 2.55rem; height: 2.55rem; place-items: center; border-radius: var(--radius-10); background: var(--surface-accent); color: var(--text-accent); }.metric-grid .visual-metric > span { width: 3.25rem; height: 3.25rem; background: transparent; }.metric-grid .visual-metric img { width: 100%; height: 100%; object-fit: contain; filter: drop-shadow(var(--shadow-sm)); }.metric-grid article > div { display: grid; min-width: 0; }.metric-grid strong { font-family: var(--font-family-display); font-size: var(--text-title-sm); }.metric-grid small, .metric-grid em { color: var(--text-tertiary); font-size: var(--text-caption); font-style: normal; }.metric-grid em { grid-column: 1 / -1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.metric-grid .risk-metric { border-color: color-mix(in srgb, var(--danger-400) 42%, var(--border-default)); }.metric-grid .risk-metric > span { background: var(--danger-surface); color: var(--danger-600); }.metric-grid .risk-metric strong { color: var(--danger-600); }
.analytics-card { padding: var(--space-20); }.analytics-card > header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-14); margin-bottom: var(--space-14); }.analytics-card > header > small { max-width: 26rem; color: var(--text-tertiary); font-size: var(--text-caption); text-align: right; }.class-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr)); gap: var(--space-10); }.class-grid > article { display: grid; gap: var(--space-12); padding: var(--space-14); border: 1px solid var(--border-subtle); border-radius: var(--radius-11); background: var(--surface-muted); transition: border-color var(--transition-base), transform var(--transition-base); }.class-grid > article:hover { border-color: var(--border-accent); transform: translateY(-.1rem); }.class-grid article > header { display: flex; align-items: center; justify-content: space-between; }.class-grid article > header span { color: var(--text-tertiary); font-size: var(--text-caption); }.class-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--space-8); }.class-stats > span { display: grid; gap: var(--space-3); }.class-stats small { color: var(--text-tertiary); }.class-stats b { font-size: var(--text-subtitle); }.class-stats .danger b { color: var(--danger-600); }.completion-bar { height: .38rem; overflow: hidden; border-radius: var(--radius-16); background: var(--surface-panel); }.completion-bar span { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--primary-500), var(--success-500)); }
.analytics-columns { display: grid; grid-template-columns: minmax(0, 1.55fr) minmax(17rem, .75fr); gap: var(--space-16); align-items: start; }.student-section nav { display: flex; padding: var(--space-3); border: 1px solid var(--border-default); border-radius: var(--radius-9); background: var(--surface-muted); }.student-section nav button { padding: var(--space-6) var(--space-10); border: 0; border-radius: var(--radius-7); background: transparent; color: var(--text-secondary); font-size: var(--text-caption); }.student-section nav button.active { background: var(--surface-panel); color: var(--text-accent); box-shadow: var(--shadow-sm); }.student-list { display: grid; gap: var(--space-10); }.student-list > article { display: grid; grid-template-columns: minmax(12rem, 1fr) auto; gap: var(--space-10) var(--space-14); padding: var(--space-14); border: 1px solid var(--border-subtle); border-radius: var(--radius-11); background: color-mix(in srgb, var(--surface-panel) 80%, var(--surface-muted)); }.student-identity { display: flex; align-items: center; gap: var(--space-10); min-width: 0; }.student-identity > span { display: grid; flex: 0 0 auto; width: 2.35rem; height: 2.35rem; place-items: center; border-radius: 50%; background: var(--surface-accent); color: var(--text-accent); font-weight: 800; }.student-identity > div { display: grid; min-width: 0; }.student-identity small { overflow: hidden; color: var(--text-tertiary); text-overflow: ellipsis; white-space: nowrap; }.risk-tag { align-self: center; padding: var(--space-5) var(--space-8); border-radius: var(--radius-8); background: var(--success-surface); color: var(--success-600); font-size: var(--text-caption); font-weight: 800; }.risk-tag.high { background: var(--danger-surface); color: var(--danger-600); }.risk-tag.medium { background: var(--warning-surface); color: var(--warning-700); }.student-stats { grid-column: 1 / -1; display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--space-8); padding: var(--space-10); border-radius: var(--radius-9); background: var(--surface-muted); }.student-stats span { display: grid; gap: var(--space-3); }.student-stats small { color: var(--text-tertiary); }.student-list p { grid-column: 1 / -1; margin: 0; color: var(--text-secondary); line-height: 1.55; }.student-list footer { grid-column: 1 / -1; display: flex; align-items: center; justify-content: space-between; gap: var(--space-12); padding-top: var(--space-9); border-top: 1px solid var(--border-subtle); color: var(--text-tertiary); font-size: var(--text-caption); }.student-list footer span { display: inline-flex; align-items: center; gap: var(--space-5); min-width: 0; }.student-list time { flex: 0 0 auto; }
.weak-list { display: grid; }.weak-list article { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: var(--space-10); padding: var(--space-12) 0; border-top: 1px solid var(--border-subtle); }.weak-list article > span { color: var(--text-accent); font-family: var(--font-family-mono); font-size: var(--text-caption); font-weight: 800; }.weak-list article > div { display: grid; gap: var(--space-3); min-width: 0; }.weak-list small { color: var(--text-tertiary); }.weak-list b { color: var(--text-accent); }
@media (max-width: 1180px) { .metric-grid { grid-template-columns: repeat(3, 1fr); }.analytics-columns { grid-template-columns: 1fr; } }
@media (max-width: 760px) { .analytics-hero, .analytics-card > header { align-items: stretch; flex-direction: column; }.analytics-card > header > small { max-width: none; text-align: left; }.metric-grid { grid-template-columns: 1fr 1fr; }.student-section nav { align-self: flex-start; }.student-list footer { align-items: flex-start; flex-direction: column; }.analytics-card, .analytics-hero { padding: var(--space-16); } }
@media (max-width: 480px) { .metric-grid { grid-template-columns: 1fr; }.class-stats, .student-stats { grid-template-columns: 1fr 1fr; }.student-stats span:last-child { grid-column: 1 / -1; } }
</style>
