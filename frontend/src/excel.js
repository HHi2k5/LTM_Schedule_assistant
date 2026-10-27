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

// Chuẩn hóa giá trị giờ từ ô Excel (hỗ trợ cả kiểu Time của Excel, "7:15", "07:15", "7h15")
function parseExcelTime(raw) {
  if (raw == null || raw === '') return '';
  if (raw instanceof Date) {
    const h = String(raw.getUTCHours()).padStart(2, '0');
    const m = String(raw.getUTCMinutes()).padStart(2, '0');
    return `${h}:${m}`;
  }
  if (typeof raw === 'number' && raw >= 0 && raw < 1) {
    const totalMinutes = Math.round(raw * 1440);
    const h = String(Math.floor(totalMinutes / 60)).padStart(2, '0');
    const m = String(totalMinutes % 60).padStart(2, '0');
    return `${h}:${m}`;
  }
  const str = String(raw).trim().toLowerCase().replace('h', ':');
  const match = str.match(/^(\d{1,2}):(\d{2})/);
  if (match) {
    return `${ match[1].padStart(2, '0') }:${ match[2] }`;
  }
  return '';
}

function toMinutes(hhmm) {
  const [h, m] = hhmm.split(':').map(Number);
  return h * 60 + (m || 0);
}

function fromMinutes(total) {
  const h = String(Math.floor(total / 60)).padStart(2, '0');
  const m = String(total % 60).padStart(2, '0');
  return `${h}:${m}`;
}

// Khung 12h đến 13h được gán là Tiết 6
const DEFAULT_PERIODS = [
  { period: 1, start: '07:00', end: '07:50' },
  { period: 2, start: '08:00', end: '08:50' },
  { period: 3, start: '09:00', end: '09:50' },
  { period: 4, start: '10:00', end: '10:50' },
  { period: 5, start: '11:00', end: '11:50' },
  { period: 6, start: '12:00', end: '13:00' }, // Tiết 6: 12h00 - 13h00
  { period: 7, start: '13:00', end: '13:50' },
  { period: 8, start: '14:00', end: '14:50' },
  { period: 9, start: '15:00', end: '15:50' },
  { period: 10, start: '16:00', end: '16:50' },
  { period: 11, start: '17:00', end: '17:50' },
  { period: 12, start: '18:00', end: '18:50' }
];

