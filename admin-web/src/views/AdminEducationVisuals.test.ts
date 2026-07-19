import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

function sourceOf(path: string) {
  return readFileSync(fileURLToPath(new URL(path, import.meta.url)), 'utf8');
}

const heroSource = sourceOf('../components/AdminHeroBanner.vue');
const metricSource = sourceOf('../components/AdminMetricStrip.vue');
const dashboardSource = sourceOf('./DashboardView.vue');
const ragflowSource = sourceOf('./RagflowView.vue');
const contentSafetySource = sourceOf('./ContentSafetyView.vue');
const userSource = sourceOf('./UserView.vue');

describe('admin education visual assets', () => {
  it('keeps hero copy dynamic and separate from the generated background', () => {
    expect(heroSource).toContain('{{ title }}');
    expect(heroSource).toContain('{{ description }}');
    expect(heroSource).toContain("var(--admin-hero-image)");
    expect(heroSource).toContain('<slot name="metrics" />');
  });

  it('uses dedicated hero assets for the four primary governance pages', () => {
    expect(dashboardSource).toContain('admin-dashboard-hero.webp');
    expect(ragflowSource).toContain('admin-ragflow-hero.webp');
    expect(contentSafetySource).toContain('admin-content-safety-hero.webp');
    expect(userSource).toContain('admin-users-hero.webp');
  });

  it('renders generated status icons without replacing their accessible labels', () => {
    expect(metricSource).toContain('v-if="card.icon"');
    expect(metricSource).toContain('aria-hidden="true"');
    expect(metricSource).toContain('{{ card.label }}');
    expect(metricSource).toContain('{{ card.value }}');
  });

  it('derives displayed metrics from loaded API state', () => {
    expect(dashboardSource).toContain('taskMetrics?.totalTaskCount');
    expect(ragflowSource).toContain('spaceTotal.value = res.total');
    expect(contentSafetySource).toContain('form.blockedMarkers.length');
    expect(userSource).toContain('store.pagination.total');
  });
});
