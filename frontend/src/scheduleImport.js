import { isMonday, minutes } from './schedule.js';

function positiveInteger(value, field, row) {
  if (value == null || String(value).trim() === '' || !Number.isInteger(Number(value)) || Number(value) < 1) throw new Error(`Dòng ${row}: ${field} phải là số nguyên dương.`);
  return Number(value);
}
function readTime(value, row) {
  if (value instanceof Date) value = `${String(value.getUTCHours()).padStart(2, '0')}:${String(value.getUTCMinutes()).padStart(2, '0')}`;
  else if (typeof value === 'number' && value >= 0 && value < 1) {
    const total = Math.round(value * 1440);
    value = `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
  }
  const match = String(value ?? '').trim().match(/^(\d{1,2}):([0-5]\d)(?::00)?$/);
  if (!match || Number(match[1]) > 23) throw new Error(`Sheet TietHoc, dòng ${row}: giờ phải theo dạng HH:mm.`);
  return `${match[1].padStart(2, '0')}:${match[2]}`;
}
export function validateScheduleRows(periodRows, courseRows) {
  const periods = periodRows.map(data => ({
    period: positiveInteger(data.period, 'số tiết', data.row), start: readTime(data.start, data.row), end: readTime(data.end, data.row),
  })).sort((a, b) => a.period - b.period);
  if (periods.length > 30) throw new Error('Bảng tiết chỉ được có tối đa 30 tiết/ngày.');
  const periodMap = new Map();
  for (const period of periods) {
    if (periodMap.has(period.period)) throw new Error(`Sheet TietHoc: tiết ${period.period} bị lặp.`);
    if (minutes(period.end) <= minutes(period.start)) throw new Error(`Tiết ${period.period}: giờ kết thúc phải sau giờ bắt đầu.`);
    if (periods.some(other => other.period < period.period && minutes(other.end) > minutes(period.start))) throw new Error(`Tiết ${period.period}: giờ tiết không theo thứ tự hoặc chồng lên tiết trước.`);
    periodMap.set(period.period, period);
  }
  const seen = new Set();
  const courses = courseRows.map(data => {
    const text = key => String(data[key] ?? '').trim();
    const course = {
      courseCode: text('courseCode'), courseName: text('courseName'), room: text('room'),
      dayOfWeek: positiveInteger(data.dayOfWeek, 'thứ', data.row),
      startPeriod: positiveInteger(data.startPeriod, 'tiết bắt đầu', data.row),
      numPeriods: positiveInteger(data.numPeriods, 'số tiết', data.row), weekSchedule: String(data.weekSchedule ?? ''),
    };
    if (!course.courseCode || !course.courseName || !course.room) throw new Error(`Sheet MonHoc, dòng ${data.row}: thiếu mã môn, tên môn hoặc phòng.`);
    if (course.dayOfWeek < 2 || course.dayOfWeek > 8) throw new Error(`Sheet MonHoc, dòng ${data.row}: thứ phải từ 2 đến 8 (8 là Chủ Nhật).`);
    if (course.numPeriods > periods.length) throw new Error(`Sheet MonHoc, dòng ${data.row}: số tiết vượt bảng giờ tiết.`);
    for (let p = course.startPeriod; p < course.startPeriod + course.numPeriods; p++) if (!periodMap.has(p)) throw new Error(`Sheet MonHoc, dòng ${data.row}: chưa khai báo giờ cho tiết ${p}.`);
    if (!/^[a-zA-Z0-9 -]{1,104}$/.test(course.weekSchedule) || !/[^- ]/.test(course.weekSchedule)) throw new Error(`Sheet MonHoc, dòng ${data.row}: chuỗi tuần cần 1–104 ký tự chữ/số (có học), dấu - hoặc khoảng trắng (nghỉ).`);
    const key = JSON.stringify(course);
    if (seen.has(key)) throw new Error(`Sheet MonHoc, dòng ${data.row}: dòng lịch bị lặp.`);
    seen.add(key);
    return course;
  });
  return { periods, courses };
}
export function buildUpload(parsed, metadata, knownStart) {
  for (const key of ['teacherId', 'teacherName', 'schoolId', 'schoolName']) if (!metadata[key]?.trim()) throw new Error('Điền đủ mã/tên giảng viên và mã/tên trường.');
  if (!isMonday(metadata.semesterStartDate)) throw new Error('Ngày bắt đầu tuần 1 phải là ngày Thứ Hai hợp lệ.');
  if (knownStart && knownStart !== metadata.semesterStartDate) throw new Error('Mốc tuần khác lịch hiện có. Hãy quy đổi chuỗi tuần trong file về mốc đã lưu để không làm lệch lịch trường khác.');
  if (!/^#[0-9a-fA-F]{6}$/.test(metadata.colorCode)) throw new Error('Màu trường không hợp lệ.');
  return {
    teacherId: metadata.teacherId.trim(), teacherName: metadata.teacherName.trim(), semesterStartDate: metadata.semesterStartDate,
    school: { schoolId: metadata.schoolId.trim(), schoolName: metadata.schoolName.trim(), colorCode: metadata.colorCode, periods: parsed.periods },
    courses: parsed.courses,
  };
}
