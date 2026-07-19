<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { MessageCircle, MessagesSquare, Search, UsersRound } from 'lucide-vue-next';
import {
  employeeApi,
  type CourseLearningOverviewResponse,
  type CourseLearningStudentSummaryResponse,
  type CourseResponse,
  type EntityId,
} from '@/api';
import AppButton from '@/components/ui/AppButton.vue';
import AppEmptyState from '@/components/ui/AppEmptyState.vue';
import AppErrorState from '@/components/ui/AppErrorState.vue';
import AppLoadingState from '@/components/ui/AppLoadingState.vue';
import { useImStore } from '@/stores/im';

const props = defineProps<{ course: CourseResponse }>();
const router = useRouter();
const im = useImStore();
const overview = ref<CourseLearningOverviewResponse | null>(null);
const loading = ref(false);
const error = ref('');
const keyword = ref('');
const classFilter = ref<string>('ALL');
const openingTarget = ref('');

const classes = computed(() => overview.value?.classes.filter((item) => item.classId != null) ?? []);
const filteredStudents = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return (overview.value?.students ?? []).filter((student) => {
    const matchesClass = classFilter.value === 'ALL' || String(student.classId) === classFilter.value;
    const matchesQuery = !query || `${student.studentName} ${student.studentNo} ${student.className ?? ''}`.toLowerCase().includes(query);
    return matchesClass && matchesQuery;
  });
});

async function loadOverview() {
  loading.value = true;
  error.value = '';
  try {
    overview.value = await employeeApi.teacherCourses.learningOverview(props.course.courseId);
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '班级沟通数据加载失败';
  } finally {
    loading.value = false;
  }
}

async function openClassGroup(classId: EntityId) {
  openingTarget.value = `class:${String(classId)}`;
  error.value = '';
  try {
    const conversation = await employeeApi.teacherCourses.openClassGroup(props.course.courseId, classId);
    im.upsertConversation(conversation);
    await router.push({ name: 'employee-message-detail', params: { conversationId: String(conversation.conversationId) } });
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '班级群打开失败';
  } finally {
    openingTarget.value = '';
  }
}

async function startPrivateConversation(student: CourseLearningStudentSummaryResponse) {
  openingTarget.value = `student:${String(student.studentUserId)}`;
  error.value = '';
  try {
    const conversation = await employeeApi.teacherCourses.startStudentConversation(props.course.courseId, student.studentUserId);
    im.upsertConversation(conversation);
    await router.push({ name: 'employee-message-detail', params: { conversationId: String(conversation.conversationId) } });
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '私聊发起失败';
  } finally {
    openingTarget.value = '';
  }
}

function initials(name: string) {
  return name.trim().slice(-2) || '学生';
}

watch(() => props.course.courseId, () => {
  classFilter.value = 'ALL';
  keyword.value = '';
  void loadOverview();
});

onMounted(loadOverview);
</script>

<template>
  <section class="communication-panel">
    <header class="communication-head">
      <div><span>课程沟通</span><h2>班级群与学生私聊</h2><p>群成员和学生目录仅来自已加入当前课程的学生。</p></div>
      <label><Search :size="17" /><input v-model="keyword" placeholder="搜索姓名、学号或班级" /></label>
    </header>

    <AppErrorState v-if="error" title="课程沟通暂不可用" :description="error" action-label="重新加载" @retry="loadOverview" />
    <AppLoadingState v-if="loading && !overview" />

    <template v-else-if="overview">
      <div class="communication-summary">
        <article><span><UsersRound :size="19" /></span><strong>{{ overview.studentCount }}</strong><small>已加入课程</small></article>
        <article><span><MessagesSquare :size="19" /></span><strong>{{ classes.length }}</strong><small>课程班级</small></article>
      </div>

      <div class="communication-layout">
        <aside class="class-directory">
          <header><div><span>班级群</span><strong>{{ classes.length }}</strong></div><button type="button" :class="{ active: classFilter === 'ALL' }" @click="classFilter = 'ALL'">全部学生</button></header>
          <article v-for="item in classes" :key="String(item.classId)" :class="{ active: classFilter === String(item.classId) }">
            <button type="button" class="class-main" @click="classFilter = String(item.classId)"><span><UsersRound :size="18" /></span><span><strong>{{ item.className }}</strong><small>{{ item.studentCount }} 名学生</small></span></button>
            <AppButton size="sm" :disabled="openingTarget === `class:${String(item.classId)}`" @click="openClassGroup(item.classId!)"><MessagesSquare :size="15" />{{ openingTarget === `class:${String(item.classId)}` ? '正在打开' : '进入班级群' }}</AppButton>
          </article>
          <AppEmptyState v-if="!classes.length" title="暂无班级群" description="已加入课程的学生完成班级归属后显示。" />
        </aside>

        <main class="student-directory">
          <header><div><span>学生目录</span><h3>{{ classFilter === 'ALL' ? '全部课程学生' : classes.find((item) => String(item.classId) === classFilter)?.className }}</h3></div><small>{{ filteredStudents.length }} 人</small></header>
          <div v-if="filteredStudents.length" class="student-list">
            <article v-for="student in filteredStudents" :key="String(student.studentUserId)">
              <span class="student-avatar">{{ initials(student.studentName) }}</span>
              <div><strong>{{ student.studentName }}</strong><span>{{ student.studentNo }}<template v-if="student.className"> · {{ student.className }}</template></span></div>
              <AppButton size="sm" :disabled="openingTarget === `student:${String(student.studentUserId)}`" @click="startPrivateConversation(student)"><MessageCircle :size="15" />{{ openingTarget === `student:${String(student.studentUserId)}` ? '正在打开' : '发起私聊' }}</AppButton>
            </article>
          </div>
          <AppEmptyState v-else title="未找到学生" description="请调整班级范围或搜索关键词。" />
        </main>
      </div>
    </template>
  </section>
