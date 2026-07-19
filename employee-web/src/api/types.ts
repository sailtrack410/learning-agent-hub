export type QueryValue = string | number | boolean | null | undefined;
export type QueryParams = Record<string, QueryValue | QueryValue[]>;
export type EntityId = string | number;

export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
  requestId?: string | null;
}

export interface PageResult<T> {
  pageNo: number;
  pageSize: number;
  total: number;
  list: T[];
}

export interface CourseKnowledgePointResponse {
  knowledgePointId: EntityId;
  pointCode: string;
  pointName: string;
  summary?: string | null;
  sortOrder: number;
}

export interface CourseChapterResponse {
  chapterId: EntityId;
  chapterNo: number;
  chapterName: string;
  summary?: string | null;
  contentType: 'ARTICLE';
  contentJson?: string | null;
  status: 'DRAFT' | 'PUBLISHED';
  statusName: string;
  knowledgePoints: CourseKnowledgePointResponse[];
}

export interface CourseInvitationResponse {
  token: string;
  expiresAt: string;
}

export interface CourseResourcePreviewResponse {
  attachmentRefId: EntityId;
  fileName: string;
  previewStatus: 'PENDING' | 'CONVERTING' | 'READY' | 'FAILED';
  previewUrl?: string | null;
  downloadUrl: string;
  errorMessage?: string | null;
}

export interface CourseKnowledgeDocumentResponse {
  documentId: EntityId;
  attachmentRefId: EntityId;
  title: string;
  mimeType?: string | null;
  fileSize: number;
  parserTypeName: string;
  ingestStatus: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
  ingestStatusName: string;
  ingestProgressPercent: number;
  ingestStage: string;
  ingestStageName: string;
  vectorStatus: 'PENDING' | 'SUCCESS' | 'FAILED';
  vectorStatusName: string;
  chunkCount: number;
  errorMessage?: string | null;
  chapterId?: EntityId | null;
  chapterName?: string | null;
  knowledgePoints: Array<{ knowledgePointId: EntityId; pointName: string }>;
  createdAt: string;
  updatedAt: string;
}

export interface CourseKnowledgeDocumentRequest {
  attachmentRefId: EntityId;
  chapterId?: EntityId | null;
  knowledgePointIds: EntityId[];
}

export interface StudentProfileDimensionResponse {
  dimensionCode: string;
  dimensionName: string;
  conclusion: string;
  confidence: number;
  evidenceSummary: string;
  updatedAt: string;
}

export interface StudentProfileMessageResponse {
  messageId: EntityId;
  role: 'USER' | 'ASSISTANT';
  dimensionCode?: string | null;
  content: string;
  createdAt: string;
}

export interface StudentProfileResponse {
  profileId: EntityId;
  versionNo: number;
  completedDimensions: number;
  totalDimensions: number;
  complete: boolean;
  nextDimensionCode?: string | null;
  nextQuestion?: string | null;
  dimensions: StudentProfileDimensionResponse[];
  messages: StudentProfileMessageResponse[];
}

export type AssessmentQuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE';

export interface AssessmentResponse {
  assessmentId: EntityId;
  courseId: EntityId;
  title: string;
  description?: string | null;
  totalScore: number;
  durationMinutes: number;
  questionCount: number;
  questions: Array<{
    questionId: EntityId;
    knowledgePointId: EntityId;
    knowledgePointName: string;
    questionType: AssessmentQuestionType;
    prompt: string;
    options: Array<{ value: string; label: string }>;
    score: number;
  }>;
}

export interface AssessmentSubmitRequest {
  idempotencyKey: string;
  durationSeconds: number;
  answers: Array<{
    questionId: EntityId;
    selectedValues: string[];
    durationSeconds: number;
  }>;
}

