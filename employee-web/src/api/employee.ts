import { defaultApiClient, type ApiClient } from './http';
import type {
  AttendanceDateRangeQuery,
  AttendanceDailyResultResponse,
  AttendanceExceptionResponse,
  AssessmentAttemptResponse,
  AssessmentResponse,
  AssessmentSubmitRequest,
  AgentResponse,
  AttachmentRefResponse,
  ClockCorrectionRequestCreateRequest,
  ConfirmUploadRequest,
  CourseResponse,
  CourseArticleSaveRequest,
  CourseInvitationResponse,
  CourseKnowledgeDocumentRequest,
  CourseKnowledgeDocumentResponse,
  CourseLearningOverviewResponse,
  CourseResourcePreviewResponse,
  CourseOutlineSaveRequest,
  CourseSaveRequest,
  CreateLearningResourceTaskRequest,
  CopyEmployeeFileRequest,
  CreateEmployeeFileFolderRequest,
  CreateGroupConversationRequest,
  CreateDownloadTicketRequest,
  CreatePunchRecordRequest,
  CreateUploadTicketRequest,
  CurrentUserResponse,
  DownloadTicketResponse,
  EditImMessageRequest,
  EmployeeFileBrowserResponse,
  EmployeeFileFolderResponse,
  EmployeeFileResponse,
  EmployeePreferenceResponse,
  EntityId,
  FileListQuery,
  FilePurgeResponse,
  FileRestoreResponse,
  ImMessageListQuery,
  FileTrashRequest,
  FileTrashResponse,
  ForwardImMessageRequest,
  FriendRequest,
  GenericWorkflowRequestCreateRequest,
  GroupMemberUserIdsRequest,
  ImConversationListQuery,
  ImConversationDetailResponse,
  ImGroupFileResponse,
  ImConversationMemberResponse,
  ImConversationSummaryResponse,
  ImGroupProfileResponse,
  ImMessageActionResponse,
  ImMessageResponse,
  LeaveRequestCreateRequest,
  LeaveAccountResponse,
  LeaveTypeResponse,
  LearningPathResponse,
  LearningResourceFeedbackResponse,
  LearningResourceFeedbackType,
  LearningResourceTaskResponse,
  GeneratedLearningResourceResponse,
  LearningResourcePushResponse,
  LearningResourceUpdateRequest,
  LoginSendCodeRequest,
  LoginSendCodeResponse,
  LoginRequest,
  LoginResponse,
  MoveEmployeeFileRequest,
  OrgDepartmentTreeNodeResponse,
  OrgUserDetailResponse,
  OrgUserSummaryResponse,
  PageResult,
  PunchRecordResponse,
  QuotaResponse,
  ResetPasswordRequest,
  RemindImMessageRequest,
  SendImMessageRequest,
  SendEmailCodeRequest,
  StartAgentConversationRequest,
  StartPrivateConversationRequest,
  StudentLearningDashboardResponse,
  StudentProfileResponse,
  TranslateImMessageResponse,
  UpdateConversationPreferenceRequest,
  UpdateEmployeePreferenceRequest,
  UpdateEmployeeProfileRequest,
  UpdateGroupConversationRequest,
  UpdateReadPositionRequest,
  UploadTicketResponse,
  WorkflowRequestListQuery,
  WorkflowRequestCreateResponse,
  WorkflowRequestDetailResponse,
  WorkflowRequestStatusResponse,
  WorkflowRequestSummaryResponse,
  WorkflowTaskActionResponse,
  WorkflowTaskApproveRequest,
  WorkflowTaskRejectRequest,
  WorkflowRequestWithdrawRequest,
  WorkflowTodoResponse,
} from './types';

// 雪花 ID 可能是 BigInt，统一转字符串再 URL 编码
const pathId = (value: EntityId) => encodeURIComponent(String(value));

