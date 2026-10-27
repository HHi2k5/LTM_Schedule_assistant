import { minutes } from './schedule.js';
import { validateSchool } from './workflow.js';

function positiveInteger(value, field, row) {
  if (!/^\d+$/.test(String(value ?? '').trim()) || !Number.isSafeInteger(Number(value)) || Number(value) < 1) {
    throw new Error(`Dòng ${row}: ${field} phải là số nguyên dương.`);
  }
  return Number(value);
}

function readTime(value, row) {
  if (value instanceof Date) {
    value = `${String(value.getUTCHours()).padStart(2, '0')}:${String(value.getUTCMinutes()).padStart(2, '0')}:${String(value.getUTCSeconds()).padStart(2, '0')}`;
  } else if (typeof value === 'number' && value >= 0 && value < 1) {
    const total = Math.round(value * 86400) % 86400;
    value = `${String(Math.floor(total / 3600)).padStart(2, '0')}:${String(Math.floor(total / 60) % 60).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
  }
  const match = String(value ?? '').trim().match(/^(\d{1,2}):([0-5]\d)(?::([0-5]\d))?$/);
  if (!match || Number(match[1]) > 23) throw new Error(`Sheet TietHoc, dòng ${row}: giờ phải theo dạng HH:mm.`);
  return `${match[1].padStart(2, '0')}:${match[2]}${match[3] && match[3] !== '00' ? `:${match[3]}` : ''}`;
}

export function validateScheduleRows(periodRows, courseRows) {
  if (!periodRows.length || periodRows.length > 30) throw new Error('Sheet TietHoc cần từ 1 đến 30 tiết thực tế.');
  if (!courseRows.length || courseRows.length > 5000) throw new Error('Sheet MonHoc cần từ 1 đến 5000 dòng lịch.');
  const periods = periodRows.map(data => ({
    period: positiveInteger(data.period, 'số tiết', data.row), 
    start: readTime(data.start, data.row), 
    end: readTime(data.end, data.row),
  })).sort((a, b) => a.period - b.period);

  if (periods.length > 30) throw new Error('Bảng tiết chỉ được có tối đa 30 tiết/ngày.');
  
  const periodMap = new Map();
  for (const period of periods) {
    if (period.period > 30) throw new Error('Sheet TietHoc: số tiết phải trong khoảng 1–30.');
    if (periodMap.has(period.period)) throw new Error(`Sheet TietHoc: tiết ${period.period} bị lặp.`);
    if (minutes(period.end) <= minutes(period.start)) throw new Error(`Tiết ${period.period}: giờ kết thúc phải sau giờ bắt đầu.`);
    if (periods.some(other => other.period < period.period && minutes(other.end) > minutes(period.start))) {
      throw new Error(`Tiết ${period.period}: giờ tiết không theo thứ tự hoặc chồng lên tiết trước.`);
    }
    periodMap.set(period.period, period);
  }

  const seen = new Set();
  const courses = courseRows.map(data => {
    const text = key => String(data[key] ?? '').trim();
    const course = {
      courseCode: text('courseCode'), 
      courseName: text('courseName'), 
      room: text('room'),
      dayOfWeek: positiveInteger(data.dayOfWeek, 'thứ', data.row),
      startPeriod: positiveInteger(data.startPeriod, 'tiết bắt đầu', data.row),
      numPeriods: positiveInteger(data.numPeriods, 'số tiết', data.row), 
      weekSchedule: String(data.weekSchedule ?? ''),
    };
    if (!course.courseCode || !course.courseName || !course.room) throw new Error(`Sheet MonHoc, dòng ${data.row}: thiếu mã môn, tên môn hoặc phòng.`);
    if (course.courseCode.length > 50 || course.courseName.length > 255 || course.room.length > 100) throw new Error(`Sheet MonHoc, dòng ${data.row}: mã môn/tên môn/phòng vượt giới hạn 50/255/100 ký tự.`);
    if (course.dayOfWeek < 2 || course.dayOfWeek > 8) throw new Error(`Sheet MonHoc, dòng ${data.row}: thứ phải từ 2 đến 8 (8 là Chủ Nhật).`);
    if (course.numPeriods > periods.length) throw new Error(`Sheet MonHoc, dòng ${data.row}: số tiết vượt bảng giờ tiết.`);
    for (let p = course.startPeriod; p < course.startPeriod + course.numPeriods; p++) {
      if (!periodMap.has(p)) throw new Error(`Sheet MonHoc, dòng ${data.row}: chưa khai báo giờ cho tiết ${p}.`);
    }
    if (!/^[a-zA-Z0-9 -]{1,100}$/.test(course.weekSchedule) || !/[^- ]/.test(course.weekSchedule)) {
      throw new Error(`Sheet MonHoc, dòng ${data.row}: chuỗi tuần cần 1–100 ký tự chữ/số (có học), dấu - hoặc khoảng trắng (nghỉ).`);
    }
    const key = JSON.stringify(course);
    if (seen.has(key)) throw new Error(`Sheet MonHoc, dòng ${data.row}: dòng lịch bị lặp.`);
    seen.add(key);
    return course;
  });

  return { periods, courses };
}

export function buildUpload(parsed, metadata, knownStart) {
  const teacherId = (metadata?.teacherId || '').trim();
  const schoolId = (metadata?.schoolId || '').trim();
  const schoolName = (metadata?.schoolName || '').trim();
  const teacherName = (metadata?.teacherName || '').trim() || teacherId;

  if (!teacherId || !schoolId || !schoolName) {
    throw new Error('Điền đủ mã/tên giảng viên và mã/tên trường.');
  }
  validateSchool(schoolId, schoolName, metadata.semesterStartDate);
  if (knownStart && knownStart !== metadata.semesterStartDate) {
    throw new Error('Mốc tuần khác lịch hiện có. Hãy quy đổi chuỗi tuần trong file về mốc đã lưu để không làm lệch lịch trường khác.');
  }

  const colorCode = /^#[0-9a-fA-F]{6}$/.test(metadata.colorCode || '') ? metadata.colorCode : '#2563eb';

  return {
    teacherId,
    teacherName,
    semesterStartDate: metadata.semesterStartDate,
    school: { 
      schoolId, 
      schoolName, 
      colorCode, 
      periods: parsed.periods 
    },
    courses: parsed.courses,
  };
}
