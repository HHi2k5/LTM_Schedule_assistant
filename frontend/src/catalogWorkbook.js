import ExcelJS from 'exceljs';

export const catalogColumns = {
  lecturers: [ ['lecturerCode', 'Mã GV'], ['fullName', 'Họ tên'], ['departmentCode', 'Mã khoa'],
    ['lecturerType', 'Loại GV'], ['accountTeacherId', 'Mã tài khoản'], ['active', 'Trạng thái'], ['existingScheduleTeacherId', 'Mã hồ sơ lịch cũ'] ],
  rooms: [ ['roomCode', 'Mã phòng'], ['roomName', 'Tên phòng'], ['roomType', 'Loại phòng'],
    ['capacity', 'Sức chứa'], ['building', 'Tòa nhà'], ['active', 'Trạng thái'] ],
  assignments: [ ['lecturerCode', 'Mã GV'], ['courseCode', 'Mã môn'], ['courseName', 'Tên môn'],
    ['cohort', 'Khóa'], ['theoryWeight', 'Trọng số LT'], ['practiceWeight', 'Trọng số TH'], ['active', 'Trạng thái'] ],
};

export function catalogWorkbook(kind, rows = []) {
  const columns = catalogColumns[kind];
  if (!columns) throw new Error('Danh mục không có mẫu Excel.');
  const book = new ExcelJS.Workbook();
  const sheet = book.addWorksheet('DanhMuc');
  sheet.addRow(columns.map(([, title]) => title));
  rows.forEach(row => sheet.addRow(columns.map(([key]) => {
    const value = row[key];
    if (typeof value === 'boolean') return value ? 'TRUE' : 'FALSE';
    // Keep text as literal cell values, including names starting with =, +, - or @.
    return value ?? '';
  })));
  sheet.getRow(1).font = { bold: true, color: { argb: 'FFFFFFFF' } };
  sheet.getRow(1).fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF2563EB' } };
  sheet.columns = columns.map(([key]) => ({ width: ['fullName', 'courseName', 'roomName'].includes(key) ? 32 : 22 }));
  sheet.views = [{ state: 'frozen', ySplit: 1 }];
  return book;
}

export async function saveCatalogTemplate(kind) {
  const buffer = await catalogWorkbook(kind).xlsx.writeBuffer();
  const blob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = `danh-muc-${kind}.xlsx`; anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export async function legacyLecturerFile(candidate) {
  const rows = candidate.lecturers.map(row => ({ ...row }));
  const buffer = await catalogWorkbook('lecturers', rows).xlsx.writeBuffer();
  return new File([buffer], `giang-vien-cu-${candidate.schoolCode}.xlsx`, { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
}
