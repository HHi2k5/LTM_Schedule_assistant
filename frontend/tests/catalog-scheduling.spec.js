import { test, expect } from '@playwright/test';
import { planWorkbook } from '../src/autoScheduleWorkbook.js';
import ExcelJS from 'exceljs';

test.setTimeout(60000);
const anchor = '2026-08-10';
const planRow = { courseCode: 'NET', courseName: 'Mạng máy tính', cohort: '2024', classSize: 50, numClasses: 1, credits: 3,
  studyStartDate: anchor, studyEndDate: '2026-11-29', theoryPeriods: 6, practicePeriods: 0 };

function multipart(request) {
  const boundary = request.headers()['content-type'].split('boundary=')[1];
  const parts = request.postDataBuffer().toString('latin1').split('--' + boundary);
  const bytes = name => {
    const part = parts.find(value => value.includes(`name="${name}"`));
    return part ? Buffer.from(part.slice(part.indexOf('\r\n\r\n') + 4, part.lastIndexOf('\r\n')), 'latin1') : null;
  };
  return { configuration: JSON.parse(bytes('configuration').toString('utf8')), plan: bytes('planFile'), teacherFile: bytes('teacherFile'), roomFile: bytes('roomFile') };
}

async function boot(page, settings = {}) {
  const state = { calls: [], generated: [], optionsCalls: 0, stale: false, saved: true, empty: false, missing: false, ...settings };
  const school = { schoolCode: 'A', name: 'Trường A', active: true, revision: 0 };
  const periods = Array.from({ length: 3 }, (_, i) => ({ period: i + 1, start: `${String(7 + i).padStart(2, '0')}:00`, end: `${String(7 + i).padStart(2, '0')}:50` }));
  await page.addInitScript(() => sessionStorage.setItem('campus-access-token', 'catalog-schedule-token'));
  await page.route('https://fonts.googleapis.com/**', route => route.abort());
  await page.route('**/api/admin/institutions**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/admin/institutions', '');
    const respond = body => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
    if (!path) return respond(state.empty ? [] : [school]);
    if (path.endsWith('/scheduling-options')) {
      state.optionsCalls++;
      return respond({ school, snapshot: (state.optionsCalls === 1 ? 'a' : 'b').repeat(64),
        lecturers: [{ lecturer: { id: 1, lecturerCode: 'STAFF01', fullName: 'Nguyễn Văn A', lecturerType: 'STAFF', active: true, revision: state.generated.length,
          accountTeacherId: 'ACCOUNT01', scheduleTeacherId: state.generated.length ? 'ACCOUNT01' : null }, eligible: true, reason: null }],
        assignments: [{ id: 5, lecturerId: 1, courseCode: 'NET', courseName: 'Mạng máy tính', cohort: '2024', theoryWeight: 2, practiceWeight: 8, active: true }],
        rooms: [{ id: 10, roomCode: '101', roomName: 'Phòng 101', roomType: 'THEORY_STANDARD', capacity: 60, isLab: false, active: true },
          { id: 11, roomCode: 'LAB', roomName: 'Phòng LAB', roomType: 'LAB_PRACTICE', capacity: 30, isLab: true, active: true }],
        periodChoices: [{ id: 'periods', label: 'Bảng giờ đã lưu · 3 tiết · 07:00–09:50', periods }],
      });
    }
    return respond([]);
  });
  await page.route('**/api/schedule/**', async route => {
    const request = route.request(); const url = new URL(request.url()); const path = url.pathname.replace('/api/schedule', '');
    state.calls.push({ path, query: url.search });
    const respond = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (path === '/auth/me') return respond({ teacherId: 'ADMIN', role: 'ADMIN', username: 'manager', fullName: 'Quản lý', semesterStartDate: anchor });
    if (path === '/rooms') return respond([]);
    if (path === '/auto-generate-catalog') {
      state.generated.push(multipart(request));
      if (state.stale) { state.stale = false; return respond({ message: 'Catalog changed' }, 409); }
      if (state.missing) return respond({ message: 'Dòng 2 (NET/2024): thiếu GV có trọng số LT lớn hơn 0' }, 400);
      return respond({ schoolId: 'A', schoolName: 'Trường A', semesterStartDate: anchor, periods,
        saved: state.saved, status: state.saved ? 'SUCCESS' : 'NOT_SAVED', message: state.saved ? 'Đã lưu lịch từ danh mục.' : 'Chưa đủ tiết, giữ lịch cũ.',
        totalClassesScheduled: 1, totalLessonsScheduled: 2, totalRoomsAvailable: 1,
        scheduledClasses: [{ teacherId: 'ACCOUNT01', teacherName: 'Nguyễn Văn A', courseCode: 'NET', courseName: 'Mạng máy tính', baseCourseName: 'Mạng máy tính',
          classGroup: 'Lớp 01', cohort: '2024', classId: 'NET:LT', sessionType: 'THEORY', credits: 3, room: '101', roomCapacity: 60,
          studentCount: 50, dayOfWeek: 2, startPeriod: 1, numPeriods: 3, weekSchedule: '11' }],
        unscheduledWarnings: state.saved ? [] : ['Thiếu giờ trống cho lớp NET'],
        classWorkloads: [{ classId: 'NET:LT', requiredPeriods: 6, scheduledPeriods: state.saved ? 6 : 3, missingPeriods: state.saved ? 0 : 3 }],
        catalogContext: { schoolCode: 'A', sourceSnapshot: 'a'.repeat(64), roomIds: [10], periodChoiceId: 'periods',
          lecturers: [{ lecturerId: 1, lecturerCode: 'STAFF01', teacherId: 'ACCOUNT01', accountTeacherId: 'ACCOUNT01' }] },
      });
    }
    if (path.startsWith('/week/')) return respond({ teacherId: url.searchParams.get('teacherId'), weekIndex: 1, startDate: anchor, endDate: '2026-08-16', lessons: [], schools: [] });
    return respond({ message: `Unexpected ${path}` }, 404);
  });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Xếp lịch toàn trường.', exact: true })).toBeVisible();
  return state;
}

