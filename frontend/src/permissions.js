export const isAdmin = user => user?.role === 'ADMIN';
// Trưởng bộ môn vẫn là giảng viên: có đủ các trang của giảng viên và thêm trang phân công + quản lý giảng viên bộ môn.
export const isHead = user => user?.role === 'DEPT_HEAD';
export const canImport = user => user?.role === 'TEACHER' || isHead(user);
export const canSchedule = isAdmin;
export const canAssign = user => isHead(user);
export const canManageTeachers = user => isAdmin(user) || isHead(user);
export const defaultPage = () => 'schedule';

const TEACHER_PAGES = ['overview', 'schedule', 'courses', 'import', 'settings', 'help'];
// Admin: lịch toàn khoa, xếp lớp vào phòng, quản lý danh mục và khung giờ (không làm phân công)
const ADMIN_PAGES = ['schedule', 'autoschedule', 'periods', 'departments', 'teachers', 'rooms', 'settings', 'help'];

export function canVisit(user, page) {
  if (isAdmin(user)) return ADMIN_PAGES.includes(page);
  if (isHead(user)) return TEACHER_PAGES.includes(page) || page === 'assignments' || page === 'teachers';
  if (canImport(user)) return TEACHER_PAGES.includes(page);
  return false;
}

export function roleLabel(user) {
  if (isAdmin(user)) return 'Quản lý khoa';
  if (isHead(user)) return user?.departmentName ? `Trưởng bộ môn ${user.departmentName}` : 'Trưởng bộ môn';
  return 'Giảng viên';
}
