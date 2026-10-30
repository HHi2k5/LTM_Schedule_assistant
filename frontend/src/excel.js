import ExcelJS from 'exceljs';
import { validateScheduleRows, buildUpload } from './scheduleImport.js';
import { isMonday } from './schedule.js';
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
    const s = String(raw.getUTCSeconds()).padStart(2, '0');
    return `${h}:${m}${s === '00' ? '' : `:${s}`}`;
  }
  if (typeof raw === 'number' && raw >= 0 && raw < 1) {
    const totalSeconds = Math.round(raw * 86400) % 86400;
    const h = String(Math.floor(totalSeconds / 3600)).padStart(2, '0');
    const m = String(Math.floor(totalSeconds / 60) % 60).padStart(2, '0');
    const s = String(totalSeconds % 60).padStart(2, '0');
    return `${h}:${m}${s === '00' ? '' : `:${s}`}`;
  }
  const str = String(raw).trim().toLowerCase().replace('h', ':');
  const match = str.match(/^(\d{1,2}):(\d{2})/);
  if (match) {
    const seconds = str.match(/^\d{1,2}:\d{2}:(\d{2})$/)?.[1];
    return `${match[1].padStart(2, '0')}:${match[2]}${seconds && seconds !== '00' ? `:${seconds}` : ''}`;
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

function convertDateRangeToWeeks(rawText, semesterStartDate, dayOfWeek) {
  const str = String(rawText ?? '').trim();
  if (!str) return '';
  if (/^[a-zA-Z0-9 -]{1,100}$/.test(str) && !str.includes('/')) {
    return str;
  }

  const matches = [...str.matchAll(/(\d{1,2})\/(\d{1,2})\/(\d{2,4})/g)];
  if (matches.length === 2) {
    const parseD = m => {
      const d = Number(m[1]), mo = Number(m[2]) - 1;
      let y = Number(m[3]);
      if (y < 100) y += 2000;
      const date = new Date(Date.UTC(y, mo, d));
      return date.getUTCFullYear() === y && date.getUTCMonth() === mo && date.getUTCDate() === d ? date : new Date(NaN);
    };
    const startDate = parseD(matches[0]);
    const endDate = parseD(matches[1]);
    if (!isMonday(semesterStartDate)) throw new Error('Mốc tuần 1 phải là ngày Thứ Hai hợp lệ.');
    const semesterStart = new Date(`${semesterStartDate}T00:00:00Z`);
    const dayMs = 86400000;

    if (Number.isNaN(startDate.valueOf()) || Number.isNaN(endDate.valueOf()) || endDate < startDate
        || startDate < semesterStart || endDate >= new Date(semesterStart.valueOf() + 700 * dayMs)) {
      throw new Error('Khoảng ngày học không hợp lệ hoặc nằm ngoài 100 tuần của học kỳ.');
    }

    let weeks = '';
    for (let w = 0; w < 100; w++) {
      const date = new Date(semesterStart.valueOf() + (w * 7 + dayOfWeek - 2) * dayMs);
      weeks += date >= startDate && date <= endDate ? '1' : '-';
    }
    if (!weeks.includes('1')) throw new Error('Khoảng ngày học không có buổi học đúng thứ đã khai báo.');
    return weeks.replace(/-+$/, '');
  }
  throw new Error('Thời gian học phải là chuỗi tuần hoặc khoảng ngày dd/MM/yyyy đến dd/MM/yyyy.');
}

export async function parseWorkbook(buffer, semesterStartDate = '2026-08-10') {
  const workbook = new ExcelJS.Workbook();
  try {
    await workbook.xlsx.load(buffer);
  } catch {
    throw new Error('Không đọc được file Excel. Hãy dùng file .xlsx không có mật khẩu.');
  }

  const sheet = workbook.getWorksheet('MonHoc') || workbook.worksheets[0];
  if (!sheet) throw new Error('File Excel không có dữ liệu sheet.');
  const tietHocSheet = workbook.getWorksheet('TietHoc');
  if (!tietHocSheet) throw new Error('File Excel bắt buộc có sheet TietHoc chứa giờ thực tế.');

  const firstHeader = normalize(cellValue(sheet.getRow(1).getCell(1)));
  const thirdHeader = normalize(cellValue(sheet.getRow(1).getCell(3)));
  const isNineOrElevenColumnFormat = firstHeader === 'mamh' || thirdHeader.includes('nhom') || sheet.getRow(1).cellCount >= 8;

  if (!isNineOrElevenColumnFormat && workbook.getWorksheet('MonHoc')) {
    const monHocSheet = workbook.getWorksheet('MonHoc');
    const courseRows = readSheetByHeaders(monHocSheet, courseKeys);
    const periodRows = readSheetByHeaders(tietHocSheet, periodKeys);
    return validateScheduleRows(periodRows, courseRows);
  }

  const periodRows = readSheetByHeaders(tietHocSheet, periodKeys);
  const periodMap = new Map(periodRows.map(p => [Number(p.period), { ...p, period: Number(p.period) }]));

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
    if (curGroup != null && String(curGroup).trim() !== '') {
      const group = String(curGroup).trim();
      lastGroup = /^\d+$/.test(group) ? group.padStart(2, '0') : group;
    }

    let dayOfWeek = parseInt(dayVal, 10);
    if (isNaN(dayOfWeek)) dayOfWeek = String(dayVal).toLowerCase().includes('cn') ? 8 : 2;

    const startPeriod = parseInt(startVal, 10) || 1;
    const rawNumPeriods = cellValue(row.getCell(7));
    if (!/^\d+$/.test(String(rawNumPeriods ?? '').trim())) throw new Error(`Dòng ${index}: số tiết phải là số nguyên dương.`);
    const numPeriods = parseInt(rawNumPeriods, 10);

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
    const weekSchedule = convertDateRangeToWeeks(rawTime, semesterStartDate, dayOfWeek);

    const course = {
      courseCode: lastCode || 'MH01',
      courseName: lastGroup ? `${lastName} (Nhóm ${lastGroup})` : lastName,
      room: String(cellValue(row.getCell(8)) || 'Phòng học').trim(),
      dayOfWeek: dayOfWeek,
      startPeriod: startPeriod,
      numPeriods: numPeriods,
      weekSchedule: weekSchedule,
      row: index
    };
    if (customStart) course.customStartTime = customStart;
    if (customEnd) course.customEndTime = customEnd;
    courses.push(course);
  });

  if (!courses.length) {
    throw new Error('Không đọc được dòng môn học nào. Hãy kiểm tra lại các cột Thứ (Cột E) và Tiết bắt đầu (Cột F) trong file Excel.');
  }

  return validateScheduleRows([...periodMap.values()].sort((a, b) => a.period - b.period), courses);
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
    'Thời gian học:'
  ]);
  sheet.getRow(1).font = { bold: true };
  sheet.columns.forEach(col => { col.width = 18; });

  const tietSheet = workbook.addWorksheet('TietHoc');
  tietSheet.addRow(['period', 'start', 'end']);
  tietSheet.getRow(1).font = { bold: true };

  return workbook;
}
