import ExcelJS from 'exceljs';
import { boardStateLabel, lecturerTypeLabel } from './facultyBoard.js';
import { clock } from './schedule.js';

export function facultyBoardWorkbook(board) {
  if (board?.filters?.view !== 'export' || !Array.isArray(board.entries) || !Array.isArray(board.assignments) || !board.context?.versionId)
    throw new Error('Cần tải dữ liệu xuất toàn khoa từ máy chủ.');
  const book = new ExcelJS.Workbook(); const c = board.context;
  const info = book.addWorksheet('ThongTin');
  info.addRows([['Bản lịch', c.name], ['Trường', `${c.schoolName} (${c.schoolCode})`], ['Khoa', c.departmentName], ['Học kỳ', c.semester],
    ['ID phiên bản', c.versionId], ['Phiên bản', c.versionNumber], ['Trạng thái', boardStateLabel(c.state)], ['Tuần', board.filters.week], ['Từ ngày', board.startDate], ['Đến ngày', board.endDate],
    ['Kiểm tra trùng lúc', c.checkedAt], ['Bộ lọc', JSON.stringify(board.filters)], ['Số buổi theo bộ lọc', board.stats.sessions], ['Tiết trong tuần theo bộ lọc', board.stats.weekPeriods],
    ['Khối lượng', 'Cột tổng kỳ là toàn bộ nhu cầu của lớp/nhóm được chọn; cột tuần chỉ tính buổi khớp bộ lọc.'],
    ['Cảnh báo dữ liệu', board.warnings.join(' · ')]]);
  info.getColumn(1).width = 32; info.getColumn(2).width = 100;
  const calendar = book.addWorksheet('LichToanKhoa');
  calendar.addRow(['ID buổi', 'ID lớp/nhóm', 'Ngày', 'Giờ bắt đầu', 'Giờ kết thúc', 'Tiết bắt đầu', 'Số tiết', 'Mã môn', 'Tên môn', 'Khóa', 'Lớp/nhóm', 'LT/TH', 'Mã GV', 'Tên GV', 'Loại GV', 'Phòng', 'Sĩ số', 'Xung đột', 'Trạng thái khóa']);
  board.entries.forEach(e => calendar.addRow([e.id, e.classId, e.date, clock(e.startTime), clock(e.endTime), e.startPeriod, e.numPeriods, e.courseCode, e.courseName, e.cohort, e.classGroup,
    e.sessionType === 'PRACTICE' ? 'TH' : 'LT', e.lecturerCode, e.lecturerName, lecturerTypeLabel(e.lecturerType), e.roomCode, e.studentCount, e.conflicts.map(i => i.message).join(' · '), e.locked ? 'Đã khóa' : 'Đang mở']));
  const assignments = book.addWorksheet('PhanCongToanKhoa');
  assignments.addRow(['ID lớp/nhóm', 'Mã môn', 'Tên môn', 'Khóa', 'Lớp/nhóm', 'LT/TH', 'Mã GV', 'Tên GV', 'Loại GV', 'Tiết yêu cầu tổng kỳ', 'Tiết đã xếp tổng kỳ', 'Tiết thiếu tổng kỳ', 'Tiết tuần theo bộ lọc', 'Buổi tuần theo bộ lọc', 'Buổi trùng trong tuần']);
  board.assignments.forEach(a => assignments.addRow([a.classId, a.courseCode, a.courseName, a.cohort, a.classGroup, a.sessionType === 'PRACTICE' ? 'TH' : 'LT', a.lecturerCode, a.lecturerName,
    lecturerTypeLabel(a.lecturerType), a.requiredPeriods, a.scheduledPeriods, a.missingPeriods, a.weekPeriods, a.weekSessions, a.conflictingEntries]));
  for (const sheet of [calendar, assignments]) {
    sheet.getRow(1).font = { bold: true }; sheet.views = [{ state: 'frozen', ySplit: 1 }];
    sheet.columns.forEach(column => { column.width = 23; });
    sheet.autoFilter = { from: { row: 1, column: 1 }, to: { row: sheet.rowCount, column: sheet.columnCount } };
  }
  return { workbook: book, filename: `toan-khoa-${String(c.schoolCode).replace(/[^a-zA-Z0-9_-]/g, '_')}-v${c.versionNumber}-${c.state.toLowerCase()}-tuan-${board.filters.week}.xlsx` };
}
