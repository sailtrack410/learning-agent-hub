<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { BookOpenText, ChevronRight, Database, FileText, Filter, Search, ShieldCheck, Sparkles } from 'lucide-vue-next';
import { useRouter } from 'vue-router';
import {
  employeeApi,
  type CourseKnowledgeDocumentResponse,
  type CourseResponse,
  type EntityId,
  type GeneratedLearningResourceResponse,
} from '@/api';
import { useSessionStore } from '@/stores/session';
import AppButton from '@/components/ui/AppButton.vue';
import AppDialog from '@/components/ui/AppDialog.vue';
import AppEmptyState from '@/components/ui/AppEmptyState.vue';
import AppErrorState from '@/components/ui/AppErrorState.vue';
import AppLoadingState from '@/components/ui/AppLoadingState.vue';
import GeneratedResourceContent from '@/components/course/GeneratedResourceContent.vue';
import resourceLibraryHero from '@/assets/resource-library-hero-25d.webp';

type ResourceKind = 'ALL' | 'ARTICLE' | 'GENERATED' | 'DOCUMENT';

interface ArticleItem {
  key: string;
  courseId: EntityId;
  courseName: string;
  chapterId: EntityId;
  chapterNo: number;
  title: string;
  summary?: string | null;
  status: 'DRAFT' | 'PUBLISHED';
}

interface DocumentItem extends CourseKnowledgeDocumentResponse {
  courseId: EntityId;
  courseName: string;
}

const session = useSessionStore();
const router = useRouter();
const courses = ref<CourseResponse[]>([]);
const generatedResources = ref<GeneratedLearningResourceResponse[]>([]);
const documents = ref<DocumentItem[]>([]);
const selectedResource = ref<GeneratedLearningResourceResponse | null>(null);
const loading = ref(false);
const error = ref('');
const keyword = ref('');
const courseFilter = ref('ALL');
const kindFilter = ref<ResourceKind>('ALL');

const isTeacher = computed(() => session.educationRole === 'TEACHER');
const articles = computed<ArticleItem[]>(() => courses.value.flatMap((course) => course.chapters
  .filter((chapter) => isTeacher.value || chapter.status === 'PUBLISHED')
  .map((chapter) => ({
    key: `${String(course.courseId)}-${String(chapter.chapterId)}`,
    courseId: course.courseId,
    courseName: course.courseName,
    chapterId: chapter.chapterId,
    chapterNo: chapter.chapterNo,
    title: chapter.chapterName,
    summary: chapter.summary,
    status: chapter.status,
  }))));
const visibleArticles = computed(() => articles.value.filter((item) => matches(item.courseId, `${item.courseName} ${item.title} ${item.summary ?? ''}`)));
const visibleGenerated = computed(() => generatedResources.value.filter((item) => matches(item.courseId, `${item.courseName} ${item.chapterName} ${item.knowledgePointName} ${item.resourceTypeName} ${item.title} ${item.summary}`)));
const visibleDocuments = computed(() => documents.value.filter((item) => matches(item.courseId, `${item.courseName} ${item.chapterName ?? ''} ${item.title} ${item.parserTypeName}`)));
onMounted(loadLibrary);

async function loadLibrary() {
  loading.value = true;
  error.value = '';
  try {
    if (isTeacher.value) {
      const summaries = await employeeApi.teacherCourses.list();
      courses.value = await Promise.all(summaries.map((course) => employeeApi.teacherCourses.detail(course.courseId)));
      const [resources, documentGroups] = await Promise.all([
        employeeApi.teacherLearningResources.list(),
        Promise.all(courses.value.map(async (course) => (await employeeApi.teacherCourses.knowledgeDocuments(course.courseId))
          .map((document) => ({ ...document, courseId: course.courseId, courseName: course.courseName })))),
      ]);
      generatedResources.value = resources;
      documents.value = documentGroups.flat();
      return;
    }
    const summaries = await employeeApi.courses.list();
    courses.value = await Promise.all(summaries.map((course) => employeeApi.courses.detail(course.courseId)));
    generatedResources.value = await employeeApi.learningResources.list();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '教学资源加载失败';
  } finally {
    loading.value = false;
  }
}

