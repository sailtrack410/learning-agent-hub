<script setup lang="ts">
import { computed, ref } from 'vue';
import { useTodosSectionContext } from '../employee-shell-context';
import type { TodoRecord } from '@/stores/shell';
import AppButton from '../../components/ui/AppButton.vue';
import TodoCreateDialog from '../../components/todos/TodoCreateDialog.vue';
import todoHeroImage from '@/assets/education/generated/todo-hero.webp';
import todoPendingImage from '@/assets/education/generated/todo-pending.webp';
import todoTodayImage from '@/assets/education/generated/todo-today.webp';
import todoCompletedImage from '@/assets/education/generated/todo-completed.webp';

interface CalendarDay {
  key: string;
  label: string;
  outside: boolean;
  today: boolean;
  count: number;
}

interface CalendarSegment {
  task: TodoRecord;
  start: number;
  end: number;
  row: number;
}

interface CalendarWeek {
  key: string;
  days: CalendarDay[];
  segments: CalendarSegment[];
  rowCount: number;
}

const {
  shell,
  pendingTodoCount,
  backToList,
} = useTodosSectionContext();

const todayKey = toDateKey(new Date());
const visibleMonth = ref(startOfMonth(new Date()));
const showCreate = ref(false);

const compactMonthLabel = computed(() => `${visibleMonth.value.getMonth() + 1}月`);
const monthYearLabel = computed(() => `${visibleMonth.value.getFullYear()}`);
const weekLabels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
const monthStartKey = computed(() => toDateKey(visibleMonth.value));
const monthEndKey = computed(() => toDateKey(new Date(visibleMonth.value.getFullYear(), visibleMonth.value.getMonth() + 1, 0)));
const calendarStartKey = computed(() => toDateKey(startOfWeekMonday(visibleMonth.value)));
const calendarEndKey = computed(() => toDateKey(endOfWeekSunday(new Date(visibleMonth.value.getFullYear(), visibleMonth.value.getMonth() + 1, 0))));
const visibleTodos = computed(() => shell.filteredTodos.filter((todo) => (
  isTaskDated(todo) && todo.endDate >= calendarStartKey.value && todo.startDate <= calendarEndKey.value
)));
const monthTodoCount = computed(() => shell.todos.filter((todo) => (
  isTaskDated(todo) && todo.endDate >= monthStartKey.value && todo.startDate <= monthEndKey.value
)).length);
const todayTodoCount = computed(() => shell.todos.filter((todo) => (
  isTaskDated(todo) && todo.startDate <= todayKey && todo.endDate >= todayKey
)).length);
const completedTodoCount = computed(() => shell.todos.filter((todo) => todo.completed).length);
const todayDayLabel = String(new Date().getDate()).padStart(2, '0');
const calendarWeeks = computed(() => {
  const weeks: CalendarWeek[] = [];
  const month = visibleMonth.value.getMonth();
  const end = endOfWeekSunday(new Date(visibleMonth.value.getFullYear(), visibleMonth.value.getMonth() + 1, 0));
  let cursor = startOfWeekMonday(visibleMonth.value);

  while (cursor <= end) {
    const days = Array.from({ length: 7 }, (_, index) => {
      const date = addDays(cursor, index);
      const key = toDateKey(date);
      const dayTodos = visibleTodos.value.filter((todo) => isTaskDated(todo) && todo.startDate <= key && todo.endDate >= key);
      return {
        key,
        label: String(date.getDate()),
        outside: date.getMonth() !== month,
        today: key === todayKey,
        count: dayTodos.length,
      };
    });
    const weekStart = days[0]?.key ?? toDateKey(cursor);
    const weekEnd = days[6]?.key ?? toDateKey(addDays(cursor, 6));
    const rawSegments = visibleTodos.value
      .filter((todo) => isTaskDated(todo) && todo.endDate >= weekStart && todo.startDate <= weekEnd)
      .map((todo) => {
        const startDate = todo.startDate ?? weekStart;
        const endDate = todo.endDate ?? weekEnd;
        return {
          task: todo,
          start: Math.max(1, daysBetween(weekStart, startDate) + 1),
          end: Math.min(8, daysBetween(weekStart, endDate) + 2),
        };
      })
      .sort((a, b) => a.start - b.start || a.end - b.end || a.task.title.localeCompare(b.task.title));
    const rowEnds: number[] = [];
    const segments = rawSegments.map((segment) => {
      let rowIndex = rowEnds.findIndex((rowEnd) => segment.start >= rowEnd);
      if (rowIndex === -1) rowIndex = rowEnds.length;
      rowEnds[rowIndex] = segment.end;
      return { ...segment, row: rowIndex + 1 };
    });
    weeks.push({ key: weekStart, days, segments, rowCount: Math.max(1, rowEnds.length) });
    cursor = addDays(cursor, 7);
  }

  return weeks;
});

function toDateKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function toDateTimeMinuteKey(date: Date) {
  return `${toDateKey(date)}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

function parseDateKey(value: string) {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}

function isTaskDated(todo: TodoRecord): todo is TodoRecord & { startDate: string; endDate: string } {
  return /^\d{4}-\d{2}-\d{2}$/.test(todo.startDate ?? '') && /^\d{4}-\d{2}-\d{2}$/.test(todo.endDate ?? '');
}

function startOfMonth(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function startOfWeekMonday(date: Date) {
  const result = new Date(date);
  const diff = (result.getDay() + 6) % 7;
  result.setDate(result.getDate() - diff);
  return result;
}

function endOfWeekSunday(date: Date) {
  const result = startOfWeekMonday(date);
  result.setDate(result.getDate() + 6);
  return result;
}

function addDays(date: Date, amount: number) {
  const result = new Date(date);
  result.setDate(result.getDate() + amount);
  return result;
}

function addMinutes(date: Date, amount: number) {
  const result = new Date(date);
  result.setMinutes(result.getMinutes() + amount);
  return result;
}

function daysBetween(start: string, end: string) {
  return Math.round((parseDateKey(end).getTime() - parseDateKey(start).getTime()) / 86400000);
}

function shiftMonth(amount: number) {
  visibleMonth.value = new Date(visibleMonth.value.getFullYear(), visibleMonth.value.getMonth() + amount, 1);
}

function goToday() {
  visibleMonth.value = startOfMonth(new Date());
}

function isMinuteDateTime(value: string) {
  return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value);
}

function segmentMeta(todo: TodoRecord) {
  return [todo.tagName, todo.subtitle].filter(Boolean).join(' · ');
}

function shortDateTime(value?: string) {
  if (!value || !isMinuteDateTime(value)) return '';
  const day = Number(value.slice(8, 10));
  return `${day}日 ${value.slice(11)}`;
}

function segmentTime(todo: TodoRecord) {
  if (!todo.startAt || !todo.endAt) return todo.time || '';
  if (todo.startDate === todo.endDate) return `${shortDateTime(todo.startAt)}-${todo.endAt.slice(11)}`;
  return `${shortDateTime(todo.startAt)} - ${shortDateTime(todo.endAt)}`;
}

function segmentTitle(todo: TodoRecord) {
  return [todo.title, segmentTime(todo), segmentMeta(todo), todo.note].filter(Boolean).join('\n');
}

function selectTodo(todo: TodoRecord) {
  shell.selectItem('todos', todo.id);
}
</script>

<template>
<div class="workspace generic-workspace">
  <section class="todo-calendar-layout">
    <section class="todo-overview-hero" :style="{ '--todo-hero-image': `url(${todoHeroImage})` }">
      <div class="todo-hero-copy">
        <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
          <span aria-hidden="true">‹</span>
        </button>
        <span class="todo-hero-eyebrow">学习计划台</span>
        <h1>安排今天，也看清整个月</h1>
        <p>课程学习、复习与协作事项统一进入时间轴。</p>
        <AppButton variant="primary" size="sm" @click="showCreate = true">
          添加待办
        </AppButton>
        <div class="todo-hero-board" aria-label="待办概览">
          <article class="todo-hero-metric primary">
            <img :src="todoPendingImage" alt="" />
            <span><strong>{{ pendingTodoCount }}</strong><small>未完成</small></span>
          </article>
          <article class="todo-hero-metric">
            <img :src="todoTodayImage" alt="" />
            <span><strong>{{ todayTodoCount }}</strong><small>今天</small></span>
          </article>
          <article class="todo-hero-metric">
            <img :src="todoCompletedImage" alt="" />
            <span><strong>{{ completedTodoCount }}</strong><small>已完成</small></span>
          </article>
        </div>
      </div>
      <div class="todo-date-emblem" aria-label="当前日期">
        <small>{{ monthYearLabel }}</small>
        <strong>{{ todayDayLabel }}</strong>
        <span>{{ compactMonthLabel }}</span>
      </div>
    </section>

    <section class="todo-calendar-card">
      <header class="todo-month-title">
        <div class="todo-month-copy">
          <small>月度日历</small>
          <h3>{{ compactMonthLabel }}</h3>
          <span>{{ monthYearLabel }}</span>
        </div>
        <div class="todo-month-actions">
          <AppButton variant="secondary" size="sm" @click="shiftMonth(-1)">
            上月
          </AppButton>
          <AppButton variant="secondary" size="sm" @click="goToday">
            今天
          </AppButton>
          <AppButton variant="secondary" size="sm" @click="shiftMonth(1)">
            下月
          </AppButton>
          <AppButton variant="primary" size="sm" @click="showCreate = true">
            添加
          </AppButton>
        </div>
      </header>

      <div class="todo-calendar-body">
        <div class="todo-week-header">
          <span v-for="label in weekLabels" :key="label">{{ label }}</span>
        </div>
        <div class="todo-month-grid">
          <section
            v-for="week in calendarWeeks"
            :key="week.key"
            class="todo-week-row"
            :style="{ '--todo-week-rows': week.rowCount }"
          >
            <div class="todo-week-days">
              <div
                v-for="day in week.days"
                :key="day.key"
                class="todo-day-cell"
                :class="{ outside: day.outside, today: day.today }"
              >
                <span>{{ day.label }}</span>
                <em v-if="day.count">{{ day.count }}</em>
              </div>
            </div>
            <div class="todo-week-bars">
              <div
                v-for="segment in week.segments"
                :key="`${week.key}-${segment.task.id}`"
                role="button"
                tabindex="0"
                class="todo-calendar-bar"
                :class="[`todo-color-${segment.task.tagColor ?? 'blue'}`, { completed: segment.task.completed }]"
                :style="{ gridColumn: `${segment.start} / ${segment.end}`, gridRow: segment.row }"
                :title="segmentTitle(segment.task)"
                @click="selectTodo(segment.task)"
                @keydown.enter.prevent="selectTodo(segment.task)"
                @keydown.space.prevent="selectTodo(segment.task)"
              >
                <small>{{ segmentTime(segment.task) }}</small>
                <span>{{ segment.task.title }}</span>
                <em>{{ segment.task.tagName }}</em>
              </div>
            </div>
          </section>
        </div>
        <div v-if="!visibleTodos.length" class="todo-calendar-empty">
          <img :src="todoTodayImage" alt="" />
          <strong>本月暂无待办</strong>
          <span>点击添加安排学习任务</span>
        </div>
      </div>
    </section>
  </section>

  <TodoCreateDialog :open="showCreate" @close="showCreate = false" />
</div>
</template>

<style scoped>
.todo-calendar-layout {
  align-content: start;
  gap: var(--space-16);
  overflow: auto;
  background-color: var(--surface-canvas);
  background-image: var(--canvas-backdrop);
}

.todo-overview-hero {
  position: relative;
  isolation: isolate;
  min-height: 20rem;
  overflow: hidden;
  padding: clamp(var(--space-24), 3vw, var(--space-40));
  border: 1px solid var(--border-accent);
  border-radius: var(--radius-20);
  background-color: var(--surface-accent);
  background-image:
    linear-gradient(90deg, color-mix(in srgb, var(--surface-panel) 98%, transparent) 0%, color-mix(in srgb, var(--surface-panel) 90%, transparent) 38%, transparent 64%),
    var(--todo-hero-image);
  background-position: center;
  background-size: cover;
  box-shadow: var(--shadow-md);
}

.todo-hero-copy {
  display: grid;
  align-content: center;
  justify-items: start;
  gap: var(--space-10);
  width: min(51%, 38rem);
  min-height: 100%;
}

.todo-hero-eyebrow {
  display: inline-flex;
  align-items: center;
  min-height: 1.85rem;
  padding: 0 var(--space-10);
  border: 1px solid color-mix(in srgb, var(--border-accent) 72%, transparent);
  border-radius: 999px;
  background: var(--surface-glass);
  color: var(--text-accent);
  font-size: var(--text-caption);
  font-weight: 800;
  letter-spacing: .08em;
}

.todo-hero-copy h1 {
  margin: 0;
  font-family: var(--font-family-display);
  font-size: clamp(var(--text-title), 3vw, var(--text-headline));
  line-height: 1.15;
}

.todo-hero-copy p {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.65;
}

.todo-hero-board {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  width: 100%;
  gap: var(--space-8);
  margin-top: var(--space-6);
}

.todo-hero-metric {
  display: flex;
  align-items: center;
  gap: var(--space-8);
  min-width: 0;
  padding: var(--space-8);
  border: 1px solid color-mix(in srgb, var(--border-accent) 70%, transparent);
  border-radius: var(--radius-12);
  background: var(--surface-glass);
  box-shadow: var(--shadow-sm);
  backdrop-filter: blur(.7rem);
}

.todo-hero-metric img {
  width: 2.7rem;
  height: 2.7rem;
  flex: 0 0 auto;
  border-radius: var(--radius-10);
  object-fit: contain;
}

.todo-hero-metric span {
  display: grid;
  gap: var(--space-2);
}

.todo-hero-metric strong {
  color: var(--text-primary);
  font-family: var(--font-family-display);
  font-size: var(--text-subtitle-lg);
  line-height: 1;
}

.todo-hero-metric small {
  color: var(--text-tertiary);
  font-size: var(--text-caption);
}

.todo-date-emblem {
  position: absolute;
  right: clamp(var(--space-18), 3vw, var(--space-32));
  bottom: var(--space-20);
  display: grid;
  width: 6.4rem;
  min-height: 7.6rem;
  place-items: center;
  padding: var(--space-10);
  border: 1px solid var(--border-accent);
  border-radius: var(--radius-16);
  background: var(--surface-glass);
  box-shadow: var(--shadow-lg);
  backdrop-filter: blur(.8rem);
  transform: rotate(2deg);
}

.todo-date-emblem small,
.todo-date-emblem span {
  color: var(--text-tertiary);
  font-size: var(--text-caption);
  font-weight: 700;
}

.todo-date-emblem strong {
  color: var(--text-accent);
  font-family: var(--font-family-display);
  font-size: 2.7rem;
  line-height: 1;
}

.todo-calendar-card {
  border-radius: var(--radius-20);
  background: var(--surface-panel);
  box-shadow: var(--shadow-md);
}

.todo-month-title {
  min-height: 4.5rem;
  padding: var(--space-14) var(--space-18);
  background: var(--surface-glass);
  backdrop-filter: blur(.6rem);
}

.todo-month-copy {
  grid-template-columns: auto auto;
  align-items: baseline;
  column-gap: var(--space-8);
}

.todo-month-copy small {
  grid-column: 1 / -1;
  color: var(--text-accent);
  font-size: var(--text-caption);
  font-weight: 800;
  letter-spacing: .08em;
}

.todo-calendar-empty {
  display: grid;
  min-width: 12rem;
  justify-items: center;
  gap: var(--space-6);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-14);
  background: var(--surface-glass);
  color: var(--text-accent);
  box-shadow: var(--shadow-sm);
}

.todo-calendar-empty img {
  width: 3.5rem;
  height: 3.5rem;
  border-radius: var(--radius-12);
  object-fit: contain;
}

.todo-calendar-empty strong {
  color: var(--text-primary);
}

.todo-calendar-empty span {
  color: var(--text-tertiary);
  font-size: var(--text-caption);
}

@media (max-width: 980px) {
  .todo-overview-hero {
    min-height: 18rem;
    background-position: 64% center;
  }

  .todo-hero-copy {
    width: min(65%, 36rem);
  }
}

:global([data-theme='dark']) .todo-overview-hero {
  background-image:
    linear-gradient(90deg, color-mix(in srgb, var(--surface-panel) 98%, transparent) 0%, color-mix(in srgb, var(--surface-panel) 92%, transparent) 42%, color-mix(in srgb, var(--surface-panel) 24%, transparent) 72%),
    var(--todo-hero-image);
}
</style>
