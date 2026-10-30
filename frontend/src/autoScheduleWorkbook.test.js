import { test } from 'node:test';
import assert from 'node:assert/strict';
import ExcelJS from 'exceljs';
import { planWorkbook, teacherWorkbook, planHeaders, teacherHeaders } from './autoScheduleWorkbook.js';
import { validateManual } from './workflow.js';
import { scheduledWorkbook } from './scheduleExport.js';
import { parseWorkbook } from './excel.js';

const plan = { courseCode: 'NET', courseName: 'Mạng máy tính', cohort: '2024', classSize: 50, numClasses: 1,
  credits: 3, theoryPeriods: 30, practicePeriods: 45, studyStartDate: '2026-12-28', studyEndDate: '2027-03-28' };
const teacher = { courseCode: 'NET', courseName: plan.courseName, cohort: '2024', teacherId: 'A', teacherName: 'Nguyễn Văn A', theoryWeight: 2, practiceWeight: 8 };
const rooms = [{ roomCode: 'LT', roomType: 'THEORY', capacity: 60 }, { roomCode: 'LAB', roomType: 'PRACTICE', capacity: 30 }];

test('new XLSX plan keeps full dates, credits and syllabus workload without assigned weekdays or session lengths', async () => {
  const book = new ExcelJS.Workbook(); await book.xlsx.load(await planWorkbook([plan]).xlsx.writeBuffer());
  const sheet = book.worksheets[0];
  assert.deepEqual(planHeaders, ['STT', 'Mã môn', 'Tên học phần', 'Sĩ số', 'Khóa', 'Số lớp mở',
    'Tuần bắt đầu — từ ngày', 'Tuần kết thúc — đến ngày', 'Số tín chỉ', 'Tổng tiết LT', 'Tổng tiết TH']);
  assert.deepEqual(sheet.getRow(1).values.slice(1), planHeaders);
  assert.equal(sheet.columnCount, 11);
  assert.deepEqual(sheet.getRow(2).values.slice(1, 12), [1, 'NET', plan.courseName, 50, '2024', 1, '28/12/2026', '28/03/2027', 3, 30, 45]);
  assert.equal(planHeaders.includes('Thứ 2'), false); assert.equal(planHeaders.includes('Tiết BĐ'), false);
  const teachers = teacherWorkbook([teacher]).worksheets[0];
  assert.deepEqual(teachers.getRow(1).values.slice(1), teacherHeaders);
  assert.deepEqual(teachers.getRow(2).values.slice(-2), [2, 8]);
});

test('manual validation accepts different syllabus structures and rejects missing hours, weights and invalid windows', () => {
  for (const [lt, th] of [[45, 0], [30, 30], [30, 45], [0, 45]]) validateManual([{ ...plan, theoryPeriods: lt, practicePeriods: th }], [teacher], rooms, '2026-12-28');
  for (const patch of [{ theoryPeriods: '' }, { practicePeriods: -1 }, { theoryPeriods: 0, practicePeriods: 0 },
    { studyStartDate: '2026-02-30' }, { studyEndDate: '2026-12-27' }]) {
    assert.throws(() => validateManual([{ ...plan, ...patch }], [teacher], rooms, '2026-12-28'));
  }
  assert.throws(() => validateManual([plan], [{ ...teacher, theoryWeight: '' }], rooms));
  assert.throws(() => validateManual([plan], [{ ...teacher, practiceWeight: -1 }], rooms));
});

test('weekly schedule export and readback preserve variable hours, full dates and assignment/workload summaries', async () => {
  const base = { teacherId: 'A', teacherName: teacher.teacherName, courseCode: 'NET', baseCourseName: plan.courseName,
    classId: 'NET:2024:1:LT', classGroup: 'Lớp 01', credits: 3, room: 'LT', dayOfWeek: 2, startPeriod: 1 };
  const input = { schoolId: 'A', semesterStartDate: '2026-12-28',
    periods: [1, 2, 3].map(period => ({ period, start: `0${period + 6}:00`, end: `0${period + 6}:50` })),
    scheduledClasses: [{ ...base, numPeriods: 2, weekSchedule: '1-1-' }, { ...base, numPeriods: 3, weekSchedule: '-1-1' }],
    teacherAllocations: [{ courseCode: 'NET', cohort: '2024', teacherId: 'A', teacherName: teacher.teacherName, sessionType: 'THEORY', weight: 2, targetClasses: 1, assignedClasses: 1, scheduledPeriods: 10 }],
    classWorkloads: [{ classId: base.classId, courseCode: 'NET', cohort: '2024', classGroup: base.classGroup, sessionType: 'THEORY', teacherId: 'A', requiredPeriods: 10, scheduledPeriods: 10, missingPeriods: 0 }] };
  const { workbook } = scheduledWorkbook(input);
  assert.deepEqual(workbook.worksheets.map(s => s.name), ['MonHoc', 'TietHoc', 'PhanCongGiangVien', 'KhoiLuongLopNhom']);
  assert.match(workbook.getWorksheet('MonHoc').getCell('I2').value, /2026/);
  const parsed = await parseWorkbook(await workbook.xlsx.writeBuffer(), input.semesterStartDate);
  const total = parsed.courses.reduce((sum, c) => sum + [...c.weekSchedule].filter(m => m !== '-' && m !== ' ').length * c.numPeriods, 0);
  assert.equal(total, 10);
  assert.deepEqual(new Set(parsed.courses.map(c => c.numPeriods)), new Set([2, 3]));
  assert.equal(workbook.getWorksheet('PhanCongGiangVien').getCell('H2').value, 1);
  assert.equal(workbook.getWorksheet('KhoiLuongLopNhom').getCell('I2').value, 0);
});
