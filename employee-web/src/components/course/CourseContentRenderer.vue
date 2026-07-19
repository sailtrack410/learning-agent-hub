<script setup lang="ts">
import type { JSONContent } from '@tiptap/core';
import { computed, h, ref, type VNode } from 'vue';
import { Download, ExternalLink, X } from 'lucide-vue-next';
import { employeeApi, type EntityId } from '@/api';
import AppButton from '@/components/ui/AppButton.vue';
import AppDialog from '@/components/ui/AppDialog.vue';

const props = defineProps<{ content?: string | null }>();
const previewAttachment = ref<{ attachmentRefId: EntityId; name: string; mimeType: string } | null>(null);
const previewUrl = computed(() => previewAttachment.value ? employeeApi.courses.resourceUrl(previewAttachment.value.attachmentRefId) : '');
const documentNode = computed<JSONContent>(() => {
  try {
    return props.content ? JSON.parse(props.content) : { type: 'doc', content: [] };
  } catch {
    return { type: 'doc', content: [] };
  }
});
const RenderDocument = () => renderNode(documentNode.value, 'doc');

function renderNode(node: JSONContent, key: number | string): VNode | string | null {
  if (node.type === 'text') {
    let child: VNode | string = node.text ?? '';
    for (const mark of node.marks ?? []) {
      if (mark.type === 'bold') child = h('strong', { key }, child);
      if (mark.type === 'italic') child = h('em', { key }, child);
      if (mark.type === 'underline') child = h('u', { key }, child);
      if (mark.type === 'strike') child = h('s', { key }, child);
      if (mark.type === 'code') child = h('code', { key }, child);
      if (mark.type === 'link') child = h('a', { key, href: safeLink(String(mark.attrs?.href ?? '')), target: '_blank', rel: 'noopener noreferrer' }, child);
    }
    return child;
  }
  const children = (node.content ?? []).map((child, index) => renderNode(child, `${key}-${index}`)).filter(Boolean) as Array<VNode | string>;
  const tags: Record<string, string> = { doc: 'div', paragraph: 'p', heading: `h${node.attrs?.level ?? 2}`, bulletList: 'ul', orderedList: 'ol', listItem: 'li', blockquote: 'blockquote', codeBlock: 'pre', hardBreak: 'br', horizontalRule: 'hr' };
  const attachmentRefId = node.attrs?.attachmentRefId as EntityId | undefined;
  const src = attachmentRefId == null ? '' : employeeApi.courses.resourceUrl(attachmentRefId);
  if (node.type === 'image' && src) return h('img', { key, src, alt: String(node.attrs?.alt ?? ''), style: { width: String(node.attrs?.width ?? '100%') } });
  if (node.type === 'video' && src) return h('figure', { key, class: 'course-video' }, [
    h('video', { src, controls: true, preload: 'metadata' }),
    h('figcaption', String(node.attrs?.name ?? '教学视频')),
  ]);
  const embeddedPreviewUrl = node.attrs?.previewAvailable && attachmentRefId != null
    ? employeeApi.courses.resourcePreviewUrl(attachmentRefId)
    : '';
  if (node.type === 'attachment' && src && embeddedPreviewUrl) return h('section', { key, class: 'course-document-preview' }, [
    h('header', [
      h('span', { class: 'attachment-icon' }, '附件'),
      h('strong', String(node.attrs?.name ?? '课程资料')),
    ]),
    h('iframe', { src: embeddedPreviewUrl, title: String(node.attrs?.name ?? '课程资料预览') }),
    h('footer', [
      h('span', `${String(node.attrs?.mimeType ?? '文件')} · ${formatSize(Number(node.attrs?.size ?? 0))}`),
      h('button', { type: 'button', onClick: () => downloadResource(attachmentRefId!, String(node.attrs?.name ?? '课程资料')) }, [
        h(Download, { size: 15 }), '下载原文件',
      ]),
    ]),
  ]);
  if (node.type === 'attachment' && src) return h('button', { key, type: 'button', class: 'course-attachment', onClick: () => previewAttachment.value = {
    attachmentRefId: attachmentRefId!,
    name: String(node.attrs?.name ?? '课程资料'),
    mimeType: String(node.attrs?.mimeType ?? 'application/octet-stream'),
  } }, [
    h('span', { class: 'attachment-icon' }, '附件'),
    h('span', { class: 'attachment-copy' }, [
      h('strong', String(node.attrs?.name ?? '课程资料')),
      h('small', `${String(node.attrs?.mimeType ?? '文件')} · ${formatSize(Number(node.attrs?.size ?? 0))}`),
    ]),
    h('b', '打开'),
  ]);
  if (node.type === 'table') return h('div', { key, class: 'course-table-scroll' }, [h('table', [h('tbody', children)])]);
  if (node.type === 'tableRow') return h('tr', { key }, children);
  if (node.type === 'tableHeader' || node.type === 'tableCell') {
    const colwidth = Array.isArray(node.attrs?.colwidth) ? Number(node.attrs?.colwidth[0]) : 0;
    return h(node.type === 'tableHeader' ? 'th' : 'td', {
      key,
      colspan: Number(node.attrs?.colspan ?? 1),
      rowspan: Number(node.attrs?.rowspan ?? 1),
      style: colwidth ? { width: `${colwidth}px` } : undefined,
    }, children);
  }
  return h(tags[node.type ?? ''] ?? 'div', { key }, children);
}

