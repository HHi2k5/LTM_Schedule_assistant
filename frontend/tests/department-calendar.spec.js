import { test, expect } from '@playwright/test';

const anchor = '2026-10-26';
const base = { schoolId: 'PTIT', schoolName: 'GV: Lê Hoàng Nam (GV04)', date: anchor, dayOfWeek: 2, room: '604-LAB-A2', startPeriod: 1, endPeriod: 3, startTime: '07:00:00', endTime: '09:50:00', colorCode: '#8055bf' };
const lessons = [
  ...Array.from({ length: 4 }, (_, index) => ({ ...base, courseName: `[GV04 - Lê Hoàng Nam] [TH] Phân tích và thiết kế hệ thống (Khóa 2023 - Lớp 0${index + 1} - Tổ 1)` })),
  { ...base, dayOfWeek: 3, date: '2026-10-27', courseName: '[GV04 - Lê Hoàng Nam] Cơ sở dữ liệu' },
  { ...base, dayOfWeek: 4, date: '2026-10-28', startPeriod: 4, endPeriod: 6, startTime: '10:00', endTime: '13:00', courseName: '[GV04 - Lê Hoàng Nam] Đồ họa máy tính', colorCode: '#0284c7' },
  { ...base, dayOfWeek: 8, date: '2026-11-01', startTime: '07:00', endTime: '09:50', courseName: '[GV04 - Lê Hoàng Nam] Lớp Chủ nhật' },
];

test('department timetable aligns shared time slots and stacks readable lessons without changing personal view', async ({ page }, testInfo) => {
  await page.addInitScript(() => sessionStorage.setItem('campus-access-token', 'layout-test'));
  await page.route('https://fonts.googleapis.com/**', route => route.abort());
  await page.route('**/api/schedule/**', route => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/auth/me')) return route.fulfill({ json: { role: 'ADMIN', teacherId: 'ADMIN', username: 'admin', fullName: 'Quản lý khoa', semesterStartDate: anchor } });
    if (url.pathname.includes('/week/')) return route.fulfill({ json: { teacherId: url.searchParams.get('teacherId'), weekIndex: 1, startDate: anchor, endDate: '2026-11-01', lessons } });
    return route.fulfill({ status: 204 });
  });
  await page.goto('/');
  const table = page.locator('.department-timetable');
  await expect(table).toBeVisible();
  await expect(table.locator('tbody > tr')).toHaveCount(2);
  const monday = table.locator('tbody > tr').first().locator('td').first();
  const cards = monday.locator('.department-lesson');
  await expect(cards).toHaveCount(4);
  await expect(cards.first().locator('strong')).not.toContainText('GV04');
  await expect(cards.first()).toContainText('Lê Hoàng Nam (GV04)');
  await expect(cards.first()).toContainText('Phòng 604-LAB-A2');
  const first = await cards.nth(0).boundingBox();
  const second = await cards.nth(1).boundingBox();
  expect(first.width).toBeGreaterThan(160);
  expect(second.x).toBeCloseTo(first.x);
  expect(second.y).toBeGreaterThanOrEqual(first.y + first.height);
  expect(await cards.first().evaluate(el => el.scrollHeight <= el.clientHeight + 1)).toBe(true);
  const tuesday = await table.locator('tbody > tr').first().locator('td').nth(1).boundingBox();
  expect(tuesday.y).toBeCloseTo((await monday.boundingBox()).y);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('department-timetable.png'), fullPage: true });
  await cards.first().click();
  await expect(page.getByRole('dialog')).toContainText('604-LAB-A2');
  await page.getByRole('dialog').getByRole('button', { name: 'Đóng', exact: true }).click();
  const scroll = page.getByRole('region', { name: 'Bảng lịch toàn khoa', exact: false });
  await scroll.evaluate(el => { el.scrollLeft = el.scrollWidth; });
  await expect(page.getByRole('button', { name: 'Lớp Chủ nhật', exact: false })).toBeInViewport();
  await page.getByRole('textbox', { name: 'Lọc theo mã giảng viên', exact: false }).fill('GV04');
  await page.getByRole('button', { name: 'Tải lịch', exact: true }).click();
  await expect(page.locator('.week-calendar')).toBeVisible();
  await expect(table).toHaveCount(0);
});
