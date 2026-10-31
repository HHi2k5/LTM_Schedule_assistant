import { test } from 'node:test';
import assert from 'node:assert/strict';
import { catalogForm, catalogPayload, catalogPath, catalogValue } from './catalog.js';
import { catalogWorkbook, catalogColumns, legacyLecturerFile } from './catalogWorkbook.js';
import ExcelJS from 'exceljs';

test('edit payload preserves links and revision but never sends credentials or arbitrary response fields', () => {
  const row = { id: 12, schoolCode: 'PTIT', lecturerCode: 'L01', fullName: 'Nguyễn Văn A', departmentId: 3, lecturerType: 'VISITING',
    accountTeacherId: 'GVAB12', active: false, revision: 7, password: 'not-a-form-field', role: 'ADMIN' };
  const payload = catalogPayload('lecturers', catalogForm('lecturers', row), true);
  assert.deepEqual(payload, { lecturerCode: 'L01', fullName: 'Nguyễn Văn A', departmentId: 3, lecturerType: 'VISITING', accountTeacherId: 'GVAB12', active: false, revision: 7 });
  const form = catalogForm('lecturers'); form.fullName = ' Tên mới '; form.lecturerCode = ' L02 ';
  assert.equal(catalogPayload('lecturers', form).departmentId, null);
  assert.equal(catalogPayload('lecturers', form).fullName, 'Tên mới');
});

test('room editing maps stored room types without changing capacity or state', () => {
  const form = catalogForm('rooms', { roomCode: 'LAB', roomName: 'Phòng LAB', roomType: 'LAB_PRACTICE', capacity: 30, isLab: true, active: false, revision: 2 });
  const payload = catalogPayload('rooms', form, true);
  assert.equal(payload.roomType, 'PRACTICE'); assert.equal(payload.capacity, 30); assert.equal(payload.active, false); assert.equal(payload.revision, 2);
  assert.equal(catalogPayload('assignments', { courseCode: 'NET', courseName: 'Mạng', cohort: '2024', theoryWeight: '2', practiceWeight: '8', active: true }).practiceWeight, 8);
});

test('assignment URLs require an explicit lecturer; catalog labels distinguish faculty and cohort', () => {
  assert.throws(() => catalogPath('assignments', 'PTIT', ''), /Chọn giảng viên/);
  assert.equal(catalogPath('assignments', 'PTIT', 12), '/PTIT/lecturers/12/assignments');
  assert.equal(catalogValue('departmentId', 3, [{ id: 3, name: 'CNTT' }]), 'CNTT');
  assert.equal(catalogValue('cohort', '2024'), '2024'); assert.equal(catalogValue('lecturerType', 'VISITING'), 'Thỉnh giảng');
});

test('workbooks round-trip Vietnamese headers, literal values and zero weights', async () => {
  for (const kind of Object.keys(catalogColumns)) {
    const rows = kind === 'assignments' ? [{ lecturerCode: 'L01', courseCode: 'NET', courseName: '=Tên môn', cohort: '2024', theoryWeight: 0, practiceWeight: 8, active: false }]
      : kind === 'lecturers' ? [{ lecturerCode: 'L01', fullName: '=Nguyễn Văn A', accountTeacherId: 'GV01', active: true }]
        : [{ roomCode: '101', roomName: 'Phòng 101', roomType: 'THEORY', capacity: 60, active: true }];
    const book = new ExcelJS.Workbook(); await book.xlsx.load(await catalogWorkbook(kind, rows).xlsx.writeBuffer());
    const sheet = book.getWorksheet('DanhMuc');
    assert.deepEqual(sheet.getRow(1).values.slice(1), catalogColumns[kind].map(([, label]) => label));
    const literal = kind === 'lecturers' ? sheet.getCell('B2') : kind === 'assignments' ? sheet.getCell('C2') : null;
    if (literal) { assert.equal(literal.type, ExcelJS.ValueType.String); assert.ok(literal.value.startsWith('=')); }
    if (kind === 'assignments') { assert.equal(sheet.getCell('E2').value, 0); assert.equal(sheet.getCell('G2').value, 'FALSE'); }
  }
});

test('legacy workbook leaves uncertain faculty and lecturer type blank for review', async () => {
  const file = await legacyLecturerFile({ schoolCode: 'PTIT', lecturers: [{ lecturerCode: 'L01', fullName: 'Tên', accountTeacherId: 'GV01' }] });
  const book = new ExcelJS.Workbook(); await book.xlsx.load(await file.arrayBuffer());
  const sheet = book.getWorksheet('DanhMuc');
  assert.equal(sheet.getCell('A2').value, 'L01'); assert.equal(sheet.getCell('E2').value, 'GV01');
  assert.ok(!sheet.getCell('C2').value); assert.ok(!sheet.getCell('D2').value);
});
