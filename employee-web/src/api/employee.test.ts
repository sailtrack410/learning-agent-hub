import { describe, expect, it } from 'vitest';
import { createEmployeeApi } from './employee';
import type { ApiClient } from './http';

function createSpyClient() {
  const calls: Array<[string, string, unknown?]> = [];
  const client: ApiClient = {
    get: <T,>(path: string, options?: unknown) => {
      calls.push(['get', path, options]);
      return Promise.resolve(null as T);
    },
    post: <T,>(path: string, body?: unknown) => {
      calls.push(['post', path, body]);
      return Promise.resolve(null as T);
    },
    put: <T,>(path: string, body?: unknown) => {
      calls.push(['put', path, body]);
      return Promise.resolve(null as T);
    },
    delete: <T,>(path: string, options?: unknown) => {
      calls.push(['delete', path, options]);
      return Promise.resolve(null as T);
    },
  };
  return { client, calls };
}

describe('createEmployeeApi', () => {
  it('maps student profile dialogue and correction endpoints', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.studentProfile.current();
    await api.studentProfile.answer('我更喜欢通过代码案例学习。');
    await api.studentProfile.correct('RESOURCE_PREFERENCE', '我更喜欢短视频配合代码实操。');

    expect(calls).toEqual([
      ['get', '/api/employee/student/profile', undefined],
      ['post', '/api/employee/student/profile/messages', { message: '我更喜欢通过代码案例学习。' }],
      ['put', '/api/employee/student/profile/dimensions/RESOURCE_PREFERENCE', { conclusion: '我更喜欢短视频配合代码实操。' }],
    ]);
  });

  it('maps published course endpoints', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.courses.list();
    await api.courses.detail('8100001');
    await api.courses.enroll('8100001');
    await api.courses.assessments('8100001');
    await api.courses.assessmentDetail('8100001', '8500001');
    await api.courses.submitAssessment('8100001', '8500001', {
      idempotencyKey: 'attempt-1',
      durationSeconds: 30,
      answers: [{ questionId: '8510001', selectedValues: ['B'], durationSeconds: 30 }],
    });
    await api.courses.learningPath('8100001');
    await api.courses.completeLearningPathNode('8100001', '8700001');

    expect(calls).toEqual([
      ['get', '/api/employee/courses', undefined],
      ['get', '/api/employee/courses/8100001', undefined],
      ['post', '/api/employee/courses/8100001/enrollment', undefined],
      ['get', '/api/employee/courses/8100001/assessments', undefined],
      ['get', '/api/employee/courses/8100001/assessments/8500001', undefined],
      ['post', '/api/employee/courses/8100001/assessments/8500001/attempts', {
        idempotencyKey: 'attempt-1',
        durationSeconds: 30,
        answers: [{ questionId: '8510001', selectedValues: ['B'], durationSeconds: 30 }],
      }],
      ['get', '/api/employee/courses/8100001/learning-path', undefined],
      ['post', '/api/employee/courses/8100001/learning-path/nodes/8700001/complete', undefined],
    ]);
    expect(api.courses.resourcePreviewUrl('8200001')).toBe('/api/employee/courses/resources/8200001/preview');
  });

  it('maps teacher course management endpoints', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.teacherCourses.list();
    await api.teacherCourses.learningOverview('8100001');
    await api.teacherCourses.startStudentConversation('8100001', '8220002');
    await api.teacherCourses.openClassGroup('8100001', '8200002');
    await api.teacherCourses.saveOutline('8100001', { chapters: [], dependencies: [] });
    await api.teacherCourses.saveArticle('8100001', '8110001', { title: 'Servlet', contentJson: '{"type":"doc","content":[]}', status: 'DRAFT' });
    await api.teacherCourses.prepareResourcePreview('8100001', '8200001');
    await api.teacherCourses.resourcePreviewStatus('8100001', '8200001');
    await api.teacherCourses.knowledgeDocuments('8100001');
    await api.teacherCourses.addKnowledgeDocument('8100001', { attachmentRefId: '8200001', chapterId: '8110001', knowledgePointIds: ['8120001'] });
    await api.teacherCourses.retryKnowledgeDocument('8100001', '8300001');
    await api.teacherCourses.createInvitation('8100001');
    await api.teacherCourses.acceptInvitation('invite-token');
    await api.teacherCourses.remove('8100001');

    expect(calls).toEqual([
      ['get', '/api/employee/teacher/courses', undefined],
      ['get', '/api/employee/teacher/courses/8100001/learning-overview', undefined],
      ['post', '/api/employee/teacher/courses/8100001/students/8220002/private-conversation', undefined],
      ['post', '/api/employee/teacher/courses/8100001/classes/8200002/conversation', undefined],
      ['put', '/api/employee/teacher/courses/8100001/outline', { chapters: [], dependencies: [] }],
      ['put', '/api/employee/teacher/courses/8100001/chapters/8110001/article', { title: 'Servlet', contentJson: '{"type":"doc","content":[]}', status: 'DRAFT' }],
      ['post', '/api/employee/teacher/courses/8100001/resources/8200001/preview', undefined],
      ['get', '/api/employee/teacher/courses/8100001/resources/8200001/preview-status', undefined],
      ['get', '/api/employee/teacher/courses/8100001/knowledge-documents', undefined],
      ['post', '/api/employee/teacher/courses/8100001/knowledge-documents', { attachmentRefId: '8200001', chapterId: '8110001', knowledgePointIds: ['8120001'] }],
      ['post', '/api/employee/teacher/courses/8100001/knowledge-documents/8300001/retry', undefined],
      ['post', '/api/employee/teacher/courses/8100001/invitations', undefined],
      ['post', '/api/employee/teacher/courses/invitations/accept', { token: 'invite-token' }],
      ['delete', '/api/employee/teacher/courses/8100001', undefined],
    ]);
  });

  it('maps student generation and teacher resource governance endpoints', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.learningResources.tasks('8100001');
    await api.learningResources.createTask({
      courseId: '8100001',
      knowledgePointId: '8120001',
      idempotencyKey: 'resource-task-1',
      requestMessage: '结合代码案例学习',
    });
    await api.learningResources.retryTask('8800001');
    await api.learningResources.list({ courseId: '8100001', resourceType: 'CODE_LAB' });
    await api.learningResources.recordView('8810001');
    await api.learningResources.feedback('8810001');
    await api.learningResources.saveFeedback('8810001', 'TOO_DIFFICULT', '需要更基础的示例');
    await api.teacherLearningResources.tasks({ courseId: '8100001', status: 'RUNNING' });
    await api.teacherLearningResources.list({ courseId: '8100001', status: 'DRAFT' });
    await api.teacherLearningResources.update('8810001', { title: '标题', summary: '摘要', content: '正文' });
    await api.teacherLearningResources.review('8810001', 'APPROVE', '引用一致');
    await api.teacherLearningResources.publish('8810001');
    await api.teacherLearningResources.withdraw('8810001');
    await api.teacherLearningResources.push('8810001', 'CLASS', '9100001');

    expect(calls).toEqual([
      ['get', '/api/employee/learning-resources/tasks', { query: { courseId: '8100001' } }],
      ['post', '/api/employee/learning-resources/tasks', {
        courseId: '8100001', knowledgePointId: '8120001', idempotencyKey: 'resource-task-1', requestMessage: '结合代码案例学习',
      }],
      ['post', '/api/employee/learning-resources/tasks/8800001/retry', undefined],
      ['get', '/api/employee/learning-resources', { query: { courseId: '8100001', resourceType: 'CODE_LAB' } }],
      ['post', '/api/employee/learning-resources/8810001/view', undefined],
      ['get', '/api/employee/learning-resources/8810001/feedback', undefined],
      ['post', '/api/employee/learning-resources/8810001/feedback', { feedbackType: 'TOO_DIFFICULT', comment: '需要更基础的示例' }],
      ['get', '/api/employee/teacher/learning-resources/tasks', { query: { courseId: '8100001', status: 'RUNNING' } }],
      ['get', '/api/employee/teacher/learning-resources', { query: { courseId: '8100001', status: 'DRAFT' } }],
      ['put', '/api/employee/teacher/learning-resources/8810001', { title: '标题', summary: '摘要', content: '正文' }],
      ['post', '/api/employee/teacher/learning-resources/8810001/review', { decision: 'APPROVE', note: '引用一致' }],
      ['post', '/api/employee/teacher/learning-resources/8810001/publish', undefined],
      ['post', '/api/employee/teacher/learning-resources/8810001/withdraw', undefined],
      ['post', '/api/employee/teacher/learning-resources/8810001/pushes', { targetType: 'CLASS', targetId: '9100001' }],
    ]);
  });

  it('maps student learning dashboard endpoint', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.studentDashboard.current();

    expect(calls).toEqual([
      ['get', '/api/employee/student/learning-dashboard', undefined],
    ]);
  });

  it('maps auth endpoints to backend employee auth controller', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.auth.login({ loginName: 'lin.che', password: 'secret' });
    await api.auth.me();
    await api.auth.sendEmailCode({ email: 'lin.che@example.com', purpose: 'RESET_PASSWORD' });
    await api.auth.resetPassword({ email: 'lin.che@example.com', code: '123456', newPassword: 'Employee@654321' });
    await api.auth.logout();

    expect(calls).toEqual([
      ['post', '/api/employee/auth/login', { loginName: 'lin.che', password: 'secret' }],
      ['get', '/api/employee/auth/me', undefined],
      ['post', '/api/employee/auth/email/send-code', { email: 'lin.che@example.com', purpose: 'RESET_PASSWORD' }],
      ['post', '/api/employee/auth/password/reset', { email: 'lin.che@example.com', code: '123456', newPassword: 'Employee@654321' }],
      ['post', '/api/employee/auth/logout', undefined],
    ]);
  });

  it('maps workflow query and task actions to backend workflow controller', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.workflow.submitLeaveRequest({
      leaveTypeId: 1,
      startAt: '2026-05-01T09:00:00',
      endAt: '2026-05-01T18:00:00',
      durationMinutes: 480,
      reason: '年假',
    });
    await api.workflow.submitClockCorrectionRequest({
      targetDate: '2026-05-01',
      missingPunchType: 'CLOCK_IN',
      expectedPunchAt: '2026-05-01T09:00:00',
      reason: '补卡',
    });
    await api.workflow.submitGenericRequest({
      processCode: 'EXPENSE',
      title: '费用报销',
      summary: '客户拜访交通费',
      formPayload: { fields: [{ key: 'reason', label: '报销事由', value: '客户拜访交通费' }] },
      attachmentRefIds: [101],
    });
    await api.workflow.todos();
    await api.workflow.requests({ pageNo: 2, pageSize: 20, status: 'PENDING' });
    await api.workflow.approveTask('TASK-1', { comment: '同意' });
    await api.workflow.rejectTask('TASK-2', { comment: '不同意' });
    await api.workflow.withdrawRequest('2053928814116044802', { comment: '撤回' });
    await api.workflow.requestDetail('2053928814116044802');
    await api.workflow.requestActions('2053928814116044802');
    await api.workflow.createAttachmentDownloadTicket('2053928814116044802', '2053928981082898444', { downloadType: 'SINGLE' });

    expect(calls).toEqual([
      ['post', '/api/employee/workflow/leave-requests', {
        leaveTypeId: 1,
        startAt: '2026-05-01T09:00:00',
        endAt: '2026-05-01T18:00:00',
        durationMinutes: 480,
        reason: '年假',
      }],
      ['post', '/api/employee/workflow/clock-correction-requests', {
        targetDate: '2026-05-01',
        missingPunchType: 'CLOCK_IN',
        expectedPunchAt: '2026-05-01T09:00:00',
        reason: '补卡',
      }],
      ['post', '/api/employee/workflow/generic-requests', {
        processCode: 'EXPENSE',
        title: '费用报销',
        summary: '客户拜访交通费',
        formPayload: { fields: [{ key: 'reason', label: '报销事由', value: '客户拜访交通费' }] },
        attachmentRefIds: [101],
      }],
      ['get', '/api/employee/workflow/todos', undefined],
      ['get', '/api/employee/workflow/requests', { query: { pageNo: 2, pageSize: 20, status: 'PENDING' } }],
      ['post', '/api/employee/workflow/tasks/TASK-1/approve', { comment: '同意' }],
      ['post', '/api/employee/workflow/tasks/TASK-2/reject', { comment: '不同意' }],
      ['post', '/api/employee/workflow/requests/2053928814116044802/withdraw', { comment: '撤回' }],
      ['get', '/api/employee/workflow/requests/2053928814116044802', undefined],
      ['get', '/api/employee/workflow/requests/2053928814116044802/actions', undefined],
      ['post', '/api/employee/workflow/requests/2053928814116044802/attachments/2053928981082898444/download-tickets', { downloadType: 'SINGLE' }],
    ]);
    expect(api.workflow.downloadAttachmentUrl('WF-1')).toBe('/api/employee/workflow/attachments/download/WF-1');
  });

  it('maps attendance, leave and file endpoints without mock-only paths', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.attendance.exceptions({ startDate: '2026-05-01', endDate: '2026-05-09' });
    await api.attendance.dailyResults({ startDate: '2026-05-01', endDate: '2026-05-09' });
    await api.attendance.teamDailyResults({ userId: 42, startDate: '2026-05-01', endDate: '2026-05-09' });
    await api.attendance.punch({ punchType: 'ON_DUTY', sourceType: 'PC' });
    await api.leave.accounts();
    await api.leave.types();
    await api.files.list({ pageNo: 1, pageSize: 20 });
    await api.files.recycleBin({ pageNo: 1, pageSize: 20 });
    await api.files.quota();
    await api.files.createUploadTicket({
      ownerType: 'PERSONAL_FILE',
      ownerId: null,
      folderId: 'folder-1',
      fileName: '个人文件.pdf',
      mimeType: 'application/pdf',
      fileSize: 1024,
      contentSha256: 'a'.repeat(64),
    });
    await api.files.confirmUpload({
      uploadTicketId: 'ticket-1',
      objectKey: 'uploads/a.pdf',
      contentSha256: 'a'.repeat(64),
      fileSize: 1024,
      ownerType: 'PERSONAL_FILE',
      ownerId: null,
      folderId: 'folder-1',
      displayName: '个人文件.pdf',
    });
    await api.files.browser({ folderId: 'folder-1', keyword: '个人', fileType: 'document' });
    await api.files.folders();
    await api.files.createFolder({ parentFolderId: 'folder-1', folderName: '报销' });
    await api.files.move('2053928981082898444', { targetFolderId: 'folder-1' });
    await api.files.copy('2053928981082898444', { targetFolderId: 'folder-1', displayName: '个人文件副本.pdf' });
    await api.files.createDownloadTicket('2053928981082898444', { downloadType: 'PERSONAL_FILE', reason: '查看材料' });
    await api.files.trash('2053928981082898444', { reason: '删除' });
    await api.files.restore('2053928981082898444', { reason: '恢复' });
    await api.files.purge('2053928981082898444');

    expect(calls).toEqual([
      ['get', '/api/employee/attendance/exceptions', { query: { startDate: '2026-05-01', endDate: '2026-05-09' } }],
      ['get', '/api/employee/attendance/daily-results', { query: { startDate: '2026-05-01', endDate: '2026-05-09' } }],
      ['get', '/api/employee/attendance/team/daily-results', { query: { userId: 42, startDate: '2026-05-01', endDate: '2026-05-09' } }],
      ['post', '/api/employee/attendance/punch', { punchType: 'ON_DUTY', sourceType: 'PC' }],
      ['get', '/api/employee/leave/accounts', undefined],
      ['get', '/api/employee/leave/types', undefined],
      ['get', '/api/employee/files', { query: { pageNo: 1, pageSize: 20 } }],
      ['get', '/api/employee/files/recycle-bin', { query: { pageNo: 1, pageSize: 20 } }],
      ['get', '/api/employee/files/quota', undefined],
      ['post', '/api/employee/files/upload-tickets', {
        ownerType: 'PERSONAL_FILE',
        ownerId: null,
        folderId: 'folder-1',
        fileName: '个人文件.pdf',
        mimeType: 'application/pdf',
        fileSize: 1024,
        contentSha256: 'a'.repeat(64),
      }],
      ['post', '/api/employee/files/confirm-upload', {
        uploadTicketId: 'ticket-1',
        objectKey: 'uploads/a.pdf',
        contentSha256: 'a'.repeat(64),
        fileSize: 1024,
        ownerType: 'PERSONAL_FILE',
        ownerId: null,
        folderId: 'folder-1',
        displayName: '个人文件.pdf',
      }],
      ['get', '/api/employee/files/browser', { query: { folderId: 'folder-1', keyword: '个人', fileType: 'document' } }],
      ['get', '/api/employee/files/folders', undefined],
      ['post', '/api/employee/files/folders', { parentFolderId: 'folder-1', folderName: '报销' }],
      ['post', '/api/employee/files/2053928981082898444/move', { targetFolderId: 'folder-1' }],
      ['post', '/api/employee/files/2053928981082898444/copy', { targetFolderId: 'folder-1', displayName: '个人文件副本.pdf' }],
      ['post', '/api/employee/files/2053928981082898444/download-tickets', { downloadType: 'PERSONAL_FILE', reason: '查看材料' }],
      ['post', '/api/employee/files/2053928981082898444/trash', { reason: '删除' }],
      ['post', '/api/employee/files/2053928981082898444/restore', { reason: '恢复' }],
      ['delete', '/api/employee/files/2053928981082898444', undefined],
    ]);
    expect(api.files.downloadUrl('DL-1')).toBe('/api/employee/files/download/DL-1');
  });

  it('maps IM conversations and messages to backend IM controller', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.im.conversations({ beforeLastMessageAt: '2026-05-12T09:30:00', beforeConversationId: 7001, size: 20 });
    await api.im.conversationDetail(7001);
    await api.im.messages(7001, { beforeSeq: 20, size: 10 });
    await api.im.startPrivateConversation({ targetUserId: 2002 });
    await api.im.createGroupConversation({ title: '项目推进群', memberUserIds: [2002, 2004] });
    await api.im.sendMessage(7001, {
      messageType: 'TEXT',
      text: '我下午两点同步。',
      clientMessageId: 'client-msg-001',
    });
    await api.im.updateReadPosition(7001, { lastReadSeq: 3 });

    expect(calls).toEqual([
      ['get', '/api/employee/im/conversations', { query: { beforeLastMessageAt: '2026-05-12T09:30:00', beforeConversationId: 7001, size: 20 } }],
      ['get', '/api/employee/im/conversations/7001', undefined],
      ['get', '/api/employee/im/conversations/7001/messages', { query: { beforeSeq: 20, size: 10 } }],
      ['post', '/api/employee/im/private-conversations', { targetUserId: 2002 }],
      ['post', '/api/employee/im/group-conversations', { title: '项目推进群', memberUserIds: [2002, 2004] }],
      ['post', '/api/employee/im/conversations/7001/messages', {
        messageType: 'TEXT',
        text: '我下午两点同步。',
        clientMessageId: 'client-msg-001',
      }],
      ['post', '/api/employee/im/conversations/7001/read-position', { lastReadSeq: 3 }],
    ]);
  });

  it('maps new IM and Agent endpoints that are implemented by backend controllers', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.me.profile();
    await api.me.updateProfile({ mobile: '13900002202', avatarAttachmentRefId: '2053928981082898444' });
    await api.me.manager();
    await api.me.subordinates();
    await api.im.groupMembers(7001);
    await api.im.groupProfile(7001);
    await api.im.updateGroupProfile(7001, { title: '新群名', noticeText: '公告' });
    await api.im.addGroupMembers(7001, { memberUserIds: [2002] });
    await api.im.removeGroupMembers(7001, { memberUserIds: [2002] });
    await api.im.leaveGroup(7001);
    await api.im.dismissGroup(7001);
    await api.im.groupFiles(7001);
    await api.im.createGroupFileDownloadTicket(7001, '2053928981082898444', { downloadType: 'SINGLE', reason: '预览' });
    await api.im.trashGroupFile(7001, '2053928981082898444', { reason: '删除' });
    await api.im.updatePreference(7001, { pin: true });
    await api.im.startAgentConversation({ agentId: 9001 });
    await api.im.editMessage(7001, 7101, { text: '更新后的消息' });
    await api.im.recallMessage(7001, 7101);
    await api.im.translateMessage(7001, 7101);
    await api.im.forwardMessage(7001, 7101, { targetConversationId: 7002 });
    await api.im.favoriteMessage(7001, 7101);
    await api.im.remindMessage(7001, 7101, { note: '提醒' });
    await api.im.deleteMessage(7001, 7101);
    await api.im.createMessageAttachmentDownloadTicket('2053928814116044802', '2053928814116044811', '2053928981082898444', { downloadType: 'SINGLE' });
    await api.agents.list({ keyword: 'HR' });

    expect(calls).toEqual([
      ['get', '/api/employee/me/profile', undefined],
      ['put', '/api/employee/me/profile', { mobile: '13900002202', avatarAttachmentRefId: '2053928981082898444' }],
      ['get', '/api/employee/me/manager', undefined],
      ['get', '/api/employee/me/subordinates', undefined],
      ['get', '/api/employee/im/group-conversations/7001/members', undefined],
      ['get', '/api/employee/im/group-conversations/7001/profile', undefined],
      ['post', '/api/employee/im/group-conversations/7001/profile', { title: '新群名', noticeText: '公告' }],
      ['post', '/api/employee/im/group-conversations/7001/members', { memberUserIds: [2002] }],
      ['post', '/api/employee/im/group-conversations/7001/members/remove', { memberUserIds: [2002] }],
      ['post', '/api/employee/im/group-conversations/7001/leave', undefined],
      ['post', '/api/employee/im/group-conversations/7001/dismiss', undefined],
      ['get', '/api/employee/im/group-conversations/7001/files', undefined],
      ['post', '/api/employee/im/group-conversations/7001/files/2053928981082898444/download-tickets', { downloadType: 'SINGLE', reason: '预览' }],
      ['post', '/api/employee/im/group-conversations/7001/files/2053928981082898444/trash', { reason: '删除' }],
      ['post', '/api/employee/im/conversations/7001/preference', { pin: true }],
      ['post', '/api/employee/im/agent-conversations', { agentId: 9001 }],
      ['post', '/api/employee/im/conversations/7001/messages/7101/edit', { text: '更新后的消息' }],
      ['post', '/api/employee/im/conversations/7001/messages/7101/recall', undefined],
      ['post', '/api/employee/im/conversations/7001/messages/7101/translate', undefined],
      ['post', '/api/employee/im/conversations/7001/messages/7101/forward', { targetConversationId: 7002 }],
      ['post', '/api/employee/im/conversations/7001/messages/7101/favorite', undefined],
      ['post', '/api/employee/im/conversations/7001/messages/7101/reminder', { note: '提醒' }],
      ['post', '/api/employee/im/conversations/7001/messages/7101/delete', undefined],
      ['post', '/api/employee/im/conversations/2053928814116044802/messages/2053928814116044811/attachments/2053928981082898444/download-tickets', { downloadType: 'SINGLE' }],
      ['get', '/api/employee/agents', { query: { keyword: 'HR' } }],
    ]);
    expect(api.im.downloadAttachmentUrl('IM-1')).toBe('/api/employee/im/attachments/download/IM-1');
  });

  it('allows profile updates to preserve the current avatar ref', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.me.updateProfile({ mobile: '13900002202', avatarAttachmentRefId: '2053928981082898444' });

    expect(calls).toEqual([
      ['put', '/api/employee/me/profile', { mobile: '13900002202', avatarAttachmentRefId: '2053928981082898444' }],
    ]);
  });
});
