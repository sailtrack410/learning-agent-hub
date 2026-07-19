<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { CheckCircle2, Pencil, RefreshCw } from 'lucide-vue-next';
import { employeeApi, type StudentProfileDimensionResponse, type StudentProfileResponse } from '@/api';
import AppButton from '@/components/ui/AppButton.vue';
import AppErrorState from '@/components/ui/AppErrorState.vue';
import AppLoadingState from '@/components/ui/AppLoadingState.vue';
import studentProfileHero from '@/assets/student-profile-hero-25d.webp';
import profileConversationIcon from '@/assets/education/icons/profile-conversation.webp';
import profileDimensionsIcon from '@/assets/education/icons/profile-dimensions.webp';
import profileCompletionIcon from '@/assets/education/icons/profile-completion.webp';
import profileConfidenceIcon from '@/assets/education/icons/profile-confidence.webp';
import resultVersionIcon from '@/assets/education/icons/result-version.webp';

const profile = ref<StudentProfileResponse | null>(null);
const loading = ref(true);
const submitting = ref(false);
const error = ref('');
const answer = ref('');
const correcting = ref<StudentProfileDimensionResponse | null>(null);
const correction = ref('');

const progress = computed(() => {
  if (!profile.value?.totalDimensions) return 0;
  return Math.round(profile.value.completedDimensions / profile.value.totalDimensions * 100);
});

const averageConfidence = computed(() => {
  const dimensions = profile.value?.dimensions ?? [];
  if (!dimensions.length) return 0;
  return Math.round(dimensions.reduce((total, dimension) => total + dimension.confidence, 0) / dimensions.length * 100);
});

const latestUpdatedAt = computed(() => {
  const timestamps = (profile.value?.dimensions ?? []).map((dimension) => new Date(dimension.updatedAt).getTime()).filter(Number.isFinite);
  if (!timestamps.length) return '等待画像结论';
  return updatedLabel(new Date(Math.max(...timestamps)).toISOString());
});

const resultSummaries = computed(() => [
  { label: '画像完成度', value: `${progress.value}%`, detail: `${profile.value?.completedDimensions ?? 0} / ${profile.value?.totalDimensions ?? 0} 个维度`, image: profileCompletionIcon },
  { label: '平均置信度', value: `${averageConfidence.value}%`, detail: '综合当前画像结论', image: profileConfidenceIcon },
  { label: '结果版本', value: `V${profile.value?.versionNo ?? 0}`, detail: latestUpdatedAt.value, image: resultVersionIcon },
]);

async function loadProfile() {
  loading.value = true;
  error.value = '';
  try {
    profile.value = await employeeApi.studentProfile.current();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '学习画像加载失败';
  } finally {
    loading.value = false;
  }
}

async function submitAnswer() {
  const message = answer.value.trim();
  if (message.length < 2 || submitting.value) return;
  submitting.value = true;
  error.value = '';
  try {
    profile.value = await employeeApi.studentProfile.answer(message);
    answer.value = '';
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '画像回答提交失败';
  } finally {
    submitting.value = false;
  }
}

function openCorrection(dimension: StudentProfileDimensionResponse) {
  correcting.value = dimension;
  correction.value = dimension.conclusion;
}

async function submitCorrection() {
  const dimension = correcting.value;
  const conclusion = correction.value.trim();
  if (!dimension || !conclusion || submitting.value) return;
  submitting.value = true;
  error.value = '';
  try {
    profile.value = await employeeApi.studentProfile.correct(dimension.dimensionCode, conclusion);
    correcting.value = null;
    correction.value = '';
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '画像纠正失败';
  } finally {
    submitting.value = false;
  }
}

function confidenceLabel(value: number) {
  return `${Math.round(value * 100)}%`;
}

function updatedLabel(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
}

onMounted(loadProfile);
</script>

