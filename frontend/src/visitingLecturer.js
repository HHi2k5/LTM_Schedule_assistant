import { addDays } from './schedule.js';

const integer = (value, min, max, label) => {
  const number = Number(value);
  if (String(value ?? '').trim() === '' || !Number.isInteger(number) || number < min || number > max) throw new Error(`${label} cần số nguyên ${min}–${max}.`);
  return number;
};
const text = (value, max, label) => {
  const result = String(value ?? '').trim(); if (!result || result.length > max) throw new Error(`${label} cần 1–${max} ký tự.`); return result;
};
const validDate = value => /^\d{4}-\d{2}-\d{2}$/.test(value || '') && !Number.isNaN(new Date(`${value}T12:00:00Z`).valueOf()) && new Date(`${value}T12:00:00Z`).toISOString().slice(0, 10) === value;

export function expandFixedRows(rows, planStart) {
  const output = [], maxDate = addDays(planStart, 699);
  for (const [index, row] of rows.entries()) {
    if (!validDate(row.startDate) || !validDate(row.endDate) || row.endDate < row.startDate || row.startDate < planStart || row.endDate > maxDate) throw new Error(`Buổi cố định dòng ${index + 1}: khoảng ngày không hợp lệ trong 100 tuần.`);
    const repeat = row.dayOfWeek !== '' && row.dayOfWeek != null;
    const day = repeat ? integer(row.dayOfWeek, 2, 8, 'Thứ lặp') : null;
    if (!repeat && row.startDate !== row.endDate) throw new Error('Lịch nhiều tuần cần chọn thứ lặp.');
    const roomId = integer(row.roomId, 1, Number.MAX_SAFE_INTEGER, 'Phòng'), startPeriod = integer(row.startPeriod, 1, 30, 'Tiết bắt đầu'), numPeriods = integer(row.numPeriods, 1, 30, 'Số tiết');
    let matched = false;
    for (let date = row.startDate; date <= row.endDate; date = addDays(date, 1)) {
      const weekday = new Date(`${date}T12:00:00Z`).getUTCDay();
      if (!repeat || (weekday || 7) + 1 === day) { matched = true; output.push({ classId: row.classId || '$NEW', date, roomId, startPeriod, numPeriods }); }
      if (output.length > 2000) throw new Error('Tối đa 2000 buổi cố định mỗi đề xuất.');
    }
    if (!matched) throw new Error(`Buổi cố định dòng ${index + 1}: khoảng ngày không chứa thứ đã chọn.`);
  }
  return output;
}

