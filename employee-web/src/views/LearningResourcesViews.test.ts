import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const currentDir = dirname(fileURLToPath(import.meta.url));

describe('learning resource views', () => {
  it('keeps student generation recoverable and exposes the five-role execution flow', () => {
    const source = readFileSync(resolve(currentDir, 'StudentLearningResourcesView.vue'), 'utf8');

    expect(source).toContain("employeeApi.learningResources.tasks(courseId.value)");
    expect(source).toContain("employeeApi.learningResources.retryTask(task.taskId)");
    expect(source).toContain('employeeApi.learningResources.recordView(resource.resourceId)');
    expect(source).toContain('employeeApi.learningResources.saveFeedback');
    expect(source).toContain('反馈会用于调整资源偏好和后续推荐');
    expect(source).toContain("'PROFILE_ANALYST'");
    expect(source).toContain("'FACT_CHECKER'");
    expect(source).toContain('实时生成内容');
    expect(source).toContain('完成前未经事实校验');
    expect(source).toContain('activeTraceStep.inputSummary');
    expect(source).toContain('activeTraceStep.outputSummary');
    expect(source).toContain('页面刷新或离开后仍可在这里查看最新状态');
  });

  it('requires teacher-side review before publishing and uses project dialogs', () => {
    const source = readFileSync(resolve(currentDir, '../components/course/TeacherLearningResourcePanel.vue'), 'utf8');

    expect(source).toContain("teacherLearningResources.review");
    expect(source).toContain("teacherLearningResources.publish");
    expect(source).toContain("teacherLearningResources.withdraw");
    expect(source).toContain('<AppDialog v-if="rejectTarget">');
    expect(source).toContain('<AppDialog v-if="pushTarget" wide>');
    expect(source).toContain('teacherLearningResources.push');
    expect(source).not.toContain('window.confirm');
  });

  it('covers teacher course creation, outline editing, collaboration and owner-only deletion', () => {
    const source = readFileSync(resolve(currentDir, 'sections/TeacherCoursesSection.vue'), 'utf8');

    expect(source).toContain('employeeApi.teacherCourses.create');
    expect(source).toContain('employeeApi.teacherCourses.update');
    expect(source).toContain('employeeApi.teacherCourses.saveOutline');
    expect(source).toContain('employeeApi.teacherCourses.createInvitation');
    expect(source).toContain('employeeApi.teacherCourses.acceptInvitation');
    expect(source).toContain("const isOwner = computed(() => selected.value?.teacherRole === 'OWNER')");
    expect(source).toContain('<AppDialog v-if="deleteDialogOpen"');
    expect(source).toContain('employeeApi.teacherCourses.remove');
    expect(source).not.toContain('window.confirm');
  });

  it('exposes course analytics with risk, class and weak-point states', () => {
    const section = readFileSync(resolve(currentDir, 'sections/TeacherCoursesSection.vue'), 'utf8');
    const analytics = readFileSync(resolve(currentDir, '../components/course/CourseLearningAnalyticsPanel.vue'), 'utf8');

    expect(section).toContain('aria-label="学情分析"');
    expect(section).toContain('<CourseLearningAnalyticsPanel');
    expect(analytics).toContain('teacherCourses.learningOverview');
    expect(analytics).toContain('风险学生与学习进度');
    expect(analytics).toContain('班级学习状态');
    expect(analytics).toContain('薄弱知识点');
    expect(analytics).toContain('@media (max-width: 760px)');
  });

  it('limits course communication to enrolled students and opens existing messages', () => {
    const section = readFileSync(resolve(currentDir, 'sections/TeacherCoursesSection.vue'), 'utf8');
    const communication = readFileSync(resolve(currentDir, '../components/course/CourseCommunicationPanel.vue'), 'utf8');

    expect(section).toContain('aria-label="班级沟通"');
    expect(section).toContain('<CourseCommunicationPanel');
    expect(communication).toContain('teacherCourses.learningOverview');
    expect(communication).toContain('teacherCourses.openClassGroup');
    expect(communication).toContain('teacherCourses.startStudentConversation');
    expect(communication).toContain('im.upsertConversation(conversation)');
    expect(communication).toContain("name: 'employee-message-detail'");
    expect(communication).toContain('仅来自已加入当前课程的学生');
  });

  it('keeps generated resource details readable and inside the dialog width', () => {
    const content = readFileSync(resolve(currentDir, '../components/course/GeneratedResourceContent.vue'), 'utf8');
    const dialog = readFileSync(resolve(currentDir, '../components/ui/AppDialog.vue'), 'utf8');
    const tokens = readFileSync(resolve(currentDir, '../design-system/tokens.css'), 'utf8');

    expect(content).toContain('min-width: 0; max-width: 100%');
    expect(content).toContain('overflow-wrap: anywhere');
    expect(content).toContain('max-width: 100%; overflow: auto');
    expect(content).toContain('background: var(--surface-inverse); color: var(--text-inverse)');
    expect(tokens).toContain('--surface-inverse: var(--neutral-900);');
    expect(dialog).toContain('box-sizing: border-box');
  });

  it('restores course knowledge ingest progress after refresh and keeps failed tasks retryable', () => {
    const section = readFileSync(resolve(currentDir, 'sections/TeacherCoursesSection.vue'), 'utf8');

    expect(section).toContain('employeeApi.teacherCourses.knowledgeDocuments(selected.value.courseId)');
    expect(section).toContain('scheduleKnowledgePoll()');
    expect(section).toContain('document.ingestStageName');
    expect(section).toContain('document.ingestProgressPercent');
    expect(section).toContain('retryKnowledgeDocument(document)');
  });

  it('converts Office previews asynchronously and resumes pending attachments after refresh', () => {
    const editor = readFileSync(resolve(currentDir, 'CourseArticleEditor.vue'), 'utf8');

    expect(editor).toContain('void resumeAttachmentPreviews()');
    expect(editor).toContain('employeeApi.teacherCourses.prepareResourcePreview');
    expect(editor).toContain('employeeApi.teacherCourses.resourcePreviewStatus');
    expect(editor).toContain('markAttachmentPreviewReady(attachmentRefId)');
    expect(editor).toContain('正在生成 ${previewTaskCount} 个附件预览');
  });

  it('aggregates the student learning home without mutating learning state', () => {
    const source = readFileSync(resolve(currentDir, 'sections/CoursesSection.vue'), 'utf8');

    expect(source).toContain('employeeApi.studentDashboard.current()');
    expect(source).toContain('今日任务');
    expect(source).toContain('学习进度');
    expect(source).toContain('平均掌握度');
    expect(source).toContain('待完成练习');
    expect(source).toContain('为什么现在学这些');
    expect(source).not.toContain('createLearningPath');
    expect(source).toContain('employeeApi.courses.enroll(courseId)');
    expect(source).toContain("course.enrolled ? '已加入' : '加入课程'");
  });

  it('keeps the learning path inside a vertically scrollable viewport', () => {
    const source = readFileSync(resolve(currentDir, 'CourseLearningPathView.vue'), 'utf8');
    const tokens = readFileSync(resolve(currentDir, '../design-system/tokens.css'), 'utf8');

    expect(source).toContain("import learningPathHero from '@/assets/learning-path-hero-3d.webp';");
    expect(source).toContain("import learningStatCompleted from '@/assets/learning-stat-completed.webp';");
    expect(source).toContain("import learningStatTime from '@/assets/learning-stat-time.webp';");
    expect(source).toContain("import learningStatFocus from '@/assets/learning-stat-focus.webp';");
    expect(source).toContain('<div class="hero-visual">');
    expect(source).toContain('<img :src="learningPathHero" alt="" aria-hidden="true" />');
    expect(source).toContain(':src="learningPathHero"');
    expect(source).toContain('class="summary-visual" aria-hidden="true"');
    expect(source).toContain('summary-card summary-card-focus');
    expect(source).toContain('class="learning-metrics"');
    expect(source).toContain('class="mastery-track" aria-hidden="true"');
    expect(source).toContain('const visibleTodayNodes = computed(() =>');
    expect(source).toContain('recentlyCompletedNodeId.value = String(node.nodeId);');
    expect(source).toContain('String(node.sequenceNo).padStart(2, \'0\')');
    expect(source).toContain('grid-template-rows: auto auto auto;');
    expect(source).toContain('.today-actions { display: contents; }');
    expect(source).toContain('.today-actions .secondary-button { grid-column: 3; grid-row: 2; }');
    expect(source).toContain('.today-actions .primary-button { grid-column: 3; grid-row: 3; }');
    expect(source).toContain('height: auto;');
    expect(source).toContain('class="path-node-card"');
    expect(source).toContain('class="recommendation-footer"');
    expect(source).toContain('bottom: 1.5rem;');
    expect(tokens).toContain('--surface-muted: color-mix(in srgb, var(--surface-panel) 72%, var(--surface-canvas));');
    expect(source).toContain('.node-status.status-in_progress { background: var(--surface-accent); color: var(--text-accent); }');
    expect(source).not.toContain('background: var(--primary-50)');
    expect(source).not.toContain('background: var(--success-50)');
    expect(source).toContain('.learning-path-page { box-sizing: border-box; width: 100%; height: 100%; min-height: 0;');
    expect(source).toContain('overflow-x: hidden; overflow-y: auto;');
    expect(source).toContain('.learning-path-page { padding: var(--space-12); padding-bottom: 8rem; }');
  });

  it('uses dedicated generated visuals for learning metrics, profile results, and personal settings', () => {
    const profile = readFileSync(resolve(currentDir, 'sections/StudentProfileSection.vue'), 'utf8');
    const settings = readFileSync(resolve(currentDir, 'sections/SettingsSection.vue'), 'utf8');
    const courses = readFileSync(resolve(currentDir, 'sections/CoursesSection.vue'), 'utf8');
    const shell = readFileSync(resolve(currentDir, '../styles/employee-shell.css'), 'utf8');

    expect(profile).toContain('class="profile-result-strip"');
    expect(profile).toContain('class="dimension-conclusion"');
    expect(profile).toContain("import profileConversationIcon from '@/assets/education/icons/profile-conversation.webp';");
    expect(profile).toContain("import profileDimensionsIcon from '@/assets/education/icons/profile-dimensions.webp';");
    expect(profile).toContain("import profileCompletionIcon from '@/assets/education/icons/profile-completion.webp';");
    expect(profile).toContain("import profileConfidenceIcon from '@/assets/education/icons/profile-confidence.webp';");
    expect(profile).toContain("import resultVersionIcon from '@/assets/education/icons/result-version.webp';");
    expect(profile).not.toContain('const dimensionVisuals =');
    expect(profile).toContain('height: 100%; min-height: 0;');
    expect(settings).toContain("import personalCenterBackground from '@/assets/education/personal-center-background-wide.webp';");
    expect(settings).toContain("import learningIdentityIcon from '@/assets/education/icons/learning-identity.webp';");
    expect(settings).toContain("import learningAffiliationIcon from '@/assets/education/icons/learning-affiliation.webp';");
    expect(settings).toContain("import accountSecurityIcon from '@/assets/education/icons/account-security.webp';");
    expect(settings).toContain("import learningPreferencesIcon from '@/assets/education/icons/learning-preferences.webp';");
    expect(settings).toContain("import avatarSettingsIcon from '@/assets/education/icons/avatar-settings.webp';");
    expect(settings).toContain("'--personal-center-background': `url(${personalCenterBackground})`");
    expect(courses).toContain("import todayTasksIcon from '@/assets/education/icons/today-tasks.webp';");
    expect(courses).toContain("import learningProgressIcon from '@/assets/education/icons/learning-progress.webp';");
    expect(courses).toContain("import knowledgeMasteryIcon from '@/assets/education/icons/knowledge-mastery.webp';");
    expect(courses).toContain("import pendingPracticeIcon from '@/assets/education/icons/pending-practice.webp';");
    expect(courses).toContain("import recommendedResourcesIcon from '@/assets/education/icons/recommended-resources.webp';");
    expect(shell).toContain('.employee-shell.section-profile {');
    expect(shell).toContain('.employee-shell.section-profile .main-pane {');
  });

  it('organizes education resources by course and opens source documents with a real link', () => {
    const source = readFileSync(resolve(currentDir, 'sections/EducationResourcesSection.vue'), 'utf8');

    expect(source).toContain('按课程整理学习资料');
    expect(source).toContain('课程文章');
    expect(source).toContain('个性化资源');
    expect(source).toContain('课程资料');
    expect(source).toContain('<a class="document-link"');
    expect(source).toContain('rel="noopener noreferrer"');
    expect(source).not.toContain('<AppButton variant="secondary" size="sm" :href=');
    expect(source).not.toContain('v-if="!totalVisible"');
  });

  it('uses education identity and organization semantics in personal settings', () => {
    const settings = readFileSync(resolve(currentDir, 'sections/SettingsSection.vue'), 'utf8');
    const shell = readFileSync(resolve(currentDir, '../stores/shell.ts'), 'utf8');

    expect(settings).toContain("const educationRoleName = computed(() => (isTeacher.value ? '课程教师' : '学生'))");
    expect(settings).toContain("const educationIdLabel = computed(() => (isTeacher.value ? '教师编号' : '学号'))");
    expect(settings).toContain('所属学院');
    expect(settings).toContain('所属班级');
    expect(settings).toContain('平台身份');
    expect(settings).not.toContain('任职状态');
    expect(settings).not.toContain('入职日期');
    expect(shell).toContain("subtitle: '学院 / 班级 / 平台身份'");
    expect(shell).toContain("title: '学习偏好'");
  });

  it('offers an explicit logout action for student and teacher accounts', () => {
    const shell = readFileSync(resolve(currentDir, 'MessagesView.vue'), 'utf8');

    expect(shell).toContain('aria-label="退出登录"');
    expect(shell).toContain('await session.logout()');
    expect(shell).toContain('<span>退出</span>');
  });

  it('uses the shared visual language on authentication and teacher workspaces', () => {
    const shell = readFileSync(resolve(currentDir, 'MessagesView.vue'), 'utf8');
    const styles = readFileSync(resolve(currentDir, '../styles/employee-shell.css'), 'utf8');
    const tokens = readFileSync(resolve(currentDir, '../design-system/tokens.css'), 'utf8');
    const teacher = readFileSync(resolve(currentDir, 'sections/TeacherCoursesSection.vue'), 'utf8');

    expect(shell).toContain("import learningPathHero from '@/assets/learning-path-hero-3d.webp';");
    expect(shell).toContain('class="auth-hero-visual"');
    expect(shell).toContain('平台核心能力');
    expect(styles).toContain('background: var(--hero-gradient);');
    expect(tokens).toContain('--hero-gradient: linear-gradient(135deg, var(--surface-panel)');
    expect(teacher).toContain("import courseDashboardHero from '@/assets/course-dashboard-hero-3d.webp';");
    expect(teacher).toContain('aria-label="课程管理概览"');
    expect(teacher).toContain('aria-label="课程工作区"');
  });
});
