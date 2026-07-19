<script setup lang="ts">
const props = withDefaults(defineProps<{
  variant?: 'primary' | 'secondary' | 'ghost' | 'destructive' | 'link';
  size?: 'sm' | 'md' | 'lg';
  block?: boolean;
  selected?: boolean;
  disabled?: boolean;
  type?: 'button' | 'submit' | 'reset';
}>(), {
  variant: 'secondary',
  size: 'md',
  block: false,
  selected: false,
  disabled: false,
  type: 'button',
});
</script>

<template>
  <button
    class="ui-button"
    :class="[
      `variant-${props.variant}`,
      `size-${props.size}`,
      { block: props.block, selected: props.selected },
    ]"
    :type="props.type"
    :disabled="props.disabled"
  >
    <slot />
  </button>
</template>

<style scoped>
.ui-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-8);
  min-height: 2.75rem;
  padding: var(--space-8) var(--space-16);
  border: 1px solid transparent;
  border-radius: var(--radius-10);
  font-family: var(--font-family-body);
  font-size: var(--text-body-sm);
  font-weight: 600;
  line-height: 1;
  transition: color var(--transition-base), background-color var(--transition-base), border-color var(--transition-base), box-shadow var(--transition-base), transform var(--transition-base);
}

.ui-button:active:not(:disabled) {
  transform: scale(0.98);
}

.ui-button:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--surface-panel), 0 0 0 4px color-mix(in srgb, var(--primary-500) 34%, transparent);
}

.ui-button:disabled {
  cursor: not-allowed;
  background: var(--surface-panel-muted);
  color: var(--text-tertiary);
  border-color: var(--border-subtle);
  transform: none;
}

.ui-button.block {
  width: 100%;
}

.ui-button.size-sm {
  min-height: 2.25rem;
  padding-inline: var(--space-12);
  font-size: var(--text-caption-md);
}

.ui-button.size-lg {
  min-height: 3rem;
  padding-inline: var(--space-20);
  font-size: var(--text-body-md);
}

.ui-button.variant-primary {
  border-color: var(--primary-600);
  background: linear-gradient(135deg, var(--primary-500), var(--primary-600));
  color: var(--text-inverse);
  box-shadow: var(--shadow-button);
}

.ui-button.variant-primary:hover:not(:disabled),
.ui-button.variant-primary:focus-visible {
  background: linear-gradient(135deg, var(--primary-600), var(--primary-700));
  box-shadow: var(--shadow-md);
}

.ui-button.variant-secondary {
  background: var(--surface-panel);
  border-color: var(--border-default);
  color: var(--text-primary);
  box-shadow: var(--shadow-sm);
}

.ui-button.variant-secondary:hover:not(:disabled),
.ui-button.variant-secondary.selected {
  border-color: var(--border-accent);
  background: var(--surface-accent);
  color: var(--text-accent);
}

.ui-button.variant-ghost {
  background: transparent;
  color: var(--text-secondary);
}

.ui-button.variant-ghost:hover:not(:disabled),
.ui-button.variant-ghost.selected {
  background: color-mix(in srgb, var(--primary-100) 50%, transparent);
  color: var(--text-accent);
}

.ui-button.variant-destructive {
  background: var(--danger-500);
  color: var(--text-inverse);
}

.ui-button.variant-destructive:hover:not(:disabled) {
  background: var(--danger-600);
}

.ui-button.variant-link {
  min-height: auto;
  padding: 0;
  background: transparent;
  color: var(--text-accent);
}

.ui-button.variant-link:hover:not(:disabled) {
  color: var(--primary-600);
}

@media (max-width: 640px) {
  .ui-button.size-sm {
    min-height: 2.75rem;
  }
}
</style>