<template>
  <main class="profile-page">
    <AppLoadingState v-if="loading" title="正在加载学习画像" />
    <AppErrorState v-else-if="error && !profile" title="学习画像加载失败" :description="error" action-label="重新加载" @retry="loadProfile" />

    <template v-else-if="profile">
      <header class="profile-hero">
        <div class="profile-hero-copy">
          <span class="eyebrow">动态学习画像</span>
          <h1>让学习导师真正了解你</h1>
          <p>通过自然语言逐步完善 8 个维度，画像结论会保留依据和版本记录。</p>
        </div>
        <div class="profile-hero-visual">
          <img :src="studentProfileHero" alt="" aria-hidden="true" />
          <div class="progress-card" aria-label="画像完成进度">
            <strong>{{ progress }}%</strong>
            <span>{{ profile.completedDimensions }} / {{ profile.totalDimensions }} 个维度</span>
            <div class="progress-track"><i :style="{ width: `${progress}%` }" /></div>
            <small>当前版本 V{{ profile.versionNo }}</small>
          </div>
        </div>
      </header>

      <section class="profile-result-strip" aria-label="画像结果摘要">
        <article v-for="item in resultSummaries" :key="item.label" class="profile-result-card">
          <img :src="item.image" alt="" aria-hidden="true" />
          <div><small>{{ item.label }}</small><strong>{{ item.value }}</strong><span>{{ item.detail }}</span></div>
        </article>
      </section>

      <p v-if="error" class="inline-error">{{ error }}</p>

      <section class="profile-layout">
        <article class="dialogue-panel">
          <header class="section-head">
            <div>
              <span class="section-icon"><img :src="profileConversationIcon" alt="" aria-hidden="true" /></span>
              <div><h2>画像对话</h2><p>按你的真实情况回答，没有标准答案。</p></div>
            </div>
          </header>

          <div class="message-list" aria-live="polite">
            <div v-for="message in profile.messages" :key="String(message.messageId)" class="profile-message" :class="message.role.toLowerCase()">
              <span>{{ message.role === 'ASSISTANT' ? '学习导师' : '我' }}</span>
              <p>{{ message.content }}</p>
            </div>
          </div>

          <form v-if="!profile.complete" class="answer-box" @submit.prevent="submitAnswer">
            <label for="profile-answer">继续回答</label>
            <textarea id="profile-answer" v-model="answer" maxlength="1000" rows="4" :placeholder="profile.nextQuestion ?? '请输入回答'" />
            <div><small>{{ answer.trim().length }} / 1000</small><AppButton variant="primary" type="submit" :disabled="answer.trim().length < 2 || submitting">{{ submitting ? '提交中' : '提交回答' }}</AppButton></div>
          </form>
          <div v-else class="complete-banner"><CheckCircle2 :size="20" /><span><strong>基础画像已完成</strong><small>后续练习和反馈会继续更新画像。</small></span></div>
        </article>

        <section class="dimension-panel">
          <header class="section-head">
            <div><span class="section-icon"><img :src="profileDimensionsIcon" alt="" aria-hidden="true" /></span><div><h2>画像维度</h2><p>结论、置信度和依据均可追溯。</p></div></div>
            <AppButton variant="ghost" size="sm" aria-label="刷新画像" @click="loadProfile"><RefreshCw :size="16" /></AppButton>
          </header>

          <div class="dimension-grid">
            <article
              v-for="dimension in profile.dimensions"
              :key="dimension.dimensionCode"
              class="dimension-card"
            >
              <header><div><small>画像结果</small><strong>{{ dimension.dimensionName }}</strong></div><span>{{ confidenceLabel(dimension.confidence) }}</span></header>
              <div class="confidence-track" aria-hidden="true"><i :style="{ width: confidenceLabel(dimension.confidence) }" /></div>
              <p class="dimension-conclusion">{{ dimension.conclusion }}</p>
              <dl><div><dt>依据</dt><dd>{{ dimension.evidenceSummary }}</dd></div><div><dt>更新时间</dt><dd>{{ updatedLabel(dimension.updatedAt) }}</dd></div></dl>
              <AppButton variant="ghost" size="sm" @click="openCorrection(dimension)"><Pencil :size="14" />纠正结论</AppButton>
            </article>
            <article v-for="index in profile.totalDimensions - profile.completedDimensions" :key="`pending-${index}`" class="dimension-card pending">
              <span>等待对话补充</span><p>完成左侧问题后生成结论与依据。</p>
            </article>
          </div>
        </section>
      </section>

      <div v-if="correcting" class="correction-mask" @click.self="correcting = null">
        <form class="correction-dialog" @submit.prevent="submitCorrection">
          <span class="eyebrow">纠正画像</span><h2>{{ correcting.dimensionName }}</h2>
          <textarea v-model="correction" maxlength="1000" rows="6" />
          <div><AppButton variant="secondary" type="button" @click="correcting = null">取消</AppButton><AppButton variant="primary" type="submit" :disabled="!correction.trim() || submitting">保存纠正</AppButton></div>
        </form>
      </div>
    </template>
  </main>
