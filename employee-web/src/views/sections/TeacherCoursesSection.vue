<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowLeft, BarChart3, BookMarked, BookOpenText, Bot, Check, ChevronDown, ChevronUp, Copy, Database, FileUp, FolderKanban, Grid2X2, ImagePlus, Link2, List, MessageCircleMore, PencilLine, Plus, RefreshCw, Save, Search, Settings2, Sparkles, Trash2, Upload, UsersRound } from 'lucide-vue-next';
import { employeeApi, type CourseChapterResponse, type CourseKnowledgeDocumentResponse, type CourseOutlineSaveRequest, type CourseResponse, type CourseSaveRequest, type EntityId } from '@/api';
import AppButton from '@/components/ui/AppButton.vue';
import AppDialog from '@/components/ui/AppDialog.vue';
import AppErrorState from '@/components/ui/AppErrorState.vue';
import AppLoadingState from '@/components/ui/AppLoadingState.vue';
import CourseLearningAnalyticsPanel from '@/components/course/CourseLearningAnalyticsPanel.vue';
import CourseCommunicationPanel from '@/components/course/CourseCommunicationPanel.vue';
import TeacherLearningResourcePanel from '@/components/course/TeacherLearningResourcePanel.vue';
import { usePersonalFileTransfer } from '@/composables/usePersonalFileTransfer';
import courseDashboardHero from '@/assets/course-dashboard-hero-3d.webp';

type ChapterDraft = CourseOutlineSaveRequest['chapters'][number];

const { uploadCourseResource } = usePersonalFileTransfer();
const route = useRoute();
const router = useRouter();
const courses = ref<CourseResponse[]>([]);
const selected = ref<CourseResponse | null>(null);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const keyword = ref('');
const viewMode = ref<'grid' | 'list'>('grid');
const editorPanel = ref<'basic' | 'chapters' | 'knowledge' | 'analytics' | 'communication' | 'resources'>('basic');
const activeChapterIndex = ref(0);
const createDialogOpen = ref(false);
const deleteDialogOpen = ref(false);
const inviteDialogOpen = ref(false);
const invitationLink = ref('');
const invitationExpiresAt = ref('');
const notice = ref('');
const pendingCoverFile = ref<File | null>(null);
const coverInput = ref<HTMLInputElement | null>(null);
const knowledgeInput = ref<HTMLInputElement | null>(null);
const knowledgeDocuments = ref<CourseKnowledgeDocumentResponse[]>([]);
const knowledgeUploading = ref(false);
const knowledgeChapterId = ref<EntityId | null>(null);
const knowledgePointIds = ref<EntityId[]>([]);
let knowledgePollTimer: number | undefined;

const courseForm = reactive<CourseSaveRequest>({ courseCode: '', courseName: '', summary: '', learningObjectives: '', prerequisites: '', teacherName: '', coverAttachmentRefId: null, totalHours: 0, status: 'DRAFT' });
const outlineForm = reactive<CourseOutlineSaveRequest>({ chapters: [], dependencies: [] });

const filteredCourses = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  if (!query) return courses.value;
  return courses.value.filter((course) => `${course.courseName} ${course.courseCode} ${course.teacherName ?? ''}`.toLowerCase().includes(query));
});
const activeChapter = computed(() => outlineForm.chapters[activeChapterIndex.value] ?? null);
const isOwner = computed(() => selected.value?.teacherRole === 'OWNER');
const publishedCourseCount = computed(() => courses.value.filter((course) => course.status === 'PUBLISHED').length);
const managedChapterCount = computed(() => courses.value.reduce((total, course) => total + course.chapterCount, 0));
const coverPreview = computed(() => pendingCoverFile.value
  ? URL.createObjectURL(pendingCoverFile.value)
  : resourceUrl(courseForm.coverAttachmentRefId));

async function loadCourses() {
  loading.value = true;
  error.value = '';
  try {
    courses.value = await employeeApi.teacherCourses.list();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '课程加载失败';
  } finally {
    loading.value = false;
  }
}

async function openCourse(courseId: EntityId) {
  loading.value = true;
  error.value = '';
  try {
    selected.value = await employeeApi.teacherCourses.detail(courseId);
    fillEditor(selected.value);
    await loadKnowledgeDocuments();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '课程加载失败';
  } finally {
    loading.value = false;
  }
}

async function loadKnowledgeDocuments() {
  if (!selected.value) return;
  try {
    knowledgeDocuments.value = await employeeApi.teacherCourses.knowledgeDocuments(selected.value.courseId);
    scheduleKnowledgePoll();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '课程资料状态加载失败';
  }
}

function scheduleKnowledgePoll() {
  window.clearTimeout(knowledgePollTimer);
  if (!knowledgeDocuments.value.some((document) => document.ingestStatus === 'PENDING' || document.ingestStatus === 'RUNNING')) return;
  knowledgePollTimer = window.setTimeout(loadKnowledgeDocuments, 2000);
}

async function uploadKnowledgeDocument(file: File) {
  if (!selected.value) return;
  knowledgeUploading.value = true;
  error.value = '';
  try {
    const uploaded = await uploadCourseResource(file, selected.value.courseId);
    const document = await employeeApi.teacherCourses.addKnowledgeDocument(selected.value.courseId, {
      attachmentRefId: uploaded.attachmentRefId,
      chapterId: knowledgeChapterId.value,
      knowledgePointIds: knowledgePointIds.value,
    });
    knowledgeDocuments.value = [document, ...knowledgeDocuments.value.filter((item) => String(item.documentId) !== String(document.documentId))];
    notice.value = '资料已进入后台解析与向量化队列';
    scheduleKnowledgePoll();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '课程资料入库失败';
  } finally {
    knowledgeUploading.value = false;
    if (knowledgeInput.value) knowledgeInput.value.value = '';
  }
}

async function retryKnowledgeDocument(document: CourseKnowledgeDocumentResponse) {
  if (!selected.value) return;
  try {
    const updated = await employeeApi.teacherCourses.retryKnowledgeDocument(selected.value.courseId, document.documentId);
    knowledgeDocuments.value = knowledgeDocuments.value.map((item) => String(item.documentId) === String(updated.documentId) ? updated : item);
    scheduleKnowledgePoll();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '课程资料重试失败';
  }
}

function toggleKnowledgePoint(pointId: EntityId) {
  const index = knowledgePointIds.value.findIndex((id) => String(id) === String(pointId));
  if (index >= 0) knowledgePointIds.value.splice(index, 1);
  else knowledgePointIds.value.push(pointId);
}

