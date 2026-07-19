<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { CheckCircle2, MessageCircle, ChevronLeft, Sparkles, X } from 'lucide-vue-next';
import type { AgentResponse } from '@/api';
import { employeeApi } from '@/api';
import { useAgentsSectionContext } from '../employee-shell-context';
import { agentTypeLabel, humanizeText, identityModeLabel, statusText } from '@/utils/employee-format';
import AppAvatar from '../../components/ui/AppAvatar.vue';
import AppButton from '../../components/ui/AppButton.vue';
import AppEmptyState from '../../components/ui/AppEmptyState.vue';
import AppTag from '../../components/ui/AppTag.vue';
import agentTutorHero from '@/assets/agent-tutor-hero-3d.webp';

const {
  shell,
  showToast,
  navigateMessagesConversation,
  startAgentConversationFromSearch,
} = useAgentsSectionContext();

const agents = ref<AgentResponse[]>([]);
const agentsLoading = ref(false);
const agentsError = ref('');
const agentAvatarUrls = ref<Record<string, string>>({});
const profileAgent = ref<AgentResponse | null>(null);

const keyword = computed(() => shell.search.agents.trim());
const filteredAgents = computed(() => {
  const kw = keyword.value.toLowerCase();
  if (!kw) return agents.value;
  return agents.value.filter((item) => (
    `${item.agentName} ${item.agentCode} ${item.agentType}`.toLowerCase().includes(kw)
  ));
});
// 已添加的 AI 助手 agentCode 集合（基于现有会话）
const addedAgentCodes = computed(() => new Set(
  shell.conversations
    .filter((item) => item.kind === 'agent')
    .map((item) => item.tag)
    .filter(Boolean),
));
// agentCode → conversationId 映射，用于点击已添加卡片跳转
const addedAgentConversationIds = computed(() => {
  const map = new Map<string, string>();
  shell.conversations.forEach((item) => {
    if (item.kind === 'agent' && item.tag) {
      map.set(item.tag, item.id);
    }
  });
  return map;
});
const addedAgents = computed(() => filteredAgents.value.filter((a) => addedAgentCodes.value.has(a.agentCode)));
const availableAgents = computed(() => filteredAgents.value.filter((a) => !addedAgentCodes.value.has(a.agentCode)));

async function loadAgents() {
  if (agentsLoading.value) return;
  agentsLoading.value = true;
  agentsError.value = '';
  try {
    agents.value = await employeeApi.agents.list({});
  } catch (error) {
    agentsError.value = error instanceof Error ? error.message : '学习导师加载失败';
    showToast(agentsError.value);
  } finally {
    agentsLoading.value = false;
  }
}

async function refreshAgentAvatarUrls(rows: AgentResponse[]) {
  const entries = await Promise.all(rows.map(async (agent) => {
    if (!agent.avatarAttachmentRefId) return [String(agent.agentId), ''] as const;
    try {
      const ticket = await employeeApi.files.createDownloadTicket(agent.avatarAttachmentRefId, { downloadType: 'INLINE' });
      const url = employeeApi.files.downloadUrl(ticket.ticketNo);
      return [String(agent.agentId), url] as const;
    } catch {
      return [String(agent.agentId), ''] as const;
    }
  }));
  agentAvatarUrls.value = Object.fromEntries(entries);
}

function openAgentProfile(agent: AgentResponse) {
  profileAgent.value = agent;
}

function closeAgentProfile() {
  profileAgent.value = null;
}

async function startConversation(agent: AgentResponse) {
  profileAgent.value = null;
  await startAgentConversationFromSearch(agent);
}

function openExistingConversation(agent: AgentResponse) {
  const conversationId = addedAgentConversationIds.value.get(agent.agentCode);
  profileAgent.value = null;
  if (conversationId) {
    navigateMessagesConversation(conversationId);
  }
}

function backToList() {
  shell.setMobileView('list');
}

function agentVisibleType(agent: AgentResponse) {
  return agent.agentTypeName || agentTypeLabel(agent.agentType);
}

onMounted(() => {
  void loadAgents();
});

watch(agents, (rows) => {
  void refreshAgentAvatarUrls(rows);
}, { immediate: true });
</script>

