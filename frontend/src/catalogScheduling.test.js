import { test } from 'node:test';
import assert from 'node:assert/strict';
import { catalogConfiguration, validateCatalogOptions, resultLecturer } from './catalogScheduling.js';
import { validatePlans } from './workflow.js';
import { scheduledWorkbook } from './scheduleExport.js';

const options = {
  school: { schoolCode: 'A' }, snapshot: 'a'.repeat(64),
  lecturers: [{ lecturer: { id: 1, lecturerCode: 'STAFF01' }, eligible: true }, { lecturer: { id: 2 }, eligible: false }],
  rooms: [{ id: 10 }], assignments: [{ id: 5, lecturerId: 1, courseCode: 'NET', cohort: '2024', theoryWeight: 2, practiceWeight: 8 }],
  periodChoices: [{ id: 'p', periods: [{ period: 1, start: '07:00', end: '07:50' }] }],
};

test('per-run weights are numeric, limited to selected eligible assignments and leave defaults unchanged', () => {
  const before = structuredClone(options);
  const config = catalogConfiguration(options, [1], [10], { 5: { theoryWeight: '9', practiceWeight: '0' }, 999: { theoryWeight: 1, practiceWeight: 1 } }, 'p', true);
  assert.deepEqual(config.weights, [{ assignmentId: 5, theoryWeight: 9, practiceWeight: 0 }]);
  assert.equal(config.replaceSchoolSchedule, true); assert.deepEqual(options, before);
  for (const weight of ['', -1, 1.5, 10001]) assert.throws(() => catalogConfiguration(options, [1], [10], { 5: { theoryWeight: weight, practiceWeight: 1 } }, 'p', true), /Trọng số/);
});

test('invalid or unconfirmed catalog selections cannot be sent', () => {
  assert.throws(() => validateCatalogOptions({}), /không hợp lệ/);
  for (const ids of [[], [2], [1, 1]]) assert.throws(() => catalogConfiguration(options, ids, [10], {}, 'p', true), /giảng viên/);
  assert.throws(() => catalogConfiguration(options, [1], [999], {}, 'p', true), /phòng/);
  assert.throws(() => catalogConfiguration(options, [1], [10], {}, 'other', true), /bảng giờ/);
  assert.throws(() => catalogConfiguration(options, [1], [10], {}, 'p', false), /toàn trường/);
});

test('department drafts cannot request a whole-school replacement and still validate resources', () => {
  assert.equal(catalogConfiguration(options, [1], [10], {}, 'p', false, true).replaceSchoolSchedule, false);
  assert.equal(catalogConfiguration(options, [1], [10], {}, 'p', true, true).replaceSchoolSchedule, false);
  assert.throws(() => catalogConfiguration(options, [2], [10], {}, 'p', false, true), /giảng viên/);
});

test('plan validation works without teacher and room files', () => {
  const row = { courseCode: 'NET', courseName: 'Mạng', cohort: '2024', classSize: 50, numClasses: 1, credits: 3,
    theoryPeriods: 6, practicePeriods: 0, studyStartDate: '2026-08-10', studyEndDate: '2026-11-29' };
  validatePlans([row], '2026-08-10');
  assert.throws(() => validatePlans([{ ...row, studyStartDate: '2026-07-01' }], '2026-08-10'), /100 tuần/);
  assert.throws(() => validatePlans([]), /5000/);
});

test('catalog display and workbook distinguish school codes, accounts and schedule lookup identities', () => {
  const identity = { lecturerId: 1, lecturerCode: 'STAFF01', teacherId: 'CAT_LOOKUP', accountTeacherId: null };
  const result = { schoolId: 'A', semesterStartDate: '2026-08-10', periods: [{ period: 1, start: '07:00', end: '07:50' }],
    scheduledClasses: [{ teacherId: 'CAT_LOOKUP', teacherName: 'GV', courseCode: 'NET', courseName: 'Mạng', baseCourseName: 'Mạng', classGroup: '01', room: '101', dayOfWeek: 2, startPeriod: 1, numPeriods: 1, weekSchedule: '1' }],
    catalogContext: { lecturers: [identity] } };
  assert.equal(resultLecturer(result, 'CAT_LOOKUP').lecturerCode, 'STAFF01');
  assert.equal(resultLecturer({}, 'GV01').lecturerCode, 'GV01');
  const { workbook } = scheduledWorkbook(result);
  assert.equal(workbook.getWorksheet('MonHoc').getCell('J2').value, 'CAT_LOOKUP');
  assert.deepEqual(workbook.getWorksheet('DanhTinhGiangVien').getRow(2).values.slice(1), ['STAFF01', '', 'CAT_LOOKUP']);
});
