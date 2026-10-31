import { test } from 'node:test';
import assert from 'node:assert/strict';
import { addDays } from './schedule.js';
import { busyWindowsFromSlots, expandFixedRows, initialVisitingForm, visitingInput, validateVisitingPreview } from './visitingLecturer.js';

const detail = { plan: { revision: 2, semesterStartDate: '2026-08-10' }, draft: { summary: { id: 100 }, courses: [] } };
const options = { revision: 2, baseVersionId: 100, snapshot: 'a'.repeat(64), lecturers: [{ id: 9 }], classes: [{ classId: 'NET:LT', courseCode: 'NET', courseName: 'Mạng', cohort: '2024', studyStartDate: '2026-08-10', studyEndDate: '2026-11-29' }], busySlots: [] };
const initial = () => ({ ...initialVisitingForm(detail, options), classIds: ['NET:LT'] });

test('receiving a class sends its ID without adding a new workload or uploading assigned hours', () => {
  const form = initial(); form.role = 'ADMIN'; form.sessions = [{ invalid: true }];
  const payload = visitingInput(detail, options, form);
  assert.deepEqual(payload.classIds, ['NET:LT']); assert.equal(payload.newClass, null); assert.deepEqual(payload.sessions, []); assert.equal(payload.role, undefined);
  assert.throws(() => visitingInput(detail, options, { ...form, classIds: [] }), /Chọn/);
  assert.throws(() => visitingInput(detail, { ...options, revision: 3 }, form), /khớp/);
  const restored = initialVisitingForm({ ...detail, draft: { ...detail.draft, visitingChange: { lecturerId: 10 } } }, { ...options, lecturers: [{ id: 9 }, { id: 10 }] });
  assert.equal(restored.lecturerId, '10');
});

test('fixed weekly dates expand into actual Sundays across a year boundary without changing period counts', () => {
  const sessions = expandFixedRows([{ classId: 'P:LT', startDate: '2027-01-03', endDate: '2027-01-10', dayOfWeek: '8', roomId: '10', startPeriod: '4', numPeriods: '3' }], '2026-12-28');
  assert.deepEqual(sessions.map(s => s.date), ['2027-01-03', '2027-01-10']); assert.equal(sessions.reduce((sum, s) => sum + s.numPeriods, 0), 6);
  assert.throws(() => expandFixedRows([{ startDate: '2026-08-10', endDate: '2026-08-10', dayOfWeek: '8', roomId: 10, startPeriod: 1, numPeriods: 3 }], '2026-08-10'), /không chứa/);
  assert.throws(() => expandFixedRows([{ startDate: '2026-08-10', endDate: '2026-08-17', dayOfWeek: '', roomId: 10, startPeriod: 1, numPeriods: 3 }], '2026-08-10'), /chọn thứ/);
});

test('busy dates compress back into weekly windows while preserving gaps and second-accurate clocks', () => {
  const dates = ['2026-08-10', '2026-08-17', '2026-08-31'];
  const windows = busyWindowsFromSlots(dates.map(date => ({ lecturerId: 9, date, startTime: '07:05:30', endTime: '08:40:45', reason: 'Ngoài trường' })), 9);
  assert.equal(windows.length, 2); assert.equal(windows[0].dayOfWeek, '2'); assert.equal(windows[0].endDate, '2026-08-17'); assert.equal(windows[1].startDate, '2026-08-31');
  assert.equal(windows[0].startTime, '07:05:30'); assert.equal(busyWindowsFromSlots([], 9).length, 0);
  const many = [0, 1].flatMap(offset => Array.from({ length: 90 }, (_, week) => ({ lecturerId: 9, date: addDays('2026-08-10', week * 7 + offset), startTime: '07:00', endTime: '08:00', reason: 'Bận' })));
  assert.equal(busyWindowsFromSlots(many, 9).length, 2);
});

test('new classes carry an explicit independent demand and fixed sessions instead of receiving an old class', () => {
  const form = initial(); form.action = 'NEW'; form.newClass = { ...form.newClass, studentCount: '40', credits: '3', totalPeriods: '6', parentClassId: 'unused-parent' };
  form.sessions = [{ classId: '$NEW', startDate: '2026-08-16', endDate: '2026-08-23', dayOfWeek: '8', roomId: '10', startPeriod: '1', numPeriods: '3' }];
  const payload = visitingInput(detail, options, form);
  assert.deepEqual(payload.classIds, []); assert.equal(payload.newClass.totalPeriods, 6); assert.equal(payload.newClass.parentClassId, null); assert.equal(payload.sessions.length, 2); assert.equal(payload.timing, 'FIXED');
});

test('invalid busy ranges and equal or reversed times cannot silently clear availability', () => {
  const form = initial(); const busy = { startDate: '2026-08-10', endDate: '2026-08-10', dayOfWeek: '', startTime: '07:00:30', endTime: '07:00:30', reason: '' };
  assert.throws(() => visitingInput(detail, options, { ...form, busySlots: [busy] }), /kiểm tra/);
  assert.throws(() => visitingInput(detail, options, { ...form, busySlots: [{ ...busy, endTime: '08:00', dayOfWeek: '8' }] }), /không chứa/);
  const valid = visitingInput(detail, options, { ...form, busySlots: [{ ...busy, endTime: '08:00:30' }] }); assert.equal(valid.busySlots[0].startTime, '07:00:30');
});

test('a concrete proposal must match the saved revision and version; conflicts can remain in a saved draft', () => {
  const payload = visitingInput(detail, options, initial());
  const preview = { previewToken: 'b'.repeat(64), revision: 2, baseVersionId: 100, complete: false, canConfirm: false, changes: [], beforeEntries: [], afterEntries: [], busySlots: [], conflicts: [{ type: 'WORKLOAD' }] };
  assert.equal(validateVisitingPreview(preview, payload), preview);
  assert.throws(() => validateVisitingPreview({ ...preview, revision: 1 }, payload), /khớp/);
  assert.throws(() => validateVisitingPreview({ ...preview, previewToken: '' }, payload), /khớp/);
});
