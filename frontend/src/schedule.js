import { getAccessToken, setAccessToken } from './authSession.js';
export const dayNames = ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ Nhật'];

export const minutes = value => {
  const [hour, minute, second] = String(value).split(':').map(Number);
  return hour * 60 + (minute || 0) + (second || 0) / 60;
};

export const clock = value => String(value ?? '').replace(/:00$/, match => String(value).length === 8 ? '' : match);

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
  const timeStamp = value => String(value).slice(0, 8).split(':').map(part => part.padStart(2, '0')).join('').padEnd(6, '0');
  const escape = value => String(value ?? '').replace(/\\/g, '\\\\').replace(/\r?\n/g, '\\n').replace(/,/g, '\\,').replace(/;/g, '\\;');
  const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z');
  return ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Campus//Teaching Schedule//VI', ...lessons.flatMap((lesson, index) => [
    'BEGIN:VEVENT', `UID:${encodeURIComponent(lesson.schoolId)}-${lesson.date}-${index}@campus.local`, `DTSTAMP:${stamp}`,
    `DTSTART;TZID=Asia/Ho_Chi_Minh:${lesson.date.replaceAll('-', '')}T${timeStamp(lesson.startTime)}`,
    `DTEND;TZID=Asia/Ho_Chi_Minh:${lesson.date.replaceAll('-', '')}T${timeStamp(lesson.endTime)}`,
    `SUMMARY:${escape(lesson.courseName)}`, `LOCATION:${escape(`${lesson.schoolName} ·${lesson.room}`)}`, 'END:VEVENT',
  ]), 'END:VCALENDAR', ''].join('\r\n');
}

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
  const baseUrl = (typeof import.meta !== 'undefined' && import.meta.env?.VITE_API_BASE_URL) 
    ? import.meta.env.VITE_API_BASE_URL.replace(/\/+$/, '') 
    : '';
  const fullUrl = `${baseUrl}/api/schedule${path.startsWith('/') ? path : `/${path}`}`;
  let response;
  try { 
    const headers = new Headers(options.headers);
    const token = getAccessToken();
    if (token && !['/auth/login', '/auth/register'].includes(path)) headers.set('Authorization', `Bearer ${token}`);
    response = await fetch(fullUrl, { ...options, headers });
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new Error('Không kết nối được máy chủ. Kiểm tra kết nối mạng rồi thử lại.');
  }
  const body = await response.text();
  let data;
  try { 
    data = body ? JSON.parse(body) : null; 
  } catch { 
    /* Tránh lỗi nếu server trả text rỗng */ 
  }
  if (!response.ok) {
    if (response.status === 401 && !['/auth/login', '/auth/register'].includes(path)) {
      setAccessToken('');
      globalThis.window?.dispatchEvent(new Event('campus-auth-expired'));
    }
    const detail = data?.message || data?.detail || data?.error;
    const messages = { 400: 'Dữ liệu không hợp lệ. Kiểm tra thông tin đã nhập.', 401: 'Phiên đăng nhập hết hạn hoặc thông tin đăng nhập không đúng.', 403: 'Tài khoản không có quyền thực hiện thao tác này.', 404: 'Không tìm thấy hồ sơ hoặc lịch được yêu cầu.', 409: 'Dữ liệu xung đột với thông tin hiện có. Kiểm tra lại trước khi gửi.', 413: 'File vượt giới hạn dung lượng cho phép.', 500: 'Máy chủ gặp lỗi. Thử lại sau; nếu vừa lưu lịch, hãy tải lại lịch để kiểm tra.' };
    const error = new Error(detail ? String(detail) : messages[response.status] || `Không thực hiện được yêu cầu (HTTP ${response.status}).`);
    error.status = response.status;
    throw error;
  }
  if (!data || typeof data !== 'object') throw new Error('Máy chủ trả dữ liệu không hợp lệ. Kiểm tra cấu hình địa chỉ API.');
  return data;
}