function convertDateRangeToWeeks(rawText) {
  const str = String(rawText ?? '').trim();
  if (!str) return '1111111111111111';
  if (/^[a-zA-Z0-9 -]{1,104}$/.test(str) && !str.includes('/')) {
    return str;
  }

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
    const semesterStart = new Date(Date.UTC(2026, 7, 10));

    const maxWeeks = 16;
    const startWeek = Math.floor((startDate - semesterStart) / (7 * 86400000)) + 1;
    const endWeek = Math.floor((endDate - semesterStart) / (7 * 86400000)) + 1;

    let weeks = '';
    for (let w = 1; w <= maxWeeks; w++) {
      weeks += (w >= Math.max(1, startWeek) && w <= Math.min(maxWeeks, endWeek)) ? '1' : '-';
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

  const sheet = workbook.getWorksheet('MonHoc') || workbook.worksheets[0];
  if (!sheet) throw new Error('File Excel không có dữ liệu sheet.');

  const firstHeader = normalize(cellValue(sheet.getRow(1).getCell(1)));
  const thirdHeader = normalize(cellValue(sheet.getRow(1).getCell(3)));
  const isNineOrElevenColumnFormat = firstHeader === 'mamh' || thirdHeader.includes('nhom') || sheet.getRow(1).cellCount >= 8;

  if (!isNineOrElevenColumnFormat && workbook.getWorksheet('MonHoc')) {
    const monHocSheet = workbook.getWorksheet('MonHoc');
    const tietHocSheet = workbook.getWorksheet('TietHoc');
    const courseRows = readSheetByHeaders(monHocSheet, courseKeys);
    const periodRows = tietHocSheet ? readSheetByHeaders(tietHocSheet, periodKeys) : DEFAULT_PERIODS.map((p, idx) => ({ ...p, row: idx + 1 }));
    return validateScheduleRows(periodRows, courseRows);
  }

  // Khởi tạo bảng giờ từ DEFAULT_PERIODS (Tiết 6 = 12:00-13:00)
  const periodMap = new Map(DEFAULT_PERIODS.map(p => [p.period, { ...p }]));

  let lastCode = '';
  let lastName = '';
  let lastGroup = '01';
  const courses = [];

  sheet.eachRow((row, index) => {
    if (index === 1) return;
    const dayVal = cellValue(row.getCell(5));   // Cột E: Thứ
    let startVal = cellValue(row.getCell(6));   // Cột F: Tiết BĐ
    const customStart = parseExcelTime(cellValue(row.getCell(10))); // Cột J: Giờ bắt đầu
    const customEnd = parseExcelTime(cellValue(row.getCell(11)));   // Cột K: Giờ kết thúc

    // Nếu dòng có khung giờ 12:00 - 13:00 mà chưa điền tiết thì tự gán Tiết 6
    if ((startVal == null || String(startVal).trim() === '') && customStart === '12:00') {
      startVal = 6;
    }

    if (dayVal == null || String(dayVal).trim() === '' || startVal == null || String(startVal).trim() === '') return;

    const curCode = cellValue(row.getCell(1));
    const curName = cellValue(row.getCell(2));
    const curGroup = cellValue(row.getCell(3));

    if (curCode != null && String(curCode).trim() !== '') lastCode = String(curCode).trim();
    if (curName != null && String(curName).trim() !== '') lastName = String(curName).trim();
    if (curGroup != null && String(curGroup).trim() !== '') lastGroup = String(curGroup).trim().padStart(2, '0');

    let dayOfWeek = parseInt(dayVal, 10);
    if (isNaN(dayOfWeek)) dayOfWeek = String(dayVal).toLowerCase().includes('cn') ? 8 : 2;

    const startPeriod = parseInt(startVal, 10) || 1;
    const numPeriods = parseInt(cellValue(row.getCell(7)), 10) || 1;

    // Nếu người dùng nhập Giờ bắt đầu & Giờ kết thúc trên dòng môn học -> Cập nhật giờ của các tiết tương ứng
    if (customStart && customEnd && toMinutes(customEnd) > toMinutes(customStart)) {
      const startMin = toMinutes(customStart);
      const endMin = toMinutes(customEnd);
      const slotLen = Math.floor((endMin - startMin) / numPeriods);
      for (let i = 0; i < numPeriods; i++) {
        const pNum = startPeriod + i;
        const pStart = fromMinutes(startMin + i * slotLen);
        const pEnd = (i === numPeriods - 1) ? customEnd : fromMinutes(startMin + (i + 1) * slotLen);
        periodMap.set(pNum, { period: pNum, start: pStart, end: pEnd });
      }
    }

    const rawTime = cellValue(row.getCell(9)); // Cột I: Thời gian học
    const weekSchedule = convertDateRangeToWeeks(rawTime);

    courses.push({
      courseCode: lastCode || 'MH01',
      courseName: lastGroup ? `${lastName} (Nhóm ${lastGroup})` : lastName,
      room: String(cellValue(row.getCell(8)) || 'Phòng học').trim(),
      dayOfWeek: dayOfWeek,
      startPeriod: startPeriod,
      numPeriods: numPeriods,
      weekSchedule: weekSchedule,
      customStartTime: customStart || undefined,
      customEndTime: customEnd || undefined,
      row: index
    });
  });

  if (!courses.length) {
    throw new Error('Không đọc được dòng môn học nào. Hãy kiểm tra lại các cột Thứ (Cột E) và Tiết bắt đầu (Cột F) trong file Excel.');
  }

  // Chuẩn hóa lại các tiết trong periodMap để không bị chồng lấn giờ
  const sortedPeriods = [];
  let cursorMin = 420; // 07:00
  const maxPeriod = Math.max(12, ...periodMap.keys());
  for (let p = 1; p <= maxPeriod; p++) {
    const item = periodMap.get(p);
    if (item && toMinutes(item.start) >= cursorMin) {
      sortedPeriods.push({ period: p, start: item.start, end: item.end, row: p });
      cursorMin = toMinutes(item.end);
    } else if (item) {
      const dur = Math.max(15, toMinutes(item.end) - toMinutes(item.start));
      const s = fromMinutes(cursorMin);
      const e = fromMinutes(cursorMin + dur);
      sortedPeriods.push({ period: p, start: s, end: e, row: p });
      cursorMin += dur;
    } else {
      const s = fromMinutes(cursorMin);
      const e = fromMinutes(cursorMin + 45);
      sortedPeriods.push({ period: p, start: s, end: e, row: p });
      cursorMin += 45;
    }
  }

  return validateScheduleRows(sortedPeriods, courses);
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
  const sheet = workbook.addWorksheet('MonHoc');
  sheet.addRow([
    'Mã MH', 'Tên môn học', 'Nhóm tổ', 'Số tín chỉ',
    'Thứ', 'Tiết bắt đầu', 'Số tiết', 'Phòng',
    'Thời gian học:', 'Giờ bắt đầu', 'Giờ kết thúc'
  ]);
  sheet.addRow(['INT1342M', 'Phân tích và thiết kế hệ thống thông tin', 7, 3, 6, 6, 1, '505-NT', '14/08/26 đến 20/11/26', '12:00', '13:00']);
  sheet.addRow(['INT14189', 'Quản lý dự án phần mềm', 2, 3, 2, 1, 3, '601-NT', '10/08/26 đến 23/11/26', '07:15', '09:45']);
  sheet.addRow(['INT1313', 'Lập trình mạng', 1, 3, 4, 7, 3, '704-A2', '12/08/26 đến 25/11/26', '13:00', '15:50']);
  sheet.getRow(1).font = { bold: true };
  sheet.columns.forEach(col => { col.width = 18; });

  const tietSheet = workbook.addWorksheet('TietHoc');
  tietSheet.addRow(['period', 'start', 'end']);
  DEFAULT_PERIODS.forEach(p => tietSheet.addRow([p.period, p.start, p.end]));
  tietSheet.getRow(1).font = { bold: true };

  return workbook;
}