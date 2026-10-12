export const courses = [
  { id: 'INT14189', name: 'Quản lý dự án phần mềm', group: '02', room: '601-NT', students: 42, color: 'green' },
  { id: 'INT14149', name: 'IoT và ứng dụng', group: '08', room: '501-NT', students: 35, color: 'blue' },
  { id: 'INT14151', name: 'Phát triển các hệ thống thông minh', group: '05', room: '601-NT', students: 38, color: 'purple' },
  { id: 'INT1313', name: 'Lập trình mạng', group: '03', room: '402-A3', students: 40, color: 'orange' },
];
export const sessions = [
  { id: 1, course: 0, day: 0, start: 9, end: 11, type: 'regular' },
  { id: 2, course: 1, day: 1, start: 13, end: 15, type: 'regular' },
  { id: 3, course: 1, day: 2, start: 7, end: 9, type: 'regular' },
  { id: 4, course: 3, day: 2, start: 10, end: 12, type: 'regular' },
  { id: 5, course: 0, day: 3, start: 13, end: 15, type: 'makeup' },
  { id: 6, course: 2, day: 4, start: 7, end: 9, type: 'regular' },
  { id: 7, course: 3, day: 4, start: 14, end: 16, type: 'regular' },
];
export const dayNames = ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ Nhật'];
export function weekDates(offset = 0) { return dayNames.map((_, i) => new Date(2026, 9, 5 + offset * 7 + i, 12)); }
export function shortDate(date) { return date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' }); }
export function fullDate(date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`; }
export function time(hour) { return `${String(hour).padStart(2, '0')}:00`; }
