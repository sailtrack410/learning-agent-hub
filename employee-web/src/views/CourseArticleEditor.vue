<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowLeft, Check, CloudUpload, Download, ExternalLink, Eye, FileText, ListTree, Save, Send, Tags, X } from 'lucide-vue-next';
import { employeeApi, type CourseChapterResponse, type CourseResponse, type EntityId } from '@/api';
import AppButton from '@/components/ui/AppButton.vue';
import AppErrorState from '@/components/ui/AppErrorState.vue';
import AppLoadingState from '@/components/ui/AppLoadingState.vue';
import AppDialog from '@/components/ui/AppDialog.vue';
import CourseContentRenderer from '@/components/course/CourseContentRenderer.vue';
import CourseRichTextEditor from '@/components/course/CourseRichTextEditor.vue';
import { usePersonalFileTransfer } from '@/composables/usePersonalFileTransfer';
import { courseArticleEditSnapshot } from '@/utils/course-article';

type ResourceKind = 'image' | 'video' | 'attachment';

const route = useRoute();
const router = useRouter();
const { uploadCourseResource } = usePersonalFileTransfer();
const course = ref<CourseResponse | null>(null);
const chapter = ref<CourseChapterResponse | null>(null);
const title = ref('');
const summary = ref('');
const contentJson = ref('');
const status = ref<'DRAFT' | 'PUBLISHED'>('DRAFT');
const loading = ref(true);
const saving = ref(false);
const uploading = ref(false);
const savedSnapshot = ref('');
const error = ref('');
const savedAt = ref('');
const resourceKind = ref<ResourceKind>('image');
const resourceInput = ref<HTMLInputElement | null>(null);
const editorRef = ref<InstanceType<typeof CourseRichTextEditor> | null>(null);
const previewOpen = ref(false);
const previewAttachment = ref<{ attachmentRefId: EntityId; name: string; mimeType: string } | null>(null);
const previewAttachmentUrl = computed(() => previewAttachment.value ? employeeApi.courses.resourceUrl(previewAttachment.value.attachmentRefId) : '');
const previewTaskCount = ref(0);
const previewTaskIds = new Set<string>();
let previewPollingActive = true;

const courseId = computed(() => String(route.params.courseId));
const chapterId = computed(() => String(route.params.chapterId));
const headings = computed(() => {
  try {
    const document = JSON.parse(contentJson.value);
    return (document.content ?? [])
      .filter((node: { type?: string }) => node.type === 'heading')
      .map((node: { attrs?: { level?: number }; content?: Array<{ text?: string }> }) => ({
        level: Number(node.attrs?.level ?? 2),
        text: (node.content ?? []).map((item) => item.text ?? '').join('') || '未命名标题',
      }));
  } catch {
    return [];
  }
});
const wordCount = computed(() => {
  try {
    const document = JSON.parse(contentJson.value);
    let text = '';
    const read = (node: { text?: string; content?: Array<Record<string, unknown>> }) => {
      text += node.text ?? '';
      node.content?.forEach(read);
    };
    read(document);
    return text.replace(/\s/g, '').length;
  } catch {
    return 0;
  }
});
const dirty = computed(() => savedSnapshot.value !== courseArticleEditSnapshot(
  title.value,
  summary.value,
  contentJson.value,
  status.value,
));

async function loadArticle() {
  loading.value = true;
  error.value = '';
  try {
    course.value = await employeeApi.teacherCourses.detail(courseId.value);
    chapter.value = course.value.chapters.find((item) => String(item.chapterId) === chapterId.value) ?? null;
    if (!chapter.value) throw new Error('课程文章不存在');
    title.value = chapter.value.chapterName;
    summary.value = chapter.value.summary ?? '';
    contentJson.value = chapter.value.contentJson || '{"type":"doc","content":[{"type":"paragraph"}]}';
    status.value = chapter.value.status;
    await nextTick();
    savedSnapshot.value = courseArticleEditSnapshot(title.value, summary.value, contentJson.value, status.value);
    void resumeAttachmentPreviews();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '文章加载失败';
  } finally {
    loading.value = false;
  }
}

