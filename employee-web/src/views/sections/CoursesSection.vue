<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowLeft, ArrowRight, BookOpenText, ClipboardCheck, Clock3, Compass, Grid2X2, List, ListChecks, Route as RouteIcon, Search, Sparkles } from 'lucide-vue-next';
import { employeeApi, type AssessmentResponse, type CourseChapterResponse, type CourseResponse, type EntityId, type StudentLearningDashboardResponse } from '@/api';
import AppErrorState from '@/components/ui/AppErrorState.vue';
import AppLoadingState from '@/components/ui/AppLoadingState.vue';
import CourseContentRenderer from '@/components/course/CourseContentRenderer.vue';
import CourseAssessmentPanel from '@/components/course/CourseAssessmentPanel.vue';
import TeacherCoursesSection from './TeacherCoursesSection.vue';
import { useSessionStore } from '@/stores/session';
import courseDashboardHero from '@/assets/course-dashboard-hero-3d.webp';
import todayTasksIcon from '@/assets/education/icons/today-tasks.webp';
import learningProgressIcon from '@/assets/education/icons/learning-progress.webp';
import knowledgeMasteryIcon from '@/assets/education/icons/knowledge-mastery.webp';
import pendingPracticeIcon from '@/assets/education/icons/pending-practice.webp';
import recommendedResourcesIcon from '@/assets/education/icons/recommended-resources.webp';

const session = useSessionStore();
const route = useRoute();
const router = useRouter();
const courses = ref<CourseResponse[]>([]);
const selected = ref<CourseResponse | null>(null);
const activeChapter = ref<CourseChapterResponse | null>(null);
const assessments = ref<AssessmentResponse[]>([]);
const dashboard = ref<StudentLearningDashboardResponse | null>(null);
const activeAssessment = ref<AssessmentResponse | null>(null);
const loading = ref(false);
const error = ref('');
const dashboardError = ref('');
const keyword = ref('');
const viewMode = ref<'grid' | 'list'>('grid');
const joiningCourseId = ref('');

const filteredCourses = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  if (!query) return courses.value;
  return courses.value.filter((course) => `${course.courseName} ${course.courseCode} ${course.teacherName ?? ''}`.toLowerCase().includes(query));
});

async function loadStudentHome() {
  loading.value = true;
  error.value = '';
  dashboardError.value = '';
  try {
    const [courseResult, dashboardResult] = await Promise.allSettled([
      employeeApi.courses.list(),
      employeeApi.studentDashboard.current(),
    ]);
    if (courseResult.status === 'fulfilled') courses.value = courseResult.value;
    else error.value = courseResult.reason instanceof Error ? courseResult.reason.message : '课程加载失败';
    if (dashboardResult.status === 'fulfilled') dashboard.value = dashboardResult.value;
    else dashboardError.value = dashboardResult.reason instanceof Error ? dashboardResult.reason.message : '学习概览加载失败';
  } finally {
    loading.value = false;
  }
}

function percent(value?: number | null) {
  return value == null ? '—' : `${Math.round(value)}%`;
}

function openDashboardPath(courseId: EntityId) {
  void router.push({ name: 'student-course-learning-path', params: { courseId: String(courseId) } });
}

async function openCourse(courseId: EntityId, chapterId?: EntityId | null) {
  loading.value = true;
  error.value = '';
  try {
    const listedCourse = courses.value.find((course) => String(course.courseId) === String(courseId));
    if (!listedCourse?.enrolled) {
      joiningCourseId.value = String(courseId);
      const enrolledCourse = await employeeApi.courses.enroll(courseId);
      courses.value = courses.value.map((course) => String(course.courseId) === String(courseId) ? { ...course, ...enrolledCourse } : course);
    }
    const [course, courseAssessments] = await Promise.all([
      employeeApi.courses.detail(courseId),
      employeeApi.courses.assessments(courseId),
    ]);
    selected.value = course;
    assessments.value = courseAssessments;
    activeChapter.value = chapterId == null
      ? null
      : course.chapters.find((chapter) => String(chapter.chapterId) === String(chapterId)) ?? null;
    activeAssessment.value = null;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '课程加载失败';
  } finally {
    joiningCourseId.value = '';
    loading.value = false;
  }
}

