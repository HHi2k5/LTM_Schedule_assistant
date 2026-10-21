import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseWorkbook, templateWorkbook, buildUpload, firstWeek } from './excel.js';
import { addDays, isMonday, layoutLessons, minutes, api, calendarFile } from './schedule.js';
import { parseJsonSchedule, templateJson } from './jsonSchedule.js';

const metadata = { teacherId: 'GV01', teacherName: 'Nguyễn Văn A', schoolId: 'PTIT', schoolName: 'Học viện PTIT', semesterStartDate: '2026-08-10', colorCode: '#177c62' };
test('JSON template preserves the backend contract, UTF-8 BOM and minute-accurate times', () => {
  const input = templateJson();
  input.school.periods[0].start = '07:20:00';
  const result = parseJsonSchedule('\uFEFF' + JSON.stringify(input), '2026-08-10');
  assert.equal(result.teacherId, 'GV01');
  assert.equal(result.school.periods[0].start, '07:20');
  assert.equal(result.teacherName, 'Nguyễn Văn A');
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
test('backend week-number markers and leading spaces retain their original week positions in JSON and Excel', async () => {
  const input = templateJson();
  input.courses[0].weekSchedule = ' 23-56--';
  const result = parseJsonSchedule(JSON.stringify(input));
  assert.equal(result.courses[0].weekSchedule, ' 23-56--');
  assert.equal(firstWeek(result.courses), 2);
  const workbook = templateWorkbook();
  workbook.getWorksheet('MonHoc').getCell('G2').value = ' 23-56--';
  assert.equal((await parseWorkbook(await workbook.xlsx.writeBuffer())).courses[0].weekSchedule, ' 23-56--');
});
test('real XLSX template round trip produces the backend upload contract', async () => {
  const workbook = templateWorkbook();
  workbook.getWorksheet('TietHoc').getCell('B2').value = '07:10';
  const parsed = await parseWorkbook(await workbook.xlsx.writeBuffer());
  const upload = buildUpload(parsed, metadata, '2026-08-10');
  assert.equal(upload.school.periods[0].start, '07:10');
  assert.equal(upload.courses[0].weekSchedule, 'xxxx-xxxx');
  assert.equal(upload.courses[0].startPeriod, 1);
  assert.equal(upload.courses[0].numPeriods, 2);
  assert.equal(upload.school.schoolId, 'PTIT');
  assert.equal(firstWeek([{ weekSchedule: '---xx' }, { weekSchedule: '-xx' }]), 2);
  assert.throws(() => buildUpload(parsed, { ...metadata, semesterStartDate: '2026-08-17' }, '2026-08-10'), /Mốc tuần/);
  assert.throws(() => buildUpload(parsed, { ...metadata, semesterStartDate: '2026-08-11' }), /Thứ Hai/);
});
test('rejects corrupt workbooks, missing sheets/columns, invalid references and duplicate rows before saving', async () => {
  await assert.rejects(parseWorkbook(Buffer.from('not an excel file')), /Không đọc được/);
  const cases = [
    [workbook => workbook.removeWorksheet('TietHoc'), /Thiếu sheet TietHoc/],
    [workbook => { workbook.getWorksheet('MonHoc').getCell('D1').value = 'invalid'; }, /thiếu cột dayOfWeek/],
    [workbook => { workbook.getWorksheet('MonHoc').getCell('D2').value = 9; }, /thứ phải từ 2 đến 8/],
    [workbook => { workbook.getWorksheet('MonHoc').getCell('E2').value = 3; }, /tiết 4/],
    [workbook => { workbook.getWorksheet('MonHoc').getCell('G2').value = '---------'; }, /chuỗi tuần/],
    [workbook => { workbook.getWorksheet('MonHoc').getCell('G2').value = { formula: '1+1', result: 2 }; }, /công thức/],
    [workbook => { workbook.getWorksheet('TietHoc').getCell('C2').value = '06:50'; }, /giờ kết thúc/],
    [workbook => { workbook.getWorksheet('TietHoc').getCell('B3').value = '07:40'; }, /chồng/],
    [workbook => { workbook.getWorksheet('MonHoc').addRow(workbook.getWorksheet('MonHoc').getRow(2).values.slice(1)); }, /bị lặp/],
  ];
  for (const [mutate, pattern] of cases) {
    const workbook = templateWorkbook(); mutate(workbook);
    await assert.rejects(parseWorkbook(await workbook.xlsx.writeBuffer()), pattern);
  }
});
test('reads Vietnamese headers and native Excel time cells', async () => {
  const workbook = templateWorkbook();
  workbook.getWorksheet('MonHoc').getRow(1).values = ['Mã môn', 'Tên môn', 'Phòng', 'Thứ', 'Tiết bắt đầu', 'Số tiết', 'Chuỗi tuần'];
  workbook.getWorksheet('TietHoc').getRow(1).values = ['Tiết', 'Giờ bắt đầu', 'Giờ kết thúc'];
  workbook.getWorksheet('TietHoc').getCell('B2').value = (7 * 60 + 20) / 1440;
  const parsed = await parseWorkbook(await workbook.xlsx.writeBuffer());
  assert.equal(parsed.periods[0].start, '07:20');
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
      assert.equal(url, '/api/schedule/upload-school');
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