</template>

<style scoped>
.profile-page { box-sizing: border-box; width: 100%; height: 100%; min-height: 0; padding: var(--space-28); overflow-x: hidden; overflow-y: auto; overscroll-behavior: contain; background-color: var(--surface-canvas); background-image: var(--canvas-backdrop); color: var(--text-primary); }
.profile-hero { position: relative; display: grid; grid-template-columns: minmax(0, 1fr) minmax(20rem, 30rem); min-height: 18rem; gap: var(--space-20); overflow: hidden; padding: var(--space-32); border: 1px solid var(--border-accent); border-radius: var(--radius-20); background: var(--hero-gradient); box-shadow: var(--shadow-md); }
.profile-hero::after { position: absolute; right: -5rem; bottom: -7rem; width: 22rem; height: 22rem; border: 1px solid color-mix(in srgb, var(--primary-300) 36%, transparent); border-radius: 50%; content: ''; }
.profile-hero-copy { position: relative; z-index: 2; display: grid; align-content: center; justify-items: start; max-width: 45rem; }
.profile-hero h1 { margin: var(--space-8) 0; font-family: var(--font-family-display); font-size: clamp(var(--font-size-26), 3vw, var(--font-size-40)); line-height: 1.18; }
.profile-hero p, .section-head p { margin: 0; color: var(--text-secondary); line-height: 1.7; }
.eyebrow { color: var(--primary-700); font-size: var(--text-caption-md); font-weight: 700; letter-spacing: .08em; }
.profile-hero-visual { position: relative; z-index: 1; min-width: 0; min-height: 15rem; }
.profile-hero-visual > img { position: absolute; top: -3.5rem; right: -4rem; width: 34rem; max-width: none; filter: drop-shadow(0 1.2rem 1.6rem color-mix(in srgb, var(--primary-800) 18%, transparent)); animation: profile-hero-float 6.4s ease-in-out infinite; }
.progress-card { position: absolute; z-index: 2; bottom: var(--space-6); left: 0; min-width: 13.75rem; padding: var(--space-16); border: 1px solid color-mix(in srgb, var(--primary-300) 45%, transparent); border-radius: var(--radius-12); background: var(--surface-glass); box-shadow: var(--shadow-md); backdrop-filter: blur(.75rem); }
.progress-card strong { display: block; color: var(--text-accent); font-family: var(--font-family-display); font-size: var(--font-size-32); }
.progress-card span, .progress-card small { color: var(--text-secondary); }
.progress-track { height: 8px; margin: var(--space-12) 0; overflow: hidden; border-radius: var(--radius-8); background: var(--primary-100); }
.progress-track i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--primary-500), var(--success-500)); transition: width 240ms ease; }
.profile-result-strip { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-12); margin-top: var(--space-16); }
.profile-result-card { position: relative; isolation: isolate; display: grid; grid-template-columns: 5.2rem minmax(0, 1fr); align-items: center; min-width: 0; min-height: 7.25rem; overflow: hidden; padding: var(--space-14) var(--space-16); border: 1px solid var(--border-default); border-radius: var(--radius-14, var(--radius-16)); background: linear-gradient(145deg, var(--surface-panel), color-mix(in srgb, var(--surface-accent) 68%, var(--surface-panel))); box-shadow: var(--shadow-sm); }
.profile-result-card::after { position: absolute; z-index: -1; right: -3rem; bottom: -4rem; width: 10rem; height: 10rem; border: 1px solid color-mix(in srgb, var(--border-accent) 34%, transparent); border-radius: 50%; content: ''; }
.profile-result-card img { width: 5rem; height: 5rem; object-fit: contain; filter: drop-shadow(0 .5rem .8rem color-mix(in srgb, var(--primary-800) 14%, transparent)); }
.profile-result-card div { display: grid; min-width: 0; gap: var(--space-3); }
.profile-result-card small, .profile-result-card span { overflow: hidden; color: var(--text-tertiary); font-size: var(--text-caption); text-overflow: ellipsis; white-space: nowrap; }
.profile-result-card strong { color: var(--text-primary); font-family: var(--font-family-display); font-size: var(--font-size-24); }
.profile-layout { display: grid; grid-template-columns: minmax(320px, .8fr) minmax(480px, 1.2fr); align-items: start; gap: var(--space-20); margin-top: var(--space-20); }
.dialogue-panel, .dimension-panel { min-width: 0; padding: var(--space-20); border: 1px solid var(--border-default); border-radius: var(--radius-16); background: var(--surface-panel); box-shadow: var(--shadow-sm); }
.section-head, .section-head > div { display: flex; align-items: center; justify-content: space-between; gap: var(--space-12); }
.section-head h2 { margin: 0 0 var(--space-4); font-size: var(--font-size-22); }
.section-icon { display: grid; width: 3.25rem; height: 3.25rem; flex: 0 0 auto; place-items: center; }
.section-icon img { width: 100%; height: 100%; object-fit: contain; filter: drop-shadow(0 .35rem .55rem color-mix(in srgb, var(--primary-800) 12%, transparent)); }
.message-list { display: flex; flex-direction: column; gap: var(--space-12); max-height: 420px; margin: var(--space-20) 0; overflow: auto; }
.profile-message { max-width: 88%; }
.profile-message.user { align-self: flex-end; }
.profile-message span { display: block; margin-bottom: var(--space-4); color: var(--text-tertiary); font-size: var(--text-caption-md); }
.profile-message p { margin: 0; padding: var(--space-12) var(--space-14); border: 1px solid var(--border-subtle); border-radius: var(--radius-12); background: var(--surface-panel-muted); line-height: 1.65; }
.profile-message.user p { border-color: var(--primary-600); background: linear-gradient(135deg, var(--primary-500), var(--primary-600)); color: var(--text-inverse); box-shadow: var(--shadow-sm); }
.answer-box { padding-top: var(--space-16); border-top: 1px solid var(--border-subtle); }
.answer-box label { display: block; margin-bottom: var(--space-8); font-weight: 700; }
.answer-box textarea, .correction-dialog textarea { box-sizing: border-box; width: 100%; padding: var(--space-12); resize: vertical; border: 1px solid var(--border-default); border-radius: var(--radius-10); background: var(--surface-panel); color: var(--text-primary); font: inherit; line-height: 1.6; }
.answer-box > div, .correction-dialog > div { display: flex; justify-content: space-between; align-items: center; gap: var(--space-12); margin-top: var(--space-10); }
.answer-box small { color: var(--text-tertiary); }
.complete-banner { display: flex; gap: var(--space-10); padding: var(--space-14); border-radius: var(--radius-10); background: var(--success-50); color: var(--success-800); }
.complete-banner span { display: flex; flex-direction: column; }
.dimension-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-12); margin-top: var(--space-20); }
.dimension-card { position: relative; isolation: isolate; overflow: hidden; padding: var(--space-18); border: 1px solid var(--border-default); border-radius: var(--radius-16); background: linear-gradient(155deg, var(--surface-panel) 34%, color-mix(in srgb, var(--surface-accent) 72%, var(--surface-panel))); box-shadow: var(--shadow-sm); transition: border-color var(--transition-base), box-shadow var(--transition-base), transform var(--transition-base); }
.dimension-card::after { position: absolute; z-index: -1; top: -3rem; right: -3rem; width: 8rem; height: 8rem; border: 1px solid color-mix(in srgb, var(--border-accent) 34%, transparent); border-radius: 50%; content: ''; pointer-events: none; }
.dimension-card:hover { border-color: var(--border-accent); box-shadow: var(--shadow-md); transform: translateY(-.12rem); }
.dimension-card header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-8); }
.dimension-card header > div { display: grid; gap: var(--space-4); }
.dimension-card header small { color: var(--text-accent); font-size: var(--text-caption); font-weight: 800; letter-spacing: .08em; }
.dimension-card header span { color: var(--primary-700); font-weight: 700; }
.confidence-track { height: .32rem; margin-top: var(--space-10); overflow: hidden; border-radius: 999px; background: var(--surface-panel-strong); }
.confidence-track i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--primary-500), var(--success-500)); }
.dimension-conclusion { min-height: 48px; margin-bottom: var(--space-16); color: var(--text-primary); font-size: var(--text-body-lg); font-weight: 650; line-height: 1.65; }
.dimension-card dl { display: grid; gap: var(--space-8); margin: var(--space-12) 0; }
.dimension-card dl div { padding-top: var(--space-8); border-top: 1px solid var(--border-subtle); }
.dimension-card dt { color: var(--text-tertiary); font-size: var(--text-caption-md); }
.dimension-card dd { margin: var(--space-3) 0 0; color: var(--text-secondary); font-size: var(--text-body-sm); line-height: 1.5; }
.dimension-card.pending { display: grid; place-content: center; min-height: 170px; text-align: center; color: var(--text-tertiary); }
.inline-error { padding: var(--space-10) var(--space-14); border-radius: var(--radius-8); background: var(--surface-danger); color: var(--text-danger); }
.correction-mask { position: fixed; inset: 0; z-index: 80; display: grid; place-items: center; padding: var(--space-20); background: var(--overlay-backdrop); backdrop-filter: blur(.45rem); }
.correction-dialog { width: min(520px, 100%); padding: var(--space-24); border-radius: var(--radius-16); background: var(--surface-panel); box-shadow: var(--shadow-lg); }
.correction-dialog h2 { margin: var(--space-8) 0 var(--space-16); }
@keyframes profile-hero-float { 0%, 100% { transform: translate3d(0, 0, 0) rotate(.5deg); } 50% { transform: translate3d(0, -.55rem, 0) rotate(-.4deg); } }
@media (max-width: 1000px) { .profile-layout { grid-template-columns: 1fr; } .profile-hero { grid-template-columns: 1fr; }.profile-hero-visual { min-height: 17rem; } .profile-result-strip { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .profile-page { padding: var(--space-12); padding-bottom: 7rem; } .profile-hero { min-height: 0; padding: var(--space-20); } .profile-hero h1 { font-size: var(--font-size-26); } .profile-hero-visual { min-height: 14rem; }.profile-hero-visual > img { top: -1.5rem; right: -4rem; width: 27rem; }.progress-card { bottom: 0; min-width: 11rem; padding: var(--space-12); } .profile-result-card { grid-template-columns: 4.5rem minmax(0, 1fr); min-height: 6.25rem; }.profile-result-card img { width: 4.25rem; height: 4.25rem; } .dialogue-panel, .dimension-panel { padding: var(--space-14); } .dimension-grid { grid-template-columns: 1fr; } }
@media (prefers-reduced-motion: reduce) { .profile-hero-visual > img { animation: none; } }
</style>
