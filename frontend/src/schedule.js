export const dayNames = ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ Nhật'];
export const minutes = value => {
  const [hour, minute] = String(value).split(':').map(Number);
  return hour * 60 + minute;
};
export const clock = value => String(value).slice(0, 5);
export const dateLabel = value => value ? value.split('-').reverse().join('/') : '';
export function addDays(value, days) {
  const date = new Date(`${value}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}
export function isMonday(value) {
  const date = new Date(`${value}T12:00:00Z`);
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(date.valueOf()) && date.toISOString().slice(0, 10) === value && date.getUTCDay() === 1;
}
export function currentWeek(start) {
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  return Math.max(1, Math.floor((new Date(`${today}T12:00:00Z`) - new Date(`${start}T12:00:00Z`)) / 604800000) + 1);
}

export function calendarFile(lessons) {
  const escape = value => String(value ?? '').replace(/\\/g, '\\\\').replace(/\r?\n/g, '\\n').replace(/,/g, '\\,').replace(/;/g, '\\;');
  const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z');
  return ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Campus//Teaching Schedule//VI', ...lessons.flatMap((lesson, index) => [
    'BEGIN:VEVENT', `UID:${encodeURIComponent(lesson.schoolId)}-${lesson.date}-${index}@campus.local`, `DTSTAMP:${stamp}`,
    `DTSTART;TZID=Asia/Ho_Chi_Minh:${lesson.date.replaceAll('-', '')}T${clock(lesson.startTime).replace(':', '')}00`,
    `DTEND;TZID=Asia/Ho_Chi_Minh:${lesson.date.replaceAll('-', '')}T${clock(lesson.endTime).replace(':', '')}00`,
    `SUMMARY:${escape(lesson.courseName)}`, `LOCATION:${escape(`${lesson.schoolName} · ${lesson.room}`)}`, 'END:VEVENT',
  ]), 'END:VCALENDAR', ''].join('\r\n');
}

// Give every overlapping lesson its own lane, including overlap within one school.
export function layoutLessons(lessons) {
  const result = [];
  for (let day = 2; day <= 8; day++) {
    const sorted = lessons.filter(l => l.dayOfWeek === day).sort((a, b) => minutes(a.startTime) - minutes(b.startTime));
    let group = [], groupEnd = -1;
    const flush = () => {
      const ends = [];
      const placed = group.map(lesson => {
        let lane = ends.findIndex(end => end <= minutes(lesson.startTime));
        if (lane < 0) lane = ends.length;
        ends[lane] = minutes(lesson.endTime);
        return { ...lesson, lane };
      });
      result.push(...placed.map(lesson => ({ ...lesson, lanes: ends.length })));
    };
    for (const lesson of sorted) {
      if (minutes(lesson.startTime) >= groupEnd && group.length) { flush(); group = []; }
      group.push(lesson);
      groupEnd = group.length === 1 ? minutes(lesson.endTime) : Math.max(groupEnd, minutes(lesson.endTime));
    }
    if (group.length) flush();
  }
  return result;
}

export async function api(path, options = {}) {
  let response;
  try { response = await fetch(`${import.meta.env?.VITE_API_BASE_URL || ''}/api/schedule${path}`, options); }
  catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new Error('Không kết nối được máy chủ. Kiểm tra backend đang chạy ở cổng 8080.');
  }
  const body = await response.text();
  let data;
  try { data = body ? JSON.parse(body) : null; } catch { /* Handle non-JSON server errors below. */ }
  if (!response.ok) {
    const detail = data?.message || data?.detail || data?.error;
    throw new Error(detail ? String(detail) : `Không tải/lưu được lịch (HTTP ${response.status}). Kiểm tra máy chủ và mã giảng viên.`);
  }
  if (!data || typeof data !== 'object') throw new Error('Máy chủ trả dữ liệu không hợp lệ. Kiểm tra cấu hình địa chỉ API.');
  return data;
}
