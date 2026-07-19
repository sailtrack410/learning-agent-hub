<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { EditorContent, useEditor } from '@tiptap/vue-3';
import { Extension, Node, mergeAttributes } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import {
  addColumnAfter, addRowAfter, addRowBefore, columnResizing, deleteColumn,
  deleteRow, deleteTable, tableEditing,
} from '@tiptap/pm/tables';
import type { Command } from '@tiptap/pm/state';
import {
  Bold, Code2, FilePlus2, Heading1, Heading2, Heading3, ImagePlus, Italic, Link2,
  List, ListOrdered, Minus, Quote, Redo2, RemoveFormatting, Strikethrough, Table2,
  Trash2, Underline, Undo2, Unlink, Video,
} from 'lucide-vue-next';
import type { EntityId } from '@/api';

const props = defineProps<{ modelValue: string; uploading?: boolean }>();
const emit = defineEmits<{
  'update:modelValue': [value: string];
  uploadResource: [kind: 'image' | 'video' | 'attachment'];
  previewAttachment: [attachment: { attachmentRefId: EntityId; name: string; mimeType: string }];
  downloadAttachment: [attachment: { attachmentRefId: EntityId; name: string }];
}>();

const linkPanelOpen = ref(false);
const linkUrl = ref('');

const CourseImage = Node.create({
  name: 'image', group: 'block', atom: true, draggable: true, selectable: true,
  addAttributes: () => ({ attachmentRefId: { default: null }, src: { default: '' }, alt: { default: '' }, width: { default: '100%' } }),
  parseHTML: () => [{ tag: 'img[data-course-resource]' }],
  renderHTML: ({ HTMLAttributes }) => ['img', mergeAttributes(HTMLAttributes, { 'data-course-resource': '', style: `width: ${HTMLAttributes.width}` })],
});

const CourseVideo = Node.create({
  name: 'video', group: 'block', atom: true,
  addAttributes: () => ({ attachmentRefId: { default: null }, src: { default: '' }, name: { default: '' }, mimeType: { default: '' } }),
  parseHTML: () => [{ tag: 'video[data-course-resource]' }],
  renderHTML: ({ HTMLAttributes }) => ['video', mergeAttributes(HTMLAttributes, { 'data-course-resource': '', controls: 'true' })],
});

const CourseAttachment = Node.create({
  name: 'attachment', group: 'block', atom: true, selectable: true,
  addAttributes: () => ({ attachmentRefId: { default: null }, src: { default: '' }, previewAvailable: { default: false }, name: { default: '' }, mimeType: { default: '' }, size: { default: 0 } }),
  parseHTML: () => [{ tag: '[data-course-attachment]' }],
  renderHTML: ({ HTMLAttributes }) => {
    if (HTMLAttributes.previewAvailable) {
      const previewUrl = `/api/employee/courses/resources/${HTMLAttributes.attachmentRefId}/preview`;
      return ['section', mergeAttributes(HTMLAttributes, { 'data-course-attachment': '', class: 'attachment-preview', contenteditable: 'false' }),
        ['header', ['span', { class: 'attachment-badge' }, '附件'], ['strong', HTMLAttributes.name || '课程附件']],
        ['iframe', { src: previewUrl, title: HTMLAttributes.name || '课程附件预览' }],
        ['footer', ['span', HTMLAttributes.mimeType || '文件'], ['button', { type: 'button', 'data-attachment-download': '' }, '下载原文件']],
      ];
    }
    return ['a', mergeAttributes(HTMLAttributes, { 'data-course-attachment': '', href: HTMLAttributes.src, role: 'button' }), [
      'span', { class: 'attachment-badge' }, '附件',
    ], ['span', { class: 'attachment-name' }, HTMLAttributes.name || '课程附件'], ['span', { class: 'attachment-action' }, '预览']];
  },
});

