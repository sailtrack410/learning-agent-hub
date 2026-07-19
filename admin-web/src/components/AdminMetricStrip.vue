<script setup lang="ts">
interface MetricCard {
  label: string;
  value: number | string;
  icon?: string;
  hint?: string;
  tone?: 'primary' | 'success' | 'warning' | 'danger';
}

defineProps<{
  cards: MetricCard[];
}>();
</script>

<template>
  <div class="metric-strip">
    <div v-for="card in cards" :key="card.label" :class="['metric-card', `tone-${card.tone ?? 'primary'}`, { 'has-icon': card.icon }]">
      <img v-if="card.icon" :src="card.icon" alt="" aria-hidden="true" />
      <div class="metric-copy">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <small v-if="card.hint">{{ card.hint }}</small>
      </div>
    </div>
  </div>
</template>

<style scoped>
.metric-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 160px), 1fr));
  gap: var(--space-base);
  width: 100%;
}

.metric-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: center;
  align-content: center;
  gap: var(--space-md);
  min-height: 104px;
  padding: var(--space-base);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: linear-gradient(145deg, var(--panel-strong), var(--metric-card-tint));
  transition: border-color 200ms ease, box-shadow 200ms ease, transform 200ms ease;
}

.metric-card.has-icon {
  grid-template-columns: auto minmax(0, 1fr);
}

.metric-card:hover {
  border-color: var(--green-light);
  box-shadow: var(--shadow);
  transform: translateY(-2px);
}

.metric-card img {
  width: 60px;
  height: 60px;
  object-fit: contain;
  filter: drop-shadow(0 8px 12px var(--metric-icon-shadow));
}

.metric-copy {
  display: grid;
  gap: var(--space-xs);
}

.metric-card span {
  color: var(--muted);
  font-size: var(--text-xs);
  font-weight: 700;
}

.metric-card strong {
  font-size: var(--text-lg);
  line-height: 1.2;
}

.metric-card small {
  overflow: hidden;
  color: var(--muted);
  font-size: var(--text-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tone-success { --metric-card-tint: var(--success-bg); }
.tone-warning { --metric-card-tint: var(--warning-bg); }
.tone-danger { --metric-card-tint: var(--error-bg); }

@media (max-width: 820px) {
  .metric-strip {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }
}
</style>
