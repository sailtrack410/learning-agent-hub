<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Plus, RefreshCcw, Save, ShieldAlert, ShieldCheck, UserRoundCheck, X } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { ContentSafetyPolicyResponse } from '@/api/types';
import { errorMessage } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminHeroBanner from '@/components/AdminHeroBanner.vue';
import AdminSkeleton from '@/components/AdminSkeleton.vue';
import contentSafetyHero from '@/assets/education/admin-content-safety-hero.webp';

const toast = useToast();
const loading = ref(true);
const saving = ref(false);
const loadError = ref('');
const markerDraft = ref('');
const form = reactive({
  promptInjectionDetectionEnabled: true,
  reviewConfidenceThreshold: 0.75,
  blockedMarkers: [] as string[],
  versionNo: 1,
});

const thresholdPercent = computed(() => Math.round(form.reviewConfidenceThreshold * 100));
const thresholdValid = computed(() => {
  const rawValue = form.reviewConfidenceThreshold as number | string;
  if (String(rawValue).trim() === '') return false;
  const value = Number(rawValue);
  return Number.isFinite(value) && value >= 0 && value <= 1;
});
const canSave = computed(() => (
  !saving.value
  && form.versionNo > 0
  && thresholdValid.value
  && (!form.promptInjectionDetectionEnabled || form.blockedMarkers.length > 0)
));

function applyPolicy(policy: ContentSafetyPolicyResponse) {
  form.promptInjectionDetectionEnabled = policy.promptInjectionDetectionEnabled;
  form.reviewConfidenceThreshold = Number(policy.reviewConfidenceThreshold);
  form.blockedMarkers = [...policy.blockedMarkers];
  form.versionNo = policy.versionNo;
}

async function load() {
  loading.value = true;
  loadError.value = '';
  try {
    applyPolicy(await adminApi.contentSafety.get());
  } catch (error) {
    loadError.value = errorMessage(error, '内容安全策略加载失败');
  } finally {
    loading.value = false;
  }
}

function addMarker() {
  const marker = markerDraft.value.trim();
  if (!marker) return;
  if (form.blockedMarkers.some((item) => item.toLocaleLowerCase() === marker.toLocaleLowerCase())) {
    toast.error('这条危险指令标记已经存在');
    return;
  }
  if (form.blockedMarkers.length >= 50) {
    toast.error('危险指令标记不能超过50条');
    return;
  }
  form.blockedMarkers.push(marker);
  markerDraft.value = '';
}

function removeMarker(index: number) {
  form.blockedMarkers.splice(index, 1);
}

