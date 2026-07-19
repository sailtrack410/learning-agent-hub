import { createRouter, createWebHashHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';
import { useSessionStore } from '@/stores/session';

const MessagesView = () => import('@/views/MessagesView.vue');
const MessagesSection = () => import('@/views/sections/MessagesSection.vue');
const EducationResourcesSection = () => import('@/views/sections/EducationResourcesSection.vue');
const FilesSection = () => import('@/views/sections/FilesSection.vue');
const TodosSection = () => import('@/views/sections/TodosSection.vue');
const AgentsSection = () => import('@/views/sections/AgentsSection.vue');
const SettingsSection = () => import('@/views/sections/SettingsSection.vue');
const CoursesSection = () => import('@/views/sections/CoursesSection.vue');
const StudentProfileSection = () => import('@/views/sections/StudentProfileSection.vue');
const CourseLearningPathView = () => import('@/views/CourseLearningPathView.vue');
const StudentLearningResourcesView = () => import('@/views/StudentLearningResourcesView.vue');
const CourseArticleEditor = () => import('@/views/CourseArticleEditor.vue');

export const employeeRoutes = [
  {
    path: '/',
    redirect: '/courses',
  },
  {
    path: '/',
    component: MessagesView,
    children: [
      {
        path: 'courses',
        name: 'employee-courses',
        component: CoursesSection,
        meta: { section: 'courses' },
      },
      {
        path: 'profile',
        name: 'employee-profile',
        component: StudentProfileSection,
        meta: { section: 'profile', educationRole: 'STUDENT' },
      },
      {
        path: 'courses/:courseId/learning-path',
        name: 'student-course-learning-path',
        component: CourseLearningPathView,
        meta: { section: 'courses', educationRole: 'STUDENT' },
      },
      {
        path: 'courses/:courseId/learning-resources',
        name: 'student-learning-resources',
        component: StudentLearningResourcesView,
        meta: { section: 'courses', educationRole: 'STUDENT' },
      },
      {
        path: 'courses/:courseId/articles/:chapterId',
        name: 'teacher-course-article',
        component: CourseArticleEditor,
        meta: { section: 'courses', educationRole: 'TEACHER' },
      },
      {
        path: 'messages',
        name: 'employee-messages',
        component: MessagesSection,
        meta: { section: 'messages' },
      },
      {
        path: 'messages/:conversationId',
        name: 'employee-message-detail',
        component: MessagesSection,
        meta: { section: 'messages' },
      },
      {
        path: 'todos',
        name: 'employee-todos',
        component: TodosSection,
        meta: { section: 'todos' },
      },
      {
        path: 'todos/:todoId',
        name: 'employee-todo-detail',
        component: TodosSection,
        meta: { section: 'todos' },
      },
      {
        path: 'resources',
        name: 'employee-resources',
        component: EducationResourcesSection,
        meta: { section: 'resources' },
      },
      {
        path: 'files',
        name: 'employee-files',
        component: FilesSection,
        meta: { section: 'files' },
      },
      {
        path: 'agents',
        name: 'employee-agents',
        component: AgentsSection,
        meta: { section: 'agents' },
      },
      {
        path: 'settings',
        name: 'employee-settings',
        component: SettingsSection,
        meta: { section: 'settings' },
      },
      {
        path: 'settings/:panel',
        name: 'employee-settings-detail',
        component: SettingsSection,
        meta: { section: 'settings' },
      },
      {
        path: 'processes',
        redirect: '/courses',
      },
      {
        path: 'reports',
        redirect: '/courses',
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/courses',
  },
] satisfies RouteRecordRaw[];

const router = createRouter({
  history: createWebHashHistory(),
  routes: employeeRoutes,
});

router.beforeEach(async (to) => {
  const session = useSessionStore();
  if (!session.initialized) await session.loadCurrentUser();
  if (!session.isAuthenticated) return true;
  const requiredRole = to.meta.educationRole;
  if (typeof requiredRole === 'string' && session.educationRole !== requiredRole) return '/courses';
  return true;
});

export default router;
