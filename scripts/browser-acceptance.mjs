import { mkdir } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const playwrightModules = process.env.PLAYWRIGHT_NODE_MODULES;
if (!playwrightModules) throw new Error('缺少 PLAYWRIGHT_NODE_MODULES');
const require = createRequire(import.meta.url);
const { chromium } = require(`${playwrightModules}/playwright`);

const browserExecutable = process.env.PLAYWRIGHT_BROWSER_EXECUTABLE;
const outputDirectory = fileURLToPath(new URL('../deliverables/screenshots/browser-acceptance/', import.meta.url));
const issues = [];

function requiredEnvironment(name, fallback) {
  const value = process.env[name] || fallback;
  if (!value) throw new Error(`缺少 ${name}`);
  return value;
}

async function assertText(page, text) {
  await page.getByText(text, { exact: false }).first().waitFor({ state: 'visible', timeout: 15_000 });
}

async function verifyTeacherResources(page) {
  await page.locator('.workspace-tabs button.active', { hasText: '生成资源' }).waitFor({ state: 'visible', timeout: 15_000 });
  await page.locator('.task-table > article').nth(1).waitFor({ state: 'visible', timeout: 15_000 });
  await page.locator('.governance-list > article').nth(9).waitFor({ state: 'visible', timeout: 15_000 });
  const taskCount = await page.locator('.task-table > article').count();
  const resourceCount = await page.locator('.governance-list > article').count();
  if (taskCount !== 2 || resourceCount !== 10) {
    throw new Error(`教师端演示数据不完整：生成任务 ${taskCount} 条，资源 ${resourceCount} 条`);
  }
  await assertText(page, '已发布资源');
}

async function assertNoOverflow(page, label) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
  if (overflow) throw new Error(`${label} 存在页面级横向溢出`);
}

async function attachDiagnostics(page, label) {
  page.on('console', message => {
    if (message.type() === 'error' || message.type() === 'warning') {
      issues.push(`${label} ${message.type()}: ${message.text()}`);
    }
  });
  page.on('pageerror', error => issues.push(`${label} pageerror: ${error.message}`));
  page.on('requestfailed', request => issues.push(`${label} requestfailed: ${request.method()} ${request.url()}`));
}

async function loginBusiness(page, loginName, password) {
  await page.locator('input[name="loginName"]').fill(loginName);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole('button', { name: '进入平台' }).click();
  await page.waitForSelector('text=课程', { timeout: 15_000 });
}

