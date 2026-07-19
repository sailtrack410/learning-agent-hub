import { describe, expect, it } from 'vitest';
import permissionSource from './PermissionView.vue?raw';
import auditSource from './AuditLogView.vue?raw';
import userSource from './UserView.vue?raw';
import contentSafetySource from './ContentSafetyView.vue?raw';
import appSource from '../App.vue?raw';
import adminToastSource from '../components/AdminToast.vue?raw';
import toastSource from '../composables/useToast.ts?raw';
import adminIndexSource from '../../index.html?raw';

describe('admin remaining interaction contracts', () => {
  it('keeps permission assignment in role configuration', () => {
    expect(permissionSource).toContain("router.push('/roles')");
    expect(permissionSource).toContain('roleNames(row.permissionId)');
    expect(permissionSource).not.toContain('createPermission');
  });

  it('supports audit pagination and detail inspection', () => {
    expect(auditSource).toContain('<AdminPagination');
    expect(auditSource).toContain('detailVisible.value = true');
    expect(auditSource).toContain('prettyJson(selected.detailJson)');
  });

  it('shows field-level employee validation', () => {
    expect(userSource).toContain('userCreateErrors');
    expect(userSource).toContain('aria-invalid');
    expect(userSource).toContain('userCreateInvalid');
  });

  it('supports content safety threshold, markers and optimistic version updates', () => {
    expect(contentSafetySource).toContain('reviewConfidenceThreshold');
    expect(contentSafetySource).toContain('blockedMarkers');
    expect(contentSafetySource).toContain('expectedVersion: form.versionNo');
    expect(contentSafetySource).toContain('promptInjectionDetectionEnabled');
  });

  it('provides visible toast feedback to every admin route', () => {
    expect(appSource).toContain('<AdminToast>');
    expect(appSource).toContain('<RouterView />');
    expect(adminToastSource).toContain('<slot />');
    expect(toastSource).not.toContain('console.');
  });

  it('uses the education product name in the browser title', () => {
    expect(adminIndexSource).toContain('<title>智能学习平台 · 运营管理</title>');
    expect(adminIndexSource).not.toContain('Workforce Hub');
  });
});
