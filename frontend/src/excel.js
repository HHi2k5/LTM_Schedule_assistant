import ExcelJS from 'exceljs';
import { validateScheduleRows } from './scheduleImport.js';
import { validSemesterStart } from './workflow.js';
export { buildUpload } from './scheduleImport.js';

function cellValue(cell) {
  const value = cell?.value;
  if (value && typeof value === 'object' && !(value instanceof Date)) {
    if ('formula' in value || 'sharedFormula' in value) return cell.result ?? '';
    if (value.richText) return value.richText.map(part => part.text).join('');
    if (value.text != null) return value.text;
  }
  return value;
}

function studyWeeks(raw, anchor, day) {
  const text = String(raw ?? '').trim();
  if (!text.includes('/')) return text;
  if (!validSemesterStart(anchor)) throw new Error('Chọn mốc Thứ Hai trong năm 2000–2100 trước khi đọc khoảng ngày.');
  const matches = [...text.matchAll(/(\d{1,2})\/(\d{1,2})\/(\d{2,4})/g)];
  if (matches.length !== 2) throw new Error('Cần khoảng ngày dd/MM/yyyy - dd/MM/yyyy.');
  const dates = matches.map(([, d, m, y]) => {
    const year = Number(y) < 100 ? Number(y) + 2000 : Number(y);
    const date = new Date(Date.UTC(year, Number(m) - 1, Number(d)));
    if (date.getUTCFullYear() !== year || date.getUTCMonth() !== Number(m) - 1 || date.getUTCDate() !== Number(d)) {
      throw new Error('Khoảng học có ngày không tồn tại.');
    }
    return date.valueOf();
  });
  const start = new Date(`${anchor}T00:00:00Z`).valueOf();
  if (dates[0] < start || dates[1] >= start + 700 * 86400000 || dates[1] < dates[0]) {
    throw new Error('Khoảng ngày phải nằm trong 100 tuần tính từ mốc tuần 1.');
  }
  const weeks = Array.from({ length: 100 }, (_, week) => {
    const date = start + (week * 7 + day - 2) * 86400000;
    return date >= dates[0] && date <= dates[1] ? '1' : '-';
  }).join('').replace(/-+$/, '');
  if (!weeks) throw new Error('Khoảng ngày không có buổi học theo thứ đã chọn.');
  return weeks;
}

export async function parseWorkbook(buffer, semesterStartDate) {
  const workbook = new ExcelJS.Workbook();
  try { await workbook.xlsx.load(buffer); }
  catch { throw new Error('Không đọc được file Excel. Hãy dùng file .xlsx không có mật khẩu.'); }
  const sheet = workbook.getWorksheet('MonHoc') || workbook.worksheets[0];
  const times = workbook.getWorksheet('TietHoc');
  if (!times) throw new Error('Thiếu sheet TietHoc (period, start, end). Cần bảng giờ thực của trường.');
  if (!sheet || sheet === times || sheet.getRow(1).cellCount < 9) throw new Error('Sheet MonHoc cần đủ 9 cột theo mẫu tải về.');
  const periods = [];
  times.eachRow((row, index) => {
    if (index === 1 || !row.values.some(value => value != null && String(value).trim())) return;
    periods.push({ period: cellValue(row.getCell(1)), start: cellValue(row.getCell(2)), end: cellValue(row.getCell(3)), row: index });
  });
  let code = '', name = '', group = '01';
  const courses = [];
  sheet.eachRow((row, index) => {
    if (index === 1 || !row.values.some(value => value != null && String(value).trim())) return;
    try {
      const text = column => String(cellValue(row.getCell(column)) ?? '').trim();
      if (text(1)) code = text(1);
      if (text(2)) name = text(2);
      if (text(3)) group = text(3);
      const rawDay = text(5).toUpperCase();
      const day = /^(CN|CHỦ NHẬT|8)$/.test(rawDay) ? 8 : /^(?:THỨ\s*)?[2-7]$/.test(rawDay) ? Number(rawDay.slice(-1)) : NaN;
      if (!Number.isInteger(day)) throw new Error('Thứ phải từ 2 đến 8 hoặc CN.');
      if (!code || !name || !text(6) || !text(7)) throw new Error('Thiếu mã/tên môn hoặc tiết bắt đầu/số tiết.');
      courses.push({ courseCode: code, courseName: `${name} (Nhóm ${group})`, room: text(8), dayOfWeek: day,
        startPeriod: text(6), numPeriods: text(7), weekSchedule: studyWeeks(text(9), semesterStartDate, day), row: index });
    } catch (error) { throw new Error(`Sheet MonHoc, dòng ${index}: ${error.message}`); }
  });
  return validateScheduleRows(periods, courses);
}

export function firstWeek(courses) {
  const weeks = courses.map(course => course.weekSchedule.search(/[^- ]/) + 1).filter(week => week > 0);
  return weeks.length ? Math.min(...weeks) : 1;
}

export function templateWorkbook() {
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet('MonHoc');
  sheet.addRow(['Mã MH', 'Tên môn học', 'Nhóm tổ', 'Số tín chỉ', 'Thứ', 'Tiết bắt đầu', 'Số tiết', 'Phòng', 'Thời gian học']);
  const times = workbook.addWorksheet('TietHoc');
  times.addRow(['period', 'start', 'end']);
  times.getCell('A1').note = 'Điền số tiết thực tế (1–30). Không để bảng giờ trống.';
  times.getCell('B1').note = 'Điền giờ bắt đầu thực của trường theo HH:mm, ví dụ 07:20.';
  times.getCell('C1').note = 'Điền giờ kết thúc thực của trường theo HH:mm, ví dụ 08:10.';
  workbook.worksheets.forEach(worksheet => {
    worksheet.getRow(1).font = { bold: true };
    worksheet.columns.forEach(column => { column.width = 22; });
    worksheet.views = [{ state: 'frozen', ySplit: 1 }];
  });
  return workbook;
}
