import { test, expect } from '@playwright/test';
import { templateWorkbook } from '../src/excel.js';
import { addDays } from '../src/schedule.js';

const teacherId = 'GV_1234567890ABCDEF1234567890ABCDEF';
const anchor = '2026-08-10';
const mime = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const slot = { teacherId: 'GV01', teacherName: 'Nguyễn Văn A', courseCode: 'NET', courseName: 'Mạng máy tính', baseCourseName: 'Mạng máy tính', classGroup: '01', cohort: '2024', credits: 3, sessionType: 'THEORY', studentCount: 50, room: '101', roomCapacity: 60, dayOfWeek: 2, startPeriod: 1, numPeriods: 1, weekSchedule: '---11' };

async function boot(page, { role = 'TEACHER', anonymous = false, restoreFailures = 0, lessons = [], schools } = {}) {
  const state = { calls: [], saved: false, passwordStatus: 200, autoSaved: false, emptyAuto: false, restoreFailures, roomCount: 1, lessons, schools, weekFailure: false };
  const user = { role, teacherId: role === 'ADMIN' ? 'ADMIN' : teacherId, username: role.toLowerCase(), fullName: role === 'ADMIN' ? 'Quản lý đào tạo' : 'Nguyễn Văn A', semesterStartDate: anchor };
  if (!anonymous) await page.addInitScript(() => sessionStorage.setItem('campus-access-token', 'test-token'));
  await page.route('https://fonts.googleapis.com/**', route => route.abort());
  await page.route('**/api/schedule/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/schedule', '');
    state.calls.push({ path, query: url.search, method: request.method(), body: request.postData(), authorization: request.headers().authorization });
    const respond = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (path === '/auth/me') {
      if (state.restoreFailures > 0) return respond({ message: 'Máy chủ đang bận.' }, 500);
      return respond(user);
    }
    if (path === '/auth/register') return respond({ ...user, status: 'SUCCESS', accessToken: 'registered-token' });
    if (path === '/auth/change-password') return state.passwordStatus === 200
      ? respond({ status: 'SUCCESS', message: 'Đổi mật khẩu thành công.' })
      : respond({ status: 'ERROR', message: 'Mật khẩu hiện tại không đúng.' }, state.passwordStatus);
    if (path === '/rooms') return respond(Array.from({ length: state.roomCount }, (_, i) => ({ roomCode: String(i + 1), schoolCode: url.searchParams.get('schoolId') })));
    if (path === '/auto-generate-3files') return respond({
      saved: state.autoSaved, status: state.autoSaved ? 'SUCCESS' : 'NOT_SAVED', message: state.autoSaved ? 'Đã lưu đầy đủ lịch.' : 'Không đủ phòng, lịch hiện có được giữ nguyên.',
      schoolId: 'PTIT', schoolName: 'Học viện PTIT', semesterStartDate: anchor,
      scheduledClasses: state.emptyAuto ? [] : [slot], totalClassesScheduled: state.emptyAuto ? 0 : 1,
      totalRoomsAvailable: 1, unscheduledWarnings: state.autoSaved ? [] : ['Thiếu phòng LAB cho một tổ thực hành.'],
      periods: [{ period: 1, start: '07:20', end: '08:10' }],
    });
    if (path === '/upload-excel-teacher') { state.saved = true; return respond({ status: 'SUCCESS' }); }
    if (path.startsWith('/week/')) {
      if (state.weekFailure) return respond({ message: 'Máy chủ đang bận.' }, 500);
      const week = Number(path.split('/').at(-1));
      const startDate = addDays(anchor, (week - 1) * 7);
      return respond({ teacherId: url.searchParams.get('teacherId'), weekIndex: week, startDate, endDate: addDays(startDate, 6), lessons: state.lessons, schools: state.schools });
    }
    return respond({ message: `Unexpected API: ${path}` }, 404);
  });
  await page.goto('/');
  if (!anonymous && !restoreFailures) await expect(page.getByRole('heading', { name: role === 'ADMIN' ? 'Xếp lịch toàn trường.' : 'Lịch giảng dạy.' })).toBeVisible();
  return state;
}