async function verifyStudent(browser) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  await page.goto(`${requiredEnvironment('STUDENT_WEB_URL', 'http://127.0.0.1:5174')}/#/courses`);
  await page.waitForLoadState('networkidle');
  await loginBusiness(page, requiredEnvironment('STUDENT_LOGIN', 'student.demo'), requiredEnvironment('STUDENT_PASSWORD'));
  await attachDiagnostics(page, '学生端');
  await assertText(page, 'Java Web 应用开发');
  await page.getByRole('button', { name: /JAVA_WEB.*Java Web 应用开发/s }).click();
  await assertText(page, '课程章节');
  await assertText(page, '课程测验');
  await page.getByRole('button', { name: '我的学习路径' }).click();
  await assertText(page, '为什么推荐');
  await page.goto(`${requiredEnvironment('STUDENT_WEB_URL', 'http://127.0.0.1:5174')}/#/profile`);
  await assertText(page, '动态学习画像');
  await assertText(page, '资源偏好');
  await page.goto(`${requiredEnvironment('STUDENT_WEB_URL', 'http://127.0.0.1:5174')}/#/courses/8100001/learning-resources`);
  await assertText(page, '多智能体个性化资源');
  await assertText(page, '事实校验');
  await page.locator('.task-list > button').first().click();
  await page.locator('.agent-dag > button').nth(2).click();
  await assertText(page, '输入摘要');
  await assertText(page, '输出摘要');
  await assertNoOverflow(page, '学生端桌面');
  await page.screenshot({ path: `${outputDirectory}/学生端-核心闭环-桌面.png`, fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await assertNoOverflow(page, '学生端移动');
  await page.screenshot({ path: `${outputDirectory}/学生端-核心闭环-移动.png`, fullPage: true });
  await context.close();
}

async function verifyTeacher(browser) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  await page.goto(`${requiredEnvironment('STUDENT_WEB_URL', 'http://127.0.0.1:5174')}/#/courses`);
  await page.waitForLoadState('networkidle');
  await loginBusiness(page, requiredEnvironment('TEACHER_LOGIN', 'teacher.demo'), requiredEnvironment('TEACHER_PASSWORD'));
  await attachDiagnostics(page, '教师端');
  await assertText(page, '课程管理');
  await assertText(page, '新建课程');
  await page.getByRole('button', { name: /JAVA_WEB.*Java Web 应用开发/s }).click();
  await assertText(page, '基础信息');
  await assertText(page, '章节内容');
  await assertText(page, '课程资料库');
  await assertText(page, '学情分析');
  await assertText(page, '生成资源');
  await page.getByRole('button', { name: '课程资料库' }).click();
  await assertText(page, '资料解析与向量化');
  await page.getByRole('button', { name: '生成资源' }).click();
  await assertText(page, '生成任务与内容审核');
  await verifyTeacherResources(page);
  await assertNoOverflow(page, '教师端桌面');
  await page.screenshot({ path: `${outputDirectory}/教师端-课程运营-桌面.png`, fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await assertNoOverflow(page, '教师端移动');
  await page.screenshot({ path: `${outputDirectory}/教师端-课程运营-移动.png`, fullPage: true });
  await context.close();
}

async function verifyAdmin(browser) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const login = await context.request.post(`${requiredEnvironment('ADMIN_WEB_URL', 'http://127.0.0.1:5173')}/api/admin/auth/login`, {
    data: {
      loginName: requiredEnvironment('ADMIN_LOGIN', 'admin.demo'),
      password: requiredEnvironment('ADMIN_PASSWORD'),
    },
  });
  if (!login.ok()) throw new Error(`管理员登录失败: ${login.status()}`);
  const page = await context.newPage();
  await attachDiagnostics(page, '管理端');
  await page.goto(`${requiredEnvironment('ADMIN_WEB_URL', 'http://127.0.0.1:5173')}/#/dashboard`);
  await page.waitForLoadState('networkidle');
  await assertText(page, '运营概览');
  await page.goto(`${requiredEnvironment('ADMIN_WEB_URL', 'http://127.0.0.1:5173')}/#/ai-models`);
  await page.locator('.ai-page-tabs button').filter({ hasText: '默认模型' }).click();
  await assertText(page, '画像分析、路径规划、内容生成、练习生成和事实校验');
  await page.goto(`${requiredEnvironment('ADMIN_WEB_URL', 'http://127.0.0.1:5173')}/#/content-safety`);
  await assertText(page, '内容安全');
  await assertText(page, '提示词注入');
  await page.goto(`${requiredEnvironment('ADMIN_WEB_URL', 'http://127.0.0.1:5173')}/#/permissions`);
  await assertText(page, '权限点');
  await assertNoOverflow(page, '管理端桌面');
  await page.screenshot({ path: `${outputDirectory}/管理端-运营治理-桌面.png`, fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await assertNoOverflow(page, '管理端移动');
  await page.screenshot({ path: `${outputDirectory}/管理端-运营治理-移动.png`, fullPage: true });
  await context.close();
}

await mkdir(outputDirectory, { recursive: true });
const browser = await chromium.launch({ headless: true, executablePath: browserExecutable || undefined });
try {
  await verifyStudent(browser);
  await verifyTeacher(browser);
  await verifyAdmin(browser);
  if (issues.length) throw new Error(`浏览器控制台或网络异常:\n${issues.join('\n')}`);
  process.stdout.write('浏览器验收通过：学生、教师、管理员三角色，桌面与移动端共 6 个页面。\n');
} finally {
  await browser.close();
}