const Table = Node.create({
  name: 'table', group: 'block', content: 'tableRow+', isolating: true,
  parseHTML: () => [{ tag: 'table' }],
  renderHTML: ({ HTMLAttributes }) => ['div', { class: 'table-scroll' }, ['table', mergeAttributes(HTMLAttributes), ['tbody', 0]]],
  addProseMirrorPlugins: () => [columnResizing({ handleWidth: 6, lastColumnResizable: true }), tableEditing()],
});
const TableRow = Node.create({
  name: 'tableRow', content: '(tableCell | tableHeader)+',
  parseHTML: () => [{ tag: 'tr' }], renderHTML: ({ HTMLAttributes }) => ['tr', mergeAttributes(HTMLAttributes), 0],
});
const cellAttributes = {
  colspan: { default: 1, parseHTML: (element: HTMLElement) => Number(element.getAttribute('colspan') || 1) },
  rowspan: { default: 1, parseHTML: (element: HTMLElement) => Number(element.getAttribute('rowspan') || 1) },
  colwidth: {
    default: null,
    parseHTML: (element: HTMLElement) => element.getAttribute('data-colwidth')?.split(',').map(Number) ?? null,
    renderHTML: (attributes: Record<string, unknown>) => attributes.colwidth ? {
      'data-colwidth': (attributes.colwidth as number[]).join(','),
      style: `width: ${(attributes.colwidth as number[])[0]}px`,
    } : {},
  },
};
const TableCell = Node.create({
  name: 'tableCell', content: 'block+', isolating: true, addAttributes: () => cellAttributes,
  parseHTML: () => [{ tag: 'td' }], renderHTML: ({ HTMLAttributes }) => ['td', mergeAttributes(HTMLAttributes), 0],
});
const TableHeader = Node.create({
  name: 'tableHeader', content: 'block+', isolating: true, addAttributes: () => cellAttributes,
  parseHTML: () => [{ tag: 'th' }], renderHTML: ({ HTMLAttributes }) => ['th', mergeAttributes(HTMLAttributes), 0],
});
const TableSchema = Extension.create({
  name: 'tableSchema',
  extendNodeSchema: (extension) => ({
    tableRole: { table: 'table', tableRow: 'row', tableCell: 'cell', tableHeader: 'header_cell' }[extension.name],
  }),
});

const editor = useEditor({
  extensions: [StarterKit, CourseImage, CourseVideo, CourseAttachment, Table, TableRow, TableCell, TableHeader, TableSchema],
  content: parseContent(props.modelValue),
  editorProps: {
    attributes: { 'aria-label': '文章正文编辑区' },
    handleClick: (_view, _position, event) => {
      const target = event.target as HTMLElement;
      const attachment = target.closest<HTMLElement>('[data-course-attachment]');
      if (!attachment) return false;
      event.preventDefault();
      if (target.closest('[data-attachment-download]')) {
        emit('downloadAttachment', {
          attachmentRefId: attachment.getAttribute('attachmentrefid') ?? '',
          name: attachment.getAttribute('name') ?? '课程附件',
        });
        return true;
      }
      if (attachment.getAttribute('previewavailable') === 'true') return true;
      emit('previewAttachment', {
        attachmentRefId: attachment.getAttribute('attachmentrefid') ?? '',
        name: attachment.getAttribute('name') ?? '课程附件',
        mimeType: attachment.getAttribute('mimetype') ?? 'application/octet-stream',
      });
      return true;
    },
  },
  onUpdate: ({ editor: currentEditor }) => emit('update:modelValue', JSON.stringify(currentEditor.getJSON())),
});

const wordCount = computed(() => editor.value?.getText().replace(/\s/g, '').length ?? 0);
const headings = computed(() => {
  const result: Array<{ level: number; text: string }> = [];
  editor.value?.state.doc.descendants((node) => {
    if (node.type.name === 'heading') result.push({ level: Number(node.attrs.level), text: node.textContent || '未命名标题' });
  });
  return result;
});
const selectedImageWidth = computed(() => String(editor.value?.getAttributes('image').width ?? ''));

watch(() => props.modelValue, (value) => {
  if (!editor.value) return;
  const next = parseContent(value);
  if (JSON.stringify(editor.value.getJSON()) !== JSON.stringify(next)) editor.value.commands.setContent(next);
});

