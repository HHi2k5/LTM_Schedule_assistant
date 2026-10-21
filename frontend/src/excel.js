import ExcelJS from 'exceljs';
import { validateScheduleRows } from './scheduleImport.js';
export { buildUpload } from './scheduleImport.js';

const courseKeys = ['courseCode', 'courseName', 'room', 'dayOfWeek', 'startPeriod', 'numPeriods', 'weekSchedule'];
const periodKeys = ['period', 'start', 'end'];
const normalize = value => String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
const aliases = {
  courseCode: ['ma mon', 'ma lop'], courseName: ['ten mon', 'mon hoc'], room: ['phong', 'phong hoc'],
  dayOfWeek: ['thu'], startPeriod: ['tiet bat dau'], numPeriods: ['so tiet'], weekSchedule: ['lich tuan', 'chuoi tuan'],
  period: ['tiet'], start: ['gio bat dau'], end: ['gio ket thuc'],
};
function cellValue(cell) {
  const value = cell.value;
  if (value && typeof value === 'object' && !(value instanceof Date)) {
    if ('formula' in value || 'sharedFormula' in value) throw new Error(`Ô ${cell.address}: hãy thay công thức bằng giá trị trước khi nhập.`);
    if (value.richText) return value.richText.map(part => part.text).join('');
    if (value.text != null) return value.text;
    throw new Error(`Ô ${cell.address}: dữ liệu ô không được hỗ trợ.`);
  }
  return value;
}
function rows(sheet, keys) {
  if (!sheet) throw new Error(`Thiếu sheet ${keys === courseKeys ? 'MonHoc' : 'TietHoc'}. Hãy dùng file mẫu.`);
  if (sheet.rowCount > 5001 || sheet.columnCount > 50) throw new Error(`Sheet ${sheet.name}: tối đa 5.000 dòng dữ liệu và 50 cột.`);
  const columns = {};
  sheet.getRow(1).eachCell((cell, index) => {
    const header = normalize(cellValue(cell));
    const key = keys.find(key => [key, ...(aliases[key] || [])].some(alias => normalize(alias) === header));
    if (key) {
      if (columns[key]) throw new Error(`Sheet ${sheet.name}: cột ${key} bị lặp.`);
      columns[key] = index;
    }
  });
  for (const key of keys) if (!columns[key]) throw new Error(`Sheet ${sheet.name}, dòng 1: thiếu cột ${key}.`);
  const result = [];
  sheet.eachRow((row, index) => {
    if (index === 1) return;
    const data = Object.fromEntries(keys.map(key => [key, cellValue(row.getCell(columns[key]))]));
    if (Object.values(data).every(value => value == null || value === '')) return;
    result.push({ ...data, row: index });
  });
  if (!result.length) throw new Error(`Sheet ${sheet.name}: chưa có dữ liệu.`);
  return result;
}
export async function parseWorkbook(buffer) {
  const workbook = new ExcelJS.Workbook();
  try { await workbook.xlsx.load(buffer); }
  catch { throw new Error('Không đọc được file Excel. Hãy dùng file .xlsx không có mật khẩu theo mẫu.'); }
  return validateScheduleRows(rows(workbook.getWorksheet('TietHoc'), periodKeys), rows(workbook.getWorksheet('MonHoc'), courseKeys));
}
export function firstWeek(courses) {
  return Math.min(...courses.map(course => course.weekSchedule.search(/[^- ]/) + 1));
}
export function templateWorkbook() {
  const workbook = new ExcelJS.Workbook();
  const courses = workbook.addWorksheet('MonHoc');
  courses.addRow(courseKeys);
  courses.addRow(['INT1313', 'Lập trình mạng', '402-A3', 2, 1, 2, 'xxxx-xxxx']);
  const periods = workbook.addWorksheet('TietHoc');
  periods.addRow(periodKeys);
  periods.addRow([1, '07:00', '07:50']);
  periods.addRow([2, '08:00', '08:50']);
  periods.addRow([3, '09:00', '09:50']);
  for (const sheet of [courses, periods]) {
    sheet.getRow(1).font = { bold: true };
    sheet.views = [{ state: 'frozen', ySplit: 1 }];
    sheet.columns.forEach(column => { column.width = 24; });
  }
  const guide = workbook.addWorksheet('HuongDan');
  [
    'Thay các dòng ví dụ bằng lịch của bạn. Giữ tên sheet và tiêu đề cột.',
    'MonHoc: mỗi dòng là một khung dạy. dayOfWeek: 2 = Thứ Hai, ..., 8 = Chủ Nhật.',
    'startPeriod: tiết đầu. numPeriods: số tiết liên tiếp. Khai báo đủ các tiết trong TietHoc.',
    'weekSchedule: ký tự thứ nhất ứng với tuần 1; x hoặc 1 = học, - = nghỉ. Ví dụ xxxx-xxxx nghỉ tuần 5.',
    'Định dạng cột weekSchedule là văn bản, giữ nguyên dấu - và mọi vị trí tuần.',
    'TietHoc: giờ thực tế của trường, dạng HH:mm. Có thể bắt đầu 07:10 hoặc 07:20.',
    'Dùng chung mốc tuần 1 của giảng viên cho tất cả trường; tự quy đổi tuần trước khi nhập.',
    'Nhập lại cùng mã trường thay toàn bộ lịch và bảng tiết của trường đó.',
    'Không dùng công thức, ô gộp hoặc mật khẩu. Tối đa 5 MB và 5.000 dòng mỗi sheet.',
  ].forEach(line => guide.addRow([line]));
  guide.getColumn(1).width = 120;
  return workbook;
}
