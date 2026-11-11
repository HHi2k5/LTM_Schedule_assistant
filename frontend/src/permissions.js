export const isAdmin = user => user?.role === 'ADMIN';
// Trưởng bộ môn vẫn là giảng viên: có đủ các trang của giảng viên và thêm trang phân công.
export const isHead = user => user?.role === 'DEPT_HEAD';
export const canImport = user => user?.role === 'TEACHER' || isHead(user);
export const canSchedule = isAdmin;
export const canAssign = user => isAdmin(user) || isHead(user);
export const defaultPage = () => 'schedule';

const TEACHER_PAGES = ['overview', 'schedule', 'courses', 'import', 'settings', 'help'];
// Admin: lịch toàn khoa, xếp lớp vào phòng, quản lý danh mục và phân công (không có 'courses' - Môn dạy trong tuần)
const ADMIN_PAGES = ['schedule', 'autoschedule', 'departments', 'teachers', 'rooms', 'assignments', 'settings', 'help'];

export function canVisit(user, page) {
  if (isAdmin(user)) return ADMIN_PAGES.includes(page);
  if (isHead(user)) return TEACHER_PAGES.includes(page) || page === 'assignments';
  if (canImport(user)) return TEACHER_PAGES.includes(page);
  return false;
}

export function roleLabel(user) {
  if (isAdmin(user)) return 'Quản lý khoa';
  if (isHead(user)) return user?.departmentName ? `Trưởng bộ môn ${user.departmentName}` : 'Trưởng bộ môn';
  return 'Giảng viên';
}