function matches(courseId: EntityId, content: string) {
  if (courseFilter.value !== 'ALL' && String(courseId) !== courseFilter.value) return false;
  const query = keyword.value.trim().toLowerCase();
  return !query || content.toLowerCase().includes(query);
}

function openArticle(item: ArticleItem) {
  void router.push({ name: 'employee-courses', query: { courseId: String(item.courseId), chapterId: String(item.chapterId) } });
}

function documentResourceUrl(attachmentRefId: EntityId) {
  return employeeApi.courses.resourceUrl(attachmentRefId);
}
</script>

<template>
  <main class="education-library">
    <header class="library-hero">
      <div class="library-hero-copy"><span><BookOpenText :size="16" />{{ isTeacher ? '教学资源中心' : '学习资源中心' }}</span><h1>{{ isTeacher ? '按课程管理教学资源' : '按课程整理学习资料' }}</h1><p>{{ isTeacher ? '统一查看章节文章、课程资料入库状态和学生个性化资源。' : '课程文章与个性化资源按课程、章节和资源类型集中呈现。' }}</p></div>
      <div class="library-hero-visual" aria-hidden="true"><img :src="resourceLibraryHero" alt="" /><div class="hero-count"><strong>{{ articles.length + generatedResources.length + documents.length }}</strong><span>资源总数</span></div></div>
    </header>

    <AppLoadingState v-if="loading" />
    <AppErrorState v-else-if="error" title="教学资源加载失败" :description="error" action-label="重新加载" @retry="loadLibrary" />

    <template v-else>
      <section class="library-toolbar">
        <label><Search :size="17" /><input v-model="keyword" placeholder="搜索课程、章节、知识点或资源" /></label>
        <div><Filter :size="16" /><select v-model="courseFilter"><option value="ALL">全部课程</option><option v-for="course in courses" :key="String(course.courseId)" :value="String(course.courseId)">{{ course.courseName }}</option></select></div>
      </section>

      <nav class="kind-tabs" aria-label="资源类型筛选">
        <button type="button" :class="{ active: kindFilter === 'ALL' }" @click="kindFilter = 'ALL'">全部资源</button>
        <button type="button" :class="{ active: kindFilter === 'ARTICLE' }" @click="kindFilter = 'ARTICLE'">课程文章</button>
        <button type="button" :class="{ active: kindFilter === 'GENERATED' }" @click="kindFilter = 'GENERATED'">个性化资源</button>
        <button v-if="isTeacher" type="button" :class="{ active: kindFilter === 'DOCUMENT' }" @click="kindFilter = 'DOCUMENT'">课程资料</button>
      </nav>

      <section v-if="kindFilter === 'ALL' || kindFilter === 'ARTICLE'" class="resource-section">
        <header><div><span>课程内容</span><h2>章节文章</h2></div><small>{{ visibleArticles.length }} 篇</small></header>
        <div v-if="visibleArticles.length" class="resource-grid">
          <button v-for="item in visibleArticles" :key="item.key" type="button" class="resource-card" @click="openArticle(item)">
            <span class="resource-icon"><BookOpenText :size="21" /></span><span class="resource-copy"><small>{{ item.courseName }} · 第 {{ item.chapterNo }} 章</small><strong>{{ item.title }}</strong><p>{{ item.summary || '进入课程查看章节正文与附件。' }}</p></span><span class="resource-foot"><em :class="item.status.toLowerCase()">{{ item.status === 'PUBLISHED' ? '已发布' : '草稿' }}</em><span>打开课程<ChevronRight :size="16" /></span></span>
          </button>
        </div>
        <AppEmptyState v-else title="暂无课程文章" description="当前课程或搜索条件下没有可展示的章节文章。" />
      </section>

      <section v-if="kindFilter === 'ALL' || kindFilter === 'GENERATED'" class="resource-section">
        <header><div><span>个性化学习</span><h2>智能生成资源</h2></div><small>{{ visibleGenerated.length }} 份</small></header>
        <div v-if="visibleGenerated.length" class="resource-grid">
          <button v-for="item in visibleGenerated" :key="String(item.resourceId)" type="button" class="resource-card" @click="selectedResource = item">
            <span class="resource-icon generated"><Sparkles :size="21" /></span><span class="resource-copy"><small>{{ item.courseName }} · {{ item.chapterName }}</small><strong>{{ item.title }}</strong><p>{{ item.resourceTypeName }} · {{ item.knowledgePointName }} · {{ item.summary }}</p></span><span class="resource-foot"><em :class="item.validationStatus.toLowerCase()"><ShieldCheck :size="13" />{{ item.validationStatusName }}</em><span>查看资源<ChevronRight :size="16" /></span></span>
          </button>
        </div>
        <AppEmptyState v-else title="暂无个性化资源" description="完成资源生成并经教师发布后，资源会出现在这里。" />
      </section>

      <section v-if="isTeacher && (kindFilter === 'ALL' || kindFilter === 'DOCUMENT')" class="resource-section">
        <header><div><span>课程知识库</span><h2>入库资料</h2></div><small>{{ visibleDocuments.length }} 份</small></header>
        <div v-if="visibleDocuments.length" class="document-list">
          <article v-for="item in visibleDocuments" :key="String(item.documentId)"><span class="resource-icon"><Database :size="20" /></span><div><small>{{ item.courseName }}<template v-if="item.chapterName"> · {{ item.chapterName }}</template></small><strong>{{ item.title }}</strong><p>{{ item.parserTypeName }} · {{ item.chunkCount }} 个切片<template v-if="item.knowledgePoints.length"> · {{ item.knowledgePoints.map((point) => point.pointName).join('、') }}</template></p></div><em :class="item.ingestStatus.toLowerCase()">{{ item.ingestStatusName }}</em><a class="document-link" :href="documentResourceUrl(item.attachmentRefId)" target="_blank" rel="noopener noreferrer"><FileText :size="15" />查看原文件</a></article>
        </div>
        <AppEmptyState v-else title="暂无课程资料" description="在课程资料库上传文件后，可在这里按课程查看入库状态。" />
      </section>

    </template>

    <AppDialog v-if="selectedResource" wide><div class="resource-dialog"><GeneratedResourceContent :resource="selectedResource" /><footer><AppButton variant="primary" @click="selectedResource = null">关闭</AppButton></footer></div></AppDialog>
  </main>