async function save() {
  if (!canSave.value) return;
  saving.value = true;
  try {
    const policy = await adminApi.contentSafety.update({
      promptInjectionDetectionEnabled: form.promptInjectionDetectionEnabled,
      reviewConfidenceThreshold: Number(form.reviewConfidenceThreshold),
      blockedMarkers: form.blockedMarkers,
      expectedVersion: form.versionNo,
    });
    applyPolicy(policy);
    toast.success('内容安全策略已保存');
  } catch (error) {
    toast.error(errorMessage(error, '内容安全策略保存失败'));
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <AdminSkeleton v-if="loading" :lines="6" />
  <main v-else-if="loadError" class="page-grid">
    <AdminPanel :span="12" title="内容安全策略" subtitle="策略加载失败，未对现有规则进行任何修改">
      <div class="load-error">
        <ShieldAlert :size="22" />
        <p>{{ loadError }}</p>
        <button class="secondary-button" type="button" @click="load">
          <RefreshCcw :size="16" />重新加载
        </button>
      </div>
    </AdminPanel>
  </main>
  <main v-else class="page-grid safety-page">
    <AdminHeroBanner
      eyebrow="内容安全治理"
      title="在生成之前拦截风险，在发布之前保留复核"
      description="统一控制学生补充诉求拦截与生成资源的教师复核边界。"
      :image="contentSafetyHero"
    >
      <template #metrics>
        <div class="hero-metric"><strong>{{ thresholdPercent }}%</strong><span>复核阈值</span></div>
        <div class="hero-metric"><strong>{{ form.blockedMarkers.length }}</strong><span>危险标记</span></div>
        <div class="hero-metric"><strong>V{{ form.versionNo }}</strong><span>策略版本</span></div>
      </template>
      <template #actions>
        <span :class="['status-pill', { disabled: !form.promptInjectionDetectionEnabled }]">
          {{ form.promptInjectionDetectionEnabled ? '注入拦截已启用' : '注入拦截已停用' }}
        </span>
      </template>
    </AdminHeroBanner>

    <AdminPanel :span="7" title="规则配置" subtitle="保存后立即应用于新创建的个性化学习资源任务">
      <div class="policy-form">
        <label class="switch-row">
          <span>
            <strong>提示词注入与越权指令拦截</strong>
            <small>阻止学生通过补充诉求改变系统规则、调用工具或执行危险数据库指令</small>
          </span>
          <input v-model="form.promptInjectionDetectionEnabled" type="checkbox" />
        </label>

        <label class="threshold-field">
          <span>
            <strong>教师复核置信度阈值</strong>
            <small>允许范围 0 到 1，建议保持在 0.70 到 0.90 之间</small>
          </span>
          <div>
            <input v-model.number="form.reviewConfidenceThreshold" type="number" min="0" max="1" step="0.01" />
            <b>{{ thresholdPercent }}%</b>
          </div>
        </label>

        <section class="marker-editor" aria-labelledby="marker-title">
          <div class="marker-head">
            <div>
              <strong id="marker-title">危险指令标记</strong>
              <small>支持中英文短语，不区分大小写，最多50条</small>
            </div>
            <span>{{ form.blockedMarkers.length }}/50</span>
          </div>
          <div class="marker-input">
            <input
              v-model="markerDraft"
              maxlength="100"
              placeholder="例如：忽略之前、调用工具"
              @keydown.enter.prevent="addMarker"
            />
            <button class="secondary-button" type="button" :disabled="!markerDraft.trim()" @click="addMarker">
              <Plus :size="16" />添加
            </button>
          </div>
          <div v-if="form.blockedMarkers.length" class="marker-list">
            <span v-for="(marker, index) in form.blockedMarkers" :key="`${marker}-${index}`">
              {{ marker }}
              <button type="button" :aria-label="`移除 ${marker}`" @click="removeMarker(index)"><X :size="14" /></button>
            </span>
          </div>
          <p v-else class="empty-marker">暂无危险指令标记</p>
        </section>

        <footer class="form-actions">
          <p v-if="form.promptInjectionDetectionEnabled && !form.blockedMarkers.length" class="form-error">
            启用注入拦截时至少保留一条危险指令标记
          </p>
          <button class="primary-button" type="button" :disabled="!canSave" @click="save">
            <Save :size="16" />{{ saving ? '保存中' : '保存策略' }}
          </button>
        </footer>
      </div>
    </AdminPanel>

    <AdminPanel :span="5" title="生效规则" subtitle="策略只影响新任务，不改写历史资源和审核记录">
      <div class="rule-flow">
        <article>
          <ShieldAlert :size="20" />
          <div><strong>提交前拦截</strong><p>学生补充诉求命中危险标记时，任务不会创建，也不会发送给外部模型。</p></div>
        </article>
        <article>
          <UserRoundCheck :size="20" />
          <div><strong>低置信度转复核</strong><p>事实校验置信度低于 {{ thresholdPercent }}% 时，资源自动标记为需要教师复核。</p></div>
        </article>
        <article>
          <ShieldCheck :size="20" />
          <div><strong>版本与审计</strong><p>并发修改会被拒绝，每次成功保存都会记录管理员、版本和规则摘要。</p></div>
        </article>
      </div>
    </AdminPanel>
  </main>
</template>

<style scoped>
.safety-page { align-items: start; }
.status-pill.disabled { background: var(--error-bg); color: var(--red-dark); }
.policy-form { display: grid; gap: var(--space-xl); }
.switch-row, .threshold-field { display: flex; align-items: center; justify-content: space-between; gap: var(--space-xl); padding-bottom: var(--space-lg); border-bottom: 1px solid var(--line); }
.switch-row > span, .threshold-field > span, .marker-head > div { display: grid; gap: var(--space-xs); }
.switch-row small, .threshold-field small, .marker-head small { color: var(--muted); font-size: var(--text-xs); line-height: 1.5; }
.switch-row input { width: 20px; min-height: 20px; accent-color: var(--green); }
.threshold-field > div { display: flex; align-items: center; gap: var(--space-md); width: 190px; }
.threshold-field input { text-align: right; }
.threshold-field b { min-width: 42px; color: var(--green-dark); }
.marker-editor { display: grid; gap: var(--space-md); }
.marker-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-base); }
.marker-head > span { color: var(--muted); font-size: var(--text-xs); }
.marker-input { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--space-sm); }
.marker-list { display: flex; flex-wrap: wrap; gap: var(--space-sm); }
.marker-list > span { display: inline-flex; align-items: center; gap: var(--space-xs); max-width: 100%; min-height: 32px; padding: 0 var(--space-sm) 0 var(--space-md); border-radius: var(--radius-pill); background: var(--pill-bg); color: var(--green-dark); font-size: var(--text-sm); word-break: break-all; }
.marker-list button { display: inline-grid; width: 24px; height: 24px; padding: 0; border: 0; border-radius: var(--radius-circle); background: transparent; color: inherit; place-items: center; }
.marker-list button:hover { background: var(--panel); }
.empty-marker { margin: 0; padding: var(--space-lg); border: 1px dashed var(--line); border-radius: var(--radius-md); color: var(--muted); text-align: center; }
.form-actions { display: flex; align-items: center; justify-content: flex-end; gap: var(--space-base); padding-top: var(--space-lg); border-top: 1px solid var(--line); }
.form-actions .form-error { margin-right: auto; }
.rule-flow { display: grid; gap: var(--space-md); }
.rule-flow article { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: var(--space-md); padding: var(--space-base); border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--table-head-bg); }
.rule-flow svg { color: var(--green-dark); }
.rule-flow p { margin: var(--space-xs) 0 0; color: var(--muted); font-size: var(--text-sm); line-height: 1.6; }
.load-error { display: flex; align-items: center; gap: var(--space-md); color: var(--red-dark); }
.load-error p { flex: 1; margin: 0; }
@media (max-width: 900px) {
  .span-7, .span-5 { grid-column: span 12; }
}
@media (max-width: 640px) {
  .switch-row, .threshold-field { align-items: flex-start; flex-direction: column; }
  .threshold-field > div { width: 100%; }
  .marker-input { grid-template-columns: 1fr; }
  .marker-input button { width: 100%; }
  .form-actions { align-items: stretch; flex-direction: column; }
  .form-actions .primary-button { width: 100%; }
  .load-error { align-items: stretch; flex-direction: column; }
}
</style>