async function navigate(page, name) {
  const menu = page.getByRole('button', { name: 'Mở điều hướng' });
  if (await menu.isVisible()) await menu.click();
  await page.getByRole('button', { name, exact: true }).click();
}
async function noOverflow(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
}
async function screenshot(page, testInfo, name) {
  await noOverflow(page);
  await page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true });
}
async function workbookFile(edit = () => {}) {
  const workbook = templateWorkbook();
  workbook.getWorksheet('MonHoc').addRow(['NET', 'Networking', '01', 3, 2, 1, 1, '101', '11/08/26 - 24/08/26']);
  workbook.getWorksheet('TietHoc').addRow([1, '07:20', '08:10']);
  edit(workbook);
  return { name: 'schedule.xlsx', mimeType: mime, buffer: Buffer.from(await workbook.xlsx.writeBuffer()) };
}
async function runManual(page) {
  await page.getByText('Hoặc nhập/chỉnh sửa nhanh', { exact: false }).click();
  await page.getByRole('button', { name: 'Xếp lịch từ 3 bảng dữ liệu trên' }).click();
}

test('weekly calendar shows every server period after reload and uses each reference school real times', async ({ page }, testInfo) => {
  const periods = Array.from({ length: 12 }, (_, i) => {
    const hour = String(7 + i + (i >= 5 ? 1 : 0)).padStart(2, '0');
    return { period: i + 1, start: `${hour}:00`, end: `${hour}:50` };
  });
  const schools = [
    { schoolId: 'PTIT', schoolName: 'PTIT', periods },
    { schoolId: 'OTHER', schoolName: 'Other school', periods: [{ period: 1, start: '07:20', end: '08:10' }, { period: 2, start: '08:30', end: '09:20' }] },
  ];
  const lessons = [
    { schoolId: 'PTIT', schoolName: 'PTIT', courseName: 'Networking', room: '101', dayOfWeek: 2, date: anchor, startPeriod: 4, endPeriod: 6, startTime: '10:00', endTime: '13:50' },
    { schoolId: 'OTHER', schoolName: 'Other school', courseName: 'Algorithms', room: '201', dayOfWeek: 3, date: addDays(anchor, 1), startPeriod: 1, endPeriod: 1, startTime: '07:20', endTime: '08:10' },
  ];
  await boot(page, { lessons, schools });
  const axis = page.locator('.time-axis > span');
  const expected = periods.map(period => String(period.period));
  await expect(axis).toHaveText(expected);
  await expect(axis.nth(5)).toHaveAttribute('title', '13:00 – 13:50');
  expect(await axis.nth(5).evaluate(element => parseFloat(element.style.top))).toBeCloseTo(399);
  const networking = page.locator('.session-card').filter({ hasText: 'Networking' });
  await expect(networking.locator('.lesson-card-school')).toBeVisible();
  expect(await networking.evaluate(element => parseFloat(element.style.top))).toBeCloseTo(200);

  // An old browser import must not replace the current period table from the server.
  await page.evaluate(id => localStorage.setItem('campus-period-configs', JSON.stringify({ [id]: { PTIT: { periods: [{ period: 99, start: '06:00', end: '06:50' }] } } })), teacherId);
  await page.reload();
  await expect(axis).toHaveText(expected);
  await expect(axis.first()).toHaveAttribute('title', '07:00 – 07:50');
  await screenshot(page, testInfo, 'complete-period-axis');

  await page.getByLabel('Tiết tham chiếu', { exact: false }).selectOption('OTHER');
  await expect(axis).toHaveText(['1', '2']);
  await expect(axis.first()).toHaveAttribute('title', '07:20 – 08:10');
  expect(await axis.first().evaluate(element => parseFloat(element.style.top))).toBeCloseTo(25);
  expect(await networking.evaluate(element => parseFloat(element.style.top))).toBeCloseTo(200);
});

const shortLesson = {
  schoolId: 'PTIT', schoolName: 'Học viện Công nghệ Bưu chính Viễn thông', colorCode: '#2563eb',
  courseName: 'Phân tích và thiết kế hệ thống thông tin (Nhóm 7)', room: '505-NT',
  dayOfWeek: 6, date: '2026-08-14', startPeriod: 6, endPeriod: 6, startTime: '12:00', endTime: '12:50',
};