function safeLink(value: string) {
  return /^(https?:|mailto:)/i.test(value) ? value : '#';
}

function formatSize(bytes: number) {
  if (!bytes) return '课程资源';
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

async function downloadAttachment() {
  if (!previewAttachment.value) return;
  await downloadResource(previewAttachment.value.attachmentRefId, previewAttachment.value.name);
}

async function downloadResource(attachmentRefId: EntityId, name: string) {
  const response = await fetch(employeeApi.courses.resourceUrl(attachmentRefId));
  if (!response.ok) return;
  const objectUrl = URL.createObjectURL(await response.blob());
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = name;
  anchor.click();
  URL.revokeObjectURL(objectUrl);
}

function openAttachmentExternal() {
  window.open(previewUrl.value, '_blank', 'noopener');
}
</script>

<template>
  <div class="course-rich-content"><RenderDocument /></div>
  <AppDialog v-if="previewAttachment" wide>
    <header class="attachment-preview-header">
      <div><strong>{{ previewAttachment.name }}</strong><small>{{ previewAttachment.mimeType }}</small></div>
      <button type="button" title="关闭预览" @click="previewAttachment = null"><X :size="18" /></button>
    </header>
    <iframe class="attachment-preview-frame" :src="previewUrl" :title="previewAttachment.name" />
    <footer class="attachment-preview-actions">
      <AppButton @click="downloadAttachment"><Download :size="16" />下载</AppButton>
      <AppButton variant="primary" @click="openAttachmentExternal"><ExternalLink :size="16" />新窗口打开</AppButton>
    </footer>
  </AppDialog>
</template>

<style scoped>
.course-rich-content { color: var(--text-primary); font-size: var(--text-body-lg); line-height: 1.85; }.course-rich-content :deep(h1) { margin: 1.6em 0 .6em; font-size: var(--text-headline); }.course-rich-content :deep(h2) { margin: 1.5em 0 .55em; font-size: var(--text-title); }.course-rich-content :deep(h3) { margin: 1.4em 0 .5em; font-size: var(--text-subtitle); }.course-rich-content :deep(p) { margin: .85em 0; }.course-rich-content :deep(a) { color: var(--text-accent); }.course-rich-content :deep(blockquote) { margin: 1.5em 0; padding: var(--space-10) var(--space-18); border-left: 3px solid var(--border-accent); background: var(--surface-muted); color: var(--text-secondary); }.course-rich-content :deep(pre) { overflow: auto; padding: var(--space-20); border-radius: var(--radius-8); background: var(--surface-rail); color: var(--text-inverse); }.course-rich-content :deep(code:not(pre code)) { padding: var(--space-4) var(--space-6); border-radius: var(--radius-4); background: var(--surface-muted); color: var(--text-accent); }.course-rich-content :deep(img), .course-rich-content :deep(.course-video video) { display: block; max-width: 100%; max-height: 38rem; margin: var(--space-24) auto; border-radius: var(--radius-12); object-fit: contain; background: var(--surface-muted); }.course-rich-content :deep(.course-video video) { width: 100%; }.course-rich-content :deep(.course-video) { margin: var(--space-24) 0; }.course-rich-content :deep(.course-video figcaption) { margin-top: var(--space-8); color: var(--text-tertiary); font-size: var(--text-caption); text-align: center; }.course-rich-content :deep(.course-attachment) { display: flex; width: 100%; align-items: center; gap: var(--space-14); margin: var(--space-20) 0; padding: var(--space-16); border: 1px solid var(--border-default); border-radius: var(--radius-10); background: var(--surface-muted); color: var(--text-primary); text-align: left; cursor: pointer; }.course-rich-content :deep(.course-attachment:hover) { border-color: var(--border-accent); }.course-rich-content :deep(.attachment-icon) { padding: var(--space-8); border-radius: var(--radius-6); background: var(--surface-accent); color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; }.course-rich-content :deep(.attachment-copy) { display: grid; flex: 1; gap: var(--space-4); }.course-rich-content :deep(.attachment-copy small) { color: var(--text-tertiary); }.course-rich-content :deep(.course-attachment b) { color: var(--text-accent); }.course-rich-content :deep(.course-table-scroll) { overflow-x: auto; margin: var(--space-24) 0; }.course-rich-content :deep(table) { width: 100%; table-layout: fixed; border-collapse: collapse; }.course-rich-content :deep(th), .course-rich-content :deep(td) { min-width: 5rem; padding: var(--space-10) var(--space-12); border: 1px solid var(--border-default); vertical-align: top; }.course-rich-content :deep(th) { background: var(--surface-muted); font-weight: 800; }.course-rich-content :deep(th p), .course-rich-content :deep(td p) { margin: 0; }
.course-rich-content :deep(.course-document-preview) { overflow: hidden; margin: var(--space-24) 0; border: 1px solid var(--border-default); border-radius: var(--radius-10); background: var(--surface-panel); }.course-rich-content :deep(.course-document-preview header), .course-rich-content :deep(.course-document-preview footer) { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-12) var(--space-16); }.course-rich-content :deep(.course-document-preview header strong) { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.course-rich-content :deep(.course-document-preview iframe) { display: block; width: 100%; height: 38rem; border: 0; border-top: 1px solid var(--border-subtle); border-bottom: 1px solid var(--border-subtle); background: var(--surface-muted); }.course-rich-content :deep(.course-document-preview footer) { justify-content: space-between; color: var(--text-tertiary); font-size: var(--text-caption); }.course-rich-content :deep(.course-document-preview footer button) { display: inline-flex; align-items: center; gap: var(--space-6); padding: var(--space-6) var(--space-10); border: 1px solid var(--border-default); border-radius: var(--radius-6); background: var(--surface-panel); color: var(--text-accent); }
.attachment-preview-header { display: flex; align-items: center; justify-content: space-between; gap: var(--space-16); margin-bottom: var(--space-16); }.attachment-preview-header > div { display: grid; gap: var(--space-4); min-width: 0; }.attachment-preview-header strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.attachment-preview-header small { color: var(--text-tertiary); }.attachment-preview-header button { display: grid; flex: 0 0 auto; place-items: center; width: 2.25rem; height: 2.25rem; border: 0; border-radius: var(--radius-8); background: var(--surface-muted); color: var(--text-secondary); }.attachment-preview-frame { width: 100%; height: min(65vh, 42rem); border: 1px solid var(--border-default); border-radius: var(--radius-8); background: var(--surface-muted); }.attachment-preview-actions { display: flex; justify-content: flex-end; gap: var(--space-8); margin-top: var(--space-16); }
</style>
