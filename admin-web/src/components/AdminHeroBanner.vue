<script setup lang="ts">
defineProps<{
  eyebrow: string;
  title: string;
  description: string;
  image: string;
}>();
</script>

<template>
  <section class="admin-hero" :style="{ '--admin-hero-image': `url(${image})` }">
    <div class="admin-hero-copy">
      <span class="admin-hero-eyebrow">{{ eyebrow }}</span>
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
      <div v-if="$slots.metrics" class="admin-hero-metrics">
        <slot name="metrics" />
      </div>
      <div v-if="$slots.actions" class="admin-hero-actions">
        <slot name="actions" />
      </div>
    </div>
  </section>
</template>

<style scoped>
.admin-hero {
  position: relative;
  grid-column: span 12;
  min-height: 264px;
  overflow: hidden;
  padding: var(--space-2xl);
  border: 1px solid var(--hero-border);
  border-radius: var(--hero-radius);
  background-color: var(--hero-bg);
  background-image:
    linear-gradient(90deg, var(--hero-overlay-strong) 0%, var(--hero-overlay-strong) 38%, var(--hero-overlay-soft) 58%, var(--hero-overlay-clear) 76%),
    var(--admin-hero-image);
  background-position: center, right center;
  background-repeat: no-repeat;
  background-size: cover, auto 155%;
  box-shadow: var(--hero-shadow);
  isolation: isolate;
}

.admin-hero::after {
  position: absolute;
  inset: 0;
  z-index: 0;
  background: linear-gradient(135deg, var(--hero-sheen) 0%, var(--hero-overlay-clear) 44%);
  content: '';
  pointer-events: none;
}

.admin-hero-copy {
  position: relative;
  z-index: 1;
  display: grid;
  align-content: center;
  gap: var(--space-md);
  width: min(56%, 680px);
  min-height: 206px;
}

.admin-hero-eyebrow {
  width: fit-content;
  padding: var(--space-xs) var(--space-md);
  border: 1px solid var(--hero-chip-border);
  border-radius: var(--radius-pill);
  background: var(--hero-chip-bg);
  color: var(--green-deep);
  font-size: var(--text-xs);
  font-weight: 800;
  letter-spacing: .08em;
}

.admin-hero h2 {
  max-width: 620px;
  margin: 0;
  color: var(--ink);
  font-size: clamp(26px, 2.4vw, 38px);
  letter-spacing: -.025em;
  line-height: 1.18;
}

.admin-hero p {
  max-width: 580px;
  margin: 0;
  color: var(--muted);
  font-size: var(--text-base);
  line-height: 1.7;
}

.admin-hero-metrics,
.admin-hero-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
}

:deep(.hero-metric) {
  display: grid;
  grid-template-columns: auto auto;
  align-items: baseline;
  gap: var(--space-xs);
  min-width: 112px;
  padding: var(--space-sm) var(--space-md);
  border: 1px solid var(--hero-metric-border);
  border-radius: var(--radius-md);
  background: var(--hero-metric-bg);
  backdrop-filter: blur(10px);
}

:deep(.hero-metric strong) {
  color: var(--ink);
  font-size: var(--text-lg);
}

:deep(.hero-metric span) {
  color: var(--muted);
  font-size: var(--text-xs);
  font-weight: 700;
}

@media (max-width: 980px) {
  .admin-hero {
    min-height: 238px;
    background-image:
      linear-gradient(90deg, var(--hero-overlay-strong) 0%, var(--hero-overlay-strong) 52%, var(--hero-overlay-soft) 75%, var(--hero-overlay-clear) 100%),
      var(--admin-hero-image);
    background-size: cover, auto 135%;
  }

  .admin-hero-copy { width: 66%; }
}
</style>