test('short lesson keeps time and room visible and opens full details by click or touch', async ({ page }, testInfo) => {
  const adjacent = { ...shortLesson, courseName: 'Môn học kế tiếp', startTime: '12:50', endTime: '13:40', startPeriod: 7, endPeriod: 7 };
  await boot(page, { lessons: [shortLesson, adjacent] });
  const card = page.locator('.lesson-card').filter({ hasText: shortLesson.courseName });
  await expect(card).toHaveClass(/is-compact/);
  await card.scrollIntoViewIfNeeded();
  await expect(card.locator('.session-time')).toHaveText('12:00 – 12:50');
  await expect(card.locator('.lesson-card-room')).toHaveText('505-NT');
  await expect(card.locator('.lesson-card-school')).toBeHidden();
  const bounds = await card.boundingBox();
  const room = await card.locator('.lesson-card-room').boundingBox();
  expect(room.y).toBeGreaterThanOrEqual(bounds.y);
  expect(room.y + room.height).toBeLessThanOrEqual(bounds.y + bounds.height);
  expect(bounds.height).toBeCloseTo(51);
  const next = await page.locator('.lesson-card').filter({ hasText: adjacent.courseName }).boundingBox();
  expect(bounds.y + bounds.height).toBeLessThanOrEqual(next.y);
  await expect(card).toHaveAccessibleName(/Phòng 505-NT.*Tiết 6/);
  if (testInfo.project.name === 'mobile') await card.tap();
  else await card.click();
  const modal = page.getByRole('dialog');
  await expect(modal).toBeVisible();
  await expect(modal).toContainText(shortLesson.courseName);
  await expect(modal).toContainText(shortLesson.schoolName);
  await expect(modal).toContainText('505-NT');
  await modal.getByRole('button', { name: 'Đóng', exact: true }).click();
  await expect(card).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('tooltip')).toHaveCount(0);
  await screenshot(page, testInfo, 'short-lesson');
});

test('short lesson popup supports keyboard, Escape and pointer travel within the viewport', async ({ page }, testInfo) => {
  await boot(page, { lessons: [{ ...shortLesson, dayOfWeek: 8, date: '2026-08-16' }] });
  const card = page.locator('.lesson-card');
  await card.scrollIntoViewIfNeeded();
  await card.focus();
  const tooltip = page.getByRole('tooltip');
  await expect(tooltip).toBeVisible();
  await expect(card).toHaveAttribute('aria-describedby', await tooltip.getAttribute('id'));
  await expect(tooltip).toContainText(shortLesson.courseName);
  await expect(tooltip).toContainText(shortLesson.schoolName);
  await expect(tooltip).toContainText('505-NT');
  const bounds = await tooltip.boundingBox();
  const viewport = page.viewportSize();
  expect(bounds.x).toBeGreaterThanOrEqual(0);
  expect(bounds.y).toBeGreaterThanOrEqual(0);
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(viewport.width);
  expect(bounds.y + bounds.height).toBeLessThanOrEqual(viewport.height);
  await page.keyboard.press('Escape');
  await expect(tooltip).toHaveCount(0);
  await expect(card).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.keyboard.press('Escape');
  if (testInfo.project.name === 'desktop') {
    await page.getByLabel('Tìm môn hoặc phòng').focus();
    await card.hover();
    await expect(tooltip).toBeVisible();
    await tooltip.hover();
    await expect(tooltip).toBeVisible();
    await page.getByLabel('Tìm môn hoặc phòng').hover();
    await expect(tooltip).toHaveCount(0);
  }
  await page.getByLabel('Tìm môn hoặc phòng').focus();
  await card.focus();
  await expect(tooltip).toBeVisible();
  await page.locator('.calendar-scroll').evaluate(element => element.dispatchEvent(new Event('scroll')));
  await expect(tooltip).toHaveCount(0);
  await noOverflow(page);
});

