import { test, expect } from '@playwright/test';
import { templateWorkbook } from '../src/excel.js';
import { addDays } from '../src/schedule.js';

const teacherId = 'GV_1234567890ABCDEF1234567890ABCDEF';
const anchor = '2026-08-10';
const mime = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const slot = { teacherId: 'GV01', teacherName: 'Nguyễn Văn A', courseCode: 'NET', courseName: 'Mạng máy tính', baseCourseName: 'Mạng máy tính', classGroup: '01', cohort: '2024', credits: 3, sessionType: 'THEORY', studentCount: 50, room: '101', roomCapacity: 60, dayOfWeek: 2, startPeriod: 1, numPeriods: 1, weekSchedule: '---11' };

async function boot(page, { role = 'TEACHER', anonymous = false, restoreFailures = 0 } = {}) {
  const state = { calls: [], saved: false, passwordStatus: 200, autoSaved: false, emptyAuto: false, restoreFailures, roomCount: 1 };
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
      const week = Number(path.split('/').at(-1));
      const startDate = addDays(anchor, (week - 1) * 7);
      return respond({ teacherId: url.searchParams.get('teacherId'), weekIndex: week, startDate, endDate: addDays(startDate, 6), lessons: [] });
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