async function saveArticle(nextStatus = status.value) {
  if (!title.value.trim()) {
    error.value = '请输入文章标题';
    return;
  }
  saving.value = true;
  error.value = '';
  try {
    course.value = await employeeApi.teacherCourses.saveArticle(courseId.value, chapterId.value, {
      title: title.value,
      summary: summary.value,
      contentJson: contentJson.value,
      status: nextStatus,
    });
    chapter.value = course.value.chapters.find((item) => String(item.chapterId) === chapterId.value) ?? null;
    status.value = nextStatus;
    await nextTick();
    savedSnapshot.value = courseArticleEditSnapshot(title.value, summary.value, contentJson.value, status.value);
    savedAt.value = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '文章保存失败';
  } finally {
    saving.value = false;
  }
}

function chooseResource(kind: ResourceKind) {
  resourceKind.value = kind;
  if (resourceInput.value) {
    resourceInput.value.accept = kind === 'image'
      ? 'image/png,image/jpeg,image/gif,image/webp'
      : kind === 'video'
        ? 'video/mp4,video/webm,video/quicktime'
        : '.pdf,.doc,.docx,.ppt,.pptx,.txt,.md';
    resourceInput.value.click();
  }
}

async function insertResource(file: File) {
  uploading.value = true;
  error.value = '';
  try {
    const uploaded = await uploadCourseResource(file, courseId.value);
    const src = employeeApi.courses.resourceUrl(uploaded.attachmentRefId);
    await nextTick();
    if (resourceKind.value === 'image') editorRef.value?.insertImage(uploaded.attachmentRefId, src, uploaded.displayName);
    if (resourceKind.value === 'video') editorRef.value?.insertVideo(uploaded.attachmentRefId, src, uploaded.displayName, uploaded.mimeType);
    if (resourceKind.value === 'attachment') {
      const previewSupported = supportsDocumentPreview(uploaded.displayName, uploaded.mimeType);
      editorRef.value?.insertAttachment(uploaded.attachmentRefId, src, false, uploaded.displayName, uploaded.mimeType, uploaded.fileSize);
      if (previewSupported) void ensureAttachmentPreview(uploaded.attachmentRefId, uploaded.displayName, uploaded.mimeType);
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '课程资源上传失败';
  } finally {
    uploading.value = false;
    if (resourceInput.value) resourceInput.value.value = '';
  }
}

function backToCourse() {
  router.push({ name: 'employee-courses', query: { courseId: courseId.value } });
}

function supportsDocumentPreview(fileName: string, mimeType: string) {
  return mimeType === 'application/pdf' || /\.(pdf|doc|docx|ppt|pptx)$/i.test(fileName);
}

async function resumeAttachmentPreviews() {
  try {
    const document = JSON.parse(contentJson.value) as Record<string, unknown>;
    const pending: Array<{ attachmentRefId: EntityId; name: string; mimeType: string }> = [];
    visitContent(document, (node) => {
      if (node.type !== 'attachment') return;
      const attrs = node.attrs ?? {};
      const attachmentRefId = attrs.attachmentRefId as EntityId | undefined;
      const name = String(attrs.name ?? '');
      const mimeType = String(attrs.mimeType ?? '');
      if (attachmentRefId != null && attrs.previewAvailable !== true && supportsDocumentPreview(name, mimeType)) {
        pending.push({ attachmentRefId, name, mimeType });
      }
    });
    pending.forEach((item) => void ensureAttachmentPreview(item.attachmentRefId, item.name, item.mimeType));
  } catch {
    // 文章内容格式错误由编辑器既有兜底处理，不能阻断正文加载。
  }
}

async function ensureAttachmentPreview(attachmentRefId: EntityId, name: string, mimeType: string) {
  const taskId = String(attachmentRefId);
  if (previewTaskIds.has(taskId)) return;
  previewTaskIds.add(taskId);
  previewTaskCount.value = previewTaskIds.size;
  try {
    let state = await employeeApi.teacherCourses.prepareResourcePreview(courseId.value, attachmentRefId);
    for (let attempt = 0; previewPollingActive && attempt < 80; attempt += 1) {
      if (state.previewStatus === 'READY') {
        markAttachmentPreviewReady(attachmentRefId);
        return;
      }
      if (state.previewStatus === 'FAILED') {
        throw new Error(state.errorMessage || `${name || mimeType} 预览转换失败`);
      }
      await waitForPreviewPoll();
      state = await employeeApi.teacherCourses.resourcePreviewStatus(courseId.value, attachmentRefId);
    }
    if (previewPollingActive) throw new Error(`${name || mimeType} 预览转换超时，请稍后重新打开文章`);
  } catch (cause) {
    if (previewPollingActive) error.value = cause instanceof Error ? cause.message : '附件预览转换失败';
  } finally {
    previewTaskIds.delete(taskId);
    previewTaskCount.value = previewTaskIds.size;
  }
}

function markAttachmentPreviewReady(attachmentRefId: EntityId) {
  try {
    const document = JSON.parse(contentJson.value) as Record<string, unknown>;
    let changed = false;
    visitContent(document, (node) => {
      if (node.type !== 'attachment' || String(node.attrs?.attachmentRefId ?? '') !== String(attachmentRefId)) return;
      node.attrs = { ...(node.attrs ?? {}), previewAvailable: true };
      changed = true;
    });
    if (changed) contentJson.value = JSON.stringify(document);
  } catch {
    error.value = '附件预览已生成，但文章内容更新失败';
  }
}

function visitContent(node: Record<string, unknown>, visitor: (current: { type?: string; attrs?: Record<string, unknown> }) => void) {
  const current = node as { type?: string; attrs?: Record<string, unknown>; content?: Array<Record<string, unknown>> };
  visitor(current);
  current.content?.forEach((child) => visitContent(child, visitor));
}

function waitForPreviewPoll() {
  return new Promise<void>((resolve) => window.setTimeout(resolve, 1500));
}

async function downloadCourseAttachment(attachment: { attachmentRefId: EntityId; name: string }) {
  const response = await fetch(employeeApi.courses.resourceUrl(attachment.attachmentRefId));
  if (!response.ok) {
    error.value = '附件下载失败';
    return;
  }
  const objectUrl = URL.createObjectURL(await response.blob());
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = attachment.name;
  anchor.click();
  URL.revokeObjectURL(objectUrl);
}

async function downloadPreviewAttachment() {
  if (!previewAttachment.value) return;
  await downloadCourseAttachment(previewAttachment.value);
}

function openPreviewAttachmentExternal() {
  window.open(previewAttachmentUrl.value, '_blank', 'noopener');
}

onMounted(loadArticle);
onBeforeUnmount(() => {
  previewPollingActive = false;
});
</script>

<template>
  <div class="article-workbench">
    <input ref="resourceInput" class="resource-input" type="file" @change="($event.target as HTMLInputElement).files?.[0] && insertResource(($event.target as HTMLInputElement).files![0])" />
    <AppLoadingState v-if="loading" />
    <AppErrorState v-else-if="error && !chapter" title="文章加载失败" :description="error" action-label="返回课程" @retry="backToCourse" />
    <template v-else-if="course && chapter">
      <header class="article-topbar">
        <button type="button" class="back-course" @click="backToCourse"><ArrowLeft :size="18" /><span><small>{{ course.courseName }}</small><strong>课程内容</strong></span></button>
        <div class="save-state"><CloudUpload v-if="dirty || previewTaskCount" :size="16" /><Check v-else :size="16" /><span>{{ uploading ? '正在上传资源' : previewTaskCount ? `正在生成 ${previewTaskCount} 个附件预览` : saving ? '正在保存' : dirty ? '有未保存修改' : savedAt ? `${savedAt} 已保存` : '已保存' }}</span></div>
        <div class="topbar-actions">
          <AppButton :disabled="uploading" @click="previewOpen = true"><Eye :size="16" />预览</AppButton>
          <AppButton :disabled="saving || uploading" @click="saveArticle('DRAFT')"><Save :size="16" />保存草稿</AppButton>
          <AppButton variant="primary" :disabled="saving || uploading" @click="saveArticle('PUBLISHED')"><Send :size="16" />发布文章</AppButton>
        </div>
      </header>

      <div class="article-layout">
        <main class="article-stage">
          <article class="article-paper">
            <header class="article-metadata">
              <span>第 {{ chapter.chapterNo }} 章 · {{ status === 'PUBLISHED' ? '已发布' : '草稿' }}</span>
              <textarea v-model="title" rows="1" maxlength="128" aria-label="文章标题" placeholder="输入文章标题" />
              <textarea v-model="summary" rows="2" maxlength="1000" aria-label="文章摘要" placeholder="添加摘要，帮助学生快速了解本章内容" />
            </header>
            <CourseRichTextEditor ref="editorRef" v-model="contentJson" :uploading="uploading" @upload-resource="chooseResource" @preview-attachment="previewAttachment = $event" @download-attachment="downloadCourseAttachment" />
          </article>
        </main>

        <aside class="article-inspector">
          <section>
            <header><Eye :size="17" /><strong>发布设置</strong></header>
            <label><span>文章状态</span><select v-model="status"><option value="DRAFT">草稿</option><option value="PUBLISHED">已发布</option></select></label>
            <dl><div><dt>字数</dt><dd>{{ wordCount }}</dd></div><div><dt>预计阅读</dt><dd>{{ Math.max(1, Math.ceil(wordCount / 400)) }} 分钟</dd></div></dl>
          </section>
          <section>
            <header><ListTree :size="17" /><strong>文章大纲</strong></header>
            <ol v-if="headings.length" class="document-outline"><li v-for="(heading, index) in headings" :key="index" :class="`level-${heading.level}`">{{ heading.text }}</li></ol>
            <p v-else class="empty-aside">使用一级至三级标题后自动生成</p>
          </section>
          <section>
            <header><Tags :size="17" /><strong>关联知识点</strong></header>
            <div v-if="chapter.knowledgePoints.length" class="knowledge-tags"><span v-for="point in chapter.knowledgePoints" :key="String(point.knowledgePointId)">{{ point.pointName }}</span></div>
            <p v-else class="empty-aside">当前章节未关联知识点</p>
          </section>
          <section class="resource-guide">
            <header><FileText :size="17" /><strong>内容资源</strong></header>
            <p>图片、视频、PPT、Word、PDF 都作为正文内容插入，可与文字自由编排。</p>
            <small>单个资源最大 2 GB</small>
          </section>
        </aside>
      </div>
      <AppDialog v-if="previewOpen" wide>
        <header class="preview-header"><div><small>发布效果预览</small><strong>{{ title || '未命名文章' }}</strong></div><button type="button" title="关闭预览" @click="previewOpen = false"><X :size="18" /></button></header>
        <article class="published-preview">
          <header><span>第 {{ chapter.chapterNo }} 章</span><h1>{{ title || '未命名文章' }}</h1><p v-if="summary">{{ summary }}</p></header>
          <CourseContentRenderer :content="contentJson" />
        </article>
      </AppDialog>
      <AppDialog v-if="previewAttachment" wide>
        <header class="preview-header"><div><small>{{ previewAttachment.mimeType }}</small><strong>{{ previewAttachment.name }}</strong></div><button type="button" title="关闭预览" @click="previewAttachment = null"><X :size="18" /></button></header>
        <iframe class="attachment-preview-frame" :src="previewAttachmentUrl" :title="previewAttachment.name" />
        <footer class="preview-actions"><AppButton @click="downloadPreviewAttachment"><Download :size="16" />下载</AppButton><AppButton variant="primary" @click="openPreviewAttachmentExternal"><ExternalLink :size="16" />新窗口打开</AppButton></footer>
      </AppDialog>
    </template>
  </div>
</template>

<style scoped>
.article-workbench { height: 100%; overflow-x: hidden; overflow-y: auto; background: var(--canvas-backdrop), var(--surface-canvas); color: var(--text-primary); }.resource-input { position: fixed; width: 1px; height: 1px; opacity: 0; pointer-events: none; }
.article-topbar { position: sticky; top: 0; z-index: 10; display: grid; grid-template-columns: minmax(12rem, 1fr) auto minmax(12rem, 1fr); align-items: center; gap: var(--space-16); min-height: 4.75rem; padding: var(--space-10) var(--space-20); border-bottom: 1px solid var(--border-default); background: var(--surface-glass); box-shadow: var(--shadow-sm); backdrop-filter: blur(18px); }.back-course { display: inline-flex; align-items: center; justify-self: start; gap: var(--space-10); min-width: 0; padding: var(--space-6) var(--space-8); border: 0; border-radius: var(--radius-9); background: transparent; color: var(--text-primary); text-align: left; transition: background-color var(--transition-base); }.back-course:hover { background: var(--surface-accent); }.back-course > span { display: grid; min-width: 0; gap: 1px; }.back-course small, .back-course strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.back-course small { color: var(--text-tertiary); }.save-state { display: inline-flex; align-items: center; gap: var(--space-6); padding: var(--space-7) var(--space-10); border: 1px solid var(--border-default); border-radius: var(--radius-16); background: var(--surface-muted); color: var(--text-tertiary); font-size: var(--text-caption); }.topbar-actions { display: flex; justify-content: flex-end; gap: var(--space-8); }
.article-layout { display: grid; grid-template-columns: minmax(0, 1fr) 19rem; max-width: 98rem; margin: 0 auto; }.article-stage { min-width: 0; padding: var(--space-28) var(--space-24) var(--space-40); }.article-paper { position: relative; overflow: hidden; max-width: 66rem; min-height: calc(100vh - 8rem); margin: 0 auto; border: 1px solid var(--border-default); border-radius: var(--radius-16); background: var(--surface-panel); box-shadow: var(--shadow-lg); }.article-paper::before { position: absolute; z-index: 5; top: 0; right: 0; left: 0; height: .25rem; background: linear-gradient(90deg, var(--primary-500), var(--accent-warm)); content: ''; }.article-metadata { display: grid; gap: var(--space-12); padding: var(--space-40) clamp(var(--space-24), 7vw, 6rem) var(--space-24); border-bottom: 1px solid var(--border-subtle); background: linear-gradient(180deg, color-mix(in srgb, var(--surface-accent) 48%, var(--surface-panel)), var(--surface-panel)); }.article-metadata > span { color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; letter-spacing: .08em; }.article-metadata textarea { width: 100%; overflow: hidden; padding: 0; border: 0; background: transparent; color: var(--text-primary); resize: none; outline: 0; }.article-metadata textarea:focus { box-shadow: none; }.article-metadata textarea:first-of-type { font-family: var(--font-family-display); font-size: var(--text-headline); font-weight: 800; line-height: 1.3; }.article-metadata textarea:last-of-type { color: var(--text-secondary); font-size: var(--text-body-lg); line-height: 1.6; }
.article-inspector { position: sticky; top: 4.75rem; align-self: start; display: grid; gap: var(--space-12); max-height: calc(100vh - 4.75rem); overflow-x: hidden; overflow-y: auto; padding: var(--space-28) var(--space-20) var(--space-32) 0; }.article-inspector section { position: relative; display: grid; overflow: hidden; gap: var(--space-12); padding: var(--space-16); border: 1px solid var(--border-default); border-radius: var(--radius-12); background: var(--surface-glass); box-shadow: var(--shadow-sm); backdrop-filter: blur(12px); }.article-inspector section:first-child { border-color: var(--border-accent); }.article-inspector header { display: flex; align-items: center; gap: var(--space-8); color: var(--text-accent); }.article-inspector header strong { color: var(--text-primary); }.article-inspector label { display: grid; gap: var(--space-6); color: var(--text-secondary); font-size: var(--text-caption); }.article-inspector select { width: 100%; min-height: 2.5rem; border: 1px solid var(--border-default); border-radius: var(--radius-8); background: var(--surface-panel); color: var(--text-primary); }.article-inspector dl { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-8); }.article-inspector dl > div { display: grid; gap: var(--space-4); padding: var(--space-10); border-radius: var(--radius-8); background: var(--surface-muted); }.article-inspector dt { color: var(--text-tertiary); font-size: var(--text-caption); }.article-inspector dd { font-family: var(--font-family-display); font-weight: 800; }.document-outline { display: grid; gap: var(--space-8); margin: 0; padding: 0; list-style: none; color: var(--text-secondary); font-size: var(--text-body-sm); }.document-outline li { overflow: hidden; padding-left: var(--space-8); border-left: 2px solid var(--border-subtle); text-overflow: ellipsis; white-space: nowrap; }.document-outline .level-2 { padding-left: var(--space-16); }.document-outline .level-3 { padding-left: var(--space-28); color: var(--text-tertiary); }.knowledge-tags { display: flex; flex-wrap: wrap; gap: var(--space-6); }.knowledge-tags span { padding: var(--space-6) var(--space-8); border: 1px solid var(--border-accent); border-radius: var(--radius-16); background: var(--surface-accent); color: var(--text-accent); font-size: var(--text-caption); }.empty-aside, .resource-guide p { margin: 0; color: var(--text-tertiary); font-size: var(--text-body-sm); line-height: 1.6; }.resource-guide small { color: var(--text-tertiary); }
.preview-header { display: flex; align-items: center; justify-content: space-between; gap: var(--space-16); margin-bottom: var(--space-20); }.preview-header > div { display: grid; gap: var(--space-4); min-width: 0; }.preview-header small { color: var(--text-accent); font-weight: 800; }.preview-header strong { overflow: hidden; font-size: var(--text-title); text-overflow: ellipsis; white-space: nowrap; }.preview-header button { display: grid; flex: 0 0 auto; place-items: center; width: 2.25rem; height: 2.25rem; border: 0; border-radius: var(--radius-8); background: var(--surface-muted); color: var(--text-secondary); }.published-preview { max-width: 45rem; margin: 0 auto; padding: var(--space-16) var(--space-24) var(--space-40); }.published-preview > header { margin-bottom: var(--space-32); padding-bottom: var(--space-24); border-bottom: 1px solid var(--border-subtle); }.published-preview > header span { color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; }.published-preview > header h1 { margin: var(--space-10) 0; font-family: var(--font-family-display); font-size: var(--text-headline); line-height: 1.3; }.published-preview > header p { color: var(--text-secondary); font-size: var(--text-body-lg); line-height: 1.7; }.attachment-preview-frame { width: 100%; height: min(65vh, 42rem); border: 1px solid var(--border-default); border-radius: var(--radius-8); background: var(--surface-muted); }.preview-actions { display: flex; justify-content: flex-end; gap: var(--space-8); margin-top: var(--space-16); }
@media (max-width: 1100px) { .article-layout { grid-template-columns: minmax(0, 1fr); }.article-inspector { position: static; grid-template-columns: repeat(2, 1fr); max-height: none; padding: 0 var(--space-20) var(--space-24); }.article-paper { max-width: 60rem; } }
@media (max-width: 720px) { .article-topbar { position: static; grid-template-columns: 1fr; padding: var(--space-10) var(--space-12); }.save-state { grid-row: 3; }.topbar-actions { justify-content: stretch; }.topbar-actions > :deep(*) { flex: 1; }.article-stage { padding: var(--space-12); }.article-paper { min-height: auto; }.article-metadata { padding: var(--space-24) var(--space-18) var(--space-16); }.article-metadata textarea:first-of-type { font-size: var(--text-title); }.article-inspector { grid-template-columns: 1fr; padding: var(--space-12); } }
</style>
