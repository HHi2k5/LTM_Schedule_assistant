import { test } from 'node:test';
import assert from 'node:assert/strict';
import ExcelJS from 'exceljs';
import { scheduledRows, scheduledWorkbook, scheduleHeaders } from './scheduleExport.js';
import { parseWorkbook } from './excel.js';

const periods = [1, 2, 3, 4, 5].map(p => ({ period: p, start: `${String(p + 6).padStart(2, '0')}:00:00`, end: `${String(p + 6).padStart(2, '0')}:50:00` }));
const slot = { teacherId: 'GV01', teacherName: 'Đào Ngọc Phong', courseCode: 'INT1313', baseCourseName: 'Lập trình mạng',
  courseName: 'Lập trình mạng - Nhóm 01', classGroup: '01', credits: 3, dayOfWeek: 2, startPeriod: 1, numPeriods: 3,
  room: '101-A2', weekSchedule: '11111111--------' };
const result = { schoolId: 'PTIT', semesterStartDate: '2026-08-10', periods, scheduledClasses: [slot,
  { ...slot, startPeriod: 4, numPeriods: 2, weekSchedule: '--------11111111' }] };

test('real XLSX preserves the sample headers, teacher identity and both rotation phases', async () => {
  const { workbook, filename, missingCredits } = scheduledWorkbook(result);
  assert.equal(filename, 'lich-da-xep-PTIT-2026-08-10.xlsx');
  assert.equal(missingCredits, false);
  const readback = new ExcelJS.Workbook();
  await readback.xlsx.load(await workbook.xlsx.writeBuffer());
  assert.deepEqual(readback.worksheets.map(sheet => sheet.name), ['MonHoc', 'TietHoc']);
  const sheet = readback.getWorksheet('MonHoc');
  assert.deepEqual(sheet.getRow(1).values.slice(1), scheduleHeaders);
  assert.equal(sheet.columnCount, 11);
  assert.equal(sheet.rowCount, 3);
  assert.deepEqual(sheet.getRow(2).values.slice(1), ['INT1313', 'Lập trình mạng', '01', 3, 2, 1, 3, '101-A2', '10/08/26 đến 28/09/26', 'GV01', 'Đào Ngọc Phong']);
  assert.equal(sheet.getCell('I3').value, '05/10/26 đến 23/11/26');
  assert.equal(sheet.getCell('F3').value, 4); assert.equal(sheet.getCell('G3').value, 2);
  assert.equal(sheet.views[0].state, 'frozen');
  assert.deepEqual(readback.getWorksheet('TietHoc').getRow(2).values.slice(1), [1, '07:00', '07:50']);
});

test('week gaps, single Sundays and year boundaries never add holiday lessons', () => {
  const rows = scheduledRows({ ...result, scheduledClasses: [{ ...slot, weekSchedule: '11--11' }] });
  assert.deepEqual(rows.map(row => row[8]), ['10/08/26 đến 17/08/26', '07/09/26 đến 14/09/26']);
  const sunday = scheduledRows({ ...result, semesterStartDate: '2026-12-28', scheduledClasses: [{ ...slot, dayOfWeek: 8, weekSchedule: '-1' }] });
  assert.equal(sunday[0][8], '10/01/27 đến 10/01/27');
});

test('exported date ranges expand to exactly the active source weeks', () => {
  const weeks = ' 23-56--9';
  const rows = scheduledRows({ ...result, scheduledClasses: [{ ...slot, weekSchedule: weeks }] });
  const day = text => { const [d, m, y] = text.split('/').map(Number); return Date.UTC(2000 + y, m - 1, d); };
  const dates = rows.flatMap(row => {
    const [first, last] = row[8].split(' đến '), dates = [];
    for (let value = day(first); value <= day(last); value += 604800000) dates.push(value);
    return dates;
  });
  const expected = [...weeks].flatMap((marker, i) => marker === '-' || marker === ' ' ? [] : [Date.UTC(2026, 7, 10) + i * 604800000]);
  assert.deepEqual(dates, expected);
});

test('two alternating practice lessons per class survive XLSX export and import with the selected semester anchor', async () => {
  const practice = { ...slot, classGroup: 'Lớp 01 - Tổ 1', sessionType: 'PRACTICE', weekSchedule: '-1-2------------' };
  const { workbook } = scheduledWorkbook({ ...result, semesterStartDate: '2027-01-04', scheduledClasses: [practice,
    { ...practice, classGroup: 'Lớp 02 - Tổ 1', weekSchedule: '--1-2-----------' }] });
  assert.equal(workbook.getWorksheet('MonHoc').rowCount, 5);
  const parsed = await parseWorkbook(await workbook.xlsx.writeBuffer(), '2027-01-04');
  assert.equal(parsed.courses.length, 4);
  const activeWeeks = course => [...course.weekSchedule].flatMap((marker, i) => marker === '-' || marker === ' ' ? [] : [i + 1]);
  assert.deepEqual(parsed.courses.map(activeWeeks), [[2], [4], [3], [5]]);
  for (const course of parsed.courses) {
    assert.equal(activeWeeks(course).length, 1);
    assert.equal(course.dayOfWeek, practice.dayOfWeek);
    assert.equal(course.room, practice.room);
  }
});

test('a single practice day within a short date range uses its actual weekday', async () => {
  const { workbook } = scheduledWorkbook({ ...result, scheduledClasses: [{ ...slot, dayOfWeek: 8, weekSchedule: '-1-2' }] });
  const parsed = await parseWorkbook(await workbook.xlsx.writeBuffer(), result.semesterStartDate);
  assert.deepEqual(parsed.courses.map(course => course.weekSchedule), ['-1', '---1']);
});

test('missing credits stay blank and original names remain literal text', async () => {
  const { workbook, missingCredits } = scheduledWorkbook({ ...result, scheduledClasses: [{ ...slot, credits: null, baseCourseName: '=SUM(1,2)' }] });
  assert.equal(missingCredits, true);
  const loaded = new ExcelJS.Workbook(); await loaded.xlsx.load(await workbook.xlsx.writeBuffer());
  assert.equal(loaded.getWorksheet('MonHoc').getCell('D2').value, null);
  assert.equal(loaded.getWorksheet('MonHoc').getCell('B2').value, '=SUM(1,2)');
  assert.equal(loaded.getWorksheet('MonHoc').getCell('B2').type, ExcelJS.ValueType.String);
});

test('invalid result metadata and period references prevent misleading exports', () => {
  for (const patch of [{ semesterStartDate: '2026-08-11' }, { scheduledClasses: [] }, { periods: [] }]) {
    assert.throws(() => scheduledWorkbook({ ...result, ...patch }));
  }
  for (const patch of [{ teacherId: '' }, { weekSchedule: '----' }, { credits: -1 }, { numPeriods: 6 }, { dayOfWeek: 9 }]) {
    assert.throws(() => scheduledWorkbook({ ...result, scheduledClasses: [{ ...slot, ...patch }] }));
  }
  assert.throws(() => scheduledWorkbook({ ...result, periods: [{ period: 1, start: '07:99', end: '08:50' }] }));
});