function fillEditor(course: CourseResponse) {
  Object.assign(courseForm, {
    courseCode: course.courseCode,
    courseName: course.courseName,
    summary: course.summary ?? '',
    learningObjectives: course.learningObjectives ?? '',
    prerequisites: course.prerequisites ?? '',
    teacherName: course.teacherName ?? '',
    coverAttachmentRefId: course.coverAttachmentRefId ?? null,
    totalHours: course.totalHours,
    status: course.status,
  });
  outlineForm.chapters = course.chapters.map((chapter) => chapterDraft(chapter));
  outlineForm.dependencies = course.dependencies.map((item) => ({ ...item }));
  activeChapterIndex.value = 0;
  editorPanel.value = 'basic';
  pendingCoverFile.value = null;
}

function chapterDraft(chapter?: CourseChapterResponse): ChapterDraft {
  return {
    chapterId: chapter?.chapterId,
    chapterName: chapter?.chapterName ?? '',
    summary: chapter?.summary ?? '',
    knowledgePoints: chapter?.knowledgePoints.map((point) => ({ pointCode: point.pointCode, pointName: point.pointName, summary: point.summary ?? '' })) ?? [],
  };
}

function openCreate() {
  Object.assign(courseForm, { courseCode: '', courseName: '', summary: '', learningObjectives: '', prerequisites: '', teacherName: '', coverAttachmentRefId: null, totalHours: 0, status: 'DRAFT' });
  pendingCoverFile.value = null;
  createDialogOpen.value = true;
}

async function createCourse() {
  saving.value = true;
  error.value = '';
  try {
    let course = await employeeApi.teacherCourses.create({ ...courseForm, coverAttachmentRefId: null });
    if (pendingCoverFile.value) {
      const uploaded = await uploadCourseResource(pendingCoverFile.value, course.courseId);
      course = await employeeApi.teacherCourses.update(course.courseId, { ...courseForm, coverAttachmentRefId: uploaded.attachmentRefId });
    }
    createDialogOpen.value = false;
    await loadCourses();
    await openCourse(course.courseId);
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '课程创建失败';
  } finally {
    saving.value = false;
  }
}

async function saveBasic() {
  if (!selected.value) return;
  saving.value = true;
  error.value = '';
  try {
    if (pendingCoverFile.value) {
      const uploaded = await uploadCourseResource(pendingCoverFile.value, selected.value.courseId);
      courseForm.coverAttachmentRefId = uploaded.attachmentRefId;
    }
    selected.value = await employeeApi.teacherCourses.update(selected.value.courseId, courseForm);
    fillEditor(selected.value);
    await loadCourses();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '课程保存失败';
  } finally {
    saving.value = false;
  }
}

async function saveChapters() {
  if (!selected.value || !outlineForm.chapters.length) return;
  saving.value = true;
  error.value = '';
  try {
    selected.value = await employeeApi.teacherCourses.saveOutline(selected.value.courseId, outlineForm);
    fillEditor(selected.value);
    editorPanel.value = 'chapters';
    await loadCourses();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '章节保存失败';
  } finally {
    saving.value = false;
  }
}

function addChapter() {
  outlineForm.chapters.push(chapterDraft());
  activeChapterIndex.value = outlineForm.chapters.length - 1;
}

function removeChapter(index: number) {
  outlineForm.chapters.splice(index, 1);
  activeChapterIndex.value = Math.max(0, Math.min(activeChapterIndex.value, outlineForm.chapters.length - 1));
}

function moveChapter(index: number, offset: number) {
  const target = index + offset;
  if (target < 0 || target >= outlineForm.chapters.length) return;
  const [chapter] = outlineForm.chapters.splice(index, 1);
  outlineForm.chapters.splice(target, 0, chapter);
  activeChapterIndex.value = target;
}

function articleStatus(chapterId?: EntityId) {
  return selected.value?.chapters.find((chapter) => String(chapter.chapterId) === String(chapterId))?.status ?? 'DRAFT';
}

function writeArticle(chapterId?: EntityId) {
  if (!selected.value || chapterId == null) return;
  router.push({ name: 'teacher-course-article', params: { courseId: selected.value.courseId, chapterId } });
}

async function deleteCourse() {
  if (!selected.value) return;
  saving.value = true;
  try {
    await employeeApi.teacherCourses.remove(selected.value.courseId);
    selected.value = null;
    deleteDialogOpen.value = false;
    await loadCourses();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '课程删除失败';
  } finally {
    saving.value = false;
  }
}

async function createInvitation() {
  if (!selected.value || !isOwner.value) return;
  saving.value = true;
  error.value = '';
  try {
    const invitation = await employeeApi.teacherCourses.createInvitation(selected.value.courseId);
    invitationLink.value = `${window.location.origin}${window.location.pathname}#/courses?courseInvite=${encodeURIComponent(invitation.token)}`;
    invitationExpiresAt.value = invitation.expiresAt;
    inviteDialogOpen.value = true;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '邀请链接生成失败';
  } finally {
    saving.value = false;
  }
}

async function copyInvitation() {
  await navigator.clipboard.writeText(invitationLink.value);
  notice.value = '邀请链接已复制';
}

async function acceptInvitation() {
  const token = typeof route.query.courseInvite === 'string' ? route.query.courseInvite : '';
  if (!token) return;
  loading.value = true;
  error.value = '';
  try {
    const course = await employeeApi.teacherCourses.acceptInvitation(token);
    await router.replace({ name: 'employee-courses' });
    await loadCourses();
    selected.value = course;
    fillEditor(course);
    notice.value = `已加入“${course.courseName}”`;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '课程邀请接受失败';
  } finally {
    loading.value = false;
  }
}

function resourceUrl(attachmentRefId?: EntityId | null) {
  return attachmentRefId == null ? '' : employeeApi.courses.resourceUrl(attachmentRefId);
}

onMounted(async () => {
  await loadCourses();
  await acceptInvitation();
  if (typeof route.query.courseId === 'string') await openCourse(route.query.courseId);
});
onBeforeUnmount(() => window.clearTimeout(knowledgePollTimer));
</script>