function openChapter(chapter: CourseChapterResponse) {
  activeChapter.value = chapter;
  activeAssessment.value = null;
}

function openAssessment(assessment: AssessmentResponse) {
  activeAssessment.value = assessment;
  activeChapter.value = null;
}

function closeCourse() {
  selected.value = null;
  activeChapter.value = null;
  activeAssessment.value = null;
  assessments.value = [];
  if (route.query.courseId || route.query.chapterId) {
    void router.replace({ name: 'employee-courses' });
  }
}

function openLearningPath() {
  if (!selected.value) return;
  void router.push({ name: 'student-course-learning-path', params: { courseId: String(selected.value.courseId) } });
}

function openLearningResources() {
  if (!selected.value) return;
  void router.push({ name: 'student-learning-resources', params: { courseId: String(selected.value.courseId) } });
}

function resourceUrl(attachmentRefId?: EntityId | null) {
  return attachmentRefId == null ? '' : employeeApi.courses.resourceUrl(attachmentRefId);
}

onMounted(async () => {
  if (session.educationRole === 'TEACHER') return;
  await loadStudentHome();
  const requestedCourseId = Array.isArray(route.query.courseId) ? route.query.courseId[0] : route.query.courseId;
  const requestedChapterId = Array.isArray(route.query.chapterId) ? route.query.chapterId[0] : route.query.chapterId;
  if (requestedCourseId) await openCourse(requestedCourseId, requestedChapterId);
});
</script>

