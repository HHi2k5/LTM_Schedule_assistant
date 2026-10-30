import { test } from 'node:test';
import assert from 'node:assert/strict';
import { templateWorkbook, parseWorkbook } from './excel.js';
import { validateAutoResult, validateNewPassword, validateManual, validateFile, firstActiveWeek, clampWeek } from './workflow.js';
import { canVisit } from './permissions.js';
import { api, calendarFile, clock, minutes } from './schedule.js';
import { setAccessToken, getAccessToken } from './authSession.js';
import { scheduledWorkbook } from './scheduleExport.js';

async function fixture(edit = () => {}) {
  const workbook = templateWorkbook();
  workbook.getWorksheet('MonHoc').addRow(['NET', 'Networking', 'A', 3, 2, 1, 1, '101', '11/08/26 - 24/08/26']);
  workbook.getWorksheet('TietHoc').addRow([1, '07:20', '08:10']);
  edit(workbook);
  return parseWorkbook(await workbook.xlsx.writeBuffer(), '2026-08-10');
}

test('personal workbook uses real periods and only actual weekdays within the date range', async () => {
  const parsed = await fixture();
  assert.deepEqual(parsed.periods, [{ period: 1, start: '07:20', end: '08:10' }]);
  assert.equal(parsed.courses[0].weekSchedule, '-11');
  assert.equal(parsed.courses[0].courseName, 'Networking (Nhóm A)');
  assert.equal((await fixture(wb => { wb.getWorksheet('MonHoc').getRow(2).getCell(9).value = '1'.repeat(100); })).courses[0].weekSchedule.length, 100);
});

test('numeric Excel clocks and nonzero seconds survive preview and Excel/calendar export', async () => {
  const parsed = await fixture(workbook => { workbook.getWorksheet('TietHoc').getRow(2).getCell(2).value = (7 * 3600 + 20 * 60 + 30) / 86400; });
  assert.equal(parsed.periods[0].start, '07:20:30');
  assert.equal(clock(parsed.periods[0].start), '07:20:30');
  assert.equal(minutes('07:20:30'), 440.5);
  const source = { schoolId: 'A', semesterStartDate: '2026-08-10', periods: parsed.periods,
    scheduledClasses: [{ ...parsed.courses[0], teacherId: 'GV01', teacherName: 'Teacher', baseCourseName: 'Networking', classGroup: '01' }] };
  assert.equal(scheduledWorkbook(source).workbook.getWorksheet('TietHoc').getRow(2).getCell(2).value, '07:20:30');
  assert.match(calendarFile([{ schoolId: 'A', date: '2026-08-10', startTime: '07:20:30', endTime: '08:10' }]), /T072030/);
});

test('API status errors keep the session except protected 401 and preserve server detail', async () => {
  const original = globalThis.fetch;
  try {
    for (const status of [400, 401, 403, 404, 409, 413, 500]) {
      setAccessToken('admin-token');
      globalThis.fetch = async () => new Response('{}', { status });
      await assert.rejects(api('/auth/change-password', { method: 'POST' }), error => error.status === status && error.message.length > 10);
      assert.equal(getAccessToken(), status === 401 ? '' : 'admin-token');
    }
    globalThis.fetch = async () => new Response(JSON.stringify({ message: 'Server validation detail' }), { status: 400 });
    await assert.rejects(api('/auth/change-password'), /Server validation detail/);
  } finally { globalThis.fetch = original; setAccessToken(''); }
});

test('workbook rejects missing hours, incomplete rows, decimal counts and invalid/beyond-limit dates and weeks', async () => {
  const cases = [
    wb => wb.removeWorksheet(wb.getWorksheet('TietHoc').id),
    wb => { wb.getWorksheet('TietHoc').getRow(2).getCell(1).value = 31; },
    wb => { wb.getWorksheet('MonHoc').getRow(2).getCell(7).value = '1.5'; },
    wb => { wb.getWorksheet('MonHoc').getRow(2).getCell(5).value = ''; },
    wb => { wb.getWorksheet('MonHoc').getRow(2).getCell(9).value = '31/02/26 - 24/08/26'; },
    wb => { wb.getWorksheet('MonHoc').getRow(2).getCell(9).value = '11/08/26 - 12/08/26'; },
    wb => { wb.getWorksheet('MonHoc').getRow(2).getCell(9).value = '09/08/26 - 24/08/26'; },
    wb => { wb.getWorksheet('MonHoc').getRow(2).getCell(9).value = '1'.repeat(101); },
  ];
  for (const edit of cases) await assert.rejects(fixture(edit));
});

test('result contract distinguishes saved and preview, rejects ambiguous success and chooses first teacher week', () => {
  const context = { schoolId: 'A', schoolName: 'School A', semesterStartDate: '2026-08-10', scheduledClasses: [{ teacherId: 'GV01', weekSchedule: '---1' }] };
  assert.equal(validateAutoResult({ ...context, saved: false, status: 'NOT_SAVED' }).saved, false);
  assert.equal(validateAutoResult({ ...context, saved: true, status: 'SUCCESS' }).saved, true);
  for (const flags of [{ status: 'SUCCESS' }, { saved: false, status: 'SUCCESS' }, { saved: 'true', status: 'SUCCESS' }]) assert.throws(() => validateAutoResult({ ...context, ...flags }));
  assert.equal(firstActiveWeek([...context.scheduledClasses, { teacherId: 'GV02', weekSchedule: '1' }], 'GV01'), 4);
  assert.equal(clampWeek(101), 100);
  assert.equal(canVisit({ role: 'ADMIN' }, 'accounts'), false);
  assert.equal(canVisit({ role: 'TEACHER' }, 'accounts'), false);
});

test('password byte limit and upload boundaries reject invalid input', () => {
  validateNewPassword('a'.repeat(72));
  validateNewPassword('ế'.repeat(24));
  assert.throws(() => validateNewPassword('ế'.repeat(25)));
  assert.throws(() => validateNewPassword('short'));
  for (const file of [null, { name: 'bad.txt', size: 1 }, { name: 'a.xlsx', size: 0 }, { name: 'a.xlsx', size: 10485761 }]) assert.throws(() => validateFile(file));
});

test('manual tables report incomplete rows rather than dropping them or defaulting numbers', () => {
  const plans = [{ courseCode: 'NET', courseName: 'Networking', cohort: '2024', classSize: 50, numClasses: 1, credits: 3, theoryPeriods: 30, practicePeriods: 30, studyStartDate: '2026-08-10', studyEndDate: '2026-11-29' }];
  const teachers = [{ courseCode: 'NET', courseName: 'Networking', cohort: '2024', teacherId: 'GV01', teacherName: 'Teacher', theoryWeight: 2, practiceWeight: 8 }];
  const rooms = [{ roomCode: '101', roomType: 'THEORY', capacity: 60 }];
  validateManual(plans, teachers, rooms);
  assert.throws(() => validateManual([...plans, { ...plans[0], courseName: '' }], teachers, rooms), /dòng 2/);
  assert.throws(() => validateManual([{ ...plans[0], classSize: 0 }], teachers, rooms), /sĩ số/);
  assert.throws(() => validateManual(plans, [{ ...teachers[0], teacherId: '' }], rooms), /mã GV/);
  assert.throws(() => validateManual(plans, teachers, [...rooms, rooms[0]]), /bị lặp/);
});
