import { describe, expect, it } from 'vitest';
import type { RouteRecordRaw } from 'vue-router';

describe('employee router', () => {
  it('redirects the user root to the course section', async () => {
    const employeeRoutes = await loadEmployeeRoutes();
    const root = employeeRoutes.find((route) => route.path === '/');

    expect(root?.redirect).toBe('/courses');
  });

  it.each([
    ['/courses', 'employee-courses', 'courses'],
    ['/profile', 'employee-profile', 'profile'],
    ['/courses/8100001/learning-path', 'student-course-learning-path', 'courses'],
    ['/courses/8100001/learning-resources', 'student-learning-resources', 'courses'],
    ['/courses/8100001/articles/8110001', 'teacher-course-article', 'courses'],
    ['/messages', 'employee-messages', 'messages'],
    ['/messages/conv-1001', 'employee-message-detail', 'messages'],
    ['/todos', 'employee-todos', 'todos'],
    ['/todos/todo-1001', 'employee-todo-detail', 'todos'],
    ['/resources', 'employee-resources', 'resources'],
    ['/files', 'employee-files', 'files'],
    ['/agents', 'employee-agents', 'agents'],
    ['/settings', 'employee-settings', 'settings'],
    ['/settings/security', 'employee-settings-detail', 'settings'],
  ])('resolves %s to a real section route', async (path, name, section) => {
    const employeeRoutes = await loadEmployeeRoutes();
    const route = findRoute(employeeRoutes, path);

    expect(route?.name).toBe(name);
    expect(route?.meta?.section).toBe(section);
  });

  it.each(['/workbench', '/contacts'])('removes the unused office route %s', async (path) => {
    const employeeRoutes = await loadEmployeeRoutes();
    expect(findRoute(employeeRoutes, path)?.name).toBeUndefined();
  });

  it('lazy-loads the shell and section components', async () => {
    const employeeRoutes = await loadEmployeeRoutes();
    const shellRoute = employeeRoutes.find((route) => route.children?.length);
    const sectionRoutes = shellRoute?.children?.filter((route) => route.component) ?? [];

    expect(typeof shellRoute?.component).toBe('function');
    expect(sectionRoutes.length).toBeGreaterThan(0);
    expect(sectionRoutes.every((route) => typeof route.component === 'function')).toBe(true);
  });

  it('marks student and teacher-only routes for role isolation', async () => {
    const employeeRoutes = await loadEmployeeRoutes();

    expect(findRoute(employeeRoutes, '/profile')?.meta?.educationRole).toBe('STUDENT');
    expect(findRoute(employeeRoutes, '/courses/8100001/learning-path')?.meta?.educationRole).toBe('STUDENT');
    expect(findRoute(employeeRoutes, '/courses/8100001/learning-resources')?.meta?.educationRole).toBe('STUDENT');
    expect(findRoute(employeeRoutes, '/courses/8100001/articles/8110001')?.meta?.educationRole).toBe('TEACHER');
    expect(findRoute(employeeRoutes, '/courses')?.meta?.educationRole).toBeUndefined();
  });
});

async function loadEmployeeRoutes(): Promise<RouteRecordRaw[]> {
  Object.defineProperty(globalThis, 'location', {
    value: {
      host: '127.0.0.1:5174',
      pathname: '/',
      search: '',
      hash: '#/',
    },
    configurable: true,
  });
  Object.defineProperty(globalThis, 'document', {
    value: {
      addEventListener: () => undefined,
      createElement: () => ({}),
      querySelector: () => null,
      removeEventListener: () => undefined,
    },
    configurable: true,
  });
  Object.defineProperty(globalThis, 'window', {
    value: {
      location: globalThis.location,
      document: globalThis.document,
      history: {
        state: null,
        replaceState: () => undefined,
        pushState: () => undefined,
      },
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    },
    configurable: true,
  });
  const module = await import('./index');
  return module.employeeRoutes;
}

function findRoute(employeeRoutes: RouteRecordRaw[], path: string) {
  const normalizedPath = path.replace(/^\//, '');
  return employeeRoutes.find((route) => route.path === path)
    ?? employeeRoutes.flatMap((route) => route.children ?? []).find((route) => {
      const routePath = route.path.replace(/\/:[^/]+/g, '/[^/]+');
      return new RegExp(`^${routePath}$`).test(normalizedPath);
    });
}