</template>

<style scoped>
.communication-panel { display: grid; gap: var(--space-18); min-width: 0; }.communication-head { position: relative; isolation: isolate; display: flex; overflow: hidden; align-items: flex-end; justify-content: space-between; gap: var(--space-20); padding: var(--space-24); border: 1px solid var(--border-default); border-radius: var(--radius-16); background: linear-gradient(135deg, var(--surface-panel), var(--surface-accent)); box-shadow: var(--shadow-sm); }.communication-head::after { position: absolute; z-index: -1; top: -5rem; right: 22rem; width: 12rem; height: 12rem; border: 1px solid var(--border-accent); border-radius: 50%; content: ''; opacity: .38; }.communication-head > div > span, .class-directory > header span, .student-directory > header span { color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; letter-spacing: .08em; }.communication-head h2, .student-directory h3 { margin: var(--space-4) 0; font-family: var(--font-family-display); }.communication-head p { margin: 0; color: var(--text-secondary); line-height: 1.6; }.communication-head label { display: flex; align-items: center; gap: var(--space-8); width: min(22rem, 100%); padding: 0 var(--space-12); border: 1px solid var(--border-default); border-radius: var(--radius-10); background: var(--surface-glass); color: var(--text-tertiary); box-shadow: var(--shadow-sm); }.communication-head input { width: 100%; min-width: 0; min-height: 2.75rem; border: 0; outline: 0; background: transparent; color: var(--text-primary); }.communication-summary { display: grid; grid-template-columns: repeat(2, minmax(0, 14rem)); gap: var(--space-10); }.communication-summary article { position: relative; display: grid; grid-template-columns: auto 1fr; overflow: hidden; align-items: center; gap: var(--space-10); padding: var(--space-14); border: 1px solid var(--border-default); border-radius: var(--radius-12); background: var(--surface-panel); box-shadow: var(--shadow-sm); }.communication-summary article::after { position: absolute; right: -2rem; bottom: -2rem; width: 5rem; height: 5rem; border: 1px solid var(--border-accent); border-radius: 50%; content: ''; opacity: .28; }.communication-summary article > span { display: grid; width: 2.4rem; height: 2.4rem; place-items: center; border-radius: var(--radius-9); background: var(--surface-accent); color: var(--text-accent); }.communication-summary strong { font-family: var(--font-family-display); font-size: var(--text-title-sm); }.communication-summary small { grid-column: 2; color: var(--text-tertiary); }.communication-layout { display: grid; grid-template-columns: minmax(18rem, .8fr) minmax(0, 1.4fr); gap: var(--space-16); align-items: start; }.class-directory, .student-directory { min-width: 0; overflow: hidden; border: 1px solid var(--border-default); border-radius: var(--radius-16); background: var(--surface-panel); box-shadow: var(--shadow-sm); }.class-directory > header, .student-directory > header { display: flex; align-items: center; justify-content: space-between; gap: var(--space-12); padding: var(--space-16); border-bottom: 1px solid var(--border-subtle); background: var(--surface-muted); }.class-directory > header > div { display: flex; align-items: center; gap: var(--space-8); }.class-directory > header button { padding: var(--space-6) var(--space-10); border: 1px solid var(--border-default); border-radius: var(--radius-8); background: var(--surface-panel); color: var(--text-secondary); }.class-directory > header button.active { border-color: var(--border-accent); background: var(--surface-accent); color: var(--text-accent); }.class-directory > article { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: var(--space-10); padding: var(--space-12) var(--space-14); border-top: 1px solid var(--border-subtle); transition: background-color var(--transition-base); }.class-directory > article:hover, .class-directory > article.active { background: var(--surface-accent); }.class-main { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: var(--space-10); min-width: 0; padding: 0; border: 0; background: transparent; color: var(--text-primary); text-align: left; }.class-main > span:first-child { display: grid; width: 2.3rem; height: 2.3rem; place-items: center; border-radius: var(--radius-8); background: var(--surface-muted); color: var(--text-accent); }.class-main > span:last-child { display: grid; min-width: 0; }.class-main strong, .class-main small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.class-main small, .student-directory > header small { color: var(--text-tertiary); }.student-list { display: grid; }.student-list article { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: var(--space-12); padding: var(--space-14) var(--space-16); border-top: 1px solid var(--border-subtle); transition: background-color var(--transition-base); }.student-list article:hover { background: color-mix(in srgb, var(--surface-accent) 54%, transparent); }.student-avatar { display: grid; width: 2.6rem; height: 2.6rem; place-items: center; border: 1px solid var(--border-accent); border-radius: 50%; background: var(--surface-accent); color: var(--text-accent); font-weight: 800; box-shadow: var(--shadow-sm); }.student-list article > div { display: grid; min-width: 0; gap: var(--space-3); }.student-list article > div span { overflow: hidden; color: var(--text-tertiary); font-size: var(--text-caption); text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 840px) { .communication-head { align-items: stretch; flex-direction: column; }.communication-head label { width: 100%; }.communication-layout { grid-template-columns: 1fr; } }
@media (max-width: 560px) { .communication-head { padding: var(--space-16); }.communication-summary { grid-template-columns: repeat(2, 1fr); }.class-directory > article { grid-template-columns: 1fr; }.class-directory > article :deep(.ui-button) { width: 100%; }.student-list article { grid-template-columns: auto minmax(0, 1fr); }.student-list article :deep(.ui-button) { grid-column: 1 / -1; width: 100%; } }
</style>