function insertImage(attachmentRefId: EntityId, src: string, alt: string) {
  editor.value?.chain().focus().insertContent({ type: 'image', attrs: { attachmentRefId, src, alt, width: '100%' } }).run();
}
function insertVideo(attachmentRefId: EntityId, src: string, name: string, mimeType: string) {
  editor.value?.chain().focus().insertContent({ type: 'video', attrs: { attachmentRefId, src, name, mimeType } }).run();
}
function insertAttachment(attachmentRefId: EntityId, src: string, previewAvailable: boolean, name: string, mimeType: string, size: number) {
  editor.value?.chain().focus().insertContent({ type: 'attachment', attrs: { attachmentRefId, src, previewAvailable, name, mimeType, size } }).run();
}
function setImageWidth(width: string) {
  editor.value?.chain().focus().updateAttributes('image', { width }).run();
}
function insertTable() {
  const cell = (type: 'tableHeader' | 'tableCell') => ({ type, content: [{ type: 'paragraph' }] });
  editor.value?.chain().focus().insertContent({
    type: 'table',
    content: [
      { type: 'tableRow', content: [cell('tableHeader'), cell('tableHeader'), cell('tableHeader')] },
      { type: 'tableRow', content: [cell('tableCell'), cell('tableCell'), cell('tableCell')] },
      { type: 'tableRow', content: [cell('tableCell'), cell('tableCell'), cell('tableCell')] },
    ],
  }).run();
}
function runTableCommand(command: Command) {
  if (!editor.value) return;
  command(editor.value.state, editor.value.view.dispatch);
  editor.value.commands.focus();
}
function openLinkPanel() {
  linkUrl.value = String(editor.value?.getAttributes('link').href ?? '');
  linkPanelOpen.value = true;
}
function applyLink() {
  const href = normalizeLink(linkUrl.value);
  if (!href || !editor.value) return;
  editor.value.chain().focus().setMark('link', { href }).run();
  linkPanelOpen.value = false;
}
function removeLink() {
  editor.value?.chain().focus().unsetMark('link').run();
  linkPanelOpen.value = false;
}

defineExpose({ insertImage, insertVideo, insertAttachment, wordCount, headings });
onBeforeUnmount(() => editor.value?.destroy());

function parseContent(value: string) {
  if (!value) return { type: 'doc', content: [{ type: 'paragraph' }] };
  try { return JSON.parse(value); } catch { return { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: value }] }] }; }
}
function normalizeLink(value: string) {
  const url = value.trim();
  if (!url) return '';
  if (/^(https?:|mailto:)/i.test(url)) return url;
  return `https://${url}`;
}
</script>

