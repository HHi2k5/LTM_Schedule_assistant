export const boardDefaults = { week: 1, view: 'calendar', groupBy: 'lecturer', page: 0, lecturerId: '', courseCode: '', cohort: '', classId: '', roomCode: '', sessionType: '', lecturerType: '', conflictsOnly: false, q: '' };
export const boardStateLabel = state => ({ DRAFT: 'Bản nháp', CONFIRMED: 'Đang áp dụng', HISTORICAL: 'Phiên bản lịch sử' })[state] || state;
export const lecturerTypeLabel = type => ({ STAFF: 'Cơ hữu', VISITING: 'Thỉnh giảng', UNKNOWN: 'Chưa lưu loại GV' })[type] || 'Chưa phân công';

export function restoreBoard(raw) {
  try {
    const saved = JSON.parse(raw); const result = { ...boardDefaults };
    if (!saved || typeof saved !== 'object') return result;
    for (const key of ['lecturerId', 'courseCode', 'cohort', 'classId', 'roomCode', 'q']) if (typeof saved[key] === 'string' && saved[key].length <= (key === 'classId' ? 255 : key === 'q' ? 200 : 50)) result[key] = saved[key];
    if (result.lecturerId && !/^[1-9]\d*$/.test(result.lecturerId)) result.lecturerId = '';
    if (Number.isInteger(saved.week) && saved.week >= 1 && saved.week <= 100) result.week = saved.week;
    if (['calendar', 'assignments', 'sessions'].includes(saved.view)) result.view = saved.view;
    if (['lecturer', 'class'].includes(saved.groupBy)) result.groupBy = saved.groupBy;
    if (['THEORY', 'PRACTICE'].includes(saved.sessionType)) result.sessionType = saved.sessionType;
    if (['STAFF', 'VISITING', 'UNKNOWN'].includes(saved.lecturerType)) result.lecturerType = saved.lecturerType;
    result.conflictsOnly = saved.conflictsOnly === true;
    return result;
  } catch { return { ...boardDefaults }; }
}

export function boardQuery(filters, view = filters.view) {
  const params = new URLSearchParams();
  for (const key of Object.keys(boardDefaults)) {
    const value = key === 'view' ? view : key === 'page' && view === 'export' ? 0 : filters[key];
    if (value !== '' && value !== false && value != null) params.set(key, String(value));
  }
  return params.toString();
}

export function validateBoard(data, planId, versionId, filters) {
  if (data?.context?.planId !== planId || data.context.versionId !== versionId || data.filters?.week !== filters.week
      || data.filters?.view !== filters.view || !['groups', 'assignments', 'entries', 'warnings'].every(key => Array.isArray(data[key]))
      || !data.options || !['lecturers', 'courses', 'cohorts', 'classes', 'rooms'].every(key => Array.isArray(data.options[key]))
      || !data.stats || !Number.isInteger(data.pageCount) || data.pageCount < 0)
    throw new Error('Dữ liệu bảng toàn khoa không khớp phiên bản hoặc bộ lọc. Thử tải lại bảng.');
  for (const key of Object.keys(boardDefaults)) {
    const expected = key === 'page' && filters.view === 'export' ? 0 : filters[key] ?? boardDefaults[key];
    const normalize = value => value == null || value === '' ? '' : String(value).trim();
    if (normalize(data.filters[key]) !== normalize(expected)) throw new Error('Bộ lọc trả về không khớp yêu cầu. Thử tải lại bảng toàn khoa.');
  }
  return data;
}
