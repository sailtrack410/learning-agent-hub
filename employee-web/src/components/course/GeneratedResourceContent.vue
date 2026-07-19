<script setup lang="ts">
import { computed } from 'vue';
import { BookOpenCheck, FileSearch, ShieldCheck } from 'lucide-vue-next';
import type { GeneratedLearningResourceResponse } from '@/api';
import { renderMarkdownText } from '@/utils/markdown';

const props = defineProps<{
  resource: GeneratedLearningResourceResponse;
}>();

const renderedMarkdown = computed(() => (
  props.resource.contentFormat === 'MARKDOWN'
    ? renderMarkdownText(props.resource.content)
    : ''
));

const mindMap = computed(() => {
  if (props.resource.contentFormat !== 'MERMAID') return null;
  const items = props.resource.content
    .replace(/```mermaid|```/gi, '')
    .split('\n')
    .filter((line) => line.trim() && line.trim().toLowerCase() !== 'mindmap')
    .map((line) => ({ indent: line.match(/^\s*/)?.[0].length ?? 0, label: cleanMindMapLabel(line.trim()) }));
  if (!items.length) return null;
  const root = items[0].label;
  const childIndent = items[1]?.indent ?? 2;
  const branches: Array<{ label: string; children: string[] }> = [];
  for (const item of items.slice(1)) {
    if (item.indent <= childIndent || !branches.length) {
      branches.push({ label: item.label, children: [] });
    } else {
      branches[branches.length - 1]?.children.push(item.label);
    }
  }
  return { root, branches };
});

function cleanMindMapLabel(value: string) {
  return value
    .replace(/^root\s*/i, '')
    .replace(/^\(+|\)+$/g, '')
    .replace(/^\[+|\]+$/g, '')
    .replace(/^"|"$/g, '')
    .trim();
}
</script>

<template>
  <article class="generated-resource-content">
    <header class="resource-heading">
      <div>
        <span>{{ resource.resourceTypeName }} · {{ resource.knowledgePointName }}</span>
        <h2>{{ resource.title }}</h2>
        <p>{{ resource.summary }}</p>
      </div>
      <span class="validation-badge" :class="resource.validationStatus.toLowerCase()">
        <ShieldCheck :size="16" />{{ resource.validationStatusName }} · {{ Math.round(resource.confidence * 100) }}%
      </span>
    </header>

    <section v-if="renderedMarkdown" class="generated-markdown" v-html="renderedMarkdown" />

    <section v-else-if="mindMap" class="mind-map" aria-label="思维导图">
      <div class="mind-root"><BookOpenCheck :size="22" /><strong>{{ mindMap.root }}</strong></div>
      <div class="mind-branches">
        <article v-for="branch in mindMap.branches" :key="branch.label">
          <strong>{{ branch.label }}</strong>
          <ul v-if="branch.children.length">
            <li v-for="child in branch.children" :key="child">{{ child }}</li>
          </ul>
        </article>
      </div>
    </section>

    <aside class="validation-reason">
      <ShieldCheck :size="17" />
      <span><strong>事实校验结论</strong>{{ resource.validationReason }}</span>
    </aside>

    <section class="citation-section">
      <header><FileSearch :size="18" /><strong>知识来源</strong><span>{{ resource.citations.length }} 条引用</span></header>
      <div v-if="resource.citations.length" class="citation-list">
        <details v-for="citation in resource.citations" :key="String(citation.chunkId)">
          <summary>
            <span>{{ citation.documentTitle }}</span>
            <small>{{ citation.pageNo ? `第 ${citation.pageNo} 页 · ` : '' }}相关度 {{ Math.round(citation.relevanceScore * 100) }}%</small>
          </summary>
          <p>{{ citation.excerpt }}</p>
        </details>
      </div>
      <p v-else class="citation-empty">当前资源没有可展示的引用。</p>
    </section>
  </article>
</template>