<template>
  <div class="article-editor">
    <div v-if="editor" class="toolbar-viewport">
      <div class="article-toolbar" role="toolbar" aria-label="文章编辑工具栏">
        <div class="toolbar-group">
          <button type="button" :class="{ active: editor.isActive('heading', { level: 1 }) }" title="一级标题" @click="editor.chain().focus().toggleHeading({ level: 1 }).run()"><Heading1 :size="18" /></button>
          <button type="button" :class="{ active: editor.isActive('heading', { level: 2 }) }" title="二级标题" @click="editor.chain().focus().toggleHeading({ level: 2 }).run()"><Heading2 :size="18" /></button>
          <button type="button" :class="{ active: editor.isActive('heading', { level: 3 }) }" title="三级标题" @click="editor.chain().focus().toggleHeading({ level: 3 }).run()"><Heading3 :size="18" /></button>
        </div>
        <div class="toolbar-group">
          <button type="button" :class="{ active: editor.isActive('bold') }" title="加粗" @click="editor.chain().focus().toggleBold().run()"><Bold :size="17" /></button>
          <button type="button" :class="{ active: editor.isActive('italic') }" title="斜体" @click="editor.chain().focus().toggleItalic().run()"><Italic :size="17" /></button>
          <button type="button" :class="{ active: editor.isActive('underline') }" title="下划线" @click="editor.chain().focus().toggleMark('underline').run()"><Underline :size="17" /></button>
          <button type="button" :class="{ active: editor.isActive('strike') }" title="删除线" @click="editor.chain().focus().toggleStrike().run()"><Strikethrough :size="17" /></button>
          <button type="button" :class="{ active: editor.isActive('code') }" title="行内代码" @click="editor.chain().focus().toggleCode().run()"><Code2 :size="17" /></button>
        </div>
        <div class="toolbar-group">
          <button type="button" :class="{ active: editor.isActive('bulletList') }" title="无序列表" @click="editor.chain().focus().toggleBulletList().run()"><List :size="18" /></button>
          <button type="button" :class="{ active: editor.isActive('orderedList') }" title="有序列表" @click="editor.chain().focus().toggleOrderedList().run()"><ListOrdered :size="18" /></button>
          <button type="button" :class="{ active: editor.isActive('blockquote') }" title="引用" @click="editor.chain().focus().toggleBlockquote().run()"><Quote :size="17" /></button>
          <button type="button" :class="{ active: editor.isActive('codeBlock') }" title="代码块" @click="editor.chain().focus().toggleCodeBlock().run()"><Code2 :size="17" /></button>
          <button type="button" title="分割线" @click="editor.chain().focus().setHorizontalRule().run()"><Minus :size="18" /></button>
        </div>
        <div class="toolbar-group resource-tools">
          <button type="button" :disabled="uploading" title="插入图片" @click="emit('uploadResource', 'image')"><ImagePlus :size="18" /><span>图片</span></button>
          <button type="button" :disabled="uploading" title="插入视频" @click="emit('uploadResource', 'video')"><Video :size="18" /><span>视频</span></button>
          <button type="button" :disabled="uploading" title="插入附件" @click="emit('uploadResource', 'attachment')"><FilePlus2 :size="18" /><span>附件</span></button>
          <button type="button" title="插入 3×3 表格" @click="insertTable"><Table2 :size="18" /><span>表格</span></button>
        </div>
        <div class="toolbar-group toolbar-tail">
          <button type="button" :class="{ active: editor.isActive('link') }" title="插入链接" @click="openLinkPanel"><Link2 :size="17" /></button>
          <button type="button" title="清除格式" @click="editor.chain().focus().unsetAllMarks().clearNodes().run()"><RemoveFormatting :size="17" /></button>
          <button type="button" :disabled="!editor.can().undo()" title="撤销" @click="editor.chain().focus().undo().run()"><Undo2 :size="17" /></button>
          <button type="button" :disabled="!editor.can().redo()" title="重做" @click="editor.chain().focus().redo().run()"><Redo2 :size="17" /></button>
        </div>
      </div>
    </div>
    <div v-if="editor?.isActive('image')" class="image-tools" aria-label="图片尺寸">
      <span>图片宽度</span>
      <button v-for="width in ['25%', '50%', '75%', '100%']" :key="width" type="button" :class="{ active: selectedImageWidth === width }" @click="setImageWidth(width)">{{ width }}</button>
    </div>
    <div v-if="editor?.isActive('table')" class="table-tools" aria-label="表格操作">
      <span>表格</span>
      <button type="button" @click="runTableCommand(addRowBefore)">上方加行</button>
      <button type="button" @click="runTableCommand(addRowAfter)">下方加行</button>
      <button type="button" @click="runTableCommand(deleteRow)">删除行</button>
      <button type="button" @click="runTableCommand(addColumnAfter)">右侧加列</button>
      <button type="button" @click="runTableCommand(deleteColumn)">删除列</button>
      <button type="button" class="danger" @click="runTableCommand(deleteTable)"><Trash2 :size="15" />删除表格</button>
    </div>
    <form v-if="linkPanelOpen" class="link-panel" @submit.prevent="applyLink">
      <input v-model="linkUrl" autofocus placeholder="输入链接地址" />
      <button type="submit">应用</button>
      <button type="button" title="移除链接" @click="removeLink"><Unlink :size="16" /></button>
    </form>
    <EditorContent :editor="editor" class="article-editor-content" />
    <footer class="editor-status"><span>{{ wordCount }} 字</span><span>表格列宽可拖拽调整</span></footer>
  </div>