<template>
<div class="workspace generic-workspace">
  <header class="workspace-head">
    <div class="workspace-title-group">
      <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
        <ChevronLeft :size="22" />
      </button>
      <div class="workspace-head-text">
        <h2 class="workspace-title">学习导师</h2>
      </div>
    </div>
  </header>

  <section class="content-stack agents-directory">
    <section class="agents-hero">
      <div class="agents-hero-copy">
        <span><Sparkles :size="16" />多智能体学习支持</span>
        <h1>选择你的学习导师</h1>
        <p>课程答疑、学习规划和资源推荐由不同导师按职责协同完成。</p>
        <div class="agents-hero-stats">
          <span><strong>{{ addedAgents.length }}</strong><small>已添加</small></span>
          <span><strong>{{ availableAgents.length }}</strong><small>可添加</small></span>
        </div>
      </div>
      <div class="agents-hero-visual" aria-hidden="true"><img :src="agentTutorHero" alt="" /></div>
    </section>

    <p v-if="agentsLoading" class="form-hint">加载中…</p>
    <p v-else-if="agentsError" class="dialog-error">{{ agentsError }}</p>
    <AppEmptyState
      v-else-if="!filteredAgents.length"
      :title="keyword ? '未匹配到学习导师' : '暂无可用学习导师'"
      icon="workbench"
    />

    <article v-if="addedAgents.length" class="agents-group">
      <header class="agents-group-head">
        <h3>已添加</h3>
        <p>{{ addedAgents.length }} 个</p>
      </header>
      <div class="agents-card-grid">
        <button
          v-for="agent in addedAgents"
          :key="`added-${agent.agentId}`"
          type="button"
          class="agent-card"
          @click="openAgentProfile(agent)"
        >
          <AppAvatar
            :label="humanizeText(agent.agentName)"
            :image="agentAvatarUrls[String(agent.agentId)]"
            kind="agent"
            tone="success"
            size="lg"
            status="online"
          />
          <div class="agent-card-copy">
            <strong>{{ humanizeText(agent.agentName) }}</strong>
            <small v-if="agentVisibleType(agent) !== humanizeText(agent.agentName)">{{ agentVisibleType(agent) }}</small>
          </div>
          <span class="agent-card-state added"><CheckCircle2 :size="15" />已添加</span>
        </button>
      </div>
    </article>

    <article v-if="availableAgents.length" class="agents-group">
      <header class="agents-group-head">
        <h3>可添加</h3>
        <p>{{ availableAgents.length }} 个</p>
      </header>
      <div class="agents-card-grid">
        <button
          v-for="agent in availableAgents"
          :key="`avail-${agent.agentId}`"
          type="button"
          class="agent-card"
          @click="openAgentProfile(agent)"
        >
          <AppAvatar
            :label="humanizeText(agent.agentName)"
            :image="agentAvatarUrls[String(agent.agentId)]"
            kind="agent"
            tone="neutral"
            size="lg"
          />
          <div class="agent-card-copy">
            <strong>{{ humanizeText(agent.agentName) }}</strong>
            <small v-if="agentVisibleType(agent) !== humanizeText(agent.agentName)">{{ agentVisibleType(agent) }}</small>
          </div>
          <span class="agent-card-state"><MessageCircle :size="15" />查看</span>
        </button>
      </div>
    </article>
  </section>

  <Teleport to="body">
    <div v-if="profileAgent" class="agent-profile-overlay" @click="closeAgentProfile" />
    <section v-if="profileAgent" class="agent-profile-dialog">
      <button class="agent-profile-close" type="button" aria-label="关闭" @click="closeAgentProfile">
        <X :size="17" />
      </button>
      <header class="agent-profile-head">
        <AppAvatar
          :label="humanizeText(profileAgent.agentName)"
          :image="agentAvatarUrls[String(profileAgent.agentId)]"
          kind="agent"
          tone="success"
          size="xl"
          status="online"
        />
        <h3>{{ humanizeText(profileAgent.agentName) }}</h3>
        <div class="agent-profile-badges">
          <AppTag tone="neutral">{{ agentVisibleType(profileAgent) }}</AppTag>
          <AppTag v-if="profileAgent.publishedVersionLabel" tone="primary">
            v{{ profileAgent.publishedVersionLabel }}
          </AppTag>
        </div>
      </header>
      <dl class="agent-profile-detail">
        <div>
          <dt>身份模式</dt>
          <dd>{{ profileAgent.identityModeName || identityModeLabel(profileAgent.identityMode) }}</dd>
        </div>
        <div>
          <dt>状态</dt>
          <dd>{{ profileAgent.statusName || statusText(profileAgent.status) }}</dd>
        </div>
      </dl>
      <div class="agent-profile-actions">
        <AppButton
          v-if="addedAgentCodes.has(profileAgent.agentCode)"
          variant="primary"
          size="sm"
          @click="openExistingConversation(profileAgent)"
        >
          <MessageCircle :size="15" />
          继续对话
        </AppButton>
        <AppButton
          v-else
          variant="primary"
          size="sm"
          @click="startConversation(profileAgent)"
        >
          <MessageCircle :size="15" />
          开始对话
        </AppButton>
        <AppButton variant="secondary" size="sm" @click="closeAgentProfile">
          关闭
        </AppButton>
      </div>
    </section>
  </Teleport>
</div>
</template>

