import ExcelJS from 'exceljs';
import { validateScheduleRows, buildUpload } from './scheduleImport.js';
export { buildUpload } from './scheduleImport.js';

const courseKeys = ['courseCode', 'courseName', 'room', 'dayOfWeek', 'startPeriod', 'numPeriods', 'weekSchedule'];
const periodKeys = ['period', 'start', 'end'];

const normalize = value => String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/[^a-zA-Z0-9]/g, '').toLowerCase();

function cellValue(cell) {
  const value = cell?.value;
  if (value && typeof value === 'object' && !(value instanceof Date)) {
    if ('formula' in value || 'sharedFormula' in value) return cell.result ?? '';
    if (value.richText) return value.richText.map(part => part.text).join('');
    if (value.text != null) return value.text;
  }
  return value;
}

const DEFAULT_PERIODS = [
  { period: 1, start: '07:00', end: '07:50' },
  { period: 2, start: '08:00', end: '08:50' },
  { period: 3, start: '09:00', end: '09:50' },
  { period: 4, start: '10:00', end: '10:50' },
  { period: 5, start: '11:00', end: '11:50' },
  { period: 6, start: '13:00', end: '13:50' },
  { period: 7, start: '14:00', end: '14:50' },
  { period: 8, start: '15:00', end: '15:50' },
  { period: 9, start: '16:00', end: '16:50' },
  { period: 10, start: '17:00', end: '17:50' },
  { period: 11, start: '18:00', end: '18:50' },
  { period: 12, start: '19:00', end: '19:50' }
];

// Hàm chuyển đổi khoảng thời gian thành chuỗi tuần chính xác (16 tuần chuẩn)
function convertDateRangeToWeeks(rawText) {
  const str = String(rawText ?? '').trim();
  if (!str) return '1111111111111111';

  // Nếu đã là chuỗi tuần hợp lệ thì giữ nguyên
  if (/^[a-zA-Z0-9 -]{1,104}$/.test(str) && !str.includes('/')) {
    return str;
  }

  // Tìm 2 mốc ngày dd/MM/yy hoặc dd/MM/yyyy
  const matches = [...str.matchAll(/(\d{1,2})\/(\d{1,2})\/(\d{2,4})/g)];
  if (matches.length >= 2) {
    const parseD = m => {
      const d = Number(m[1]), mo = Number(m[2]) - 1;
      let y = Number(m[3]);
      if (y < 100) y += 2000;
      return new Date(Date.UTC(y, mo, d));
    };
    const startDate = parseD(matches[0]);
    const endDate = parseD(matches[1]);
    
    // Mốc Thứ 2 tuần 1 của kỳ học (10/08/2026)
    const semesterStart = new Date(Date.UTC(2026, 7, 10));
    
    const maxWeeks = 16;
    const startWeek = Math.floor((startDate - semesterStart) / (7 * 86400000)) + 1;
    const endWeek = Math.floor((endDate - semesterStart) / (7 * 86400000)) + 1;

    let weeks = '';
    for (let w = 1; w <= maxWeeks; w++) {
      if (w >= Math.max(1, startWeek) && w <= Math.min(maxWeeks, endWeek)) {
        weeks += '1'; // Tuần có học
      } else {
        weeks += '-'; // Tuần nghỉ
      }
    }
    return weeks;
  }

  return '1111111111111111';
}