</template>

<style scoped>
.article-editor { position: relative; min-width: 0; background: var(--surface-panel); }
.toolbar-viewport { position: sticky; top: 0; z-index: 4; border-bottom: 1px solid var(--border-default); background: color-mix(in srgb, var(--surface-panel) 94%, transparent); backdrop-filter: blur(12px); }
.article-toolbar { display: flex; align-items: center; justify-content: center; gap: var(--space-4); width: 100%; min-height: 3.25rem; padding: var(--space-6) var(--space-8); }
.toolbar-group { display: flex; align-items: center; gap: 2px; padding-right: var(--space-4); border-right: 1px solid var(--border-subtle); }.toolbar-tail { margin-left: 0; border-right: 0; }.article-toolbar button, .image-tools button { display: inline-flex; align-items: center; justify-content: center; gap: var(--space-4); width: 2rem; min-width: 2rem; height: 2rem; padding: 0; border: 0; border-radius: var(--radius-6); background: transparent; color: var(--text-secondary); white-space: nowrap; }.article-toolbar button:hover, .article-toolbar button.active, .image-tools button:hover, .image-tools button.active { background: var(--surface-accent); color: var(--text-accent); }.article-toolbar button:disabled { opacity: .4; }.resource-tools span { display: none; }
.image-tools { display: flex; align-items: center; justify-content: center; gap: var(--space-6); padding: var(--space-8); border-bottom: 1px solid var(--border-subtle); background: var(--surface-muted); color: var(--text-tertiary); font-size: var(--text-caption); }.image-tools button { height: 1.9rem; font-size: var(--text-caption); }
.table-tools { display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: var(--space-6); padding: var(--space-8); border-bottom: 1px solid var(--border-subtle); background: var(--surface-muted); color: var(--text-tertiary); font-size: var(--text-caption); }.table-tools button { display: inline-flex; align-items: center; gap: var(--space-4); height: 1.9rem; padding: 0 var(--space-8); border: 1px solid var(--border-default); border-radius: var(--radius-6); background: var(--surface-panel); color: var(--text-secondary); }.table-tools button:hover { border-color: var(--border-accent); color: var(--text-accent); }.table-tools button.danger:hover { color: var(--text-danger); }
.link-panel { position: absolute; z-index: 6; top: 4rem; right: var(--space-12); display: flex; gap: var(--space-6); padding: var(--space-8); border: 1px solid var(--border-default); border-radius: var(--radius-10); background: var(--surface-panel); box-shadow: var(--shadow-md); }.link-panel input { width: 18rem; border-color: var(--border-default); background: var(--surface-panel); color: var(--text-primary); }.link-panel button { background: var(--surface-accent); color: var(--text-accent); }
.article-editor-content :deep(.ProseMirror) { min-height: 48rem; padding: var(--space-40) clamp(var(--space-24), 8vw, 6.5rem) var(--space-56); color: var(--text-primary); font-size: var(--text-body-lg); line-height: 1.9; outline: none; }.article-editor-content :deep(.ProseMirror > *:first-child) { margin-top: 0; }.article-editor-content :deep(h1) { margin: 1.7em 0 .6em; font-size: var(--text-headline); line-height: 1.3; }.article-editor-content :deep(h2) { margin: 1.6em 0 .55em; font-size: var(--text-title); line-height: 1.4; }.article-editor-content :deep(h3) { margin: 1.45em 0 .5em; font-size: var(--text-subtitle); }.article-editor-content :deep(p) { margin: .85em 0; }.article-editor-content :deep(blockquote) { margin: 1.5em 0; padding: var(--space-8) var(--space-20); border-left: 3px solid var(--border-accent); background: var(--surface-muted); color: var(--text-secondary); }.article-editor-content :deep(pre) { overflow: auto; padding: var(--space-20); border-radius: var(--radius-10); background: var(--surface-rail); color: var(--text-inverse); }.article-editor-content :deep(code:not(pre code)) { padding: var(--space-4) var(--space-6); border-radius: var(--radius-4); background: var(--surface-muted); color: var(--text-accent); }.article-editor-content :deep(a) { color: var(--text-accent); text-decoration: underline; }.article-editor-content :deep(img), .article-editor-content :deep(video) { display: block; max-width: 100%; max-height: 36rem; margin: var(--space-24) auto; border-radius: var(--radius-12); object-fit: contain; background: var(--surface-muted); }.article-editor-content :deep(video) { width: 100%; }.article-editor-content :deep(a[data-course-attachment]) { display: flex; align-items: center; gap: var(--space-12); margin: var(--space-20) 0; padding: var(--space-14) var(--space-16); border: 1px solid var(--border-default); border-radius: var(--radius-10); background: var(--surface-muted); color: var(--text-primary); text-decoration: none; cursor: pointer; }.article-editor-content :deep(.attachment-badge) { padding: var(--space-6) var(--space-8); border-radius: var(--radius-6); background: var(--surface-accent); color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; }.article-editor-content :deep(.attachment-name) { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.article-editor-content :deep(.attachment-action) { color: var(--text-accent); font-size: var(--text-caption); font-weight: 700; }.article-editor-content :deep(.table-scroll) { overflow-x: auto; margin: var(--space-24) 0; }.article-editor-content :deep(table) { width: 100%; table-layout: fixed; border-collapse: collapse; }.article-editor-content :deep(th), .article-editor-content :deep(td) { position: relative; min-width: 5rem; padding: var(--space-10) var(--space-12); border: 1px solid var(--border-default); vertical-align: top; }.article-editor-content :deep(th) { background: var(--surface-muted); font-weight: 800; }.article-editor-content :deep(th p), .article-editor-content :deep(td p) { margin: 0; }.article-editor-content :deep(.column-resize-handle) { position: absolute; top: 0; right: -3px; bottom: 0; width: 6px; background: var(--border-accent); pointer-events: none; }.article-editor-content :deep(.resize-cursor) { cursor: col-resize; }.article-editor-content :deep(.ProseMirror-selectednode) { outline: 2px solid var(--border-accent); outline-offset: 3px; }
.article-editor-content :deep(.attachment-preview) { overflow: hidden; margin: var(--space-24) 0; border: 1px solid var(--border-default); border-radius: var(--radius-10); background: var(--surface-panel); }.article-editor-content :deep(.attachment-preview header), .article-editor-content :deep(.attachment-preview footer) { display: flex; align-items: center; gap: var(--space-10); padding: var(--space-12) var(--space-16); }.article-editor-content :deep(.attachment-preview header strong) { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.article-editor-content :deep(.attachment-preview iframe) { display: block; width: 100%; height: 34rem; border: 0; border-top: 1px solid var(--border-subtle); border-bottom: 1px solid var(--border-subtle); background: var(--surface-muted); }.article-editor-content :deep(.attachment-preview footer) { justify-content: space-between; color: var(--text-tertiary); font-size: var(--text-caption); }.article-editor-content :deep(.attachment-preview footer span) { min-width: 0; overflow-wrap: anywhere; }.article-editor-content :deep(.attachment-preview footer button) { flex: 0 0 auto; padding: var(--space-6) var(--space-10); border: 1px solid var(--border-default); border-radius: var(--radius-6); background: var(--surface-panel); color: var(--text-accent); white-space: nowrap; }
.editor-status { display: flex; justify-content: space-between; padding: var(--space-8) var(--space-16); border-top: 1px solid var(--border-subtle); color: var(--text-tertiary); font-size: var(--text-caption); }
@media (max-width: 760px) { .toolbar-viewport { position: static; }.article-toolbar { display: grid; grid-template-columns: repeat(7, 2rem); gap: var(--space-4); }.toolbar-group { display: contents; }.article-editor-content :deep(.ProseMirror) { min-height: 34rem; padding: var(--space-24) var(--space-18) var(--space-40); }.link-panel { right: var(--space-6); left: var(--space-6); }.link-panel input { min-width: 0; width: 100%; }.image-tools { flex-wrap: wrap; }.table-tools { justify-content: flex-start; } }
</style>