<template>
  <div class="teacher-page">
    <input ref="coverInput" class="visually-hidden-input" type="file" accept="image/png,image/jpeg,image/webp" @change="pendingCoverFile = ($event.target as HTMLInputElement).files?.[0] ?? null" />
    <input ref="knowledgeInput" class="visually-hidden-input" type="file" accept=".txt,.md,.pdf,.doc,.docx,.ppt,.pptx,.csv,.png,.jpg,.jpeg,.webp" @change="($event.target as HTMLInputElement).files?.[0] && uploadKnowledgeDocument(($event.target as HTMLInputElement).files![0])" />

    <AppErrorState v-if="error" class="teacher-error" title="操作失败" :description="error" action-label="重试" @retry="selected ? openCourse(selected.courseId) : loadCourses()" />
    <p v-if="notice" class="teacher-notice"><Check :size="16" />{{ notice }}</p>
    <AppLoadingState v-if="loading && !courses.length" />

    <template v-else-if="!selected">
      <header class="library-head">
        <div class="library-head-copy">
          <span class="library-kicker"><Sparkles :size="16" />教师课程工作台</span>
          <h1>把一门课，经营成完整的学习旅程</h1>
          <p>从课程框架、章节文章到班级运营与智能资源，在一个工作台持续维护。</p>
          <div class="library-stats" aria-label="课程管理概览">
            <span><strong>{{ courses.length }}</strong><small>管理课程</small></span>
            <span><strong>{{ publishedCourseCount }}</strong><small>已发布</small></span>
            <span><strong>{{ managedChapterCount }}</strong><small>课程章节</small></span>
          </div>
          <AppButton variant="primary" size="lg" @click="openCreate"><Plus :size="18" />新建课程</AppButton>
        </div>
        <div class="library-head-visual" aria-hidden="true">
          <span class="visual-orbit"></span>
          <img :src="courseDashboardHero" alt="" />
          <span class="visual-caption"><FolderKanban :size="18" /><span><strong>{{ publishedCourseCount }}/{{ courses.length }}</strong><small>课程已发布</small></span></span>
        </div>
      </header>
      <div class="library-toolbar">
        <label><Search :size="17" /><input v-model="keyword" placeholder="搜索课程名称、编码或教师" /></label>
        <div><button type="button" :class="{ active: viewMode === 'grid' }" aria-label="网格视图" @click="viewMode = 'grid'"><Grid2X2 :size="17" /></button><button type="button" :class="{ active: viewMode === 'list' }" aria-label="列表视图" @click="viewMode = 'list'"><List :size="18" /></button></div>
      </div>
      <section class="teacher-library" :class="`mode-${viewMode}`">
        <button v-for="course in filteredCourses" :key="String(course.courseId)" type="button" class="teacher-card" @click="openCourse(course.courseId)">
          <span class="card-cover"><img v-if="course.coverAttachmentRefId" :src="resourceUrl(course.coverAttachmentRefId)" :alt="course.courseName" /><span v-else><BookOpenText :size="34" />{{ course.courseCode }}</span><em :class="{ published: course.status === 'PUBLISHED' }">{{ course.statusName }}</em></span>
          <span class="card-body"><small>{{ course.courseCode }} · {{ course.teacherRole === 'OWNER' ? '我创建的' : '协作课程' }}</small><strong>{{ course.courseName }}</strong><p>{{ course.summary || '暂无课程简介' }}</p><span>{{ course.totalHours }} 学时 · {{ course.chapterCount }} 章 · {{ course.knowledgePointCount }} 个知识点</span></span>
        </button>
        <p v-if="!filteredCourses.length" class="empty-library">暂无课程</p>
      </section>
    </template>

    <template v-else>
      <header class="workspace-head">
        <button type="button" class="workspace-back" @click="selected = null"><ArrowLeft :size="18" />全部课程</button>
        <div class="workspace-course-cover">
          <img v-if="selected.coverAttachmentRefId" :src="resourceUrl(selected.coverAttachmentRefId)" :alt="selected.courseName" />
          <BookOpenText v-else :size="28" />
        </div>
        <div class="workspace-identity">
          <span>{{ selected.courseCode }} · {{ selected.teacherRole === 'OWNER' ? '我创建的课程' : '协作课程' }}</span>
          <h1>{{ selected.courseName }}</h1>
          <p>{{ selected.summary || '完善课程简介，让学习者更快理解课程价值。' }}</p>
          <div class="workspace-facts"><span>{{ selected.totalHours }} 学时</span><span>{{ selected.chapterCount }} 章</span><span>{{ selected.knowledgePointCount }} 个知识点</span></div>
        </div>
        <div class="workspace-actions"><AppButton v-if="isOwner" @click="createInvitation"><Link2 :size="16" />邀请教师</AppButton><em :class="{ published: selected.status === 'PUBLISHED' }">{{ selected.statusName }}</em></div>
      </header>
      <nav class="workspace-tabs" aria-label="课程工作区">
        <button type="button" aria-label="基础信息" :class="{ active: editorPanel === 'basic' }" @click="editorPanel = 'basic'"><Settings2 :size="19" /><span><strong>基础信息</strong><small>封面与发布</small></span></button>
        <button type="button" aria-label="章节内容" :class="{ active: editorPanel === 'chapters' }" @click="editorPanel = 'chapters'"><BookMarked :size="19" /><span><strong>章节内容</strong><small>文章与知识点</small></span></button>
        <button type="button" aria-label="课程资料库" :class="{ active: editorPanel === 'knowledge' }" @click="editorPanel = 'knowledge'"><Database :size="19" /><span><strong>课程资料库</strong><small>解析与检索</small></span></button>
        <button type="button" aria-label="学情分析" :class="{ active: editorPanel === 'analytics' }" @click="editorPanel = 'analytics'"><BarChart3 :size="19" /><span><strong>学情分析</strong><small>进度与薄弱点</small></span></button>
        <button type="button" aria-label="班级沟通" :class="{ active: editorPanel === 'communication' }" @click="editorPanel = 'communication'"><MessageCircleMore :size="19" /><span><strong>班级沟通</strong><small>群聊与私聊</small></span></button>
        <button type="button" aria-label="生成资源" :class="{ active: editorPanel === 'resources' }" @click="editorPanel = 'resources'"><Bot :size="19" /><span><strong>生成资源</strong><small>智能体协作</small></span></button>
      </nav>

      <form v-if="editorPanel === 'basic'" class="basic-editor" @submit.prevent="saveBasic">
        <section class="cover-editor">
          <div><img v-if="coverPreview" :src="coverPreview" alt="课程封面预览" /><span v-else><ImagePlus :size="38" />课程封面</span></div>
          <AppButton type="button" @click="coverInput?.click()"><Upload :size="16" />选择封面</AppButton><small>建议 16:9，支持 JPG、PNG、WebP</small>
        </section>
        <section class="basic-fields">
          <label><span>课程编码</span><input v-model="courseForm.courseCode" maxlength="64" required /></label><label><span>课程名称</span><input v-model="courseForm.courseName" maxlength="128" required /></label>
          <label><span>授课教师</span><input v-model="courseForm.teacherName" maxlength="64" /></label><label><span>总学时</span><input v-model.number="courseForm.totalHours" type="number" min="0" max="10000" /></label>
          <label class="wide"><span>课程简介</span><textarea v-model="courseForm.summary" maxlength="1000" /></label><label class="wide"><span>学习目标</span><textarea v-model="courseForm.learningObjectives" /></label><label class="wide"><span>先修要求</span><textarea v-model="courseForm.prerequisites" maxlength="1000" /></label>
          <label><span>课程状态</span><select v-model="courseForm.status"><option value="DRAFT">草稿</option><option value="PUBLISHED">发布</option></select></label>
        </section>
        <section class="teacher-members"><header><UsersRound :size="18" /><strong>管理教师</strong></header><div><span v-for="teacher in selected.teachers" :key="String(teacher.userId)">{{ teacher.teacherName }}<small>{{ teacher.memberRole === 'OWNER' ? '所有者' : '协作者' }}</small></span></div></section>
        <footer><AppButton v-if="isOwner" type="button" variant="destructive" @click="deleteDialogOpen = true"><Trash2 :size="16" />删除课程</AppButton><span v-else>协作者可编辑课程与章节</span><AppButton type="submit" variant="primary" :disabled="saving"><Save :size="16" />保存基础信息</AppButton></footer>
      </form>

      <div v-else-if="editorPanel === 'chapters'" class="chapter-workspace">
        <aside class="chapter-list"><header><span>章节</span><AppButton size="sm" @click="addChapter"><Plus :size="15" />添加</AppButton></header><button v-for="(chapter, index) in outlineForm.chapters" :key="String(chapter.chapterId ?? index)" type="button" :class="{ active: index === activeChapterIndex }" @click="activeChapterIndex = index"><span>{{ String(index + 1).padStart(2, '0') }}</span><span><strong>{{ chapter.chapterName || '未命名章节' }}</strong><small>{{ articleStatus(chapter.chapterId) === 'PUBLISHED' ? '文章已发布' : '文章草稿' }}</small></span></button></aside>
        <main v-if="activeChapter" class="chapter-editor">
          <header><div><span>第 {{ activeChapterIndex + 1 }} 章</span><h2>{{ activeChapter.chapterName || '新章节' }}</h2></div><div><AppButton size="sm" :disabled="activeChapterIndex === 0" @click="moveChapter(activeChapterIndex, -1)"><ChevronUp :size="15" /></AppButton><AppButton size="sm" :disabled="activeChapterIndex === outlineForm.chapters.length - 1" @click="moveChapter(activeChapterIndex, 1)"><ChevronDown :size="15" /></AppButton><AppButton variant="destructive" size="sm" @click="removeChapter(activeChapterIndex)"><Trash2 :size="15" /></AppButton></div></header>
          <div class="chapter-fields"><label><span>章节名称</span><input v-model="activeChapter.chapterName" maxlength="128" /></label><label class="wide"><span>章节简介</span><textarea v-model="activeChapter.summary" maxlength="1000" /></label></div>
          <section class="article-entry"><div><PencilLine :size="24" /><span><strong>章节文章</strong><small>正文中可混排图文、教学视频和课程附件</small></span></div><AppButton variant="primary" :disabled="!activeChapter.chapterId" @click="writeArticle(activeChapter.chapterId)">{{ activeChapter.chapterId ? '写文章' : '保存章节后写文章' }}</AppButton></section>
          <section class="knowledge-editor"><header><span>关联知识点</span><AppButton size="sm" @click="activeChapter.knowledgePoints.push({ pointCode: '', pointName: '', summary: '' })"><Plus :size="14" />添加知识点</AppButton></header><div v-for="(point, pointIndex) in activeChapter.knowledgePoints" :key="pointIndex"><input v-model="point.pointCode" placeholder="编码" /><input v-model="point.pointName" placeholder="知识点名称" /><input v-model="point.summary" placeholder="简介" /><AppButton variant="destructive" size="sm" @click="activeChapter.knowledgePoints.splice(pointIndex, 1)"><Trash2 :size="14" /></AppButton></div></section>
          <footer><AppButton variant="primary" :disabled="saving || !outlineForm.chapters.length" @click="saveChapters"><Save :size="16" />保存全部章节</AppButton></footer>
        </main>
        <section v-else class="chapter-empty"><BookOpenText :size="36" /><p>添加章节后开始编辑内容</p><AppButton @click="addChapter"><Plus :size="16" />添加第一章</AppButton></section>
      </div>
      <section v-else-if="editorPanel === 'knowledge'" class="knowledge-library">
        <header><div><span>课程知识库</span><h2>资料解析与向量化</h2><p>资料入库后可供学习导师检索，并保留章节、知识点和原始文件来源。</p></div><AppButton variant="primary" :disabled="knowledgeUploading" @click="knowledgeInput?.click()"><FileUp :size="16" />{{ knowledgeUploading ? '正在上传' : '上传资料' }}</AppButton></header>
        <div class="knowledge-relations">
          <label><span>关联章节</span><select v-model="knowledgeChapterId"><option :value="null">整门课程</option><option v-for="chapter in selected.chapters" :key="String(chapter.chapterId)" :value="chapter.chapterId">第 {{ chapter.chapterNo }} 章 · {{ chapter.chapterName }}</option></select></label>
          <fieldset><legend>关联知识点</legend><label v-for="point in selected.chapters.flatMap((chapter) => chapter.knowledgePoints)" :key="String(point.knowledgePointId)"><input type="checkbox" :checked="knowledgePointIds.some((id) => String(id) === String(point.knowledgePointId))" @change="toggleKnowledgePoint(point.knowledgePointId)" />{{ point.pointName }}</label><span v-if="!selected.knowledgePointCount">请先在章节内容中创建知识点</span></fieldset>
        </div>
        <div v-if="knowledgeDocuments.length" class="knowledge-document-list">
          <article v-for="document in knowledgeDocuments" :key="String(document.documentId)">
            <span class="document-icon"><Database :size="20" /></span><div><strong>{{ document.title }}</strong><p>{{ document.parserTypeName }}<template v-if="document.chapterName"> · {{ document.chapterName }}</template><template v-if="document.knowledgePoints.length"> · {{ document.knowledgePoints.map((point) => point.pointName).join('、') }}</template></p><div v-if="document.ingestStatus === 'PENDING' || document.ingestStatus === 'RUNNING'" class="document-progress"><span><small>{{ document.ingestStageName || '等待处理' }}</small><b>{{ document.ingestProgressPercent ?? 0 }}%</b></span><progress :value="document.ingestProgressPercent ?? 0" max="100" /></div><small v-if="document.errorMessage" class="document-error">{{ document.errorMessage }}</small></div><span class="document-status" :class="document.ingestStatus.toLowerCase()">{{ document.ingestStatusName }}<small v-if="document.ingestStatus === 'SUCCESS'">{{ document.chunkCount }} 个切片</small></span><AppButton v-if="document.ingestStatus === 'FAILED'" size="sm" @click="retryKnowledgeDocument(document)"><RefreshCw :size="15" />重试</AppButton>
          </article>
        </div>
        <div v-else class="knowledge-empty"><Database :size="34" /><strong>还没有入库资料</strong><p>先选择章节和知识点，再上传讲义、PPT、Word、PDF 或图片。</p></div>
      </section>
      <CourseLearningAnalyticsPanel v-else-if="editorPanel === 'analytics'" :course="selected" />
      <CourseCommunicationPanel v-else-if="editorPanel === 'communication'" :course="selected" />
      <TeacherLearningResourcePanel v-else :course="selected" />
    </template>

    <AppDialog v-if="createDialogOpen" wide><form class="create-dialog" @submit.prevent="createCourse"><header><h2>新建课程</h2><p>填写基础信息，创建后进入课程发布工作台。</p></header><section class="create-cover" @click="coverInput?.click()"><img v-if="coverPreview" :src="coverPreview" alt="课程封面预览" /><span v-else><ImagePlus :size="34" />选择课程封面</span></section><div class="basic-fields"><label><span>课程编码</span><input v-model="courseForm.courseCode" required maxlength="64" /></label><label><span>课程名称</span><input v-model="courseForm.courseName" required maxlength="128" /></label><label><span>授课教师</span><input v-model="courseForm.teacherName" maxlength="64" /></label><label><span>总学时</span><input v-model.number="courseForm.totalHours" type="number" min="0" /></label><label class="wide"><span>课程简介</span><textarea v-model="courseForm.summary" maxlength="1000" /></label><label><span>课程状态</span><select v-model="courseForm.status"><option value="DRAFT">保存草稿</option><option value="PUBLISHED">立即发布</option></select></label></div><footer><AppButton type="button" @click="createDialogOpen = false">取消</AppButton><AppButton type="submit" variant="primary" :disabled="saving">创建课程</AppButton></footer></form></AppDialog>
    <AppDialog v-if="deleteDialogOpen"><div class="delete-dialog"><h2>删除课程</h2><p>删除“{{ selected?.courseName }}”？课程章节将同时删除。</p><footer><AppButton @click="deleteDialogOpen = false">取消</AppButton><AppButton variant="destructive" :disabled="saving" @click="deleteCourse">删除</AppButton></footer></div></AppDialog>
    <AppDialog v-if="inviteDialogOpen"><div class="invite-dialog"><header><h2>邀请教师共同管理</h2><p>链接 7 天内有效且仅可接受一次。</p></header><label><span>邀请链接</span><textarea readonly :value="invitationLink" /></label><p>有效期至 {{ new Date(invitationExpiresAt).toLocaleString() }}</p><footer><AppButton @click="inviteDialogOpen = false">关闭</AppButton><AppButton variant="primary" @click="copyInvitation"><Copy :size="16" />复制链接</AppButton></footer></div></AppDialog>
  </div>