<template>
  <TeacherCoursesSection v-if="session.educationRole === 'TEACHER'" />
  <div v-else class="course-page">
    <AppLoadingState v-if="loading && !courses.length" />
    <AppErrorState v-else-if="error && !courses.length" title="课程加载失败" :description="error" action-label="重试" @retry="loadStudentHome" />

    <template v-else-if="!selected">
      <header class="learning-home-head">
        <div class="learning-home-copy">
          <span>我的学习</span><h1>{{ session.displayName || '同学' }}，继续今天的学习</h1><p>从当前任务出发，完成课程学习、测验和个性化资源复习。</p>
          <button v-if="dashboard?.todayTasks[0]" type="button" @click="openDashboardPath(dashboard.todayTasks[0].courseId)"><span><Compass :size="18" /></span><span><small>下一项学习</small><strong>{{ dashboard.todayTasks[0].resourceTitle }}</strong></span><ArrowRight :size="18" /></button>
        </div>
        <div class="learning-home-visual" aria-hidden="true">
          <img :src="courseDashboardHero" alt="" />
          <span v-if="dashboard" class="hero-progress-note"><small>总体进度</small><strong>{{ percent(dashboard.overallProgress) }}</strong></span>
        </div>
      </header>

      <p v-if="dashboardError" class="dashboard-error">{{ dashboardError }} <button type="button" @click="loadStudentHome">重新加载</button></p>
      <section v-if="dashboard" class="learning-metrics" aria-label="学习首页指标">
        <article><span><img :src="todayTasksIcon" alt="" aria-hidden="true" /></span><div><strong>{{ dashboard.todayTaskCount }}</strong><small>今日任务</small></div><em>{{ dashboard.courseCount }} 门课程</em></article>
        <article><span><img :src="learningProgressIcon" alt="" aria-hidden="true" /></span><div><strong>{{ percent(dashboard.overallProgress) }}</strong><small>学习进度</small></div><em>活动路径总体完成度</em></article>
        <article><span><img :src="knowledgeMasteryIcon" alt="" aria-hidden="true" /></span><div><strong>{{ percent(dashboard.averageMastery) }}</strong><small>平均掌握度</small></div><em>来自知识点测验</em></article>
        <article><span><img :src="pendingPracticeIcon" alt="" aria-hidden="true" /></span><div><strong>{{ dashboard.pendingAssessmentCount }}</strong><small>待完成练习</small></div><em>尚未提交的课程测验</em></article>
        <article><span><img :src="recommendedResourcesIcon" alt="" aria-hidden="true" /></span><div><strong>{{ dashboard.recommendationCount }}</strong><small>推荐资源</small></div><em>按画像与掌握度排序</em></article>
      </section>

      <div v-if="dashboard" class="learning-focus-grid">
        <section class="focus-card today-focus">
          <header><div><span>今日学习</span><h2>按当前路径继续</h2></div><small>{{ dashboard.todayTaskCount }} 项安排</small></header>
          <div v-if="dashboard.todayTasks.length" class="today-task-list">
            <button v-for="task in dashboard.todayTasks" :key="String(task.nodeId)" type="button" @click="openDashboardPath(task.courseId)">
              <span :class="task.status.toLowerCase()"><Clock3 :size="17" /></span>
              <span><small>{{ task.courseName }} · {{ task.chapterName }}</small><strong>{{ task.resourceTitle }}</strong><p>{{ task.recommendationReason }}</p></span>
              <em>{{ task.estimatedMinutes }} 分钟</em><ArrowRight :size="17" />
            </button>
          </div>
          <div v-else class="focus-empty"><ListChecks :size="28" /><strong>暂无今日任务</strong><p>进入课程生成学习路径后，这里会汇总下一步安排。</p></div>
        </section>

        <section class="focus-card recommendation-focus">
          <header><div><span>推荐资源</span><h2>为什么现在学这些</h2></div></header>
          <div v-if="dashboard.recommendations.length" class="recommendation-list">
            <button v-for="item in dashboard.recommendations" :key="`${String(item.courseId)}-${String(item.nodeId)}`" type="button" @click="openDashboardPath(item.courseId)"><span>{{ String(item.priorityNo).padStart(2, '0') }}</span><span><small>{{ item.courseName }}</small><strong>{{ item.resourceTitle }}</strong><p>{{ item.reason }}</p></span><ArrowRight :size="16" /></button>
          </div>
          <div v-else class="focus-empty"><Sparkles :size="28" /><strong>暂无个性化推荐</strong><p>完成画像或测验后生成推荐理由。</p></div>
        </section>
      </div>

      <header class="library-head">
        <div><span>全部课程</span><h2>课程学习</h2><p>选择课程进入章节、测验、学习路径和个性化资源。</p></div>
        <div class="library-tools">
          <label class="course-search"><Search :size="17" /><input v-model="keyword" placeholder="搜索课程" /></label>
          <div class="view-switch" aria-label="课程视图">
            <button type="button" :class="{ active: viewMode === 'grid' }" aria-label="网格视图" @click="viewMode = 'grid'"><Grid2X2 :size="17" /></button>
            <button type="button" :class="{ active: viewMode === 'list' }" aria-label="列表视图" @click="viewMode = 'list'"><List :size="18" /></button>
          </div>
        </div>
      </header>

      <section class="course-library" :class="`mode-${viewMode}`">
        <button v-for="course in filteredCourses" :key="String(course.courseId)" type="button" class="course-card" @click="openCourse(course.courseId)">
          <span class="course-cover">
            <img v-if="course.coverAttachmentRefId" :src="resourceUrl(course.coverAttachmentRefId)" :alt="course.courseName" />
            <span v-else><BookOpenText :size="34" /><b>{{ course.courseCode }}</b></span>
          </span>
          <span class="course-card-body">
            <small>{{ course.courseCode }}</small>
            <strong>{{ course.courseName }}</strong>
            <p>{{ course.summary || '暂无课程简介' }}</p>
            <span class="course-card-meta"><span>{{ course.teacherName || '课程组' }}</span><span>{{ course.totalHours }} 学时</span><span>{{ course.chapterCount }} 章</span><em :class="{ enrolled: course.enrolled }">{{ joiningCourseId === String(course.courseId) ? '正在加入' : course.enrolled ? '已加入' : '加入课程' }}</em></span>
          </span>
        </button>
        <p v-if="!filteredCourses.length" class="course-empty">没有符合条件的课程</p>
      </section>
    </template>

    <template v-else>
      <header class="detail-head">
        <button type="button" class="back-button" @click="closeCourse"><ArrowLeft :size="18" />全部课程</button>
        <div class="detail-course-title">
          <span class="detail-cover"><img v-if="selected.coverAttachmentRefId" :src="resourceUrl(selected.coverAttachmentRefId)" :alt="selected.courseName" /><BookOpenText v-else :size="28" /></span>
          <div><small>{{ selected.courseCode }}</small><h1>{{ selected.courseName }}</h1><p>{{ selected.summary }}</p></div>
        </div>
        <div class="detail-action-row">
          <div class="detail-meta"><span><Clock3 :size="16" />{{ selected.totalHours }} 学时</span><span><BookOpenText :size="16" />{{ selected.chapterCount }} 章</span></div>
          <div class="detail-buttons">
            <button type="button" class="resource-button" @click="openLearningResources"><Sparkles :size="17" />个性化资源</button>
            <button type="button" class="learning-path-button" @click="openLearningPath"><RouteIcon :size="17" />我的学习路径</button>
          </div>
        </div>
      </header>

      <div class="learning-layout">
        <aside class="chapter-nav">
          <header><span>课程章节</span><strong>{{ selected.chapters.length }}</strong></header>
          <button v-for="chapter in selected.chapters" :key="String(chapter.chapterId)" type="button" :class="{ active: String(activeChapter?.chapterId) === String(chapter.chapterId) }" @click="openChapter(chapter)">
            <span>{{ String(chapter.chapterNo).padStart(2, '0') }}</span>
            <span><strong>{{ chapter.chapterName }}</strong><small>文章</small></span>
          </button>
          <header class="assessment-nav-head"><span>课程测验</span><strong>{{ assessments.length }}</strong></header>
          <button
            v-for="assessment in assessments"
            :key="String(assessment.assessmentId)"
            type="button"
            class="assessment-nav-item"
            :class="{ active: String(activeAssessment?.assessmentId) === String(assessment.assessmentId) }"
            @click="openAssessment(assessment)"
          >
            <span><ClipboardCheck :size="18" /></span>
            <span><strong>{{ assessment.title }}</strong><small>{{ assessment.questionCount }} 题 · {{ assessment.totalScore }} 分</small></span>
          </button>
          <p v-if="!assessments.length" class="assessment-nav-empty">暂无已发布测验</p>
        </aside>

        <main class="chapter-content">
          <CourseAssessmentPanel
            v-if="activeAssessment"
            :course-id="selected.courseId"
            :assessment="activeAssessment"
          />
          <section v-else-if="!activeChapter" class="course-overview">
            <span>课程导学</span><h2>{{ selected.courseName }}</h2><p>{{ selected.summary }}</p>
            <div><article><strong>学习目标</strong><p>{{ selected.learningObjectives || '暂无' }}</p></article><article><strong>先修要求</strong><p>{{ selected.prerequisites || '暂无' }}</p></article></div>
            <button type="button" class="learning-path-entry" @click="openLearningPath">
              <span><RouteIcon :size="22" /></span>
              <span><strong>进入个性化学习路径</strong><small>查看今日安排、完整路径和推荐资源</small></span>
              <span>开始学习</span>
            </button>
          </section>
          <article v-else class="chapter-reader">
            <header><span>第 {{ activeChapter.chapterNo }} 章</span><h2>{{ activeChapter.chapterName }}</h2><p>{{ activeChapter.summary }}</p></header>
            <CourseContentRenderer :content="activeChapter.contentJson" />
          </article>
        </main>
      </div>
    </template>
  </div>
