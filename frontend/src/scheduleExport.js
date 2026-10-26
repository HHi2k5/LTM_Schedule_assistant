import ExcelJS from 'exceljs';
import { addDays, isMonday, minutes } from './schedule.js';

export const scheduleHeaders = ['Mã MH', 'Tên môn học', 'Nhóm tổ', 'Số tín chỉ', 'Thứ', 'Tiết bắt đầu', 'Số tiết', 'Phòng', 'Thời gian học:', 'Mã GV', 'Tên GV'];
const shortDate = value => `${value.slice(8)}/${value.slice(5, 7)}/${value.slice(2, 4)}`;
const nonempty = value => typeof value === 'string' && value.trim().length > 0;
const integer = (value, min, max) => Number.isInteger(value) && value >= min && value <= max;

export function scheduledRows(result) {
  if (!isMonday(result?.semesterStartDate)) throw new Error('Kết quả thiếu mốc Thứ Hai của tuần 1 hợp lệ.');
  if (!result.scheduledClasses?.length) throw new Error('Chưa có lớp đã xếp để xuất Excel.');
  if (!Array.isArray(result.periods) || !result.periods.length) throw new Error('Kết quả thiếu bảng giờ tiết.');
  const periods = new Map();
  for (const period of result.periods) {
    const validTime = value => /^(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?$/.test(value || '');
    if (!integer(period.period, 1, 30) || periods.has(period.period) || !validTime(period.start) || !validTime(period.end) || minutes(period.end) <= minutes(period.start)) {
      throw new Error('Bảng giờ tiết trong kết quả không hợp lệ.');
    }
    periods.set(period.period, period);
  }
  const rows = [];
  for (const slot of result.scheduledClasses) {
    if (![slot.teacherId, slot.teacherName, slot.courseCode, slot.baseCourseName, slot.classGroup, slot.room].every(nonempty)
        || !integer(slot.dayOfWeek, 2, 8) || !integer(slot.startPeriod, 1, 30) || !integer(slot.numPeriods, 1, 30)
        || (slot.credits != null && !integer(slot.credits, 1, 30))
        || !/^[a-zA-Z0-9 -]{1,104}$/.test(slot.weekSchedule || '') || !/[^- ]/.test(slot.weekSchedule)) {
      throw new Error('Một lớp thiếu hoặc sai dữ liệu giảng viên, môn học, tiết hoặc tuần.');
    }
    for (let p = slot.startPeriod; p < slot.startPeriod + slot.numPeriods; p++) {
      if (!periods.has(p)) throw new Error(`Lớp ${slot.courseCode} thiếu giờ của tiết ${p}.`);
    }
    const weeks = slot.weekSchedule;
    for (let i = 0; i < weeks.length; i++) {
      if (weeks[i] === '-' || weeks[i] === ' ') continue;
      const first = i;
      while (i + 1 < weeks.length && weeks[i + 1] !== '-' && weeks[i + 1] !== ' ') i++;
      const start = addDays(result.semesterStartDate, first * 7 + slot.dayOfWeek - 2);
      const end = addDays(result.semesterStartDate, i * 7 + slot.dayOfWeek - 2);
      rows.push({ slot, firstWeek: first, values: [slot.courseCode, slot.baseCourseName, slot.classGroup,
        slot.credits ?? null, slot.dayOfWeek, slot.startPeriod, slot.numPeriods, slot.room,
        `${shortDate(start)} đến ${shortDate(end)}`, slot.teacherId, slot.teacherName] });
    }
  }
  rows.sort((a, b) => {
    for (const key of ['teacherId', 'courseCode', 'classGroup']) {
      const compared = a.slot[key].localeCompare(b.slot[key], 'vi', { numeric: true });
      if (compared) return compared;
    }
    return a.slot.dayOfWeek - b.slot.dayOfWeek || a.firstWeek - b.firstWeek || a.slot.startPeriod - b.slot.startPeriod;
  });
  return rows.map(row => row.values);
}

export function scheduledWorkbook(result) {
  const rows = scheduledRows(result);
  const workbook = new ExcelJS.Workbook();
  const courses = workbook.addWorksheet('MonHoc');
  courses.addRow(scheduleHeaders);
  rows.forEach(row => courses.addRow(row));
  courses.columns.forEach((column, i) => { column.width = [18, 48, 12, 12, 8, 16, 12, 16, 32, 18, 28][i]; });
  [1, 3, 9, 10, 11].forEach(column => { courses.getColumn(column).numFmt = '@'; });
  const periods = workbook.addWorksheet('TietHoc');
  periods.addRow(['period', 'start', 'end']);
  [...result.periods].sort((a, b) => a.period - b.period)
    .forEach(period => periods.addRow([period.period, period.start.slice(0, 5), period.end.slice(0, 5)]));
  periods.columns.forEach(column => { column.width = 16; });
  for (const sheet of [courses, periods]) {
    sheet.getRow(1).font = { bold: true };
    sheet.views = [{ state: 'frozen', ySplit: 1 }];
    sheet.autoFilter = { from: { row: 1, column: 1 }, to: { row: sheet.rowCount, column: sheet.columnCount } };
  }
  const school = String(result.schoolId || 'truong').replace(/[^a-zA-Z0-9_-]/g, '_');
  return { workbook, filename: `lich-da-xep-${school}-${result.semesterStartDate}.xlsx`,
    missingCredits: result.scheduledClasses.some(slot => slot.credits == null), rowCount: rows.length };
}