export async function parseWorkbook(buffer) {
  const workbook = new ExcelJS.Workbook();
  try {
    await workbook.xlsx.load(buffer);
  } catch {
    throw new Error('Không đọc được file Excel. Hãy dùng file .xlsx không có mật khẩu.');
  }

  const sheet = workbook.worksheets[0];
  if (!sheet) throw new Error('File Excel không có dữ liệu sheet.');

  const firstHeader = normalize(cellValue(sheet.getRow(1).getCell(1)));
  const thirdHeader = normalize(cellValue(sheet.getRow(1).getCell(3)));
  const isNineColumnFormat = firstHeader === 'mamh' || thirdHeader.includes('nhom') || sheet.getRow(1).cellCount >= 8;

  if (!isNineColumnFormat && workbook.getWorksheet('MonHoc')) {
    const monHocSheet = workbook.getWorksheet('MonHoc');
    const tietHocSheet = workbook.getWorksheet('TietHoc');
    const courseRows = readSheetByHeaders(monHocSheet, courseKeys);
    const periodRows = tietHocSheet ? readSheetByHeaders(tietHocSheet, periodKeys) : DEFAULT_PERIODS.map((p, idx) => ({ ...p, row: idx + 1 }));
    return validateScheduleRows(periodRows, courseRows);
  }

  let lastCode = '';
  let lastName = '';
  let lastGroup = '01';
  const courses = [];

  sheet.eachRow((row, index) => {
    if (index === 1) return; // Bỏ qua tiêu đề
    const dayVal = cellValue(row.getCell(5));   // Cột E: Thứ
    const startVal = cellValue(row.getCell(6)); // Cột F: Tiết BĐ

    if (dayVal == null || String(dayVal).trim() === '' || startVal == null || String(startVal).trim() === '') return;

    const curCode = cellValue(row.getCell(1));
    const curName = cellValue(row.getCell(2));
    const curGroup = cellValue(row.getCell(3));

    if (curCode != null && String(curCode).trim() !== '') lastCode = String(curCode).trim();
    if (curName != null && String(curName).trim() !== '') lastName = String(curName).trim();
    if (curGroup != null && String(curGroup).trim() !== '') lastGroup = String(curGroup).trim().padStart(2, '0');

    let dayOfWeek = parseInt(dayVal, 10);
    if (isNaN(dayOfWeek)) dayOfWeek = String(dayVal).toLowerCase().includes('cn') ? 8 : 2;

    const rawTime = cellValue(row.getCell(9)); // Cột I: Thời gian học
    const weekSchedule = convertDateRangeToWeeks(rawTime);

    courses.push({
      courseCode: lastCode || 'MH01',
      courseName: lastGroup ? `${lastName} (Nhóm ${lastGroup})` : lastName,
      room: String(cellValue(row.getCell(8)) || 'Phòng học').trim(),
      dayOfWeek: dayOfWeek,
      startPeriod: parseInt(startVal, 10) || 1,
      numPeriods: parseInt(cellValue(row.getCell(7)), 10) || 2,
      weekSchedule: weekSchedule,
      row: index
    });
  });

  if (!courses.length) {
    throw new Error('Không đọc được dòng môn học nào. Hãy kiểm tra lại các cột Thứ (Cột E) và Tiết bắt đầu (Cột F) trong file Excel.');
  }

  return validateScheduleRows(DEFAULT_PERIODS.map((p, idx) => ({ ...p, row: idx + 1 })), courses);
}

function readSheetByHeaders(sheet, keys) {
  const columns = {};
  sheet.getRow(1).eachCell((cell, index) => {
    const header = normalize(cellValue(cell));
    const key = keys.find(k => normalize(k) === header);
    if (key) columns[key] = index;
  });

  const result = [];
  sheet.eachRow((row, index) => {
    if (index === 1) return;
    const data = Object.fromEntries(keys.map(k => [k, cellValue(row.getCell(columns[k]))]));
    if (Object.values(data).every(v => v == null || v === '')) return;
    result.push({ ...data, row: index });
  });
  return result;
}

export function firstWeek(courses) {
  return Math.min(...courses.map(course => course.weekSchedule.search(/[^- ]/) + 1));
}

export function templateWorkbook() {
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet('ThoiKhoaBieu');
  sheet.addRow(['Mã MH', 'Tên môn học', 'Nhóm tổ', 'Số tín chỉ', 'Thứ', 'Tiết bắt đầu', 'Số tiết', 'Phòng', 'Thời gian học:']);
  sheet.getRow(1).font = { bold: true };
  sheet.columns.forEach(col => { col.width = 20; });
  return workbook;
}