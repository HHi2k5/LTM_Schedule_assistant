const commonPages = ['overview', 'schedule', 'courses', 'settings', 'help'];
export const canImport = user => user?.role === 'TEACHER';
export const canSchedule = user => user?.role === 'ADMIN';
export const defaultPage = user => canSchedule(user) ? 'autoschedule' : 'schedule';
export function canVisit(user, page) {
  return (canImport(user) || canSchedule(user)) && (
    commonPages.includes(page) || (page === 'import' && canImport(user)) || (page === 'autoschedule' && canSchedule(user))
  );
}
