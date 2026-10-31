import { test } from 'node:test';
import assert from 'node:assert/strict';
import ExcelJS from 'exceljs';
import { boardDefaults, boardQuery, restoreBoard, validateBoard } from './facultyBoard.js';
import { facultyBoardWorkbook } from './facultyBoardWorkbook.js';

function data(view = 'export') {
  return { context: { planId: 1, versionId: 100, versionNumber: 2, state: 'HISTORICAL', name: 'Kế hoạch cũ', schoolCode: 'A', schoolName: 'Trường A', departmentName: 'Khoa CNTT', semester: 'HK1', checkedAt: '2026-10-31T10:00:00Z' },
    filters: { ...boardDefaults, week: 2, view }, startDate: '2026-08-17', endDate: '2026-08-23', pageCount: 1, warnings: [],
    options: { lecturers: [], courses: [], cohorts: [], classes: [], rooms: [] }, groups: [], stats: { sessions: 1, weekPeriods: 3 },
    entries: [{ id: 101, classId: 'CLASS', date: '2026-08-23', startTime: '07:05:30', endTime: '09:40:45', startPeriod: 1, numPeriods: 3, courseCode: 'NET', courseName: '= literal name', cohort: '2024', classGroup: '01', sessionType: 'PRACTICE', lecturerCode: 'GV01', lecturerName: 'Nguyễn Văn A', lecturerType: 'VISITING', roomCode: 'LAB', studentCount: 25, conflicts: [{ type: 'ROOM_CONFLICT', message: 'Phòng trùng buổi khoa khác' }] }],
    assignments: [{ classId: 'CLASS', courseCode: 'NET', courseName: '= literal name', cohort: '2024', classGroup: '01', sessionType: 'PRACTICE', lecturerCode: 'GV01', lecturerName: 'Nguyễn Văn A', lecturerType: 'VISITING', requiredPeriods: 30, scheduledPeriods: 27, missingPeriods: 3, weekPeriods: 3, weekSessions: 1, conflictingEntries: 1 }] };
}

test('faculty preferences restore valid filters but discard damaged values and reset pagination', () => {
  assert.deepEqual(restoreBoard('{broken'), boardDefaults);
  const restored = restoreBoard(JSON.stringify({ week: 2, page: 999, view: 'assignments', groupBy: 'class', roomCode: 'LAB', lecturerType: 'VISITING', conflictsOnly: true, lecturerId: 'bad' }));
  assert.equal(restored.week, 2); assert.equal(restored.page, 0); assert.equal(restored.view, 'assignments'); assert.equal(restored.roomCode, 'LAB'); assert.equal(restored.lecturerId, '');
  assert.equal(restoreBoard('{"week":101,"view":"export","lecturerType":"ADMIN"}').week, 1);
});

test('faculty export queries retain every filter while exporting all pages', () => {
  const filters = { ...boardDefaults, week: 7, page: 3, lecturerId: '4', courseCode: 'Môn & môn', cohort: '2024', classId: 'P:TH:1', roomCode: 'LAB-A', sessionType: 'PRACTICE', lecturerType: 'VISITING', conflictsOnly: true, q: 'Cơ sở' };
  const query = new URLSearchParams(boardQuery(filters, 'export'));
  assert.equal(query.get('view'), 'export'); assert.equal(query.get('page'), '0');
  for (const [key, value] of Object.entries(filters)) if (!['view', 'page'].includes(key)) assert.equal(query.get(key), String(value));
});

test('mismatched versions, weeks, rooms or pages cannot be shown or exported as the requested board', () => {
  const board = data('calendar'); assert.equal(validateBoard(board, 1, 100, board.filters), board);
  assert.throws(() => validateBoard(board, 1, 200, board.filters), /phiên bản/);
  assert.throws(() => validateBoard(board, 1, 100, { ...board.filters, week: 3 }), /phiên bản/);
  assert.throws(() => validateBoard(board, 1, 100, { ...board.filters, roomCode: 'OTHER' }), /Bộ lọc/);
  assert.throws(() => validateBoard(board, 1, 100, { ...board.filters, page: 1 }), /Bộ lọc/);
});

test('faculty workbook preserves real dates and seconds, type, literal names, term versus weekly hours and filter metadata', async () => {
  const report = data(); report.entries[0].locked = true; const { workbook, filename } = facultyBoardWorkbook(report);
  const roundtrip = new ExcelJS.Workbook(); await roundtrip.xlsx.load(await workbook.xlsx.writeBuffer());
  const row = roundtrip.getWorksheet('LichToanKhoa').getRow(2).values.slice(1);
  assert.equal(row[2], '2026-08-23'); assert.equal(row[3], '07:05:30'); assert.equal(row[4], '09:40:45'); assert.equal(row[8], '= literal name'); assert.equal(row[14], 'Thỉnh giảng'); assert.match(row[17], /Phòng trùng/);
  assert.deepEqual(roundtrip.getWorksheet('PhanCongToanKhoa').getRow(2).values.slice(10), [30, 27, 3, 3, 1, 1]);
  assert.equal(roundtrip.getWorksheet('ThongTin').getCell('B7').value, 'Phiên bản lịch sử'); assert.equal(roundtrip.getWorksheet('ThongTin').getCell('B12').value, JSON.stringify(report.filters));
  assert.match(filename, /v2-historical-tuan-2/);
  assert.equal(row[18], 'Đã khóa');
});

test('an incomplete faculty version can export missing demands with no scheduled occurrences', () => {
  const report = data(); report.entries = []; report.stats.sessions = 0; report.stats.weekPeriods = 0;
  const { workbook } = facultyBoardWorkbook(report);
  assert.equal(workbook.getWorksheet('LichToanKhoa').rowCount, 1); assert.equal(workbook.getWorksheet('PhanCongToanKhoa').rowCount, 2);
  assert.throws(() => facultyBoardWorkbook(data('calendar')), /máy chủ/);
});