<style scoped>
.agents-directory { overflow-y: auto; background-color: var(--surface-canvas); background-image: var(--canvas-backdrop); }
.agents-hero { position: relative; display: grid; grid-template-columns: minmax(0, 1fr) minmax(18rem, 28rem); min-height: 16rem; gap: var(--space-20); overflow: hidden; padding: var(--space-28); border: 1px solid var(--border-accent); border-radius: var(--radius-20); background: var(--hero-gradient); box-shadow: var(--shadow-md); }
.agents-hero::after { position: absolute; right: -5rem; bottom: -8rem; width: 21rem; height: 21rem; border: 1px solid color-mix(in srgb, var(--primary-300) 36%, transparent); border-radius: 50%; content: ''; }
.agents-hero-copy { position: relative; z-index: 2; display: grid; max-width: 42rem; align-content: center; justify-items: start; }
.agents-hero-copy > span { display: inline-flex; align-items: center; gap: var(--space-6); color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; letter-spacing: .08em; }
.agents-hero h1 { margin: var(--space-8) 0; font-family: var(--font-family-display); font-size: clamp(var(--text-title), 3vw, var(--text-headline)); line-height: 1.2; }
.agents-hero p { max-width: 36rem; margin: 0; color: var(--text-secondary); line-height: 1.7; }
.agents-hero-stats { display: flex; gap: var(--space-10); margin-top: var(--space-20); }
.agents-hero-stats > span { display: grid; min-width: 6.5rem; gap: var(--space-2); padding: var(--space-10) var(--space-12); border: 1px solid var(--border-default); border-radius: var(--radius-12); background: var(--surface-glass); box-shadow: var(--shadow-sm); backdrop-filter: blur(.5rem); }
.agents-hero-stats strong { color: var(--text-accent); font-family: var(--font-family-display); font-size: var(--text-subtitle); }
.agents-hero-stats small { color: var(--text-tertiary); }
.agents-hero-visual { position: relative; z-index: 1; min-width: 0; min-height: 13rem; }
.agents-hero-visual img { position: absolute; top: -3.5rem; right: -4rem; width: 32rem; max-width: none; filter: drop-shadow(0 1.1rem 1.5rem color-mix(in srgb, var(--primary-800) 18%, transparent)); animation: agent-hero-float 6.2s ease-in-out infinite; }
.agents-group { padding: var(--space-20); border: 1px solid var(--border-default); border-radius: var(--radius-16); background: linear-gradient(155deg, var(--surface-panel) 78%, color-mix(in srgb, var(--surface-accent) 48%, var(--surface-panel))); box-shadow: var(--shadow-sm); }
.agents-group-head h3 { font-family: var(--font-family-display); font-size: var(--text-subtitle); }
.agents-group-head p { padding: var(--space-4) var(--space-8); border-radius: 999px; background: var(--surface-muted); }
.agent-card { min-height: 6rem; border-color: var(--border-default); border-radius: var(--radius-12); box-shadow: var(--shadow-sm); transition: border-color var(--transition-base), background-color var(--transition-base), box-shadow var(--transition-base), transform var(--transition-base); }
.agent-card:hover { border-color: var(--border-accent); background: var(--surface-panel); box-shadow: var(--shadow-md); transform: translateY(-.12rem); }
.agent-card-state { display: inline-flex; align-items: center; gap: var(--space-4); padding: var(--space-5) var(--space-8); border-radius: 999px; background: var(--surface-accent); color: var(--text-accent); font-size: var(--text-caption); font-weight: 700; }
.agent-card-state.added { background: var(--success-surface); color: var(--success-700); }
.agent-profile-overlay { background: var(--overlay-backdrop); backdrop-filter: blur(.45rem); }
.agent-profile-dialog { border-radius: var(--radius-20); box-shadow: var(--shadow-float); }
@keyframes agent-hero-float { 0%, 100% { transform: translate3d(0, 0, 0) rotate(.45deg); } 50% { transform: translate3d(0, -.5rem, 0) rotate(-.35deg); } }
@media (max-width: 900px) { .agents-hero { grid-template-columns: 1fr; }.agents-hero-visual { min-height: 15rem; }.agents-hero-visual img { top: -2rem; right: -3rem; width: 29rem; } }
@media (max-width: 640px) { .agents-directory { padding: var(--space-12); padding-bottom: 7rem; }.agents-hero { min-height: 0; padding: var(--space-20); }.agents-hero-visual { min-height: 12rem; }.agents-hero-visual img { top: -1rem; right: -4rem; width: 25rem; }.agents-hero-stats { width: 100%; }.agents-hero-stats > span { flex: 1; min-width: 0; }.agents-group { padding: var(--space-16); }.agents-card-grid { grid-template-columns: 1fr; }.agent-card-state { align-self: center; } }
@media (prefers-reduced-motion: reduce) { .agents-hero-visual img { animation: none; } }
</style>
