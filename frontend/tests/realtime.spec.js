import { test, expect } from '@playwright/test';
import { createServer } from 'node:http';
import { addDays } from '../src/schedule.js';
import { planWorkbook } from '../src/autoScheduleWorkbook.js';

test.setTimeout(90000);
const anchor = '2026-08-10';
const periods = [1, 2, 3].map(p => ({ period: p, start: `${String(p + 6).padStart(2, '0')}:00`, end: `${String(p + 6).padStart(2, '0')}:50` }));

async function liveApi() {
  const state = { room: '101', revision: 0, streams: new Map(), reads: [], requests: [] };
  const users = {
    'teacher-live': { teacherId: 'GV01', role: 'TEACHER', username: 'teacher', fullName: 'Giảng viên A', semesterStartDate: anchor },
    'admin-live': { teacherId: 'ADMIN', role: 'ADMIN', username: 'admin', fullName: 'Quản lý', semesterStartDate: anchor },
  };
  function broadcast() {
    state.revision++;
    for (const [response, user] of state.streams) {
      if (user.role === 'ADMIN' || user.teacherId === 'GV01') {
        response.write(`event: schedule-changed\ndata: ${JSON.stringify({ revision: state.revision })}\n\n`);
      }
    }
  }
  const server = createServer(async (request, response) => {
    response.setHeader('Access-Control-Allow-Origin', request.headers.origin || '*');
    response.setHeader('Access-Control-Allow-Headers', 'Authorization, Accept, Content-Type, Cache-Control, Pragma');
    response.setHeader('Access-Control-Allow-Methods', 'GET, POST, DELETE, OPTIONS');
    if (request.method === 'OPTIONS') { response.writeHead(204); response.end(); return; }
    const url = new URL(request.url, 'http://fixture');
    const path = url.pathname.replace('/api/schedule', '');
    const user = users[request.headers.authorization?.replace('Bearer ', '')];
    const json = (body, status = 200) => { response.writeHead(status, { 'Content-Type': 'application/json' }); response.end(JSON.stringify(body)); };
    if (!user) { json({ message: 'Unauthorized' }, 401); return; }
    state.requests.push({ path, authorization: request.headers.authorization, query: url.search });
    if (path === '/auth/me') { json(user); return; }
    if (path === '/events') {
      response.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-store' });
      response.write(`event: ready\ndata: ${JSON.stringify({ revision: state.revision })}\n\n`);
      state.streams.set(response, user);
      response.on('close', () => state.streams.delete(response));
      return;
    }
    if (path.startsWith('/week/')) {
      const week = Number(path.split('/').at(-1));
      state.reads.push({ teacherId: user.teacherId, week });
      const monday = addDays(anchor, (week - 1) * 7);
      json({ teacherId: url.searchParams.get('teacherId'), weekIndex: week, startDate: monday, endDate: addDays(monday, 6),
        schools: [{ schoolId: 'PTIT', schoolName: 'Khoa', periods }],
        lessons: state.room ? [{ schoolId: 'PTIT', schoolName: 'Khoa', colorCode: '#177c62', courseName: 'Mạng máy tính',
          room: state.room, dayOfWeek: 2, date: monday, startPeriod: 1, endPeriod: 3, startTime: '07:00', endTime: '09:50', isConflict: false }] : [] });
      return;
    }
    if (path === '/rooms') { json([{ roomCode: '101', capacity: 60 }]); return; }
    if (path === '/teacher-subjects') { json([{ teacherId: 'GV01', courseCode: 'NET' }]); return; }
    if (path === '/auto-generate-3files' && request.method === 'POST' && user.role === 'ADMIN') {
      for await (const chunk of request) { /* Consume the actual uploaded workbook. */ }
      state.room = '202'; broadcast();
      json({ saved: true, status: 'SUCCESS', message: 'Đã lưu lịch mới.', schoolId: 'PTIT', schoolName: 'Khoa', semesterStartDate: anchor,
        totalClassesScheduled: 1, periods, unscheduledClasses: [], unscheduledWarnings: [],
        scheduledClasses: [{ classId: 'NET:2024:1:1:LT', teacherId: 'GV01', teacherName: 'Giảng viên A', courseCode: 'NET',
          courseName: 'Mạng máy tính', baseCourseName: 'Mạng máy tính', classGroup: 'Lớp 01', cohort: '2024', credits: 3,
          sessionType: 'THEORY', studentCount: 30, room: '202', dayOfWeek: 2, startPeriod: 1, numPeriods: 3, weekSchedule: '1'.repeat(16) }] });
      return;
    }
    if (path === '/department-schedule' && request.method === 'DELETE' && user.role === 'ADMIN') {
      state.room = null; broadcast(); json({ message: 'Đã xóa lịch.' }); return;
    }
    json({ message: 'Not found' }, 404);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  return {
    state, origin: `http://127.0.0.1:${server.address().port}`,
    change(room) { state.room = room; broadcast(); },
    dropAndChange(room) { for (const response of state.streams.keys()) response.end(); state.room = room; state.revision++; },
    async close() { for (const response of state.streams.keys()) response.end(); server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); },
  };
}

async function openAccount(browser, api, role, testInfo, fixedTime = false) {
  const { baseURL, viewport, isMobile, hasTouch } = testInfo.project.use;
  const context = await browser.newContext({ baseURL, viewport, isMobile, hasTouch });
  const page = await context.newPage();
  await page.addInitScript(({ origin, role, fixedTime }) => {
    sessionStorage.setItem('campus-access-token', `${role}-live`);
    const nativeFetch = globalThis.fetch.bind(globalThis);
    globalThis.fetch = (input, options) => {
      const value = typeof input === 'string' ? input : input.url;
      const url = new URL(value, location.href);
      return nativeFetch(url.pathname.startsWith('/api/schedule/') ? `${origin}${url.pathname}${url.search}` : input, options);
    };
    if (fixedTime) {
      const NativeDate = Date, now = NativeDate.parse('2026-08-10T00:00:00Z');
      globalThis.Date = class extends NativeDate {
        constructor(...args) { super(...(args.length ? args : [now])); }
        static now() { return now; }
      };
    }
  }, { origin: api.origin, role, fixedTime });
  await page.route('https://fonts.googleapis.com/**', route => route.abort());
  await page.goto('/');
  await expect(page.getByText('Đang nhận cập nhật lịch trực tiếp', { exact: true })).toBeVisible();
  await expect(page.locator('.lesson-card-room')).toHaveText('101');
  await page.evaluate(() => { window.__calendarDocument = 'unchanged'; });
  return { page, context };
}

async function navigate(page, label) {
  const menu = page.getByRole('button', { name: 'Mở điều hướng' });
  if (await menu.isVisible()) await menu.click();
  await page.getByRole('button', { name: label, exact: true }).click();
}

test('admin saves and deletes while a teacher calendar updates without reload or losing week and search', async ({ browser }, testInfo) => {
  const api = await liveApi();
  const accounts = [];
  try {
    const teacher = await openAccount(browser, api, 'teacher', testInfo); accounts.push(teacher);
    await teacher.page.getByRole('button', { name: 'Tuần sau', exact: true }).click();
    await expect(teacher.page.locator('.calendar-toolbar .badge')).toHaveText('Tuần 2');
    await teacher.page.getByRole('textbox', { name: 'Tìm môn hoặc phòng' }).fill('Mạng');
    const admin = await openAccount(browser, api, 'admin', testInfo); accounts.push(admin);
    await navigate(admin.page, 'Xếp lịch của khoa');
    const workbook = planWorkbook([{ courseCode: 'NET', courseName: 'Mạng máy tính', classType: 'LT',
      classSize: 30, cohort: '2024', numClasses: 1, studyStartDate: anchor, studyEndDate: addDays(anchor, 105), credits: 3, totalPeriods: 48 }]);
    await admin.page.locator('#auto-plan-file').setInputFiles({ name: 'plan.xlsx',
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: Buffer.from(await workbook.xlsx.writeBuffer()) });
    const run = admin.page.getByRole('button', { name: 'Tải File Mở Lớp & Chạy Xếp Lịch Khoa' });
    await run.evaluate(button => button.scrollIntoView({ block: 'center' })); await run.click();
    await expect(teacher.page.locator('.lesson-card-room')).toHaveText('202');
    await expect(teacher.page.getByRole('textbox', { name: 'Tìm môn hoặc phòng' })).toHaveValue('Mạng');
    await expect(teacher.page.locator('.calendar-toolbar .badge')).toHaveText('Tuần 2');
    expect(await teacher.page.evaluate(() => window.__calendarDocument)).toBe('unchanged');
    expect(api.state.reads.filter(row => row.teacherId === 'GV01').at(-1).week).toBe(2);
    const subscription = api.state.requests.find(row => row.path === '/events' && row.authorization === 'Bearer teacher-live');
    expect(subscription.query).toBe('');

    admin.page.once('dialog', dialog => dialog.accept());
    await admin.page.getByRole('button', { name: 'Xóa toàn bộ lịch cũ của Khoa' }).click();
    await expect(teacher.page.locator('.lesson-card')).toHaveCount(0);
    expect(await teacher.page.evaluate(() => window.__calendarDocument)).toBe('unchanged');
  } finally { for (const account of accounts) await account.context.close(); await api.close(); }
});

test('reconnection reconciles a missed change and logout closes the live connection', async ({ browser }, testInfo) => {
  const api = await liveApi();
  let account;
  try {
    account = await openAccount(browser, api, 'teacher', testInfo);
    // Let the initial ready reconciliation finish before dropping the connection.
    await expect.poll(() => api.state.reads.filter(row => row.teacherId === 'GV01').length).toBeGreaterThanOrEqual(2);
    const initialConnections = api.state.requests.filter(row => row.path === '/events').length;
    api.dropAndChange('404');
    await expect.poll(() => api.state.requests.filter(row => row.path === '/events').length).toBeGreaterThan(initialConnections);
    await expect(account.page.locator('.lesson-card-room')).toHaveText('404');
    expect(await account.page.evaluate(() => window.__calendarDocument)).toBe('unchanged');
    await navigate(account.page, 'Đăng xuất');
    await expect(account.page.getByRole('heading', { name: 'Đăng nhập hệ thống' })).toBeVisible();
    await expect.poll(() => api.state.streams.size).toBe(0);
  } finally { if (account) await account.context.close(); await api.close(); }
});

test('the Today page also reflects schedule changes without reloading', async ({ browser }, testInfo) => {
  const api = await liveApi();
  let account;
  try {
    account = await openAccount(browser, api, 'teacher', testInfo, true);
    await navigate(account.page, 'Hôm nay');
    await expect(account.page.locator('.today-next-card .today-location')).toContainText('Phòng 101');
    api.change('505');
    await expect(account.page.locator('.today-next-card .today-location')).toContainText('Phòng 505');
    expect(await account.page.evaluate(() => window.__calendarDocument)).toBe('unchanged');
  } finally { if (account) await account.context.close(); await api.close(); }
});