test('short lesson handles narrow conflict lanes, resizing and very short durations', async ({ page }, testInfo) => {
  const conflicting = { ...shortLesson, isConflict: true, conflictReason: 'Trùng giờ với buổi thực hành.' };
  const other = { ...conflicting, courseName: 'Buổi thực hành trùng giờ', room: 'LAB-01' };
  const tiny = { ...shortLesson, courseName: 'Buổi hướng dẫn 15 phút', startTime: '13:00', endTime: '13:15', startPeriod: 7, endPeriod: 7 };
  await boot(page, { lessons: [conflicting, other, tiny] });
  const card = page.locator('.lesson-card').filter({ hasText: shortLesson.courseName });
  const neighbour = page.locator('.lesson-card').filter({ hasText: other.courseName });
  await card.scrollIntoViewIfNeeded();
  await expect(card).toHaveClass(/is-narrow/);
  await expect(card.locator('.lesson-conflict-icon')).toBeVisible();
  const bounds = await card.boundingBox();
  const next = await neighbour.boundingBox();
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(next.x);
  const small = page.locator('.lesson-card').filter({ hasText: tiny.courseName });
  await expect(small).toHaveClass(/is-tiny/);
  expect((await small.boundingBox()).height).toBeCloseTo(12.5);
  await card.focus();
  await expect(page.getByRole('tooltip')).toContainText(conflicting.conflictReason);
  await page.setViewportSize({ width: testInfo.project.name === 'desktop' ? 1200 : 430, height: 900 });
  await expect(page.getByRole('tooltip')).toHaveCount(0);
  await small.focus();
  await page.keyboard.press('Space');
  await expect(page.getByRole('dialog')).toContainText(tiny.courseName);
  await page.keyboard.press('Escape');
  await noOverflow(page);
});

async function freezeTime(page, time) {
  const instant = new Date(time);
  await page.clock.install({ time: new Date(instant.getTime() - 1000) });
  await page.clock.pauseAt(instant);
}

const dailyLessons = [
  { ...shortLesson, courseName: 'Buổi đã dạy thứ Hai', date: '2026-10-26', dayOfWeek: 2, startTime: '07:00', endTime: '08:00', isConflict: true },
  { ...shortLesson, courseName: 'Môn đang dạy', date: '2026-10-27', dayOfWeek: 3, startTime: '09:00', endTime: '10:00' },
  { ...shortLesson, courseName: 'Môn trùng giờ A', date: '2026-10-27', dayOfWeek: 3, startTime: '13:00', endTime: '14:00', isConflict: true },
  { ...shortLesson, courseName: 'Môn trùng giờ B', date: '2026-10-27', dayOfWeek: 3, startTime: '13:30', endTime: '14:30', isConflict: true },
  { ...shortLesson, courseName: 'Môn ngày mai', date: '2026-10-28', dayOfWeek: 4, startTime: '07:00', endTime: '08:00' },
];