export interface AssessmentAttemptResponse {
  attemptId: EntityId;
  assessmentId: EntityId;
  attemptNo: number;
  totalScore: number;
  earnedScore: number;
  durationSeconds: number;
  report: string;
  profileUpdated: boolean;
  submittedAt: string;
  answers: Array<{
    questionId: EntityId;
    prompt: string;
    questionType: AssessmentQuestionType;
    knowledgePointName: string;
    selectedValues: string[];
    correctValues: string[];
    correct: boolean;
    earnedScore: number;
    score: number;
    errorType?: string | null;
    analysis: string;
    durationSeconds: number;
  }>;
  diagnoses: Array<{
    knowledgePointId: EntityId;
    knowledgePointName: string;
    masteryScore: number;
    attemptCount: number;
    correctCount: number;
  }>;
}

export type LearningPathNodeStatus = 'LOCKED' | 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';

export interface LearningPathNodeResponse {
  nodeId: EntityId;
  sequenceNo: number;
  knowledgePointId: EntityId;
  knowledgePointName: string;
  chapterId: EntityId;
  chapterName: string;
  learningObjective: string;
  resourceType: 'COURSE_ARTICLE';
  resourceRefId: EntityId;
  resourceTitle: string;
  estimatedMinutes: number;
  completionCondition: string;
  status: LearningPathNodeStatus;
  masteryScore?: number | null;
  recommendationReason: string;
}

export interface LearningPathRecommendationResponse {
  recommendationId: EntityId;
  nodeId: EntityId;
  knowledgePointId: EntityId;
  knowledgePointName: string;
  resourceType: 'COURSE_ARTICLE';
  resourceRefId: EntityId;
  resourceTitle: string;
  priorityNo: number;
  reason: string;
  status: 'ACTIVE' | 'COMPLETED';
}

export interface LearningPathResponse {
  pathId: EntityId;
  courseId: EntityId;
  courseName: string;
  versionNo: number;
  profileVersionNo: number;
  sourceAssessmentAttemptId?: EntityId | null;
  generationReason: string;
  progressPercent: number;
  currentNodeId?: EntityId | null;
  createdAt: string;
  todayNodes: LearningPathNodeResponse[];
  nodes: LearningPathNodeResponse[];
  recommendations: LearningPathRecommendationResponse[];
}

export type LearningResourceType = 'EXPLANATION' | 'MIND_MAP' | 'EXERCISE' | 'CODE_LAB' | 'EXTENDED_READING';
export type LearningResourceValidationStatus = 'PASSED' | 'NEEDS_REVIEW' | 'REJECTED';
export type LearningResourcePublicationStatus = 'DRAFT' | 'PUBLISHED' | 'WITHDRAWN' | 'REJECTED';
export type LearningResourceFeedbackType = 'HELPFUL' | 'TOO_DIFFICULT' | 'TOO_EASY' | 'NOT_RELEVANT';

export interface GeneratedLearningResourceResponse {
  resourceId: EntityId;
  taskId: EntityId;
  courseId: EntityId;
  courseName: string;
  chapterId: EntityId;
  chapterName: string;
  knowledgePointId: EntityId;
  knowledgePointName: string;
  resourceType: LearningResourceType;
  resourceTypeName: string;
  title: string;
  summary: string;
  content: string;
  contentFormat: 'MARKDOWN' | 'MERMAID';
  validationStatus: LearningResourceValidationStatus;
  validationStatusName: string;
  validationReason: string;
  confidence: number;
  publicationStatus: LearningResourcePublicationStatus;
  publicationStatusName: string;
  reviewNote?: string | null;
  reviewedAt?: string | null;
  publishedAt?: string | null;
  createdAt: string;
  updatedAt: string;
  citations: Array<{
    documentId: EntityId;
    chunkId: EntityId;
    documentTitle: string;
    excerpt: string;
    pageNo?: number | null;
    relevanceScore: number;
  }>;
}

export interface LearningResourceTaskStepResponse {
  stepId: EntityId;
  roleCode: string;
  roleName: string;
  stepOrder: number;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  statusName: string;
  inputSummary?: string | null;
  outputSummary?: string | null;
  liveOutput?: string | null;
  modelName?: string | null;
  durationMs?: number | null;
  failureMessage?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
}