<style scoped>
.generated-resource-content { display: grid; gap: var(--space-24); min-width: 0; max-width: 100%; color: var(--text-primary); overflow-wrap: anywhere; }
.resource-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-20); padding-bottom: var(--space-20); border-bottom: 1px solid var(--border-subtle); }
.resource-heading > div { min-width: 0; }.resource-heading span:first-child { color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; letter-spacing: .08em; }.resource-heading h2 { margin: var(--space-6) 0; font-family: var(--font-family-display); font-size: var(--text-title); }.resource-heading p { margin: 0; color: var(--text-secondary); line-height: 1.7; }
.validation-badge { display: inline-flex; align-items: center; gap: var(--space-6); flex: 0 0 auto; padding: var(--space-8) var(--space-10); border-radius: var(--radius-8); background: var(--surface-muted); color: var(--text-secondary); font-size: var(--text-caption); font-weight: 800; }.validation-badge.passed { background: var(--success-surface); color: var(--success-600); }.validation-badge.needs_review { background: var(--warning-surface); color: var(--warning-700); }.validation-badge.rejected { background: var(--danger-surface); color: var(--danger-600); }
.generated-markdown { min-width: 0; max-width: 100%; color: var(--text-primary); line-height: 1.75; overflow-wrap: anywhere; }.generated-markdown :deep(h3), .generated-markdown :deep(h4), .generated-markdown :deep(h5), .generated-markdown :deep(h6) { margin: var(--space-24) 0 var(--space-10); font-family: var(--font-family-display); }.generated-markdown :deep(p) { margin: var(--space-10) 0; }.generated-markdown :deep(ul), .generated-markdown :deep(ol) { display: grid; gap: var(--space-8); padding-left: var(--space-24); }.generated-markdown :deep(blockquote) { margin: var(--space-16) 0; padding: var(--space-12) var(--space-16); border-left: 3px solid var(--border-accent); background: var(--surface-accent); color: var(--text-secondary); }.generated-markdown :deep(pre) { box-sizing: border-box; max-width: 100%; overflow: auto; padding: var(--space-16); border-radius: var(--radius-10); background: var(--surface-inverse); color: var(--text-inverse); white-space: pre; }.generated-markdown :deep(code) { font-family: var(--font-family-mono); }.generated-markdown :deep(table) { display: block; width: 100%; max-width: 100%; overflow-x: auto; border-collapse: collapse; }.generated-markdown :deep(th), .generated-markdown :deep(td) { padding: var(--space-10); border: 1px solid var(--border-default); text-align: left; }.generated-markdown :deep(th) { background: var(--surface-muted); }
.mind-map { display: grid; gap: var(--space-24); padding: var(--space-24); border: 1px solid var(--border-default); border-radius: var(--radius-16); background: var(--surface-muted); }.mind-root { display: inline-flex; align-items: center; justify-self: center; gap: var(--space-10); padding: var(--space-14) var(--space-20); border-radius: var(--radius-12); background: var(--primary-600); color: var(--text-inverse); box-shadow: var(--shadow-sm); }.mind-branches { position: relative; display: grid; grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr)); gap: var(--space-14); }.mind-branches::before { content: ''; position: absolute; top: calc(var(--space-14) * -1); left: 50%; width: 1px; height: var(--space-14); background: var(--border-accent); }.mind-branches article { padding: var(--space-16); border: 1px solid var(--border-default); border-top: 3px solid var(--border-accent); border-radius: var(--radius-10); background: var(--surface-panel); }.mind-branches ul { display: grid; gap: var(--space-8); margin: var(--space-12) 0 0; padding-left: var(--space-18); color: var(--text-secondary); }
.validation-reason { display: flex; align-items: flex-start; gap: var(--space-10); padding: var(--space-14); border-radius: var(--radius-10); background: var(--surface-accent); color: var(--text-accent); }.validation-reason span { display: grid; gap: var(--space-4); color: var(--text-secondary); }.validation-reason strong { color: var(--text-primary); }
.citation-section { display: grid; gap: var(--space-12); padding-top: var(--space-20); border-top: 1px solid var(--border-subtle); }.citation-section > header { display: flex; align-items: center; gap: var(--space-8); }.citation-section > header span { margin-left: auto; color: var(--text-tertiary); font-size: var(--text-caption); }.citation-list { display: grid; gap: var(--space-8); }.citation-list details { border: 1px solid var(--border-default); border-radius: var(--radius-8); background: var(--surface-panel); }.citation-list summary { display: flex; align-items: center; justify-content: space-between; gap: var(--space-12); padding: var(--space-12); cursor: pointer; }.citation-list summary span { font-weight: 700; }.citation-list small { color: var(--text-tertiary); }.citation-list p { margin: 0; padding: 0 var(--space-12) var(--space-12); color: var(--text-secondary); line-height: 1.65; }.citation-empty { margin: 0; color: var(--text-tertiary); }
@media (max-width: 640px) { .resource-heading { flex-direction: column; }.validation-badge { align-self: flex-start; }.mind-map { padding: var(--space-16); }.mind-branches { grid-template-columns: 1fr; }.citation-list summary { align-items: flex-start; flex-direction: column; } }
</style>