</template>

<style scoped>
.education-library { height: 100%; overflow: auto; padding: var(--layout-content-pad); background-color: var(--surface-canvas); background-image: var(--canvas-backdrop); color: var(--text-primary); }.library-hero { position: relative; display: grid; grid-template-columns: minmax(0, 1fr) minmax(20rem, 31rem); min-height: 17rem; gap: var(--space-20); overflow: hidden; margin-bottom: var(--space-18); padding: clamp(var(--space-24), 4vw, var(--space-40)); border: 1px solid var(--border-accent); border-radius: var(--radius-20); background: var(--hero-gradient); box-shadow: var(--shadow-md); }.library-hero::after { position: absolute; right: -5rem; bottom: -8rem; width: 23rem; height: 23rem; border: 1px solid color-mix(in srgb, var(--primary-300) 36%, transparent); border-radius: 50%; content: ''; }.library-hero-copy { position: relative; z-index: 2; display: grid; align-content: center; justify-items: start; max-width: 46rem; }.library-hero-copy > span, .resource-section > header > div > span { display: inline-flex; align-items: center; gap: var(--space-6); color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; letter-spacing: .08em; }.library-hero h1 { margin: var(--space-8) 0; font-family: var(--font-family-display); font-size: clamp(var(--text-title), 3vw, var(--text-headline)); line-height: 1.2; }.library-hero p { margin: 0; color: var(--text-secondary); line-height: 1.7; }.library-hero-visual { position: relative; z-index: 1; min-width: 0; min-height: 14rem; }.library-hero-visual > img { position: absolute; top: -3rem; right: -4rem; width: 34rem; max-width: none; filter: drop-shadow(0 1.1rem 1.5rem color-mix(in srgb, var(--primary-800) 18%, transparent)); animation: resource-hero-float 6.2s ease-in-out infinite; }.hero-count { position: absolute; z-index: 2; bottom: var(--space-8); left: 0; display: grid; min-width: 7.5rem; justify-items: start; padding: var(--space-12) var(--space-14); border: 1px solid color-mix(in srgb, var(--primary-300) 45%, transparent); border-radius: var(--radius-12); background: var(--surface-glass); box-shadow: var(--shadow-md); backdrop-filter: blur(.75rem); }.hero-count strong { color: var(--text-accent); font-family: var(--font-family-display); font-size: 2rem; line-height: 1; }.hero-count span { color: var(--text-tertiary); font-size: var(--text-caption); }
.library-toolbar { display: flex; align-items: center; gap: var(--space-10); margin-bottom: var(--space-10); padding: var(--space-8); border: 1px solid var(--border-default); border-radius: var(--radius-12); background: var(--surface-glass); box-shadow: var(--shadow-sm); backdrop-filter: blur(.5rem); }.library-toolbar > label, .library-toolbar > div { display: flex; align-items: center; gap: var(--space-8); min-height: 2.75rem; padding: 0 var(--space-12); border: 1px solid var(--border-default); border-radius: var(--radius-10); background: var(--surface-panel); color: var(--text-tertiary); }.library-toolbar > label { flex: 1; }.library-toolbar input { width: 100%; border: 0; outline: 0; background: transparent; color: var(--text-primary); }.library-toolbar select { border: 0; outline: 0; background: transparent; color: var(--text-primary); }.kind-tabs { display: flex; flex-wrap: wrap; gap: var(--space-6); margin-bottom: var(--space-18); padding: var(--space-6); border: 1px solid var(--border-subtle); border-radius: var(--radius-12); background: var(--surface-muted); }.kind-tabs button { min-height: 2.5rem; padding: var(--space-8) var(--space-12); border: 1px solid transparent; border-radius: var(--radius-10); background: transparent; color: var(--text-secondary); }.kind-tabs button.active { border-color: var(--border-accent); background: var(--surface-panel); color: var(--text-accent); font-weight: 800; box-shadow: var(--shadow-sm); }
.resource-section { position: relative; overflow: hidden; margin-bottom: var(--space-18); padding: var(--space-20); border: 1px solid var(--border-default); border-radius: var(--radius-16); background: linear-gradient(155deg, var(--surface-panel) 78%, color-mix(in srgb, var(--surface-accent) 52%, var(--surface-panel))); box-shadow: var(--shadow-sm); }.resource-section > header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-12); margin-bottom: var(--space-14); }.resource-section h2 { margin: var(--space-4) 0; font-family: var(--font-family-display); }.resource-section > header > small { padding: var(--space-5) var(--space-9); border-radius: 999px; background: var(--surface-muted); color: var(--text-tertiary); }.resource-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(18rem, 1fr)); gap: var(--space-12); }.resource-card { position: relative; display: grid; grid-template-columns: auto minmax(0, 1fr); gap: var(--space-11); overflow: hidden; padding: var(--space-16); border: 1px solid var(--border-default); border-radius: var(--radius-12); background: var(--surface-panel); color: var(--text-primary); text-align: left; box-shadow: var(--shadow-sm); transition: border-color var(--transition-base), transform var(--transition-base), box-shadow var(--transition-base); }.resource-card::before { position: absolute; top: 0; right: 0; left: 0; height: .2rem; background: linear-gradient(90deg, var(--primary-500), var(--info-400)); content: ''; opacity: .7; }.resource-card:hover { border-color: var(--border-accent); transform: translateY(-.15rem); box-shadow: var(--shadow-md); }.resource-icon { display: grid; width: 2.75rem; height: 2.75rem; place-items: center; border-radius: var(--radius-10); background: var(--surface-accent); color: var(--text-accent); }.resource-icon.generated { background: var(--success-surface); color: var(--success-600); }.resource-copy { display: grid; gap: var(--space-4); min-width: 0; }.resource-copy small, .document-list small { color: var(--text-accent); font-weight: 700; }.resource-copy strong { font-size: var(--text-body-lg); line-height: 1.4; }.resource-copy p { display: -webkit-box; margin: 0; overflow: hidden; color: var(--text-secondary); line-height: 1.6; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.resource-foot { grid-column: 1 / -1; display: flex; align-items: center; justify-content: space-between; gap: var(--space-8); padding-top: var(--space-10); border-top: 1px solid var(--border-subtle); }.resource-foot em { display: inline-flex; align-items: center; gap: var(--space-4); color: var(--text-tertiary); font-size: var(--text-caption); font-style: normal; }.resource-foot em.published, .resource-foot em.passed { color: var(--success-600); }.resource-foot em.draft, .resource-foot em.needs_review { color: var(--warning-700); }.resource-foot > span { display: inline-flex; align-items: center; gap: var(--space-4); color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; }
.document-list { display: grid; }.document-list article { display: grid; grid-template-columns: auto minmax(0, 1fr) auto auto; align-items: center; gap: var(--space-12); padding: var(--space-13) 0; border-top: 1px solid var(--border-subtle); }.document-list article > div { display: grid; gap: var(--space-3); min-width: 0; }.document-list p { overflow: hidden; margin: 0; color: var(--text-tertiary); text-overflow: ellipsis; white-space: nowrap; }.document-list em { padding: var(--space-5) var(--space-8); border-radius: var(--radius-7); background: var(--surface-muted); color: var(--text-secondary); font-size: var(--text-caption); font-style: normal; font-weight: 800; }.document-list em.success { background: var(--success-surface); color: var(--success-600); }.document-list em.failed { background: var(--danger-surface); color: var(--danger-600); }.document-list em.running, .document-list em.pending { background: var(--surface-accent); color: var(--text-accent); }.resource-dialog { display: grid; gap: var(--space-18); min-width: 0; max-width: 100%; }.resource-dialog footer { display: flex; justify-content: flex-end; padding-top: var(--space-14); border-top: 1px solid var(--border-subtle); }
.document-link { display: inline-flex; align-items: center; justify-content: center; gap: var(--space-8); min-height: 2.5rem; padding: var(--space-8) var(--space-12); border: 1px solid var(--border-default); border-radius: var(--radius-10); background: var(--surface-panel); color: var(--text-primary); font-size: var(--text-caption-md); font-weight: 600; line-height: 1; text-decoration: none; transition: border-color var(--transition-base), background-color var(--transition-base), color var(--transition-base), box-shadow var(--transition-base); }.document-link:hover, .document-link:focus-visible { border-color: var(--border-accent); background: var(--surface-accent); color: var(--text-accent); box-shadow: var(--shadow-sm); }.document-link:focus-visible { outline: none; box-shadow: 0 0 0 2px var(--surface-panel), 0 0 0 4px color-mix(in srgb, var(--primary-500) 34%, transparent); }
@keyframes resource-hero-float { 0%, 100% { transform: translate3d(0, 0, 0) rotate(.45deg); } 50% { transform: translate3d(0, -.5rem, 0) rotate(-.35deg); } }
@media (max-width: 900px) { .library-hero { grid-template-columns: 1fr; }.library-hero-visual { min-height: 16rem; }.library-hero-visual > img { top: -2rem; right: -3rem; width: 31rem; } }
@media (max-width: 760px) { .education-library { padding: var(--space-12); padding-bottom: 7rem; }.library-hero { min-height: 0; padding: var(--space-20); }.library-hero-visual { min-height: 13rem; }.library-hero-visual > img { top: -1rem; right: -4rem; width: 26rem; }.library-toolbar, .resource-section > header { align-items: stretch; flex-direction: column; }.library-toolbar { padding: var(--space-6); }.hero-count { bottom: 0; left: 0; min-width: 6rem; }.resource-section { padding: var(--space-16); }.resource-grid { grid-template-columns: 1fr; }.document-list article { grid-template-columns: auto minmax(0, 1fr); }.document-list article > em { justify-self: start; }.document-list article > :last-child { grid-column: 1 / -1; } }
@media (prefers-reduced-motion: reduce) { .library-hero-visual > img { animation: none; } }
</style>
