import ExcelJS from 'exceljs';

export const planHeaders = [
  'STT', 'Mã môn', 'Tên học phần', 'Loại lớp (LT / TH)', 'Sĩ số', 'Khóa',
  'Số lớp mở', 'Tuần bắt đầu — từ ngày', 'Tuần kết thúc — đến ngày',
  'Số tín chỉ', 'Số tiết'
];
export const teacherHeaders = ['Mã môn', 'Tên học phần', 'Khóa', 'Mã GV', 'Họ tên giảng viên', 'Trọng số LT', 'Trọng số TH'];
const excelDate = value => value ? `${value.slice(8, 10)}/${value.slice(5, 7)}/${value.slice(0, 4)}` : '';
const text = value => String(value ?? '').trim();

function workbook(name, headers, rows) {
  const book = new ExcelJS.Workbook();
  const sheet = book.addWorksheet(name);
  sheet.addRow(headers);
  rows.forEach(row => sheet.addRow(row));
  sheet.getRow(1).font = { bold: true };
  sheet.views = [{ state: 'frozen', ySplit: 1 }];
  sheet.columns.forEach((column, index) => { column.width = Math.max(16, Math.min(32, headers[index].length + 3)); });
  return book;
}

export function planWorkbook(plans) {
  return workbook('KeHoachMoLop', planHeaders, plans.map((p, i) => [
    i + 1,
    text(p.courseCode),
    text(p.courseName),
    text(p.classType || 'LT'),
    Number(p.classSize),
    text(p.cohort),
    Number(p.numClasses),
    excelDate(p.studyStartDate),
    excelDate(p.studyEndDate),
    Number(p.credits),
    Number(p.totalPeriods ?? p.theoryPeriods ?? 30)
  ]));
}

export function teacherWorkbook(teachers) {
  return workbook('DanhSachGiangVien', teacherHeaders, teachers.map(t => [text(t.courseCode), text(t.courseName),
    text(t.cohort), text(t.teacherId), text(t.teacherName), Number(t.theoryWeight), Number(t.practiceWeight)]));
}