export interface LearningResourceTaskResponse {
  taskId: EntityId;
  studentUserId?: EntityId | null;
  courseId: EntityId;
  courseName: string;
  chapterId: EntityId;
  chapterName: string;
  knowledgePointId: EntityId;
  knowledgePointName: string;
  profileVersionNo: number;
  requestMessage?: string | null;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  statusName: string;
  progressPercent: number;
  currentRoleCode?: string | null;
  currentRoleName?: string | null;
  attemptNo: number;
  modelName?: string | null;
  failureType?: string | null;
  failureMessage?: string | null;
  totalDurationMs?: number | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  createdAt: string;
  updatedAt: string;
  steps: LearningResourceTaskStepResponse[];
  resources: GeneratedLearningResourceResponse[];
}

export interface CreateLearningResourceTaskRequest {
  courseId: EntityId;
  knowledgePointId: EntityId;
  idempotencyKey: string;
  requestMessage?: string;
}

export interface LearningResourceUpdateRequest {
  title: string;
  summary: string;
  content: string;
}

export interface LearningResourceFeedbackResponse {
  feedbackId: EntityId;
  resourceId: EntityId;
  feedbackType: LearningResourceFeedbackType;
  feedbackTypeName: string;
  comment?: string | null;
  profileVersionNo?: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface CourseLearningClassSummaryResponse {
  classId?: EntityId | null;
  className: string;
  studentCount: number;
  riskStudentCount: number;
  completionRate: number;
  averageMastery?: number | null;
}

export interface CourseLearningStudentSummaryResponse {
  studentUserId: EntityId;
  studentNo: string;
  studentName: string;
  classId?: EntityId | null;
  className?: string | null;
  profileVersionNo: number;
  profileDimensionCount: number;
  profileSummary?: string | null;
  progressPercent: number;
  averageMastery?: number | null;
  assessmentAttemptCount: number;
  lastLearningAt?: string | null;
  riskLevel: 'HIGH' | 'MEDIUM' | 'LOW';
  riskReasons: string[];
}

export interface CourseWeakKnowledgePointResponse {
  knowledgePointId: EntityId;
  knowledgePointName: string;
  averageMastery?: number | null;
  assessedStudentCount: number;
  weakStudentCount: number;
}

export interface CourseLearningOverviewResponse {
  courseId: EntityId;
  courseName: string;
  studentCount: number;
  activeStudentCount: number;
  riskStudentCount: number;
  completionRate: number;
  averageMastery?: number | null;
  publishedResourceCount: number;
  resourceViewerCount: number;
  resourceViewCount: number;
  classes: CourseLearningClassSummaryResponse[];
  students: CourseLearningStudentSummaryResponse[];
  weakKnowledgePoints: CourseWeakKnowledgePointResponse[];
}

export interface LearningResourcePushResponse {
  pushId: EntityId;
  resourceId: EntityId;
  courseId: EntityId;
  targetType: 'STUDENT' | 'CLASS';
  targetId: EntityId;
  targetName: string;
  status: 'ACTIVE';
  createdAt: string;
}

export interface StudentLearningDashboardResponse {
  courseCount: number;
  todayTaskCount: number;
  overallProgress: number;
  averageMastery?: number | null;
  pendingAssessmentCount: number;
  recommendationCount: number;
  courses: Array<{
    courseId: EntityId;
    courseName: string;
    coverAttachmentRefId?: EntityId | null;
    teacherName?: string | null;
    totalHours: number;
    progressPercent: number;
    averageMastery?: number | null;
    todayTaskCount: number;
    pendingAssessmentCount: number;
    recommendationCount: number;
    currentNodeTitle?: string | null;
  }>;
  todayTasks: Array<{
    courseId: EntityId;
    courseName: string;
    nodeId: EntityId;
    chapterId: EntityId;
    chapterName: string;
    resourceTitle: string;
    estimatedMinutes: number;
    status: 'IN_PROGRESS' | 'PENDING';
    recommendationReason: string;
  }>;
  recommendations: Array<{
    courseId: EntityId;
    courseName: string;
    nodeId: EntityId;
    resourceRefId: EntityId;
    resourceTitle: string;
    priorityNo: number;
    reason: string;
  }>;
}

export interface CourseResponse {
  courseId: EntityId;
  courseCode: string;
  courseName: string;
  summary?: string | null;
  learningObjectives?: string | null;
  prerequisites?: string | null;
  teacherName?: string | null;
  coverAttachmentRefId?: EntityId | null;
  totalHours: number;
  status: 'DRAFT' | 'PUBLISHED';
  statusName: string;
  teacherRole?: 'OWNER' | 'COLLABORATOR' | null;
  enrolled?: boolean;
  teachers: Array<{
    userId: EntityId;
    teacherName: string;
    memberRole: 'OWNER' | 'COLLABORATOR';
  }>;
  chapterCount: number;
  knowledgePointCount: number;
  createdAt: string;
  updatedAt: string;
  chapters: CourseChapterResponse[];
  dependencies: Array<{ prerequisitePointCode: string; dependentPointCode: string }>;
}

export interface CourseSaveRequest {
  courseCode: string;
  courseName: string;
  summary?: string;
  learningObjectives?: string;
  prerequisites?: string;
  teacherName?: string;
  coverAttachmentRefId?: EntityId | null;
  totalHours: number;
  status: 'DRAFT' | 'PUBLISHED';
}

export interface CourseOutlineSaveRequest {
  chapters: Array<{
    chapterId?: EntityId;
    chapterName: string;
    summary?: string;
    knowledgePoints: Array<{ pointCode: string; pointName: string; summary?: string }>;
  }>;
  dependencies: Array<{ prerequisitePointCode: string; dependentPointCode: string }>;
}

export interface CourseArticleSaveRequest {
  title: string;
  summary?: string;
  contentJson: string;
  status: 'DRAFT' | 'PUBLISHED';
}

export interface LoginRequest {
  loginName: string;
  password: string;
  emailCode?: string;
  bindEmail?: string;
}

export interface LoginSendCodeRequest {
  identifier: string;
}

export interface LoginSendCodeResponse {
  maskedEmail?: string | null;
}

export interface SendEmailCodeRequest {
  email: string;
  purpose: 'BIND' | 'RESET_PASSWORD';
}

export interface ResetPasswordRequest {
  email: string;
  code: string;
  newPassword: string;
}

export interface LoginResponse {
  userId: EntityId;
  userType: string;
  displayName: string;
  loginName: string;
  requiresEmailCode?: boolean;
  maskedEmail?: string | null;
  requiresEmailBinding?: boolean;
}

export interface CurrentUserResponse extends LoginResponse {
  educationRole?: 'STUDENT' | 'TEACHER' | null;
  email?: string | null;
  primaryDeptId?: EntityId | null;
  positionId?: EntityId | null;
  managerUserId?: EntityId | null;
  employmentStatus?: string | null;
}

export interface OrgUserDetailResponse {
  userId: EntityId;
  employeeNo: string;
  realName: string;
  gender?: string | null;
  genderName?: string | null;
  mobile?: string | null;
  avatarAttachmentRefId?: EntityId | null;
  primaryDeptId?: EntityId | null;
  primaryDeptName?: string | null;
  positionId?: EntityId | null;
  positionName?: string | null;
  managerUserId?: EntityId | null;
  managerName?: string | null;
  employmentStatus?: string | null;
  status?: string | null;
  hireDate?: string | null;
  regularizedAt?: string | null;
  serviceStartDate?: string | null;
  annualLeaveStartDate?: string | null;
  workLocation?: string | null;
}

export interface UpdateEmployeeProfileRequest {
  mobile?: string | null;
  avatarAttachmentRefId: EntityId | null;
}

export type PresenceStatus = 'ONLINE' | 'BUSY' | 'AWAY' | 'DND';
export type AccentColor = 'indigo' | 'violet' | 'blue' | 'teal' | 'amber' | 'rose';
export type FontScale = 'small' | 'medium' | 'large';
export type BubbleStyle = 'rounded' | 'sharp';

export interface EmployeePreferenceResponse {
  theme: 'light' | 'dark';
  messageDensity: 'cozy' | 'compact';
  listWidth: number;
  signature: string;
  presenceStatus: PresenceStatus;
  notifSound: boolean;
  notifDesktop: boolean;
  notifMentionOnly: boolean;
  notifUnreadBadge: boolean;
  accentColor: AccentColor;
  fontScale: FontScale;
  bubbleStyle: BubbleStyle;
}

export interface UpdateEmployeePreferenceRequest {
  theme?: 'light' | 'dark';
  messageDensity?: 'cozy' | 'compact';
  listWidth?: number;
  signature?: string;
  presenceStatus?: PresenceStatus;
  notifSound?: boolean;
  notifDesktop?: boolean;
  notifMentionOnly?: boolean;
  notifUnreadBadge?: boolean;
  accentColor?: AccentColor;
  fontScale?: FontScale;
  bubbleStyle?: BubbleStyle;
}

export interface OrgUserSummaryResponse {
  userId: EntityId;
  employeeNo: string;
  realName: string;
  avatarAttachmentRefId?: EntityId | null;
  primaryDeptId?: EntityId | null;
  primaryDeptName?: string | null;
  positionId?: EntityId | null;
  positionName?: string | null;
  managerUserId?: EntityId | null;
  managerName?: string | null;
  employmentStatus?: string | null;
  status?: string | null;
  signature?: string | null;
  presenceStatus?: string | null;
}

export interface FriendRequest {
  remark?: string;
}

export interface OrgDepartmentTreeNodeResponse {
  id: EntityId;
  parentId?: EntityId | null;
  treePath?: string | null;
  deptCode?: string | null;
  deptName: string;
  deptType?: string | null;
  deptLevel?: number | null;
  leaderUserId?: EntityId | null;
  leaderName?: string | null;
  status?: string | null;
  children: OrgDepartmentTreeNodeResponse[];
}

export interface WorkflowRequestListQuery extends QueryParams {
  pageNo?: number;
  pageSize?: number;
  status?: string;
  bizType?: string;
}

export interface WorkflowTaskApproveRequest {
  comment?: string;
}

export interface WorkflowTaskRejectRequest {
  comment: string;
}

export interface WorkflowRequestWithdrawRequest {
  comment: string;
}

export interface WorkflowTodoResponse {
  todoId: EntityId;
  requestId: EntityId;
  taskId: string;
  title: string;
  summary: string;
  status: string;
  statusName?: string | null;
  priority?: string | null;
  priorityName?: string | null;
  createdAt?: string | null;
}

export interface WorkflowRequestSummaryResponse {
  requestId: EntityId;
  requestNo: string;
  bizType: string;
  bizTypeName?: string | null;
  bizId: number;
  title: string;
  status: string;
  statusName?: string | null;
  currentStepKey?: string | null;
  currentStepName?: string | null;
  submittedAt?: string | null;
  finishedAt?: string | null;
}

export interface WorkflowRequestCreateResponse {
  requestId: EntityId;
  requestNo: string;
  status: string;
  statusName?: string | null;
  flowableProcessInstanceId?: string | null;
  agentTaskId?: EntityId | null;
}

export interface WorkflowRequestStatusResponse {
  requestId: EntityId;
  status: string;
  statusName?: string | null;
}

export interface WorkflowLeaveDetailResponse {
  leaveTypeId: EntityId;
  startAt?: string | null;
  endAt?: string | null;
  durationMinutes?: number | null;
  reason?: string | null;
}

export interface WorkflowClockCorrectionDetailResponse {
  targetDate?: string | null;
  missingPunchType?: string | null;
  missingPunchTypeName?: string | null;
  expectedPunchAt?: string | null;
  reason?: string | null;
}

export interface WorkflowRequestAttachmentResponse {
  attachmentRefId: EntityId;
  displayName: string;
  mimeType: string;
  fileSize: number;
  contentSha256: string;
  scanStatus: string;
  scanStatusName?: string | null;
}

export interface WorkflowRequestDetailResponse {
  requestId: EntityId;
  requestNo: string;
  bizType: string;
  bizTypeName?: string | null;
  bizId: EntityId;
  applicantUserId: EntityId;
  title: string;
  status: string;
  statusName?: string | null;
  currentStepKey?: string | null;
  currentStepName?: string | null;
  flowableProcessInstanceId?: string | null;
  submittedAt?: string | null;
  finishedAt?: string | null;
  leave?: WorkflowLeaveDetailResponse | null;
  clockCorrection?: WorkflowClockCorrectionDetailResponse | null;
  generic?: WorkflowGenericDetailResponse | null;
  attachments: WorkflowRequestAttachmentResponse[];
}

export interface WorkflowTaskActionResponse {
  actionId: EntityId;
  requestId: EntityId;
  taskId?: string | null;
  stepKey?: string | null;
  stepName?: string | null;
  actorType?: string | null;
  actorId?: EntityId | null;
  actionType: string;
  comment?: string | null;
  agentDecisionJson?: string | null;
  beforeStatus?: string | null;
  afterStatus?: string | null;
  actedAt?: string | null;
}

export interface ImMessageResponse {
  messageId: string | number;
  conversationId: string | number;
  seqNo: number;
  senderType: string;
  senderTypeName?: string | null;
  senderId: string | number;
  messageType: string;
  messageTypeName?: string | null;
  contentJson: string;
  contentText: string;
  attachments?: ImMessageAttachmentResponse[];
  mentions?: ImMessageMentionResponse[];
  quotedMessageId?: string | number | null;
  status: string;
  statusName?: string | null;
  sentAt?: string | null;
}

export interface ImMessageMentionResponse {
  userId: EntityId;
  name?: string | null;
}

export interface ImMessageAttachmentResponse {
  attachmentRefId: EntityId;
  displayName: string;
  mimeType: string;
  fileSize: number;
  contentSha256: string;
  scanStatus: string;
  scanStatusName?: string | null;
}

export interface SendImMessageRequest {
  messageType?: 'TEXT' | 'FILE';
  text?: string;
  attachmentRefIds?: EntityId[];
  mentionUserIds?: EntityId[];
  clientMessageId?: string;
  quotedMessageId?: EntityId;
}

export interface TranslateImMessageResponse {
  messageId: EntityId;
  sourceText: string;
  translatedText: string;
}

export interface ForwardImMessageRequest {
  targetConversationId: EntityId;
}

export interface ImMessageActionResponse {
  messageId: EntityId;
  actionType: string;
  actionTypeName?: string | null;
  status: string;
  statusName?: string | null;
}

export interface RemindImMessageRequest {
  remindAt?: string | null;
  note?: string | null;
}

export interface ImConversationListQuery extends QueryParams {
  beforeLastMessageAt?: string;
  beforeConversationId?: string | number;
  size?: number;
}

export interface ImMessageListQuery extends QueryParams {
  beforeSeq?: number;
  size?: number;
}

export interface ImConversationSummaryResponse {
  conversationId: string | number;
  conversationType: string;
  conversationTypeName?: string | null;
  conversationKey?: string | null;
  title?: string | null;
  avatarFileId?: string | number | null;
  boundAgentId?: string | number | null;
  lastMessageSeq: number;
  lastReadSeq: number;
  unreadCount: number;
  lastMessage?: ImMessageResponse | null;
  lastMessageAt?: string | null;
  status: string;
  statusName?: string | null;
  pin: boolean;
  mute: boolean;
  memberRole: string;
  memberRoleName?: string | null;
  hasMentionMe?: boolean;
  hasReplyMe?: boolean;
}

export interface ImConversationDetailResponse {
  conversation: ImConversationSummaryResponse;
  members: ImConversationMemberResponse[];
  groupProfile?: ImGroupProfileResponse | null;
}

export interface ImConversationMemberResponse {
  memberId: EntityId;
  memberType: string;
  memberTypeName?: string | null;
  displayName: string;
  memberRole: string;
  memberRoleName?: string | null;
  status: string;
  statusName?: string | null;
  joinedAt?: string | null;
}

export interface ImGroupProfileResponse {
  conversationId: string | number;
  title: string;
  avatarFileId?: string | number | null;
  groupType: string;
  groupTypeName?: string | null;
  ownerUserId: string | number;
  noticeText?: string | null;
  status: string;
  statusName?: string | null;
  currentMemberRole?: string | null;
  currentMemberRoleName?: string | null;
}

export interface ImGroupFileResponse {
  attachmentRefId: EntityId;
  conversationId: EntityId;
  displayName: string;
  mimeType: string;
  fileSize: number;
  contentSha256: string;
  scanStatus: string;
  scanStatusName?: string | null;
  uploaderId?: EntityId | null;
  uploaderName?: string | null;
  createdAt?: string | null;
  canDelete: boolean;
}

export interface StartPrivateConversationRequest {
  targetUserId: EntityId;
}

export interface CreateGroupConversationRequest {
  title: string;
  memberUserIds: EntityId[];
}

export interface StartAgentConversationRequest {
  agentId: EntityId;
}

export interface UpdateReadPositionRequest {
  lastReadSeq: number;
}

export interface UpdateConversationPreferenceRequest {
  pin?: boolean;
  mute?: boolean;
}

export interface UpdateGroupConversationRequest {
  title?: string;
  noticeText?: string;
}

export interface GroupMemberUserIdsRequest {
  memberUserIds: EntityId[];
}

export interface EditImMessageRequest {
  text: string;
}

export interface LeaveRequestCreateRequest {
  leaveTypeId: EntityId;
  startAt: string;
  endAt: string;
  durationMinutes: number;
  reason: string;
  attachmentRefIds?: EntityId[];
}

export interface ClockCorrectionRequestCreateRequest {
  targetDate: string;
  missingPunchType: string;
  expectedPunchAt: string;
  reason: string;
  attachmentRefIds?: EntityId[];
}

export interface GenericWorkflowRequestCreateRequest {
  processCode: string;
  title?: string;
  summary?: string;
  formPayload: Record<string, unknown>;
  attachmentRefIds?: EntityId[];
}

export interface WorkflowGenericDetailResponse {
  processCode: string;
  summary?: string | null;
  formPayload?: Record<string, unknown> | null;
}

export interface AttendanceDateRangeQuery extends QueryParams {
  startDate?: string;
  endDate?: string;
}

export interface CreatePunchRecordRequest {
  punchAt?: string;
  punchType: string;
  sourceType: string;
  locationText?: string;
  ipAddress?: string;
  deviceId?: string;
}

export interface PunchRecordResponse {
  punchRecordId: EntityId;
  userId: EntityId;
  punchAt?: string | null;
  punchType: string;
  punchTypeName?: string | null;
  sourceType: string;
  sourceTypeName?: string | null;
  status: string;
  statusName?: string | null;
  dailyResult?: AttendanceDailyResultResponse | null;
}

export interface AttendanceDailyResultResponse {
  dailyResultId: EntityId;
  userId: EntityId;
  workDate: string;
  scheduleId?: EntityId | null;
  groupId?: EntityId | null;
  shiftId?: EntityId | null;
  resultStatus: string;
  lateMinutes?: number | null;
  earlyLeaveMinutes?: number | null;
  absenceMinutes?: number | null;
  workMinutes?: number | null;
  exceptionFlag?: boolean | null;
  calculatedAt?: string | null;
}

export interface AttendanceExceptionResponse {
  exceptionId: EntityId;
  dailyResultId: EntityId;
  userId: EntityId;
  workDate: string;
  exceptionType: string;
  exceptionTypeName?: string | null;
  exceptionDesc?: string | null;
  status: string;
  statusName?: string | null;
}

export interface LeaveAccountResponse {
  accountId: EntityId;
  userId: EntityId;
  leaveTypeId: EntityId;
  leaveCode: string;
  leaveName: string;
  balanceMode: string;
  availableMinutes: number;
  frozenMinutes: number;
  status: string;
  lastTxnType?: string | null;
  lastChangeMinutes?: number | null;
}

export interface LeaveTypeResponse {
  leaveTypeId: EntityId;
  leaveCode: string;
  leaveName: string;
  balanceMode: string;
  status: string;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CreateUploadTicketRequest {
  ownerType: string;
  ownerId?: EntityId | null;
  folderId?: EntityId | null;
  fileName: string;
  mimeType: string;
  fileSize: number;
  contentSha256: string;
  contentMd5?: string;
  clientRequestId?: string;
}

export interface ConfirmUploadRequest {
  uploadTicketId: string | number;
  objectKey: string;
  contentSha256: string;
  fileSize: number;
  ownerType: string;
  ownerId?: EntityId | null;
  folderId?: EntityId | null;
  displayName: string;
}

export interface CreateDownloadTicketRequest {
  downloadType: string;
  reason?: string;
}

export interface FileListQuery extends QueryParams {
  pageNo?: number;
  pageSize?: number;
  folderId?: EntityId | null;
  keyword?: string;
  fileType?: string;
}

export interface EmployeeFileFolderResponse {
  folderId: EntityId;
  parentFolderId?: EntityId | null;
  folderName: string;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface EmployeeFileBrowserResponse {
  currentFolderId?: EntityId | null;
  breadcrumbs: EmployeeFileFolderResponse[];
  folders: EmployeeFileFolderResponse[];
  files: EmployeeFileResponse[];
}

export interface CreateEmployeeFileFolderRequest {
  parentFolderId?: EntityId | null;
  folderName: string;
}

export interface MoveEmployeeFileRequest {
  targetFolderId?: EntityId | null;
}

export interface CopyEmployeeFileRequest {
  targetFolderId?: EntityId | null;
  displayName?: string;
}

export interface FileTrashRequest {
  reason?: string;
}

export interface FileTrashResponse {
  attachmentRefId: EntityId;
  deletedAt?: string | null;
  purgeAfter?: string | null;
}

export interface FileRestoreResponse {
  attachmentRefId: EntityId;
  restoredAt?: string | null;
}

export interface FilePurgeResponse {
  attachmentRefId: EntityId;
  purgedAt?: string | null;
  physicalCleanupScheduled?: boolean | null;
}

export interface UploadTicketResponse {
  uploadTicketId?: string | number | null;
  ticketNo?: string | null;
  deduplicated: boolean;
  attachmentRefId?: EntityId | null;
  storageProvider: string;
  bucketName?: string | null;
  objectKey?: string | null;
  uploadUrl?: string | null;
  headers: Record<string, string>;
  expireAt?: string | null;
}

export interface AttachmentRefResponse {
  attachmentRefId: EntityId;
  fileObjectId: EntityId;
  displayName: string;
  mimeType: string;
  fileSize: number;
  contentSha256: string;
  deduplicated: boolean;
  scanStatus: string;
  scanStatusName?: string | null;
  createdAt?: string | null;
}

export interface EmployeeFileResponse {
  attachmentRefId: EntityId;
  fileObjectId: EntityId;
  ownerType: string;
  ownerTypeName?: string | null;
  ownerId?: EntityId | null;
  folderId?: EntityId | null;
  originalFileName?: string | null;
  displayName: string;
  uploaderType: string;
  uploaderTypeName?: string | null;
  uploaderId?: EntityId | null;
  mimeType?: string | null;
  fileSize: number;
  scanStatus: string;
  scanStatusName?: string | null;
  deletedAt?: string | null;
  purgeAfter?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface QuotaResponse {
  personalQuotaBytes: number;
  personalUsedBytes: number;
  personalUsagePercent: number;
  orgPoolQuotaBytes?: number | null;
  orgPoolUsedBytes?: number | null;
  cacheQuotaBytes?: number | null;
  cacheUsedBytes?: number | null;
  uploadWritable: boolean;
  blockReason?: string | null;
}

export interface DownloadTicketResponse {
  downloadTicketId: EntityId;
  ticketNo: string;
  downloadUrl: string;
  expireAt?: string | null;
  fileName: string;
  fileSize: number;
}

export interface AgentResponse {
  agentId: EntityId;
  agentCode: string;
  agentName: string;
  avatarAttachmentRefId?: EntityId | null;
  agentType: string;
  agentTypeName?: string | null;
  identityMode: string;
  identityModeName?: string | null;
  publishedVersionId?: EntityId | null;
  publishedVersionNo?: number | null;
  publishedVersionLabel?: string | null;
  status: string;
  statusName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}
