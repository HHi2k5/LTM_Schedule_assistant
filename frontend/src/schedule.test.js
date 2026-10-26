import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseWorkbook, templateWorkbook, buildUpload, firstWeek } from './excel.js';
import { addDays, isMonday, layoutLessons, minutes, api, calendarFile } from './schedule.js';
import { parseJsonSchedule, templateJson as blankJson } from './jsonSchedule.js';

const metadata = { teacherId: 'GV01', teacherName: 'Nguyễn Văn A', schoolId: 'PTIT', schoolName: 'Học viện PTIT', semesterStartDate: '2026-08-10', colorCode: '#177c62' };

function templateJson() {
  const input = blankJson();
  input.teacherId = 'GV01'; input.teacherName = 'Teacher'; input.semesterStartDate = '2026-08-10';
  input.school.schoolId = 'PTIT'; input.school.schoolName = 'School';
  input.school.periods = [{ period: 1, start: '07:10', end: '08:00' }, { period: 2, start: '08:10', end: '09:00' }];
  input.courses = [{ courseCode: 'INT1313', courseName: 'Networking', room: '402', dayOfWeek: 2, startPeriod: 1, numPeriods: 2, weekSchedule: 'xxxx-xxxx' }];
  return input;
}

test('JSON template preserves the backend contract, UTF-8 BOM and minute-accurate times', () => {
  const input = templateJson();
  input.school.periods[0].start = '07:20:00';
  const result = parseJsonSchedule('\uFEFF' + JSON.stringify(input), '2026-08-10');
  assert.equal(result.teacherId, 'GV01');
  assert.equal(result.school.periods[0].start, '07:20');
  assert.deepEqual(result.courses, input.courses);
  assert.equal(result.semesterStartDate, input.semesterStartDate);
  assert.throws(() => parseJsonSchedule(JSON.stringify(input), '2026-08-17'), /Mốc tuần/);
});

test('JSON rejects syntax, schema, type and schedule errors before preview', () => {
  for (const value of ['{', 'null', '[]', '{}']) assert.throws(() => parseJsonSchedule(value), /JSON/);
  const cases = [
    [data => { data.teacherId = 123; }, /teacherId/],
    [data => { data.school = null; }, /school/],
    [data => { data.courses = []; }, /courses/],
    [data => { data.courses[0] = null; }, /courses\[0\]/],
    [data => { data.school.periods[0] = null; }, /periods\[0\]/],
    [data => { data.courses[0].dayOfWeek = true; }, /dayOfWeek/],
    [data => { data.courses[0].startPeriod = '1'; }, /startPeriod/],
    [data => { data.courses[0].dayOfWeek = 9; }, /thứ/],
    [data => { data.courses[0].startPeriod = 2; }, /tiết 3/],
    [data => { data.courses[0].weekSchedule = '--------'; }, /chuỗi tuần/],
    [data => { data.school.periods[0].start = '25:00'; }, /giờ/],
    [data => { data.school.periods[0].end = '07:00'; }, /giờ kết thúc/],
    [data => { data.courses.push({ ...data.courses[0] }); }, /bị lặp/],
  ];
  for (const [mutate, pattern] of cases) {
    const input = templateJson(); mutate(input);
    assert.throws(() => parseJsonSchedule(JSON.stringify(input)), pattern);
  }
});

test('current nine-column XLSX template reads real rows and rejects invalid day/week/period references', async () => {
  const workbook = templateWorkbook();
  const sheet = workbook.worksheets[0];
  assert.equal(sheet.columnCount, 9);
  sheet.addRow(['INT1313', 'Networking', '01', 3, 2, 1, 2, '402', '23-56--']);
  const parsed = await parseWorkbook(await workbook.xlsx.writeBuffer());
  const upload = buildUpload(parsed, metadata, '2026-08-10');
  assert.equal(upload.courses[0].courseCode, 'INT1313');
  assert.equal(upload.courses[0].weekSchedule, '23-56--');
  assert.equal(upload.school.periods.length, 12);
  assert.equal(firstWeek([{ weekSchedule: '---xx' }, { weekSchedule: '-xx' }]), 2);
  await assert.rejects(parseWorkbook(Buffer.from('not an excel file')));
  for (const [column, value] of [[5, 9], [6, 13], [9, '---------']]) {
    const previous = sheet.getRow(2).getCell(column).value;
    sheet.getRow(2).getCell(column).value = value;
    await assert.rejects(parseWorkbook(await workbook.xlsx.writeBuffer()));
    sheet.getRow(2).getCell(column).value = previous;
  }
});

test('week/date math and minute offsets preserve actual start times', () => {
  assert.equal(addDays('2026-08-10', 7), '2026-08-17');
  assert.equal(addDays('2026-12-28', 6), '2027-01-03');
  assert.equal(isMonday('2026-08-10'), true);
  assert.equal(isMonday('2026-02-30'), false);
  assert.equal((minutes('07:10') - minutes('07:00')) * 1.1, 11);
  assert.equal((minutes('07:20') - minutes('07:00')) * 1.1, 22);
});

test('overlapping lessons have separate lanes; adjacent lessons reuse the full width', () => {
  const result = layoutLessons([
    { id: 'a', dayOfWeek: 2, startTime: '07:00', endTime: '09:00' },
    { id: 'b', dayOfWeek: 2, startTime: '07:20', endTime: '08:00' },
    { id: 'c', dayOfWeek: 2, startTime: '09:00', endTime: '10:00' },
  ]);
  assert.equal(result[0].lanes, 2);
  assert.notEqual(result[0].lane, result[1].lane);
  assert.equal(result[2].lanes, 1);
});

test('calendar export preserves minutes, Vietnamese time zone and escaped text', () => {
  const content = calendarFile([{ schoolId: 'A', schoolName: 'Trường A', courseName: 'Môn, A; B\nC', room: '101', date: '2026-10-19', startTime: '07:10:00', endTime: '08:50:00' }]);
  assert.ok(content.includes('DTSTART;TZID=Asia/Ho_Chi_Minh:20261019T071000\r\n'));
  assert.ok(content.includes('DTEND;TZID=Asia/Ho_Chi_Minh:20261019T085000\r\n'));
  assert.ok(content.includes('SUMMARY:Môn\\, A\\; B\\nC\r\n'));
});

test('API passes upload JSON and reports server/network errors without simulated success', async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = async (url, options) => {
      assert.ok(url.endsWith('/api/schedule/upload-school'));
      assert.equal(options.method, 'POST');
      assert.equal(JSON.parse(options.body).teacherId, 'GV01');
      return new Response(JSON.stringify({ status: 'SUCCESS' }));
    };
    assert.equal((await api('/upload-school', { method: 'POST', body: JSON.stringify({ teacherId: 'GV01' }) })).status, 'SUCCESS');
    globalThis.fetch = async () => new Response(JSON.stringify({ message: 'Dữ liệu không hợp lệ' }), { status: 400 });
    await assert.rejects(api('/week/1'), /Dữ liệu không hợp lệ/);
    globalThis.fetch = async () => { throw new TypeError('fetch failed'); };
    await assert.rejects(api('/week/1'), /Không kết nối/);
  } finally { globalThis.fetch = original; }
});