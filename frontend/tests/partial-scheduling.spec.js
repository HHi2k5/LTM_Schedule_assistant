import { test, expect } from '@playwright/test';
import ExcelJS from 'exceljs';
import { planWorkbook } from '../src/autoScheduleWorkbook.js';

test.setTimeout(90000);

const anchor = '2026-08-10';
const mime = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const missing = { classId: 'MISSING:2024:1:1:LT', courseCode: 'MISSING', courseName: 'Môn chưa có giảng viên',
  cohort: '2024', classGroup: 'Lớp 01', sessionType: 'THEORY', requiredPeriods: 30,
  scheduledPeriods: 0, missingPeriods: 30, reasonCode: 'NO_TEACHER', reason: 'Chưa phân công giảng viên.' };
const slot = { classId: 'NET:2024:2:1:LT', teacherId: 'GV01', teacherName: 'Nguyễn Văn A',
  courseCode: 'NET', courseName: 'Mạng máy tính', baseCourseName: 'Mạng máy tính',
  classGroup: 'Lớp 01', cohort: '2024', credits: 3, sessionType: 'THEORY', studentCount: 30,
  room: '101', dayOfWeek: 2, startPeriod: 1, numPeriods: 3, weekSchedule: '1' };

async function openScheduler(page, allMissing) {
  const response = {
    schoolId: 'PTIT', schoolName: 'School', semesterStartDate: anchor,
    saved: !allMissing, status: allMissing ? 'NOT_SAVED' : 'PARTIAL_SUCCESS',
    message: allMissing ? 'Không có lớp nào xếp đủ tiết.' : 'Đã xếp và lưu 1 lớp/nhóm. Còn 1 lớp chưa xếp được.',
    scheduledClasses: allMissing ? [] : [slot], totalClassesScheduled: allMissing ? 0 : 1,
    unscheduledClasses: [missing], classWorkloads: [missing], unscheduledWarnings: [missing.reason],
    periods: [1, 2, 3].map(p => ({ period: p, start: `${String(p + 6).padStart(2, '0')}:00`,
      end: `${String(p + 6).padStart(2, '0')}:50` })),
  };
  await page.addInitScript(() => sessionStorage.setItem('campus-access-token', 'test-token'));
  await page.route('https://fonts.googleapis.com/**', route => route.abort());
  await page.route('**/api/schedule/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/schedule', '');
    let body;
    if (path === '/auth/me') body = { role: 'ADMIN', teacherId: 'ADMIN', username: 'admin',
      fullName: 'Quản lý', semesterStartDate: anchor };
    else if (path.startsWith('/week/')) body = { startDate: anchor, endDate: '2026-08-16', lessons: [], schools: [] };
    else if (path === '/rooms') body = [{ roomCode: '101', capacity: 60, schoolCode: 'PTIT' }];
    else if (path === '/teacher-subjects') body = allMissing ? [] : [{ teacherId: 'GV01', courseCode: 'NET' }];
    else if (path === '/auto-generate-3files') body = response;
    else return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' });
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
  });
  await page.goto('/');
  const menu = page.getByRole('button', { name: 'Mở điều hướng' });
  await expect(page.getByRole('button', { name: 'Xếp lịch của khoa', exact: true })).toBeAttached();
  if (await menu.isVisible()) await menu.click();
  await page.getByRole('button', { name: 'Xếp lịch của khoa', exact: true }).click();
  await expect(page.getByText('1 phòng học', { exact: false })).toBeVisible();
  const workbook = planWorkbook([{ courseCode: 'MISSING', courseName: missing.courseName, classType: 'LT',
    classSize: 30, cohort: '2024', numClasses: 1, studyStartDate: anchor, studyEndDate: '2026-12-01',
    credits: 3, totalPeriods: 30 }]);
  await page.locator('#auto-plan-file').setInputFiles({ name: 'plan.xlsx', mimeType: mime,
    buffer: Buffer.from(await workbook.xlsx.writeBuffer()) });
  const submit = page.getByRole('button', { name: 'Tải File Mở Lớp & Chạy Xếp Lịch Khoa' });
  await submit.evaluate(button => button.scrollIntoView({ block: 'center' }));
  await submit.click();
}

for (const allMissing of [false, true]) {
  test(allMissing ? 'all missing teachers still produce a visible downloadable report'
    : 'partial success keeps its result open with missing classes and downloadable scheduled lessons', async ({ page }) => {
    await openScheduler(page, allMissing);
    const report = page.getByRole('region', { name: 'Các lớp chưa xếp được' });
    await expect(report).toBeVisible();
    await expect(report).toContainText('1 lớp/nhóm thiếu giảng viên');
    await expect(report.getByRole('cell', { name: 'MISSING', exact: true })).toBeVisible();
    await expect(report).toContainText('Chưa phân công giảng viên.');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
    if (!allMissing) {
      await expect(page.getByRole('heading', { name: 'Đã lưu các lớp xếp được' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Xem TKB' })).toBeEnabled();
    }
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Xuất Excel kết quả' }).click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toBe('lich-da-xep-PTIT-2026-08-10.xlsx');
    const loaded = new ExcelJS.Workbook();
    await loaded.xlsx.readFile(await download.path());
    expect(loaded.getWorksheet('LopChuaXep').getCell('B2').value).toBe('MISSING');
    expect(loaded.getWorksheet('MonHoc').rowCount).toBe(allMissing ? 1 : 2);
  });
}
