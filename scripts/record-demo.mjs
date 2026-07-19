import { mkdir, copyFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const playwrightModules = process.env.PLAYWRIGHT_NODE_MODULES;
if (!playwrightModules) throw new Error('缺少 PLAYWRIGHT_NODE_MODULES');
const require = createRequire(import.meta.url);
const { chromium } = require(`${playwrightModules}/playwright`);

const browserExecutable = process.env.PLAYWRIGHT_BROWSER_EXECUTABLE;
const outputDirectory = fileURLToPath(new URL('../target/demo-video/recordings/', import.meta.url));
const studentWeb = process.env.STUDENT_WEB_URL || 'http://127.0.0.1:5174';
const adminWeb = process.env.ADMIN_WEB_URL || 'http://127.0.0.1:5173';

function requiredEnvironment(name) {
  const value = process.env[name];
  if (!value) throw new Error(`缺少 ${name}`);
  return value;
}

async function pause(page, milliseconds = 1800) {
  await page.waitForTimeout(milliseconds);
}

async function loginBusiness(page, loginName) {
  await page.locator('input[name="loginName"]').fill(loginName);
  await page.locator('input[name="password"]').fill(requiredEnvironment('BUSINESS_PASSWORD'));
  await page.getByRole('button', { name: '进入平台' }).click();
  await page.waitForSelector('text=课程', { timeout: 15_000 });
  await pause(page);
}

async function waitForTeacherResources(page) {
  await page.locator('.workspace-tabs button.active', { hasText: '生成资源' }).waitFor({ state: 'visible', timeout: 15_000 });
  await page.locator('.task-table > article').nth(1).waitFor({ state: 'visible', timeout: 15_000 });
  await page.locator('.governance-list > article').nth(9).waitFor({ state: 'visible', timeout: 15_000 });
  const taskCount = await page.locator('.task-table > article').count();
  const resourceCount = await page.locator('.governance-list > article').count();
  if (taskCount !== 2 || resourceCount !== 10) {
    throw new Error(`教师端录制数据不完整：生成任务 ${taskCount} 条，资源 ${resourceCount} 条`);
  }
}

async function recordStudent(browser) {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    recordVideo: { dir: outputDirectory, size: { width: 1440, height: 900 } },
  });
  const page = await context.newPage();
  await page.goto(`${studentWeb}/#/courses`);
  await page.waitForLoadState('networkidle');
  await loginBusiness(page, process.env.STUDENT_LOGIN || 'student.demo');
  await page.getByRole('button', { name: /学习画像/ }).click();
  await page.waitForSelector('text=动态学习画像');
  await pause(page, 3500);
  await page.getByRole('button', { name: /课程/ }).click();
  await page.waitForSelector('text=全部课程');
  await page.getByRole('button', { name: /JAVA_WEB.*Java Web 应用开发/s }).click();
  await pause(page, 2500);
  await page.getByRole('button', { name: '我的学习路径' }).click();
  await page.waitForSelector('text=个性化学习路径');
  await pause(page, 3500);
  await page.goto(`${studentWeb}/#/courses/8100001/learning-resources`);
  await page.waitForSelector('text=多智能体个性化资源');
  await page.locator('.task-list > button').first().click();
  await page.locator('.agent-dag > button').nth(2).click();
  await pause(page, 3500);
  const firstResource = page.locator('.resource-card').first();
  if (await firstResource.count()) {
    await firstResource.click();
    await pause(page, 3500);
  }
  const video = page.video();
  await context.close();
  await copyFile(await video.path(), `${outputDirectory}/student.webm`);
}

async function recordTeacher(browser) {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    recordVideo: { dir: outputDirectory, size: { width: 1440, height: 900 } },
  });
  const page = await context.newPage();
  await page.goto(`${studentWeb}/#/courses`);
  await page.waitForLoadState('networkidle');
  await loginBusiness(page, process.env.TEACHER_LOGIN || 'teacher.demo');
  await page.getByRole('button', { name: /JAVA_WEB.*Java Web 应用开发/s }).click();
  await pause(page, 2500);
  await page.getByRole('button', { name: '章节内容' }).click();
  await pause(page, 2500);
  await page.getByRole('button', { name: '课程资料库' }).click();
  await pause(page, 3000);
  await page.getByRole('button', { name: '学情分析' }).click();
  await pause(page, 3000);
  await page.getByRole('button', { name: '生成资源' }).click();
  await waitForTeacherResources(page);
  await pause(page, 3500);
  const video = page.video();
  await context.close();
  await copyFile(await video.path(), `${outputDirectory}/teacher.webm`);
}

async function recordAdmin(browser) {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    recordVideo: { dir: outputDirectory, size: { width: 1440, height: 900 } },
  });
  const login = await context.request.post(`${adminWeb}/api/admin/auth/login`, {
    data: {
      loginName: process.env.ADMIN_LOGIN || 'admin.demo',
      password: requiredEnvironment('ADMIN_PASSWORD'),
    },
  });
  if (!login.ok()) throw new Error(`管理员登录失败: ${login.status()}`);
  const page = await context.newPage();
  await page.goto(`${adminWeb}/#/dashboard`);
  await page.waitForLoadState('networkidle');
  await pause(page, 2500);
  await page.goto(`${adminWeb}/#/ai-models`);
  await page.locator('.ai-page-tabs button').filter({ hasText: '默认模型' }).click();
  await pause(page, 3000);
  await page.goto(`${adminWeb}/#/ragflow`);
  await pause(page, 3000);
  await page.goto(`${adminWeb}/#/content-safety`);
  await pause(page, 3500);
  await page.goto(`${adminWeb}/#/permissions`);
  await pause(page, 2500);
  const video = page.video();
  await context.close();
  await copyFile(await video.path(), `${outputDirectory}/admin.webm`);
}

await mkdir(outputDirectory, { recursive: true });
const browser = await chromium.launch({ headless: true, executablePath: browserExecutable || undefined });
try {
  await recordStudent(browser);
  await recordTeacher(browser);
  await recordAdmin(browser);
  process.stdout.write('真实系统操作录屏完成：学生、教师、管理员共 3 段。\n');
} finally {
  await browser.close();
}