</template>

<style scoped>
.teacher-page { height: 100%; overflow: auto; padding: var(--layout-content-pad); background: var(--surface-canvas); color: var(--text-primary); }.teacher-error { margin-bottom: var(--space-16); }.teacher-notice { display: flex; align-items: center; gap: var(--space-8); padding: var(--space-10) var(--space-14); border-radius: var(--radius-10); background: var(--success-surface); color: var(--success-600); }.visually-hidden-input { position: fixed; width: 1px; height: 1px; opacity: 0; pointer-events: none; }
.library-head, .workspace-head, .basic-editor footer, .chapter-editor > header, .chapter-editor > footer, .knowledge-editor > header, .create-dialog footer, .delete-dialog footer { display: flex; align-items: center; justify-content: space-between; gap: var(--space-16); }.library-head { margin-bottom: var(--space-20); }.library-head > div > span, .workspace-head > div > span, .card-body small, .chapter-editor > header span { color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; letter-spacing: .1em; }.library-head h1, .workspace-head h1 { margin: var(--space-4) 0; }.library-head p { margin: 0; color: var(--text-secondary); }
.library-toolbar { display: flex; justify-content: space-between; margin-bottom: var(--space-16); }.library-toolbar > label { display: flex; align-items: center; gap: var(--space-8); min-width: 18rem; padding: 0 var(--space-12); border: 1px solid var(--border-default); border-radius: var(--radius-10); background: var(--surface-panel); color: var(--text-tertiary); }.library-toolbar input { width: 100%; min-height: 2.5rem; border: 0; outline: 0; background: transparent; color: var(--text-primary); }.library-toolbar > div { display: flex; padding: var(--space-4); border: 1px solid var(--border-default); border-radius: var(--radius-10); background: var(--surface-panel); }.library-toolbar > div button { width: 2.2rem; border: 0; border-radius: var(--radius-8); background: transparent; color: var(--text-tertiary); }.library-toolbar > div button.active { background: var(--surface-accent); color: var(--text-accent); }
.teacher-library.mode-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(17rem, 1fr)); gap: var(--space-20); }.teacher-card { overflow: hidden; padding: 0; border: 1px solid var(--border-default); border-radius: var(--radius-16); background: var(--surface-panel); color: var(--text-primary); text-align: left; transition: transform .18s ease, box-shadow .18s ease; }.teacher-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-md); }.card-cover { position: relative; display: block; aspect-ratio: 16 / 9; overflow: hidden; background: var(--surface-accent); }.card-cover img { width: 100%; height: 100%; object-fit: cover; }.card-cover > span { display: grid; width: 100%; height: 100%; place-content: center; justify-items: center; gap: var(--space-8); color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; letter-spacing: .1em; }.card-cover em, .workspace-head > em { padding: var(--space-4) var(--space-8); border-radius: var(--radius-8); background: var(--surface-muted); color: var(--text-tertiary); font-size: var(--text-caption); font-style: normal; }.card-cover em { position: absolute; top: var(--space-10); right: var(--space-10); }.card-cover em.published, .workspace-head > em.published { background: var(--success-surface); color: var(--success-600); }.card-body { display: grid; gap: var(--space-8); padding: var(--space-18); }.card-body > strong { font-size: var(--text-subtitle); }.card-body p { min-height: 3em; margin: 0; color: var(--text-secondary); line-height: 1.5; }.card-body > span { color: var(--text-tertiary); font-size: var(--text-caption); }.teacher-library.mode-list { display: grid; gap: var(--space-12); }.mode-list .teacher-card { display: grid; grid-template-columns: 13rem 1fr; }.mode-list .card-cover { aspect-ratio: 16 / 9; }.empty-library { grid-column: 1 / -1; padding: var(--space-40); color: var(--text-tertiary); text-align: center; }
.workspace-head { margin-bottom: var(--space-16); }.workspace-head > button { display: inline-flex; align-items: center; gap: var(--space-6); padding: 0; border: 0; background: transparent; color: var(--text-accent); }.workspace-head > div { flex: 1; }.workspace-head > .workspace-actions { flex: 0 0 auto; display: flex; align-items: center; gap: var(--space-10); }.workspace-tabs { display: flex; flex-wrap: wrap; gap: var(--space-4); margin-bottom: var(--space-16); border-bottom: 1px solid var(--border-default); }.workspace-tabs button { padding: var(--space-12) var(--space-18); border: 0; border-bottom: 2px solid transparent; background: transparent; color: var(--text-secondary); transition: color 200ms ease; }.workspace-tabs button.active { border-color: var(--border-accent); color: var(--text-accent); font-weight: 800; }
.basic-editor { display: grid; grid-template-columns: 19rem 1fr; gap: var(--space-24); padding: var(--space-24); border: 1px solid var(--border-default); border-radius: var(--radius-16); background: var(--surface-panel); }.cover-editor { display: grid; align-content: start; gap: var(--space-12); }.cover-editor > div, .create-cover { aspect-ratio: 16 / 9; display: grid; overflow: hidden; place-items: center; border: 1px dashed var(--border-default); border-radius: var(--radius-12); background: var(--surface-accent); color: var(--text-accent); }.cover-editor img, .create-cover img { width: 100%; height: 100%; object-fit: cover; }.cover-editor > div > span, .create-cover > span { display: grid; justify-items: center; gap: var(--space-8); }.cover-editor small { color: var(--text-tertiary); }.basic-fields { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-14); }.basic-fields label, .chapter-fields label, .resource-editor label { display: grid; gap: var(--space-6); color: var(--text-secondary); font-size: var(--text-caption); font-weight: 700; }.basic-fields .wide, .chapter-fields .wide { grid-column: 1 / -1; }.basic-fields input, .basic-fields textarea, .basic-fields select, .chapter-fields input, .chapter-fields textarea, .chapter-fields select, .resource-editor input, .knowledge-editor input { width: 100%; min-height: 2.6rem; padding: var(--space-8) var(--space-12); border: 1px solid var(--border-default); border-radius: var(--radius-8); background: var(--surface-panel); color: var(--text-primary); }.basic-fields textarea, .chapter-fields textarea { min-height: 5rem; resize: vertical; }.teacher-members { grid-column: 1 / -1; display: grid; gap: var(--space-10); padding-top: var(--space-16); border-top: 1px solid var(--border-subtle); }.teacher-members header { display: flex; align-items: center; gap: var(--space-8); }.teacher-members > div { display: flex; flex-wrap: wrap; gap: var(--space-8); }.teacher-members span { display: inline-flex; gap: var(--space-8); padding: var(--space-8) var(--space-12); border-radius: var(--radius-10); background: var(--surface-muted); }.teacher-members small { color: var(--text-tertiary); }.basic-editor footer { grid-column: 1 / -1; padding-top: var(--space-16); border-top: 1px solid var(--border-subtle); }.basic-editor footer > span { flex: 1; color: var(--text-tertiary); font-size: var(--text-caption); }
.chapter-workspace { display: grid; grid-template-columns: 19rem minmax(0, 1fr); gap: var(--space-16); align-items: start; }.chapter-list, .chapter-editor, .chapter-empty { border: 1px solid var(--border-default); border-radius: var(--radius-16); background: var(--surface-panel); }.chapter-list { overflow: hidden; }.chapter-list > header { display: flex; align-items: center; justify-content: space-between; padding: var(--space-14); border-bottom: 1px solid var(--border-subtle); }.chapter-list > button { width: 100%; display: grid; grid-template-columns: 2.2rem 1fr; gap: var(--space-10); padding: var(--space-14); border: 0; border-bottom: 1px solid var(--border-subtle); background: transparent; color: var(--text-primary); text-align: left; }.chapter-list > button.active { background: var(--surface-accent); }.chapter-list > button > span:first-child { color: var(--text-accent); font-weight: 800; }.chapter-list > button > span:last-child { display: grid; gap: var(--space-4); }.chapter-list small { color: var(--text-tertiary); }.chapter-editor { display: grid; gap: var(--space-20); padding: var(--space-24); }.chapter-editor > header h2 { margin: var(--space-4) 0 0; }.chapter-editor > header > div:last-child { display: flex; gap: var(--space-6); }.chapter-fields { display: grid; gap: var(--space-12); }.article-entry { display: flex; align-items: center; justify-content: space-between; gap: var(--space-16); padding: var(--space-18); border: 1px solid var(--border-default); border-radius: var(--radius-12); background: var(--surface-muted); }.article-entry > div { display: flex; align-items: center; gap: var(--space-12); color: var(--text-accent); }.article-entry span { display: grid; gap: var(--space-4); color: var(--text-primary); }.article-entry small { color: var(--text-tertiary); font-weight: 400; }
.knowledge-editor { display: grid; gap: var(--space-10); padding-top: var(--space-16); border-top: 1px solid var(--border-subtle); }.knowledge-editor > div { display: grid; grid-template-columns: 9rem 12rem 1fr auto; gap: var(--space-8); }.chapter-editor > footer { justify-content: flex-end; }.chapter-empty { display: grid; min-height: 25rem; place-content: center; justify-items: center; color: var(--text-tertiary); }
.create-dialog, .delete-dialog, .invite-dialog { display: grid; gap: var(--space-20); color: var(--text-primary); }.create-dialog header h2, .delete-dialog h2, .invite-dialog h2 { margin: 0; }.create-dialog header p, .delete-dialog p, .invite-dialog p { margin-bottom: 0; color: var(--text-secondary); }.create-cover { max-width: 22rem; cursor: pointer; }.create-dialog footer, .delete-dialog footer, .invite-dialog footer { justify-content: flex-end; }.invite-dialog label { display: grid; gap: var(--space-8); }.invite-dialog textarea { min-height: 7rem; padding: var(--space-12); border: 1px solid var(--border-default); border-radius: var(--radius-8); background: var(--surface-muted); color: var(--text-primary); resize: none; }
.knowledge-library { display: grid; gap: var(--space-18); padding: var(--space-24); border: 1px solid var(--border-default); border-radius: var(--radius-16); background: var(--surface-panel); }.knowledge-library > header { display: flex; align-items: center; justify-content: space-between; gap: var(--space-16); }.knowledge-library > header span { color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; letter-spacing: .08em; }.knowledge-library h2 { margin: var(--space-4) 0; }.knowledge-library header p { margin: 0; color: var(--text-secondary); }.knowledge-relations { display: grid; grid-template-columns: minmax(14rem, 20rem) 1fr; gap: var(--space-16); padding: var(--space-16); border-radius: var(--radius-12); background: var(--surface-muted); }.knowledge-relations > label { display: grid; align-content: start; gap: var(--space-8); color: var(--text-secondary); font-size: var(--text-caption); font-weight: 700; }.knowledge-relations select { min-height: 2.6rem; border: 1px solid var(--border-default); border-radius: var(--radius-8); background: var(--surface-panel); color: var(--text-primary); }.knowledge-relations fieldset { display: flex; flex-wrap: wrap; gap: var(--space-8); margin: 0; border: 0; }.knowledge-relations legend { width: 100%; margin-bottom: var(--space-8); color: var(--text-secondary); font-size: var(--text-caption); font-weight: 700; }.knowledge-relations fieldset label { display: inline-flex; align-items: center; gap: var(--space-6); padding: var(--space-6) var(--space-10); border: 1px solid var(--border-default); border-radius: var(--radius-8); background: var(--surface-panel); color: var(--text-secondary); font-size: var(--text-caption); }.knowledge-relations input { width: auto; }.knowledge-document-list { display: grid; gap: var(--space-10); }.knowledge-document-list article { display: grid; grid-template-columns: auto minmax(0, 1fr) auto auto; align-items: center; gap: var(--space-12); padding: var(--space-14); border: 1px solid var(--border-default); border-radius: var(--radius-10); }.document-icon { display: grid; place-items: center; width: 2.5rem; height: 2.5rem; border-radius: var(--radius-8); background: var(--surface-accent); color: var(--text-accent); }.knowledge-document-list article > div { min-width: 0; }.knowledge-document-list p { overflow: hidden; margin: var(--space-4) 0 0; color: var(--text-tertiary); font-size: var(--text-caption); text-overflow: ellipsis; white-space: nowrap; }.document-progress { display: grid; gap: var(--space-5); margin-top: var(--space-8); }.document-progress > span { display: flex; justify-content: space-between; gap: var(--space-8); color: var(--text-secondary); }.document-progress b { color: var(--text-accent); font-size: var(--text-caption); }.document-progress progress { width: 100%; height: .4rem; overflow: hidden; border: 0; border-radius: 999px; background: var(--surface-muted); }.document-progress progress::-webkit-progress-bar { background: var(--surface-muted); }.document-progress progress::-webkit-progress-value, .document-progress progress::-moz-progress-bar { border-radius: 999px; background: var(--primary-500); }.document-error { color: var(--danger-600); }.document-status { display: grid; justify-items: end; color: var(--text-tertiary); font-size: var(--text-caption); font-weight: 800; }.document-status.running, .document-status.pending { color: var(--text-accent); }.document-status.success { color: var(--success-600); }.document-status.failed { color: var(--danger-600); }.document-status small { margin-top: var(--space-4); font-weight: 400; }.knowledge-empty { display: grid; min-height: 15rem; place-content: center; justify-items: center; gap: var(--space-8); color: var(--text-tertiary); text-align: center; }.knowledge-empty p { margin: 0; }
.library-head { position: relative; isolation: isolate; display: grid; grid-template-columns: minmax(0, 1fr) minmax(20rem, 30rem); min-height: 22rem; overflow: hidden; align-items: stretch; margin-bottom: var(--space-24); padding: clamp(var(--space-24), 4vw, var(--space-40)); border: 1px solid var(--border-accent); border-radius: var(--radius-24); background: var(--hero-gradient); box-shadow: var(--shadow-lg); }
.library-head::before { position: absolute; z-index: -1; top: -9rem; left: 42%; width: 28rem; height: 28rem; border: 1px solid color-mix(in srgb, var(--border-accent) 45%, transparent); border-radius: 50%; content: ''; }
.library-head-copy { position: relative; z-index: 2; display: grid; align-content: center; justify-items: start; max-width: 43rem; }
.library-kicker { display: inline-flex; align-items: center; gap: var(--space-6); color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; letter-spacing: .08em; }
.library-head h1 { max-width: 37rem; margin: var(--space-10) 0; font-family: var(--font-family-display); font-size: clamp(var(--text-headline), 3.4vw, var(--text-display)); line-height: 1.15; letter-spacing: -.025em; }
.library-head p { max-width: 39rem; font-size: var(--text-body-lg); line-height: 1.7; }
.library-stats { display: flex; flex-wrap: wrap; gap: var(--space-10); margin: var(--space-20) 0; }
.library-stats > span { display: grid; min-width: 7.5rem; gap: var(--space-2); padding: var(--space-10) var(--space-14); border: 1px solid color-mix(in srgb, var(--border-accent) 44%, transparent); border-radius: var(--radius-12); background: color-mix(in srgb, var(--surface-panel) 78%, transparent); box-shadow: var(--shadow-sm); backdrop-filter: blur(12px); }
.library-stats strong { font-family: var(--font-family-display); font-size: var(--text-title); line-height: 1; }
.library-stats small { color: var(--text-tertiary); }
.library-head-visual { position: relative; z-index: 1; min-width: 0; }
.library-head-visual img { position: absolute; z-index: 2; top: 50%; right: clamp(-2rem, -1vw, -.5rem); width: min(31rem, 115%); max-width: none; transform: translateY(-50%) rotate(1deg); filter: drop-shadow(var(--shadow-float)); }
.visual-orbit { position: absolute; top: 50%; right: 5%; width: 20rem; max-width: 90%; aspect-ratio: 1; border: 1px solid var(--border-accent); border-radius: 50%; transform: translateY(-50%); }
.visual-caption { position: absolute; z-index: 3; right: var(--space-8); bottom: var(--space-8); display: flex; align-items: center; gap: var(--space-10); padding: var(--space-12) var(--space-14); border: 1px solid var(--border-accent); border-radius: var(--radius-12); background: var(--surface-glass); color: var(--text-accent); box-shadow: var(--shadow-md); backdrop-filter: blur(16px); }
.visual-caption > span { display: grid; gap: var(--space-2); }
.visual-caption strong { color: var(--text-primary); font-size: var(--text-subtitle); }
.visual-caption small { color: var(--text-tertiary); font-size: var(--text-caption); }
.library-toolbar { align-items: center; gap: var(--space-12); padding: var(--space-10); border: 1px solid var(--border-default); border-radius: var(--radius-12); background: var(--surface-glass); box-shadow: var(--shadow-sm); backdrop-filter: blur(12px); }
.library-toolbar > label { flex: 1; max-width: 32rem; min-width: 0; border-color: transparent; background: var(--surface-muted); }
.teacher-card { position: relative; box-shadow: var(--shadow-sm); }
.teacher-card::after { position: absolute; top: 0; right: 0; left: 0; height: .22rem; background: linear-gradient(90deg, var(--primary-500), var(--accent-warm)); content: ''; opacity: 0; transition: opacity var(--transition-base); }
.teacher-card:hover::after, .teacher-card:focus-visible::after { opacity: 1; }
.teacher-card:focus-visible { outline: 2px solid var(--border-accent); outline-offset: 3px; }
.workspace-head { display: grid; grid-template-columns: auto 7rem minmax(0, 1fr) auto; align-items: center; gap: var(--space-18); margin-bottom: var(--space-16); padding: var(--space-18) var(--space-20); border: 1px solid var(--border-default); border-radius: var(--radius-16); background: linear-gradient(135deg, var(--surface-panel), var(--surface-accent)); box-shadow: var(--shadow-sm); }
.workspace-back { align-self: start; }
.workspace-course-cover { display: grid; width: 7rem; aspect-ratio: 16 / 10; overflow: hidden; place-items: center; border: 1px solid var(--border-accent); border-radius: var(--radius-12); background: var(--surface-accent); color: var(--text-accent); }
.workspace-course-cover img { width: 100%; height: 100%; object-fit: cover; }
.workspace-identity { min-width: 0; }
.workspace-identity h1 { overflow-wrap: anywhere; font-family: var(--font-family-display); }
.workspace-identity p { overflow: hidden; max-width: 55rem; margin: 0 0 var(--space-8); color: var(--text-secondary); text-overflow: ellipsis; white-space: nowrap; }
.workspace-facts { display: flex; flex-wrap: wrap; gap: var(--space-6); }
.workspace-facts span { padding: var(--space-4) var(--space-8); border-radius: var(--radius-16); background: color-mix(in srgb, var(--surface-panel) 70%, transparent); color: var(--text-tertiary); font-size: var(--text-caption); }
.workspace-tabs { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: var(--space-8); margin-bottom: var(--space-20); padding: var(--space-8); border: 1px solid var(--border-default); border-radius: var(--radius-14, var(--radius-12)); background: var(--surface-panel); box-shadow: var(--shadow-sm); }
.workspace-tabs button { position: relative; display: flex; min-width: 0; min-height: 4.25rem; align-items: center; gap: var(--space-10); padding: var(--space-10) var(--space-12); border: 1px solid transparent; border-radius: var(--radius-10); background: transparent; color: var(--text-tertiary); text-align: left; }
.workspace-tabs button > svg { flex: 0 0 auto; }
.workspace-tabs button > span { display: grid; min-width: 0; gap: var(--space-2); }
.workspace-tabs button strong, .workspace-tabs button small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.workspace-tabs button strong { color: var(--text-secondary); font-size: var(--text-body); }
.workspace-tabs button small { font-size: var(--text-caption); font-weight: 500; }
.workspace-tabs button:hover { background: var(--surface-muted); color: var(--text-accent); }
.workspace-tabs button.active { border-color: var(--border-accent); background: var(--surface-accent); color: var(--text-accent); box-shadow: var(--shadow-sm); }
.workspace-tabs button.active strong { color: var(--text-accent); }
.workspace-actions em { padding: var(--space-5) var(--space-10); border-radius: var(--radius-16); background: var(--surface-muted); color: var(--text-tertiary); font-size: var(--text-caption); font-style: normal; font-weight: 700; }
.workspace-actions em.published { background: var(--success-surface); color: var(--success-600); }
.basic-editor, .chapter-list, .chapter-editor, .chapter-empty, .knowledge-library { box-shadow: var(--shadow-sm); }
.cover-editor > div, .create-cover { position: relative; background: linear-gradient(145deg, var(--surface-accent), var(--surface-muted)); }
.article-entry { border-color: var(--border-accent); background: linear-gradient(135deg, var(--surface-accent), var(--surface-panel)); }
.knowledge-library > header { padding-bottom: var(--space-18); border-bottom: 1px solid var(--border-subtle); }
.knowledge-empty { border: 1px dashed var(--border-default); border-radius: var(--radius-12); background: var(--surface-muted); }
@media (max-width: 1100px) { .library-head { grid-template-columns: minmax(0, 1fr) minmax(16rem, 21rem); }.workspace-tabs { grid-template-columns: repeat(3, minmax(0, 1fr)); }.workspace-head { grid-template-columns: auto 6rem minmax(0, 1fr); }.workspace-actions { grid-column: 2 / -1; justify-content: flex-end; }.workspace-course-cover { width: 6rem; } }
@media (max-width: 900px) { .basic-editor, .chapter-workspace, .knowledge-relations { grid-template-columns: 1fr; }.cover-editor { max-width: 24rem; }.chapter-list { max-height: 20rem; overflow: auto; }.knowledge-editor > div { grid-template-columns: 1fr; }.library-head { grid-template-columns: minmax(0, 1fr) 15rem; min-height: 24rem; }.library-head-visual img { right: -5rem; width: 23rem; }.visual-caption { right: -1rem; }.workspace-head { grid-template-columns: auto minmax(0, 1fr); }.workspace-back { grid-column: 1 / -1; }.workspace-course-cover { width: 6.5rem; }.workspace-actions { grid-column: 1 / -1; } }
@media (max-width: 640px) { .teacher-page { padding: var(--space-12); padding-bottom: 7rem; }.library-head { grid-template-columns: 1fr; min-height: auto; padding: var(--space-20); }.library-head::before { top: auto; bottom: -8rem; left: 15%; }.library-head-copy { max-width: none; }.library-head h1 { font-size: var(--text-headline); }.library-head p { font-size: var(--text-body); }.library-stats { display: grid; grid-template-columns: repeat(3, 1fr); width: 100%; gap: var(--space-6); }.library-stats > span { min-width: 0; padding: var(--space-8); }.library-stats strong { font-size: var(--text-subtitle); }.library-head-visual { min-height: 16rem; }.library-head-visual img { top: 52%; right: -1rem; width: 21rem; }.visual-orbit { right: 2%; width: 15rem; }.visual-caption { right: 0; bottom: 0; }.library-toolbar { align-items: stretch; flex-direction: column; }.library-toolbar > label { max-width: none; min-width: 0; }.library-toolbar > div { align-self: flex-end; }.workspace-head, .knowledge-library > header { align-items: stretch; }.workspace-head { grid-template-columns: 1fr; padding: var(--space-16); }.workspace-back, .workspace-course-cover, .workspace-identity, .workspace-actions { grid-column: 1; }.workspace-course-cover { width: 100%; aspect-ratio: 16 / 6; }.workspace-identity p { white-space: normal; }.workspace-actions { justify-content: space-between; }.workspace-tabs { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-6); padding: var(--space-6); }.workspace-tabs button { min-height: 3.75rem; padding: var(--space-8); }.workspace-tabs button small { display: none; }.mode-list .teacher-card { grid-template-columns: 7rem 1fr; }.basic-editor, .chapter-editor, .knowledge-library { padding: var(--space-16); }.basic-fields, .chapter-fields { grid-template-columns: 1fr; }.basic-fields .wide, .chapter-fields .wide { grid-column: auto; }.article-entry { align-items: stretch; flex-direction: column; }.knowledge-document-list article { grid-template-columns: auto minmax(0, 1fr); }.document-status { justify-items: start; }.knowledge-document-list article > button { grid-column: 1 / -1; } }
</style>