export function createEmployeeApi(client: ApiClient = defaultApiClient) {
  return {
    auth: {
      login: (request: LoginRequest) => client.post<LoginResponse>('/api/employee/auth/login', request),
      sendLoginCode: (request: LoginSendCodeRequest) => client.post<LoginSendCodeResponse>('/api/employee/auth/login/send-code', request),
      logout: () => client.post<void>('/api/employee/auth/logout'),
      me: () => client.get<CurrentUserResponse>('/api/employee/auth/me'),
      sendEmailCode: (request: SendEmailCodeRequest) => client.post<void>('/api/employee/auth/email/send-code', request),
      bindEmail: (request: { email: string; code: string }) => client.post<void>('/api/employee/auth/email/bind', request),
      resetPassword: (request: ResetPasswordRequest) => client.post<void>('/api/employee/auth/password/reset', request),
    },
    courses: {
      list: () => client.get<CourseResponse[]>('/api/employee/courses'),
      detail: (courseId: EntityId) => client.get<CourseResponse>(`/api/employee/courses/${pathId(courseId)}`),
      enroll: (courseId: EntityId) => client.post<CourseResponse>(`/api/employee/courses/${pathId(courseId)}/enrollment`),
      assessments: (courseId: EntityId) => client.get<AssessmentResponse[]>(`/api/employee/courses/${pathId(courseId)}/assessments`),
      assessmentDetail: (courseId: EntityId, assessmentId: EntityId) => client.get<AssessmentResponse>(`/api/employee/courses/${pathId(courseId)}/assessments/${pathId(assessmentId)}`),
      submitAssessment: (courseId: EntityId, assessmentId: EntityId, request: AssessmentSubmitRequest) => client.post<AssessmentAttemptResponse>(`/api/employee/courses/${pathId(courseId)}/assessments/${pathId(assessmentId)}/attempts`, request),
      learningPath: (courseId: EntityId) => client.get<LearningPathResponse>(`/api/employee/courses/${pathId(courseId)}/learning-path`),
      completeLearningPathNode: (courseId: EntityId, nodeId: EntityId) => client.post<LearningPathResponse>(`/api/employee/courses/${pathId(courseId)}/learning-path/nodes/${pathId(nodeId)}/complete`),
      resourceUrl: (attachmentRefId: EntityId) => `/api/employee/courses/resources/${pathId(attachmentRefId)}`,
      resourcePreviewUrl: (attachmentRefId: EntityId) => `/api/employee/courses/resources/${pathId(attachmentRefId)}/preview`,
    },
    studentProfile: {
      current: () => client.get<StudentProfileResponse>('/api/employee/student/profile'),
      answer: (message: string) => client.post<StudentProfileResponse>('/api/employee/student/profile/messages', { message }),
      correct: (dimensionCode: string, conclusion: string) => client.put<StudentProfileResponse>(`/api/employee/student/profile/dimensions/${encodeURIComponent(dimensionCode)}`, { conclusion }),
    },
    studentDashboard: {
      current: () => client.get<StudentLearningDashboardResponse>('/api/employee/student/learning-dashboard'),
    },
    learningResources: {
      tasks: (courseId?: EntityId) => client.get<LearningResourceTaskResponse[]>('/api/employee/learning-resources/tasks', { query: { courseId } }),
      task: (taskId: EntityId) => client.get<LearningResourceTaskResponse>(`/api/employee/learning-resources/tasks/${pathId(taskId)}`),
      taskEventsUrl: (taskId: EntityId) => `/api/employee/learning-resources/tasks/${pathId(taskId)}/events`,
      createTask: (request: CreateLearningResourceTaskRequest) => client.post<LearningResourceTaskResponse>('/api/employee/learning-resources/tasks', request),
      retryTask: (taskId: EntityId) => client.post<LearningResourceTaskResponse>(`/api/employee/learning-resources/tasks/${pathId(taskId)}/retry`),
      list: (query: { courseId?: EntityId; resourceType?: string } = {}) => client.get<GeneratedLearningResourceResponse[]>('/api/employee/learning-resources', { query }),
      detail: (resourceId: EntityId) => client.get<GeneratedLearningResourceResponse>(`/api/employee/learning-resources/${pathId(resourceId)}`),
      recordView: (resourceId: EntityId) => client.post<void>(`/api/employee/learning-resources/${pathId(resourceId)}/view`),
      feedback: (resourceId: EntityId) => client.get<LearningResourceFeedbackResponse | null>(`/api/employee/learning-resources/${pathId(resourceId)}/feedback`),
      saveFeedback: (resourceId: EntityId, feedbackType: LearningResourceFeedbackType, comment?: string) => client.post<LearningResourceFeedbackResponse>(`/api/employee/learning-resources/${pathId(resourceId)}/feedback`, { feedbackType, comment }),
    },
    teacherCourses: {
      list: () => client.get<CourseResponse[]>('/api/employee/teacher/courses'),
      detail: (courseId: EntityId) => client.get<CourseResponse>(`/api/employee/teacher/courses/${pathId(courseId)}`),
      learningOverview: (courseId: EntityId) => client.get<CourseLearningOverviewResponse>(`/api/employee/teacher/courses/${pathId(courseId)}/learning-overview`),
      startStudentConversation: (courseId: EntityId, studentUserId: EntityId) => client.post<ImConversationSummaryResponse>(`/api/employee/teacher/courses/${pathId(courseId)}/students/${pathId(studentUserId)}/private-conversation`),
      openClassGroup: (courseId: EntityId, classId: EntityId) => client.post<ImConversationSummaryResponse>(`/api/employee/teacher/courses/${pathId(courseId)}/classes/${pathId(classId)}/conversation`),
      create: (request: CourseSaveRequest) => client.post<CourseResponse>('/api/employee/teacher/courses', request),
      update: (courseId: EntityId, request: CourseSaveRequest) => client.put<CourseResponse>(`/api/employee/teacher/courses/${pathId(courseId)}`, request),
      saveOutline: (courseId: EntityId, request: CourseOutlineSaveRequest) => client.put<CourseResponse>(`/api/employee/teacher/courses/${pathId(courseId)}/outline`, request),
      saveArticle: (courseId: EntityId, chapterId: EntityId, request: CourseArticleSaveRequest) => client.put<CourseResponse>(`/api/employee/teacher/courses/${pathId(courseId)}/chapters/${pathId(chapterId)}/article`, request),
      prepareResourcePreview: (courseId: EntityId, attachmentRefId: EntityId) => client.post<CourseResourcePreviewResponse>(`/api/employee/teacher/courses/${pathId(courseId)}/resources/${pathId(attachmentRefId)}/preview`),
      resourcePreviewStatus: (courseId: EntityId, attachmentRefId: EntityId) => client.get<CourseResourcePreviewResponse>(`/api/employee/teacher/courses/${pathId(courseId)}/resources/${pathId(attachmentRefId)}/preview-status`),
      knowledgeDocuments: (courseId: EntityId) => client.get<CourseKnowledgeDocumentResponse[]>(`/api/employee/teacher/courses/${pathId(courseId)}/knowledge-documents`),
      addKnowledgeDocument: (courseId: EntityId, request: CourseKnowledgeDocumentRequest) => client.post<CourseKnowledgeDocumentResponse>(`/api/employee/teacher/courses/${pathId(courseId)}/knowledge-documents`, request),
      retryKnowledgeDocument: (courseId: EntityId, documentId: EntityId) => client.post<CourseKnowledgeDocumentResponse>(`/api/employee/teacher/courses/${pathId(courseId)}/knowledge-documents/${pathId(documentId)}/retry`),
      remove: (courseId: EntityId) => client.delete<void>(`/api/employee/teacher/courses/${pathId(courseId)}`),
      createInvitation: (courseId: EntityId) => client.post<CourseInvitationResponse>(`/api/employee/teacher/courses/${pathId(courseId)}/invitations`),
      acceptInvitation: (token: string) => client.post<CourseResponse>('/api/employee/teacher/courses/invitations/accept', { token }),
    },
    teacherLearningResources: {
      tasks: (query: { courseId?: EntityId; status?: string } = {}) => client.get<LearningResourceTaskResponse[]>('/api/employee/teacher/learning-resources/tasks', { query }),
      task: (taskId: EntityId) => client.get<LearningResourceTaskResponse>(`/api/employee/teacher/learning-resources/tasks/${pathId(taskId)}`),
      list: (query: { courseId?: EntityId; knowledgePointId?: EntityId; resourceType?: string; status?: string } = {}) => client.get<GeneratedLearningResourceResponse[]>('/api/employee/teacher/learning-resources', { query }),
      detail: (resourceId: EntityId) => client.get<GeneratedLearningResourceResponse>(`/api/employee/teacher/learning-resources/${pathId(resourceId)}`),
      update: (resourceId: EntityId, request: LearningResourceUpdateRequest) => client.put<GeneratedLearningResourceResponse>(`/api/employee/teacher/learning-resources/${pathId(resourceId)}`, request),
      review: (resourceId: EntityId, decision: 'APPROVE' | 'REJECT', note?: string) => client.post<GeneratedLearningResourceResponse>(`/api/employee/teacher/learning-resources/${pathId(resourceId)}/review`, { decision, note }),
      publish: (resourceId: EntityId) => client.post<GeneratedLearningResourceResponse>(`/api/employee/teacher/learning-resources/${pathId(resourceId)}/publish`),
      withdraw: (resourceId: EntityId) => client.post<GeneratedLearningResourceResponse>(`/api/employee/teacher/learning-resources/${pathId(resourceId)}/withdraw`),
      push: (resourceId: EntityId, targetType: 'STUDENT' | 'CLASS', targetId: EntityId) => client.post<LearningResourcePushResponse>(`/api/employee/teacher/learning-resources/${pathId(resourceId)}/pushes`, { targetType, targetId }),
    },
    me: {
      profile: () => client.get<OrgUserDetailResponse>('/api/employee/me/profile'),
      updateProfile: (request: UpdateEmployeeProfileRequest) => client.put<OrgUserDetailResponse>('/api/employee/me/profile', request),
      manager: () => client.get<OrgUserSummaryResponse | null>('/api/employee/me/manager'),
      subordinates: () => client.get<OrgUserSummaryResponse[]>('/api/employee/me/subordinates'),
      colleagues: () => client.get<OrgUserSummaryResponse[]>('/api/employee/me/colleagues'),
      preferences: () => client.get<EmployeePreferenceResponse>('/api/employee/me/preferences'),
      updatePreferences: (request: UpdateEmployeePreferenceRequest) => client.put<EmployeePreferenceResponse>('/api/employee/me/preferences', request),
    },
    contacts: {
      friends: () => client.get<OrgUserSummaryResponse[]>('/api/employee/contacts/friends'),
      friendRequests: () => client.get<OrgUserSummaryResponse[]>('/api/employee/contacts/friends/requests'),
      addFriend: (userId: EntityId, request: FriendRequest = {}) => client.post<OrgUserSummaryResponse>(`/api/employee/contacts/friends/${pathId(userId)}/requests`, request),
      acceptFriendRequest: (userId: EntityId) => client.post<OrgUserSummaryResponse>(`/api/employee/contacts/friends/requests/${pathId(userId)}/accept`),
      removeFriend: (userId: EntityId) => client.delete<void>(`/api/employee/contacts/friends/${pathId(userId)}`),
      updateFriendRemark: (userId: EntityId, request: FriendRequest) => client.put<OrgUserSummaryResponse>(`/api/employee/contacts/friends/${pathId(userId)}`, request),
      deptTree: () => client.get<OrgDepartmentTreeNodeResponse[]>('/api/employee/contacts/dept-tree'),
    },
    workflow: {
      submitLeaveRequest: (request: LeaveRequestCreateRequest) => client.post<WorkflowRequestCreateResponse>('/api/employee/workflow/leave-requests', request),
      submitClockCorrectionRequest: (request: ClockCorrectionRequestCreateRequest) => client.post<WorkflowRequestCreateResponse>('/api/employee/workflow/clock-correction-requests', request),
      submitGenericRequest: (request: GenericWorkflowRequestCreateRequest) => client.post<WorkflowRequestCreateResponse>('/api/employee/workflow/generic-requests', request),
      todos: () => client.get<WorkflowTodoResponse[]>('/api/employee/workflow/todos'),
      requests: (query: WorkflowRequestListQuery = {}) => client.get<PageResult<WorkflowRequestSummaryResponse>>('/api/employee/workflow/requests', { query }),
      approveTask: (taskId: string, request: WorkflowTaskApproveRequest) => client.post<WorkflowRequestStatusResponse>(`/api/employee/workflow/tasks/${encodeURIComponent(taskId)}/approve`, request),
      rejectTask: (taskId: string, request: WorkflowTaskRejectRequest) => client.post<WorkflowRequestStatusResponse>(`/api/employee/workflow/tasks/${encodeURIComponent(taskId)}/reject`, request),
      withdrawRequest: (requestId: EntityId, request: WorkflowRequestWithdrawRequest) => client.post<WorkflowRequestStatusResponse>(`/api/employee/workflow/requests/${pathId(requestId)}/withdraw`, request),
      requestDetail: (requestId: EntityId) => client.get<WorkflowRequestDetailResponse>(`/api/employee/workflow/requests/${pathId(requestId)}`),
      requestActions: (requestId: EntityId) => client.get<WorkflowTaskActionResponse[]>(`/api/employee/workflow/requests/${pathId(requestId)}/actions`),
      createAttachmentDownloadTicket: (requestId: EntityId, attachmentRefId: EntityId, request: CreateDownloadTicketRequest) => (
        client.post<DownloadTicketResponse>(`/api/employee/workflow/requests/${pathId(requestId)}/attachments/${pathId(attachmentRefId)}/download-tickets`, request)
      ),
      downloadAttachmentUrl: (ticketNo: string) => `/api/employee/workflow/attachments/download/${encodeURIComponent(ticketNo)}`,
    },
    attendance: {
      punch: (request: CreatePunchRecordRequest) => client.post<PunchRecordResponse>('/api/employee/attendance/punch', request),
      dailyResults: (query: AttendanceDateRangeQuery = {}) => client.get<AttendanceDailyResultResponse[]>('/api/employee/attendance/daily-results', { query }),
      exceptions: (query: AttendanceDateRangeQuery = {}) => client.get<AttendanceExceptionResponse[]>('/api/employee/attendance/exceptions', { query }),
      teamDailyResults: (query: AttendanceDateRangeQuery & { userId?: EntityId } = {}) => client.get<AttendanceDailyResultResponse[]>('/api/employee/attendance/team/daily-results', { query }),
    },
    leave: {
      accounts: () => client.get<LeaveAccountResponse[]>('/api/employee/leave/accounts'),
      types: () => client.get<LeaveTypeResponse[]>('/api/employee/leave/types'),
    },
    im: {
      conversations: (query: ImConversationListQuery = {}) => client.get<ImConversationSummaryResponse[]>('/api/employee/im/conversations', { query }),
      conversationDetail: (conversationId: string | number) => client.get<ImConversationDetailResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}`),
      groupMembers: (conversationId: string | number) => (
        client.get<ImConversationMemberResponse[]>(`/api/employee/im/group-conversations/${encodeURIComponent(conversationId)}/members`)
      ),
      groupProfile: (conversationId: string | number) => (
        client.get<ImGroupProfileResponse>(`/api/employee/im/group-conversations/${encodeURIComponent(conversationId)}/profile`)
      ),
      groupFiles: (conversationId: string | number) => (
        client.get<ImGroupFileResponse[]>(`/api/employee/im/group-conversations/${encodeURIComponent(conversationId)}/files`)
      ),
      createGroupFileDownloadTicket: (
        conversationId: string | number,
        attachmentRefId: EntityId,
        request: CreateDownloadTicketRequest,
      ) => (
        client.post<DownloadTicketResponse>(
          `/api/employee/im/group-conversations/${encodeURIComponent(String(conversationId))}/files/${pathId(attachmentRefId)}/download-tickets`,
          request,
        )
      ),
      trashGroupFile: (conversationId: string | number, attachmentRefId: EntityId, request: FileTrashRequest) => (
        client.post<FileTrashResponse>(
          `/api/employee/im/group-conversations/${encodeURIComponent(String(conversationId))}/files/${pathId(attachmentRefId)}/trash`,
          request,
        )
      ),
      updateGroupProfile: (conversationId: string | number, request: UpdateGroupConversationRequest) => (
        client.post<ImGroupProfileResponse>(`/api/employee/im/group-conversations/${encodeURIComponent(conversationId)}/profile`, request)
      ),
      addGroupMembers: (conversationId: string | number, request: GroupMemberUserIdsRequest) => (
        client.post<ImConversationMemberResponse[]>(`/api/employee/im/group-conversations/${encodeURIComponent(conversationId)}/members`, request)
      ),
      removeGroupMembers: (conversationId: string | number, request: GroupMemberUserIdsRequest) => (
        client.post<ImConversationMemberResponse[]>(`/api/employee/im/group-conversations/${encodeURIComponent(conversationId)}/members/remove`, request)
      ),
      leaveGroup: (conversationId: string | number) => (
        client.post<ImConversationMemberResponse[]>(`/api/employee/im/group-conversations/${encodeURIComponent(conversationId)}/leave`)
      ),
      dismissGroup: (conversationId: string | number) => (
        client.post<void>(`/api/employee/im/group-conversations/${encodeURIComponent(conversationId)}/dismiss`)
      ),
      messages: (conversationId: string | number, query: ImMessageListQuery = {}) => (
        client.get<ImMessageResponse[]>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/messages`, { query })
      ),
      startPrivateConversation: (request: StartPrivateConversationRequest) => (
        client.post<ImConversationSummaryResponse>('/api/employee/im/private-conversations', request)
      ),
      createGroupConversation: (request: CreateGroupConversationRequest) => (
        client.post<ImConversationSummaryResponse>('/api/employee/im/group-conversations', request)
      ),
      startAgentConversation: (request: StartAgentConversationRequest) => (
        client.post<ImConversationSummaryResponse>('/api/employee/im/agent-conversations', request)
      ),
      sendMessage: (conversationId: string | number, request: SendImMessageRequest) => (
        client.post<ImMessageResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/messages`, request)
      ),
      editMessage: (conversationId: string | number, messageId: string | number, request: EditImMessageRequest) => (
        client.post<ImMessageResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(messageId)}/edit`, request)
      ),
      recallMessage: (conversationId: string | number, messageId: string | number) => (
        client.post<ImMessageResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(messageId)}/recall`)
      ),
      translateMessage: (conversationId: string | number, messageId: string | number) => (
        client.post<TranslateImMessageResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(messageId)}/translate`)
      ),
      forwardMessage: (conversationId: string | number, messageId: string | number, request: ForwardImMessageRequest) => (
        client.post<ImMessageResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(messageId)}/forward`, request)
      ),
      favoriteMessage: (conversationId: string | number, messageId: string | number) => (
        client.post<ImMessageActionResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(messageId)}/favorite`)
      ),
      remindMessage: (conversationId: string | number, messageId: string | number, request: RemindImMessageRequest = {}) => (
        client.post<ImMessageActionResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(messageId)}/reminder`, request)
      ),
      deleteMessage: (conversationId: string | number, messageId: string | number) => (
        client.post<ImMessageActionResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(messageId)}/delete`)
      ),
      createMessageAttachmentDownloadTicket: (
        conversationId: string | number,
        messageId: string | number,
        attachmentRefId: EntityId,
        request: CreateDownloadTicketRequest,
      ) => (
        client.post<DownloadTicketResponse>(
          `/api/employee/im/conversations/${encodeURIComponent(String(conversationId))}/messages/${encodeURIComponent(String(messageId))}/attachments/${pathId(attachmentRefId)}/download-tickets`,
          request,
        )
      ),
      updateReadPosition: (conversationId: string | number, request: UpdateReadPositionRequest) => (
        client.post<ImConversationSummaryResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/read-position`, request)
      ),
      updatePreference: (conversationId: string | number, request: UpdateConversationPreferenceRequest) => (
        client.post<ImConversationSummaryResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/preference`, request)
      ),
      downloadAttachmentUrl: (ticketNo: string) => `/api/employee/im/attachments/download/${encodeURIComponent(ticketNo)}`,
    },
    agents: {
      list: (query: { keyword?: string } = {}) => client.get<AgentResponse[]>('/api/employee/agents', { query }),
    },
    files: {
      list: (query: FileListQuery = {}) => client.get<PageResult<EmployeeFileResponse>>('/api/employee/files', { query }),
      recycleBin: (query: FileListQuery = {}) => client.get<PageResult<EmployeeFileResponse>>('/api/employee/files/recycle-bin', { query }),
      browser: (query: FileListQuery = {}) => client.get<EmployeeFileBrowserResponse>('/api/employee/files/browser', { query }),
      folders: () => client.get<EmployeeFileFolderResponse[]>('/api/employee/files/folders'),
      createFolder: (request: CreateEmployeeFileFolderRequest) => client.post<EmployeeFileFolderResponse>('/api/employee/files/folders', request),
      createUploadTicket: (request: CreateUploadTicketRequest) => client.post<UploadTicketResponse>('/api/employee/files/upload-tickets', request),
      confirmUpload: (request: ConfirmUploadRequest) => client.post<AttachmentRefResponse>('/api/employee/files/confirm-upload', request),
      quota: () => client.get<QuotaResponse>('/api/employee/files/quota'),
      createDownloadTicket: (attachmentRefId: EntityId, request: CreateDownloadTicketRequest) => client.post<DownloadTicketResponse>(`/api/employee/files/${pathId(attachmentRefId)}/download-tickets`, request),
      move: (attachmentRefId: EntityId, request: MoveEmployeeFileRequest) => client.post<EmployeeFileResponse>(`/api/employee/files/${pathId(attachmentRefId)}/move`, request),
      copy: (attachmentRefId: EntityId, request: CopyEmployeeFileRequest) => client.post<EmployeeFileResponse>(`/api/employee/files/${pathId(attachmentRefId)}/copy`, request),
      trash: (attachmentRefId: EntityId, request: FileTrashRequest) => client.post<FileTrashResponse>(`/api/employee/files/${pathId(attachmentRefId)}/trash`, request),
      restore: (attachmentRefId: EntityId, request: FileTrashRequest) => client.post<FileRestoreResponse>(`/api/employee/files/${pathId(attachmentRefId)}/restore`, request),
      purge: (attachmentRefId: EntityId) => client.delete<FilePurgeResponse>(`/api/employee/files/${pathId(attachmentRefId)}`),
      downloadUrl: (ticketNo: string) => `/api/employee/files/download/${encodeURIComponent(ticketNo)}`,
    },
  };
}

export const employeeApi = createEmployeeApi();