test('today dashboard is independent of calendar week and filters and opens the current week', async ({ page }, testInfo) => {
  await freezeTime(page, '2026-10-27T02:30:00Z');
  const state = await boot(page, { lessons: dailyLessons });
  await page.getByRole('button', { name: 'Tuần sau', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Lịch tuần Tuần 2' })).toBeVisible();
  await page.getByLabel('Tìm môn hoặc phòng').fill('không tìm thấy');
  await navigate(page, 'Hôm nay');
  await expect(page.getByRole('heading', { name: 'Hôm nay.', exact: true })).toBeVisible();
  await expect(page.locator('.today-toolbar')).toContainText('Tuần 12');
  await expect(page.locator('.today-summary-card>strong')).toHaveText(['3', '4', '3.5', '2']);
  await expect(page.locator('.today-next-card')).toContainText('Môn đang dạy');
  await expect(page.locator('.today-next-card')).toContainText('Đang dạy · còn 30 phút');
  await expect(page.locator('.today-lesson-list li')).toHaveCount(3);
  await expect(page.locator('.today-attention li')).toHaveCount(2);
  await expect(page.locator('.today-attention')).not.toContainText('Buổi đã dạy thứ Hai');
  await expect(page.locator('.week-calendar')).toHaveCount(0);
  expect(state.calls.some(call => call.path === '/week/12' && call.query.includes(teacherId))).toBe(true);
  await page.locator('.today-next-card').click();
  await expect(page.getByRole('dialog')).toContainText('Môn đang dạy');
  await page.keyboard.press('Escape');
  await screenshot(page, testInfo, 'today-dashboard');
  await page.locator('.today-attention li').first().getByRole('button').click();
  await expect(page.getByRole('heading', { name: 'Lịch tuần Tuần 12' })).toBeVisible();
  await expect(page.getByLabel('Tìm môn hoặc phòng')).toHaveValue('');
  await expect(page.locator('.session-card')).toHaveCount(dailyLessons.length);
});

test('today dashboard updates at lesson end and Vietnam midnight across a new week', async ({ page }) => {
  await freezeTime(page, '2026-11-01T16:59:58Z');
  const state = await boot(page);
  await navigate(page, 'Hôm nay');
  await expect(page.locator('.today-toolbar')).toContainText('Tuần 12');
  await expect(page.getByText('Hôm nay không có lịch dạy.', { exact: true })).toBeVisible();
  await page.clock.runFor(3000);
  await expect(page.locator('.today-toolbar')).toContainText('Tuần 13');
  await expect(page.locator('.today-toolbar time')).toContainText('2 tháng 11');
  await expect.poll(() => state.calls.some(call => call.path === '/week/13')).toBe(true);
});

test('today dashboard refreshes remaining hours and handles empty days and failed loads', async ({ page }) => {
  await freezeTime(page, '2026-10-27T02:30:00Z');
  const state = await boot(page, { lessons: dailyLessons });
  state.weekFailure = true;
  await navigate(page, 'Hôm nay');
  await expect(page.getByRole('alert')).toContainText('Máy chủ đang bận.');
  await expect(page.locator('.today-summary-card')).toHaveCount(0);
  state.weekFailure = false;
  await page.getByRole('button', { name: 'Thử tải lại Hôm nay' }).click();
  await expect(page.locator('.today-next-card')).toContainText('Môn đang dạy');
  await page.clock.fastForward(30 * 60000);
  await expect(page.locator('.today-next-card')).toContainText('Môn trùng giờ A');
  await expect(page.locator('.today-summary-card>strong')).toHaveText(['3', '3', '3', '2']);
  await expect(page.locator('.today-lesson').first()).toContainText('Đã kết thúc');
  state.lessons = [{ ...dailyLessons.at(-1), date: '2026-10-29', dayOfWeek: 5 }];
  await page.getByRole('button', { name: 'Làm mới Hôm nay' }).click();
  await expect(page.getByText('Hôm nay không có lịch dạy.', { exact: true })).toBeVisible();
  await expect(page.locator('.today-next-card')).toContainText('29/10/2026');
  await noOverflow(page);
});

test('today dashboard does not request an invalid week before semester', async ({ page }) => {
  await freezeTime(page, '2026-08-09T02:30:00Z');
  const state = await boot(page);
  await navigate(page, 'Hôm nay');
  await expect(page.getByRole('heading', { name: 'Học kỳ chưa bắt đầu' })).toBeVisible();
  expect(state.calls.some(call => call.path === '/week/0')).toBe(false);
  await noOverflow(page);
});

test('today dashboard uses the semester anchor just saved by personal import', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-10-27T02:30:00Z'));
  const state = await boot(page);
  await navigate(page, 'Nhập lịch dạy');
  await expect(page.getByText('Tuần 1 bắt đầu: 10/08/2026.', { exact: false })).toBeVisible();
  await page.getByLabel('Mã trường', { exact: true }).fill('PTIT');
  await page.getByLabel('Tên trường', { exact: true }).fill('Học viện PTIT');
  await page.getByLabel('Ngày Thứ Hai của tuần 1', { exact: false }).fill('2026-08-17');
  const workbook = templateWorkbook();
  await page.locator('input[type=file]').setInputFiles({ name: 'new-anchor.xlsx', mimeType: mime, buffer: Buffer.from(await workbook.xlsx.writeBuffer()) });
  await page.getByRole('button', { name: 'Đọc file và xem trước' }).click();
  await page.getByRole('checkbox').check();
  await page.getByRole('button', { name: 'Xác nhận lưu và xem lịch' }).click();
  await expect(page.getByRole('heading', { name: 'Lịch giảng dạy.', exact: true })).toBeVisible();
  await navigate(page, 'Hôm nay');
  await expect(page.locator('.today-toolbar')).toContainText('Tuần 11');
  expect(state.calls.some(call => call.path === '/week/11')).toBe(true);
});

test('header clock updates Vietnam time and date at midnight on desktop and mobile', async ({ page }, testInfo) => {
  await page.clock.install({ time: new Date('2026-10-27T16:00:00Z') });
  await page.clock.pauseAt(new Date('2026-10-27T16:59:58Z'));
  await boot(page, { role: 'ADMIN' });
  const clock = page.getByLabel('Giờ Việt Nam', { exact: true });
  await expect(clock).toBeVisible();
  await expect(clock).toHaveText('23:59:58');
  await expect(page.locator('.today-label')).toHaveText('Thứ Ba, 27 tháng 10, 2026');
  await page.clock.runFor(3000);
  await expect(clock).toHaveText('00:00:01');
  await expect(page.locator('.today-label')).toHaveText('Thứ Tư, 28 tháng 10, 2026');
  await noOverflow(page);
  await page.screenshot({ path: testInfo.outputPath('header-clock.png') });
});

test('registration validates UTF-8 password and only sends public account fields', async ({ page }, testInfo) => {
  const state = await boot(page, { anonymous: true });
  await page.getByRole('button', { name: 'Đăng ký mới' }).click();
  await page.getByLabel('Tên đăng nhập', { exact: true }).fill('newteacher');
  await page.getByLabel('Họ và tên giảng viên', { exact: true }).fill('Nguyễn Văn A');
  await page.getByLabel('Mật khẩu', { exact: false }).fill('ế'.repeat(25));
  await page.getByRole('button', { name: 'Hoàn tất đăng ký' }).click();
  await expect(page.getByRole('alert')).toContainText('72 byte');
  expect(state.calls.filter(call => call.path === '/auth/register')).toHaveLength(0);
  await page.getByLabel('Mật khẩu', { exact: false }).fill('password123');
  await page.getByRole('button', { name: 'Hoàn tất đăng ký' }).click();
  await expect(page.getByRole('heading', { name: 'Lịch giảng dạy.' })).toBeVisible();
  const request = state.calls.find(call => call.path === '/auth/register');
  expect(Object.keys(JSON.parse(request.body)).sort()).toEqual(['fullName', 'password', 'username']);
  await navigate(page, 'Cài đặt');
  await expect(page.getByText(teacherId, { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cấp tài khoản GV', exact: true })).toHaveCount(0);
  await screenshot(page, testInfo, 'teacher-profile');
});

test('personal import previews real times, invalidates changed metadata and reloads the correct week after save', async ({ page }, testInfo) => {
  const state = await boot(page);
  await navigate(page, 'Nhập lịch dạy');
  await expect(page.getByText('Tuần 1 bắt đầu: 10/08/2026.', { exact: false })).toBeVisible();
  await expect(page.getByLabel('Họ tên giảng viên', { exact: true })).toHaveValue('Nguyễn Văn A');
  await page.getByLabel('Mã trường', { exact: true }).fill('PTIT');
  await page.getByLabel('Tên trường', { exact: true }).fill('Học viện PTIT');
  const file = page.locator('input[type=file]');
  await file.setInputFiles({ name: 'invalid.txt', mimeType: 'text/plain', buffer: Buffer.from('bad') });
  await expect(page.getByRole('button', { name: 'Đọc file và xem trước' })).toBeDisabled();
  await file.setInputFiles(await workbookFile());
  await page.getByRole('button', { name: 'Đọc file và xem trước' }).click();
  await expect(page.getByText('Giờ tiết đã đọc:', { exact: false })).toContainText('07:20–08:10');
  await expect(page.locator('td code')).toHaveText('-11');
  await page.getByRole('checkbox').check();
  await page.getByLabel('Tên trường', { exact: true }).fill('Học viện PTIT mới');
  await expect(page.getByRole('checkbox')).toHaveCount(0);
  await page.getByRole('button', { name: 'Đọc file và xem trước' }).click();
  await screenshot(page, testInfo, 'personal-preview');
  await page.getByRole('checkbox').check();
  await page.getByRole('button', { name: 'Xác nhận lưu và xem lịch' }).click();
  await expect(page.getByRole('heading', { name: 'Lịch giảng dạy.' })).toBeVisible();
  await expect.poll(() => state.calls.some(call => call.path === '/week/2')).toBe(true);
  expect(state.calls.filter(call => call.path === '/upload-excel-teacher')).toHaveLength(1);
});

test('invalid workbook cannot reach the personal upload API', async ({ page }) => {
  const state = await boot(page);
  await navigate(page, 'Nhập lịch dạy');
  await page.getByLabel('Mã trường', { exact: true }).fill('PTIT');
  await page.getByLabel('Tên trường', { exact: true }).fill('Học viện');
  await page.locator('input[type=file]').setInputFiles(await workbookFile(workbook => { workbook.getWorksheet('MonHoc').getRow(2).getCell(7).value = '1.5'; }));
  await page.getByRole('button', { name: 'Đọc file và xem trước' }).click();
  await expect(page.getByRole('alert')).toContainText('số nguyên dương');
  expect(state.calls.filter(call => call.path === '/upload-excel-teacher')).toHaveLength(0);
});

test('unsaved auto result blocks saved schedule navigation and exports a labelled preview with original context', async ({ page }, testInfo) => {
  const state = await boot(page, { role: 'ADMIN' });
  await runManual(page);
  await expect(page.getByRole('heading', { name: 'Bản xem trước — chưa lưu' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Xem TKB' })).toBeDisabled();
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Xuất bản xem trước (.xlsx)' }).click();
  expect((await download).suggestedFilename()).toContain('xem-truoc-chua-luu-');
  await page.getByLabel('Mã trường cần xếp lịch', { exact: true }).fill('OTHER');
  await expect(page.getByText('Đầu vào đã thay đổi.', { exact: false })).toBeVisible();
  await expect(page.locator('.result-context')).toContainText('PTIT');
  expect(state.calls.filter(call => call.path.startsWith('/week/'))).toHaveLength(0);
  await screenshot(page, testInfo, 'auto-preview');
  state.autoSaved = true;
  await page.getByLabel('Mã trường cần xếp lịch', { exact: true }).fill('PTIT');
  await page.getByRole('button', { name: 'Xếp lịch từ 3 bảng dữ liệu trên' }).click();
  await expect(page.getByRole('heading', { name: 'Đã lưu lịch', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Xem TKB' }).click();
  await expect.poll(() => state.calls.some(call => call.path === '/week/4' && call.query.includes('GV01'))).toBe(true);
});

test('manual validation keeps incomplete rows and an empty result disables export', async ({ page }, testInfo) => {
  const state = await boot(page, { role: 'ADMIN' });
  await page.getByText('Hoặc nhập/chỉnh sửa nhanh', { exact: false }).click();
  await page.getByRole('button', { name: 'Thêm dòng Kế hoạch mở lớp' }).click();
  await page.getByRole('button', { name: 'Xếp lịch từ 3 bảng dữ liệu trên' }).click();
  await expect(page.getByRole('alert')).toContainText('dòng 3');
  expect(state.calls.filter(call => call.path === '/auto-generate-3files')).toHaveLength(0);
  await page.locator('.manual-fields table').first().getByRole('button', { name: 'Xóa dòng 3' }).click();
  state.emptyAuto = true;
  await page.getByRole('button', { name: 'Xếp lịch từ 3 bảng dữ liệu trên' }).click();
  await expect(page.getByRole('button', { name: 'Xuất bản xem trước (.xlsx)' })).toBeDisabled();
  await expect(page.getByRole('cell', { name: 'Chưa xếp được buổi học nào.', exact: false })).toBeVisible();
  await screenshot(page, testInfo, 'auto-empty');
});

test('school-scoped room lookup requires a room file when no inventory exists', async ({ page }) => {
  const state = await boot(page, { role: 'ADMIN' });
  state.roomCount = 0;
  await page.getByLabel('Mã trường cần xếp lịch', { exact: true }).fill('NEW');
  await expect(page.getByText('Trường NEW hiện có 0 phòng.', { exact: false })).toBeVisible();
  await page.locator('input[type=file]').first().setInputFiles(await workbookFile());
  await page.getByRole('button', { name: 'Tải 3 File Excel & Chạy xếp lịch tự động' }).click();
  await expect(page.getByRole('alert')).toContainText('Cần file phòng học');
  expect(state.calls.filter(call => call.path === '/auto-generate-3files')).toHaveLength(0);
  expect(state.calls.some(call => call.path === '/rooms' && call.query === '?schoolId=NEW')).toBe(true);
});

test('temporary restore failure preserves token and retry restores the session', async ({ page }, testInfo) => {
  const state = await boot(page, { restoreFailures: 1 });
  await expect(page.getByRole('heading', { name: 'Chưa khôi phục được phiên đăng nhập' })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem('campus-access-token'))).toBe('test-token');
  await screenshot(page, testInfo, 'restore-error');
  state.restoreFailures = 0;
  await page.getByRole('button', { name: 'Thử khôi phục lại' }).click();
  await expect(page.getByRole('heading', { name: 'Lịch giảng dạy.' })).toBeVisible();
});

test('ADMIN has no account provisioning page and settings still allows own password change', async ({ page }, testInfo) => {
  const state = await boot(page, { role: 'ADMIN' });
  await expect(page.getByRole('button', { name: 'Hôm nay', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Tổng quan', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Cấp tài khoản GV', exact: true })).toHaveCount(0);
  await navigate(page, 'Cài đặt');
  await expect(page.getByRole('heading', { name: 'Đổi mật khẩu', exact: true })).toBeVisible();
  await expect(page.getByText('cấp tài khoản cho hồ sơ', { exact: false })).toHaveCount(0);
  await navigate(page, 'Trợ giúp & hỗ trợ');
  await expect(page.getByText('Giảng viên tự đăng ký tài khoản.', { exact: false })).toBeVisible();
  expect(state.calls.some(call => call.path === '/auth/provision')).toBe(false);
  await screenshot(page, testInfo, 'admin-help');
});

test('protected 401 clears session and returns to login', async ({ page }) => {
  await boot(page);
  await page.route('**/api/schedule/week/**', route => route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ message: 'Phiên hết hạn.' }) }));
  await page.getByRole('button', { name: 'Tải lịch', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập hệ thống' })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem('campus-access-token'))).toBeNull();
});

for (const role of ['TEACHER', 'ADMIN']) {
  test(`${role} changes own password in settings, validates input and keeps session on wrong current password`, async ({ page }, testInfo) => {
    const state = await boot(page, { role });
    await navigate(page, 'Cài đặt');
    const current = page.getByLabel('Mật khẩu hiện tại', { exact: true });
    const next = page.getByLabel('Mật khẩu mới', { exact: false }).and(page.locator('[name=newPassword]'));
    const confirmation = page.getByLabel('Xác nhận mật khẩu mới', { exact: true });
    const submit = page.getByRole('button', { name: 'Lưu mật khẩu mới' });
    await current.fill('old-password'); await next.fill('new-password'); await confirmation.fill('mismatch-password');
    await submit.click();
    await expect(page.getByRole('alert')).toContainText('chưa khớp');
    expect(state.calls.filter(call => call.path === '/auth/change-password')).toHaveLength(0);
    await next.fill('ế'.repeat(25)); await confirmation.fill('ế'.repeat(25)); await submit.click();
    await expect(page.getByRole('alert')).toContainText('72 byte');
    expect(state.calls.filter(call => call.path === '/auth/change-password')).toHaveLength(0);
    await next.fill('new-password'); await confirmation.fill('new-password');
    state.passwordStatus = 400; await submit.click();
    await expect(page.getByRole('alert')).toContainText('hiện tại không đúng');
    expect(await page.evaluate(() => sessionStorage.getItem('campus-access-token'))).toBe('test-token');
    state.passwordStatus = 200; await submit.click();
    await expect(page.getByRole('status').filter({ hasText: 'Đổi mật khẩu thành công.' })).toBeVisible();
    await expect(current).toHaveValue(''); await expect(next).toHaveValue(''); await expect(confirmation).toHaveValue('');
    const requests = state.calls.filter(call => call.path === '/auth/change-password');
    expect(requests).toHaveLength(2);
    expect(JSON.parse(requests[1].body)).toEqual({ currentPassword: 'old-password', newPassword: 'new-password' });
    expect(requests[1].authorization).toBe('Bearer test-token');
    expect(await page.evaluate(() => sessionStorage.getItem('campus-access-token'))).toBe('test-token');
    await screenshot(page, testInfo, 'password-settings');
  });
}