export function visitingInput(detail, options, form) {
  if (!options || options.revision !== detail.plan.revision || options.baseVersionId !== detail.draft?.summary.id || !/^[a-f0-9]{64}$/.test(options.snapshot || '')) throw new Error('Nguồn thỉnh giảng không khớp bản nháp. Mở lại bản lịch.');
  const lecturerId = integer(form.lecturerId, 1, Number.MAX_SAFE_INTEGER, 'Thỉnh giảng');
  if (!options.lecturers.some(v => v.id === lecturerId)) throw new Error('Chọn thỉnh giảng trong nguồn của khoa.');
  const creating = form.action === 'NEW';
  if (!creating && (!form.classIds.length || form.classIds.length > 100 || new Set(form.classIds).size !== form.classIds.length || form.classIds.some(id => !options.classes.some(c => c.classId === id)))) throw new Error('Chọn 1–100 lớp/nhóm có trong bản nháp.');
  const c = form.newClass;
  const newClass = creating ? { courseCode: text(c.courseCode, 50, 'Mã môn'), courseName: text(c.courseName, 180, 'Tên môn'), cohort: text(c.cohort, 50, 'Khóa'), classGroup: text(c.classGroup, 100, 'Lớp/nhóm'), sessionType: c.sessionType,
    studentCount: integer(c.studentCount, 1, 1000, 'Sĩ số'), credits: integer(c.credits, 1, 30, 'Tín chỉ'), studyStartDate: c.studyStartDate, studyEndDate: c.studyEndDate, totalPeriods: integer(c.totalPeriods, 1, 1000, 'Tổng tiết'), parentClassId: c.sessionType === 'PRACTICE' ? c.parentClassId || null : null } : null;
  if (creating && (!validDate(c.studyStartDate) || !validDate(c.studyEndDate) || c.studyEndDate < c.studyStartDate)) throw new Error('Lớp mới cần khoảng ngày học hợp lệ.');
  const timing = creating ? 'FIXED' : form.timing;
  const sessions = timing === 'FIXED' ? expandFixedRows(form.sessions, detail.plan.semesterStartDate) : [];
  if (!creating && sessions.some(s => !form.classIds.includes(s.classId) && !(form.classIds.length === 1 && s.classId === '$NEW'))) throw new Error('Buổi cố định phải thuộc lớp/nhóm đã chọn.');
  if (form.busySlots.length > 100) throw new Error('Tối đa 100 khoảng bận.');
  const busySlots = form.busySlots.map((b, index) => {
    if (!validDate(b.startDate) || !validDate(b.endDate) || b.endDate < b.startDate || !/^(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?$/.test(b.startTime) || !/^(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?$/.test(b.endTime) || b.startTime.padEnd(8, ':00') >= b.endTime.padEnd(8, ':00')) throw new Error(`Lịch bận dòng ${index + 1}: kiểm tra ngày và giờ bắt đầu/kết thúc.`);
    if (!b.dayOfWeek && b.startDate !== b.endDate) throw new Error('Lịch bận nhiều ngày cần chọn thứ lặp.');
    const dayOfWeek = b.dayOfWeek ? integer(b.dayOfWeek, 2, 8, 'Thứ bận') : null;
    if (dayOfWeek) {
      const weekday = (new Date(`${b.startDate}T12:00:00Z`).getUTCDay() || 7) + 1;
      if (addDays(b.startDate, (dayOfWeek - weekday + 7) % 7) > b.endDate) throw new Error('Khoảng ngày bận không chứa thứ lặp đã chọn.');
    }
    return { ...b, dayOfWeek, reason: String(b.reason || '').trim() };
  });
  return { revision: detail.plan.revision, baseVersionId: options.baseVersionId, snapshot: options.snapshot, lecturerId, action: creating ? 'NEW' : 'RECEIVE', timing, classIds: creating ? [] : form.classIds, newClass, sessions, busySlots };
}

export function validateVisitingPreview(result, input) {
  if (result?.revision !== input.revision || result.baseVersionId !== input.baseVersionId || !/^[a-f0-9]{64}$/.test(result.previewToken || '') || !['changes', 'beforeEntries', 'afterEntries', 'busySlots', 'conflicts'].every(key => Array.isArray(result[key]))) throw new Error('Đề xuất trả về không khớp bản nháp. Xem trước lại trước khi lưu.');
  return result;
}

export function initialVisitingForm(detail, options, chosenId) {
  const first = options.classes[0] || detail.draft.courses[0] || {};
  const preferred = chosenId || detail.draft.visitingChange?.lecturerId;
  const lecturerId = String(options.lecturers.some(v => String(v.id) === String(preferred)) ? preferred : options.lecturers[0]?.id || '');
  return { lecturerId, action: 'RECEIVE', timing: 'KEEP', classIds: [], sessions: [], busySlots: busyWindowsFromSlots(options.busySlots || [], lecturerId),
    newClass: { courseCode: first.courseCode || '', courseName: first.courseName || '', cohort: first.cohort || '', classGroup: 'Lớp TG01', sessionType: 'THEORY', studentCount: first.studentCount || first.classSize || 50, credits: first.credits || 3,
      studyStartDate: first.studyStartDate || detail.plan.semesterStartDate, studyEndDate: first.studyEndDate || addDays(detail.plan.semesterStartDate, 111), totalPeriods: 3, parentClassId: '' } };
}

export function busyWindowsFromSlots(slots, lecturerId) {
  const groups = new Map();
  slots.filter(b => String(b.lecturerId) === String(lecturerId)).forEach(b => {
    const weekday = (new Date(`${b.date}T12:00:00Z`).getUTCDay() || 7) + 1;
    const key = JSON.stringify([weekday, b.startTime, b.endTime, b.reason]);
    const group = groups.get(key) || { weekday, startTime: b.startTime, endTime: b.endTime, reason: b.reason, dates: new Set() };
    group.dates.add(b.date); groups.set(key, group);
  });
  const result = [];
  for (const group of groups.values()) {
    let first = null, previous = null;
    const flush = () => { if (first) result.push({ startDate: first, endDate: previous, dayOfWeek: first === previous ? '' : String(group.weekday), startTime: group.startTime, endTime: group.endTime, reason: group.reason }); };
    for (const date of [...group.dates].sort()) { if (previous && date !== addDays(previous, 7)) { flush(); first = null; } first ||= date; previous = date; }
    flush();
  }
  return result;
}