</template>

<style scoped>
.course-page { height: 100%; overflow: auto; padding: var(--layout-content-pad); background-color: var(--surface-canvas); background-image: var(--canvas-backdrop); color: var(--text-primary); }
.learning-home-head, .library-head, .detail-head { margin-bottom: var(--space-24); }.learning-home-head { position: relative; display: grid; grid-template-columns: minmax(0, 1fr) minmax(20rem, 31rem); min-height: 17rem; gap: var(--space-20); overflow: hidden; padding: clamp(var(--space-24), 4vw, var(--space-40)); border: 1px solid var(--border-accent); border-radius: var(--radius-20); background: var(--hero-gradient); box-shadow: var(--shadow-md); }.learning-home-head::after { position: absolute; right: -6rem; bottom: -9rem; width: 24rem; height: 24rem; border: 1px solid color-mix(in srgb, var(--primary-300) 36%, transparent); border-radius: 50%; content: ''; }.learning-home-copy { position: relative; z-index: 2; display: grid; max-width: 47rem; align-content: center; justify-items: start; }.learning-home-copy > span, .focus-card > header span { color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; letter-spacing: .1em; }.learning-home-head h1 { margin: var(--space-8) 0; font-family: var(--font-family-display); font-size: clamp(var(--text-title), 3vw, var(--text-headline)); line-height: 1.2; }.learning-home-head p { max-width: 40rem; margin: 0; color: var(--text-secondary); line-height: 1.7; }.learning-home-copy > button { display: grid; grid-template-columns: auto minmax(10rem, 1fr) auto; align-items: center; gap: var(--space-10); width: min(25rem, 100%); margin-top: var(--space-22); padding: var(--space-12); border: 1px solid var(--border-default); border-radius: var(--radius-12); background: var(--surface-glass); color: var(--text-primary); text-align: left; box-shadow: var(--shadow-sm); backdrop-filter: blur(.5rem); }.learning-home-copy > button:hover { border-color: var(--border-accent); box-shadow: var(--shadow-md); transform: translateY(-.1rem); }.learning-home-copy > button > span:first-child { display: grid; width: 2.6rem; height: 2.6rem; place-items: center; border-radius: var(--radius-10); background: var(--primary-500); color: var(--text-inverse); }.learning-home-copy > button > span:nth-child(2) { display: grid; min-width: 0; }.learning-home-copy > button small { color: var(--text-tertiary); }.learning-home-copy > button strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.learning-home-visual { position: relative; z-index: 1; min-width: 0; min-height: 14rem; }.learning-home-visual img { position: absolute; top: -4rem; right: -4rem; width: 36rem; max-width: none; filter: drop-shadow(0 1.2rem 1.6rem color-mix(in srgb, var(--primary-800) 18%, transparent)); animation: course-hero-float 6s ease-in-out infinite; }.hero-progress-note { position: absolute; z-index: 2; bottom: var(--space-8); left: 0; display: grid; min-width: 7.5rem; gap: var(--space-3); padding: var(--space-12) var(--space-14); border: 1px solid color-mix(in srgb, var(--primary-300) 46%, transparent); border-radius: var(--radius-12); background: var(--surface-glass); box-shadow: var(--shadow-md); backdrop-filter: blur(.75rem); }.hero-progress-note small { color: var(--text-tertiary); }.hero-progress-note strong { color: var(--text-accent); font-family: var(--font-family-display); font-size: var(--text-title); }.dashboard-error { display: flex; align-items: center; justify-content: space-between; gap: var(--space-10); margin: 0 0 var(--space-16); padding: var(--space-10) var(--space-14); border-radius: var(--radius-10); background: var(--danger-surface); color: var(--danger-600); }.dashboard-error button { border: 0; background: transparent; color: inherit; font-weight: 800; }
.learning-metrics { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: var(--space-10); margin-bottom: var(--space-16); }.learning-metrics article { position: relative; display: grid; grid-template-columns: auto 1fr; align-items: center; gap: var(--space-10); min-width: 0; overflow: hidden; padding: var(--space-16); border: 1px solid var(--border-default); border-radius: var(--radius-14, var(--radius-12)); background: var(--surface-panel); box-shadow: var(--shadow-sm); transition: border-color var(--transition-base), box-shadow var(--transition-base), transform var(--transition-base); }.learning-metrics article::after { position: absolute; right: -2rem; bottom: -2.5rem; width: 5rem; height: 5rem; border-radius: 50%; background: color-mix(in srgb, var(--surface-accent) 76%, transparent); content: ''; }.learning-metrics article:hover { border-color: var(--border-accent); box-shadow: var(--shadow-md); transform: translateY(-.12rem); }.learning-metrics article > span { position: relative; z-index: 1; display: grid; width: 3.4rem; height: 3.4rem; place-items: center; }.learning-metrics article > span img { width: 100%; height: 100%; object-fit: contain; filter: drop-shadow(0 .35rem .55rem color-mix(in srgb, var(--primary-800) 12%, transparent)); }.learning-metrics article > div { position: relative; z-index: 1; display: grid; min-width: 0; }.learning-metrics strong { font-family: var(--font-family-display); font-size: var(--text-title-sm); }.learning-metrics small, .learning-metrics em { position: relative; z-index: 1; color: var(--text-tertiary); font-size: var(--text-caption); font-style: normal; }.learning-metrics em { grid-column: 1 / -1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.learning-focus-grid { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(18rem, .75fr); gap: var(--space-16); margin-bottom: var(--space-28); }.focus-card { overflow: hidden; padding: var(--space-20); border: 1px solid var(--border-default); border-radius: var(--radius-16); background: linear-gradient(155deg, var(--surface-panel) 72%, color-mix(in srgb, var(--surface-accent) 58%, var(--surface-panel))); box-shadow: var(--shadow-sm); }.focus-card > header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-12); margin-bottom: var(--space-12); }.focus-card h2 { margin: var(--space-4) 0; font-family: var(--font-family-display); }.focus-card > header > small { color: var(--text-tertiary); }.today-task-list, .recommendation-list { display: grid; }.today-task-list button { display: grid; grid-template-columns: auto minmax(0, 1fr) auto auto; align-items: center; gap: var(--space-10); padding: var(--space-12) 0; border: 0; border-top: 1px solid var(--border-subtle); background: transparent; color: var(--text-primary); text-align: left; }.today-task-list button:hover, .recommendation-list button:hover { color: var(--text-accent); }.today-task-list button > span:first-child { display: grid; width: 2.25rem; height: 2.25rem; place-items: center; border-radius: var(--radius-8); background: var(--surface-muted); color: var(--text-tertiary); }.today-task-list button > span:first-child.in_progress { background: var(--surface-accent); color: var(--text-accent); }.today-task-list button > span:nth-child(2) { display: grid; gap: var(--space-3); min-width: 0; }.today-task-list small, .recommendation-list small { color: var(--text-accent); font-weight: 700; }.today-task-list p, .recommendation-list p { overflow: hidden; margin: 0; color: var(--text-secondary); font-size: var(--text-caption); text-overflow: ellipsis; white-space: nowrap; }.today-task-list em { color: var(--text-tertiary); font-size: var(--text-caption); font-style: normal; }.recommendation-list button { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: var(--space-10); padding: var(--space-12) 0; border: 0; border-top: 1px solid var(--border-subtle); background: transparent; color: var(--text-primary); text-align: left; }.recommendation-list button > span:first-child { color: var(--text-accent); font-family: var(--font-family-mono); font-size: var(--text-caption); font-weight: 800; }.recommendation-list button > span:nth-child(2) { display: grid; gap: var(--space-3); min-width: 0; }.focus-empty { display: grid; min-height: 10rem; place-content: center; justify-items: center; gap: var(--space-6); color: var(--text-tertiary); text-align: center; }.focus-empty p { margin: 0; }
.library-head { display: flex; align-items: end; justify-content: space-between; gap: var(--space-24); }
.library-head h2, .detail-head h1 { margin: var(--space-4) 0; font-family: var(--font-family-display); }
.library-head > div:first-child > span, .course-card-body small, .detail-course-title small, .course-overview > span, .chapter-reader > header > span { color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; letter-spacing: .1em; }
.library-head p, .detail-course-title p { margin: 0; color: var(--text-secondary); }
.library-tools { display: flex; gap: var(--space-10); }
.course-search { display: flex; align-items: center; gap: var(--space-8); min-width: 18rem; padding: 0 var(--space-12); border: 1px solid var(--border-default); border-radius: var(--radius-10); background: var(--surface-panel); color: var(--text-tertiary); }
.course-search input { width: 100%; min-height: 2.6rem; border: 0; outline: 0; background: transparent; color: var(--text-primary); }
.view-switch { display: flex; padding: var(--space-4); border: 1px solid var(--border-default); border-radius: var(--radius-10); background: var(--surface-panel); }
.view-switch button { width: 2.2rem; border: 0; border-radius: var(--radius-8); background: transparent; color: var(--text-tertiary); }.view-switch button.active { background: var(--surface-accent); color: var(--text-accent); }
.course-library.mode-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(17rem, 1fr)); gap: var(--space-20); }
.course-card { overflow: hidden; padding: 0; border: 1px solid var(--border-default); border-radius: var(--radius-16); background: var(--surface-panel); color: var(--text-primary); text-align: left; transition: transform .18s ease, box-shadow .18s ease; }.course-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-md); }
.course-cover { display: block; aspect-ratio: 16 / 9; overflow: hidden; background: var(--surface-accent); }.course-cover img { width: 100%; height: 100%; object-fit: cover; }.course-cover > span { display: grid; width: 100%; height: 100%; place-content: center; justify-items: center; gap: var(--space-10); color: var(--text-accent); }.course-cover b { font-size: var(--text-caption); letter-spacing: .12em; }
.course-card-body { display: grid; gap: var(--space-8); padding: var(--space-18); }.course-card-body > strong { font-size: var(--text-subtitle); }.course-card-body > p { display: -webkit-box; min-height: 3em; margin: 0; overflow: hidden; color: var(--text-secondary); line-height: 1.5; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.course-card-meta { display: flex; flex-wrap: wrap; gap: var(--space-12); color: var(--text-tertiary); font-size: var(--text-caption); }.course-card-meta em { margin-left: auto; color: var(--text-accent); font-style: normal; font-weight: 800; }.course-card-meta em.enrolled { color: var(--success-600); }
.course-library.mode-list { display: grid; gap: var(--space-12); }.mode-list .course-card { display: grid; grid-template-columns: 13rem 1fr; }.mode-list .course-cover { aspect-ratio: 16 / 9; }.mode-list .course-card-body > p { min-height: 0; }.course-empty { grid-column: 1 / -1; padding: var(--space-40); color: var(--text-tertiary); text-align: center; }
.back-button { display: inline-flex; align-items: center; gap: var(--space-6); margin-bottom: var(--space-20); padding: 0; border: 0; background: transparent; color: var(--text-accent); }
.detail-course-title { display: flex; align-items: center; gap: var(--space-16); }.detail-cover { width: 6rem; aspect-ratio: 16 / 10; display: grid; overflow: hidden; place-items: center; border-radius: var(--radius-12); background: var(--surface-accent); color: var(--text-accent); }.detail-cover img { width: 100%; height: 100%; object-fit: cover; }.detail-course-title p { max-width: 50rem; }.detail-meta { display: flex; gap: var(--space-16); margin-top: var(--space-16); color: var(--text-secondary); }.detail-meta span { display: inline-flex; align-items: center; gap: var(--space-6); }
.detail-action-row { display: flex; align-items: center; justify-content: space-between; gap: var(--space-16); margin-top: var(--space-16); }.detail-action-row .detail-meta { margin-top: 0; }.detail-buttons { display: flex; gap: var(--space-8); }.learning-path-button, .resource-button { display: inline-flex; align-items: center; gap: var(--space-6); min-height: 2.6rem; padding: 0 var(--space-14); border-radius: var(--radius-10); font-weight: 700; }.learning-path-button { border: 1px solid var(--primary-600); background: var(--primary-600); color: var(--text-inverse); }.resource-button { border: 1px solid var(--border-accent); background: var(--surface-accent); color: var(--text-accent); }
.learning-layout { display: grid; grid-template-columns: minmax(17rem, 22rem) minmax(0, 1fr); gap: var(--space-16); align-items: start; }.chapter-nav, .chapter-content { border: 1px solid var(--border-default); border-radius: var(--radius-16); background: var(--surface-panel); }.chapter-nav { overflow: hidden; }.chapter-nav > header { display: flex; justify-content: space-between; padding: var(--space-16); border-bottom: 1px solid var(--border-subtle); }.chapter-nav > button { width: 100%; display: grid; grid-template-columns: 2.3rem 1fr; gap: var(--space-10); padding: var(--space-14) var(--space-16); border: 0; border-bottom: 1px solid var(--border-subtle); background: transparent; color: var(--text-primary); text-align: left; }.chapter-nav > button.active { background: var(--surface-accent); }.chapter-nav > button > span:first-child { color: var(--text-accent); font-weight: 800; }.chapter-nav > button > span:last-child { display: grid; gap: var(--space-4); }.chapter-nav small { color: var(--text-tertiary); }
.chapter-nav > .assessment-nav-head { margin-top: var(--space-8); border-top: 1px solid var(--border-subtle); }
.assessment-nav-item > span:first-child { display: grid; place-items: center; }
.assessment-nav-empty { margin: 0; padding: var(--space-14) var(--space-16); color: var(--text-tertiary); font-size: var(--text-caption); }
.chapter-content { min-height: 28rem; padding: clamp(var(--space-20), 4vw, var(--space-40)); }.course-overview > h2, .chapter-reader h2 { margin: var(--space-8) 0; font-size: var(--text-title); }.course-overview > p, .chapter-reader header p { color: var(--text-secondary); line-height: 1.7; }.course-overview > div { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-12); margin-top: var(--space-24); }.course-overview article { padding: var(--space-18); border-radius: var(--radius-12); background: var(--surface-muted); }.course-overview article p { color: var(--text-secondary); line-height: 1.7; white-space: pre-line; }
.learning-path-entry { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: var(--space-14); width: 100%; margin-top: var(--space-16); padding: var(--space-16); border: 1px solid var(--border-accent); border-radius: var(--radius-12); background: var(--surface-accent); color: var(--text-primary); text-align: left; }.learning-path-entry > span:first-child { display: grid; width: 2.8rem; height: 2.8rem; place-items: center; border-radius: var(--radius-10); background: var(--primary-500); color: var(--text-inverse); }.learning-path-entry > span:nth-child(2) { display: grid; gap: var(--space-4); }.learning-path-entry small { color: var(--text-secondary); }.learning-path-entry > span:last-child { color: var(--text-accent); font-weight: 800; }
.chapter-reader > header { margin-bottom: var(--space-24); padding-bottom: var(--space-20); border-bottom: 1px solid var(--border-subtle); }.video-content video { width: 100%; max-height: 34rem; border-radius: var(--radius-12); background: var(--surface-inverse); }.video-content p { display: flex; align-items: center; gap: var(--space-8); color: var(--text-secondary); }.file-content { display: flex; align-items: center; gap: var(--space-16); padding: var(--space-20); border: 1px solid var(--border-default); border-radius: var(--radius-12); color: var(--text-primary); text-decoration: none; }.file-content > span { display: grid; gap: var(--space-4); flex: 1; }.file-content small { color: var(--text-tertiary); }.file-content > b { color: var(--text-accent); }
@keyframes course-hero-float { 0%, 100% { transform: translate3d(0, 0, 0) rotate(.4deg); } 50% { transform: translate3d(0, -.55rem, 0) rotate(-.3deg); } }
@media (max-width: 1120px) { .learning-metrics { grid-template-columns: repeat(3, 1fr); }.learning-focus-grid { grid-template-columns: 1fr; } }
@media (max-width: 900px) { .learning-home-head { grid-template-columns: 1fr; }.learning-home-visual { min-height: 16rem; }.learning-home-visual img { top: -3rem; right: -3rem; width: 34rem; }.learning-home-copy > button { max-width: none; }.library-head { align-items: stretch; flex-direction: column; }.learning-layout { grid-template-columns: 1fr; }.chapter-nav { max-height: 20rem; overflow: auto; } }
@media (max-width: 640px) { .course-page { padding: var(--space-12); padding-bottom: 7rem; }.learning-home-head, .focus-card { padding: var(--space-16); }.learning-home-head { min-height: 0; }.learning-home-copy > button { grid-template-columns: auto minmax(0, 1fr) auto; }.learning-home-visual { min-height: 13rem; }.learning-home-visual img { top: -1rem; right: -4.5rem; width: 29rem; }.hero-progress-note { bottom: 0; left: 0; }.learning-metrics { grid-template-columns: 1fr 1fr; }.library-tools { flex-direction: column; }.course-search { min-width: 0; }.mode-list .course-card { grid-template-columns: 7rem 1fr; }.course-overview > div { grid-template-columns: 1fr; }.detail-course-title { align-items: flex-start; }.detail-cover { width: 4.5rem; flex: 0 0 auto; }.detail-action-row { align-items: stretch; flex-direction: column; }.detail-buttons { flex-direction: column; }.learning-path-button, .resource-button { width: 100%; justify-content: center; }.learning-path-entry { grid-template-columns: auto minmax(0, 1fr); }.learning-path-entry > span:last-child { grid-column: 2; }.today-task-list button { grid-template-columns: auto minmax(0, 1fr) auto; }.today-task-list button > em { grid-column: 2; }.today-task-list button > svg { grid-column: 3; grid-row: 1 / 3; } }
@media (max-width: 420px) { .learning-metrics { grid-template-columns: 1fr; } }
@media (prefers-reduced-motion: reduce) { .learning-home-visual img { animation: none; } }
</style>