async function attachPlan(page) {
  await page.getByLabel('Kế hoạch mở lớp từ Excel').setInputFiles({ name: 'plan.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: Buffer.from(await planWorkbook([planRow]).xlsx.writeBuffer()) });
  await page.getByRole('checkbox', { name: 'Tôi đã đưa đủ kế hoạch toàn trường cho lần xếp này' }).check();
}
async function screenshot(page, testInfo, name) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true });
}

test('only the plan file is uploaded; catalog weights and rooms are selected per run and account owns the schedule', async ({ page }, testInfo) => {
  const state = await boot(page);
  await expect(page.getByRole('button', { name: 'Danh mục đã lưu', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('button', { name: 'Xếp lịch bằng danh mục đã lưu', exact: true })).toBeDisabled();
  await page.getByText('Trọng số LT/TH của lần xếp', { exact: false }).click();
  await page.getByLabel('Trọng số LT phân công 5').fill('9'); await page.getByLabel('Trọng số TH phân công 5').fill('0');
  await page.getByText('Chọn phòng (2)', { exact: true }).click(); await page.getByRole('checkbox', { name: 'Chọn phòng LAB', exact: true }).uncheck();
  await attachPlan(page); await page.getByRole('button', { name: 'Xếp lịch bằng danh mục đã lưu', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đã lưu lịch', exact: true })).toBeVisible();
  expect(state.generated).toHaveLength(1); expect(state.generated[0].teacherFile).toBeNull(); expect(state.generated[0].roomFile).toBeNull();
  expect(state.generated[0].configuration).toMatchObject({ lecturerIds: [1], roomIds: [10], weights: [{ assignmentId: 5, theoryWeight: 9, practiceWeight: 0 }], replaceSchoolSchedule: true });
  await expect(page.locator('.import-preview table')).toContainText('STAFF01');
  await expect(page.locator('.import-preview table')).toContainText('Tài khoản: ACCOUNT01');
  await expect(page.getByRole('button', { name: 'Xếp lịch bằng danh mục đã lưu', exact: true })).toBeEnabled();
  await expect(page.getByText('Đầu vào đã thay đổi.', { exact: false })).toHaveCount(0);
  await screenshot(page, testInfo, 'catalog-scheduled');

  const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Xuất lịch đã lưu (.xlsx)', exact: true }).click()]);
  const stream = await download.createReadStream(); const chunks = [];
  for await (const chunk of stream) chunks.push(chunk);
  const book = new ExcelJS.Workbook(); await book.xlsx.load(Buffer.concat(chunks));
  expect(book.getWorksheet('DanhTinhGiangVien').getRow(2).values.slice(1)).toEqual(['STAFF01', 'ACCOUNT01', 'ACCOUNT01']);
  await page.getByRole('button', { name: 'Xem TKB', exact: true }).click();
  await expect.poll(() => state.calls.some(call => call.path.startsWith('/week/') && call.query.includes('teacherId=ACCOUNT01'))).toBe(true);
});

test('a second run reuses the selected plan and catalogue without reuploading lecturer or room files', async ({ page }) => {
  const state = await boot(page); await attachPlan(page);
  await page.getByRole('button', { name: 'Xếp lịch bằng danh mục đã lưu', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đã lưu lịch', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Xếp lịch bằng danh mục đã lưu', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Xếp lịch bằng danh mục đã lưu', exact: true }).click();
  await expect.poll(() => state.generated.length).toBe(2);
  expect(state.generated[1].teacherFile).toBeNull(); expect(state.generated[1].roomFile).toBeNull();
  expect(state.generated[1].configuration.snapshot).toBe('b'.repeat(64));
});

test('stale catalogue blocks rerun until reload and preserves the chosen plan', async ({ page }) => {
  const state = await boot(page, { stale: true }); await attachPlan(page);
  await page.getByRole('button', { name: 'Xếp lịch bằng danh mục đã lưu', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Danh mục đã thay đổi');
  await expect(page.getByRole('button', { name: 'Xếp lịch bằng danh mục đã lưu', exact: true })).toBeDisabled();
  expect(state.generated).toHaveLength(1);
  await page.getByRole('button', { name: 'Tải lại dữ liệu xếp lịch', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Xếp lịch bằng danh mục đã lưu', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Xếp lịch bằng danh mục đã lưu', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đã lưu lịch', exact: true })).toBeVisible();
  expect(state.generated).toHaveLength(2); expect(state.generated[1].configuration.snapshot).toBe('b'.repeat(64));
});

test('manual plan is sent as one workbook and an incomplete result does not expose saved schedule navigation', async ({ page }) => {
  const state = await boot(page, { saved: false });
  await page.getByRole('button', { name: 'Nhập kế hoạch trực tiếp', exact: true }).click();
  for (const [label, value] of [['Mã môn', 'NET'], ['Tên môn', 'Mạng máy tính'], ['Khóa', '2024'], ['Tổng tiết LT', '6']]) await page.getByLabel(`${label} kế hoạch dòng 1`, { exact: true }).fill(value);
  await page.getByRole('checkbox', { name: 'Tôi đã đưa đủ kế hoạch toàn trường cho lần xếp này' }).check();
  await page.getByRole('button', { name: 'Xếp lịch bằng danh mục đã lưu', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Bản xem trước — chưa lưu', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Xem TKB', exact: true })).toBeDisabled();
  const book = new ExcelJS.Workbook(); await book.xlsx.load(state.generated[0].plan);
  expect(book.worksheets[0].getCell('B2').value).toBe('NET'); expect(book.worksheets[0].getCell('J2').value).toBe(6);
  expect(state.generated[0].teacherFile).toBeNull(); expect(state.generated[0].roomFile).toBeNull();
});

test('missing assignment error names the course and cohort without pretending to save', async ({ page }) => {
  const state = await boot(page, { missing: true }); await attachPlan(page);
  await page.getByRole('button', { name: 'Xếp lịch bằng danh mục đã lưu', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('NET/2024');
  await expect(page.getByRole('heading', { name: 'Đã lưu lịch', exact: true })).toHaveCount(0);
  expect(state.generated).toHaveLength(1);
});

test('an empty catalogue has an explicit management action and the three-file alternative', async ({ page }) => {
  const state = await boot(page, { empty: true });
  await expect(page.getByText('Chưa có danh mục trường.', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Ba file Excel', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Quản lý danh mục', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Danh mục nhà trường.', exact: true })).toBeVisible();
  expect(state.generated).toHaveLength(0);
});
