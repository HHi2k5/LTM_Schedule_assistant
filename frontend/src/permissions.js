export const canImport = user => user?.role === 'TEACHER';
export const canSchedule = user => user?.role === 'ADMIN';
export const defaultPage = user => canSchedule(user) ? 'schedule' : 'schedule';

export function canVisit(user, page) {
  if (canSchedule(user)) {
    // Admin chỉ có: Lịch giảng dạy (toàn khoa), Xếp lịch của khoa, Cài đặt, Trợ giúp (Bỏ 'courses' - Môn dạy trong tuần)
    return ['schedule', 'autoschedule', 'settings', 'help'].includes(page);
  }
  if (canImport(user)) {
    // Giảng viên giữ nguyên đầy đủ các trang như bình thường
    return ['overview', 'schedule', 'courses', 'import', 'settings', 'help'].includes(page);
  }
  return false;
}