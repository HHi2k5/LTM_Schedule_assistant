import { isMonday } from './schedule.js';

export const validSemesterStart = value => isMonday(value) && Number(value.slice(0, 4)) >= 2000 && Number(value.slice(0, 4)) <= 2100;
export const clampWeek = value => Math.min(100, Math.max(1, Number(value) || 1));
export function firstActiveWeek(slots, teacherId) {
  const weeks = slots.filter(slot => slot.teacherId === teacherId).map(slot => String(slot.weekSchedule || '').search(/[^- ]/) + 1).filter(week => week > 0);
  return clampWeek(weeks.length ? Math.min(...weeks) : 1);
}
export function validateFile(file, label = 'File Excel') {
  if (!file || !/\.xlsx$/i.test(file.name) || file.size < 1 || file.size > 10 * 1024 * 1024) {
    throw new Error(`${label}: cần file .xlsx không rỗng, tối đa 10 MB.`);
  }
}
export function validateSchool(id, name, start) {
  if (!id.trim() || id.trim().length > 50 || !name.trim() || name.trim().length > 255) throw new Error('Điền mã trường (tối đa 50 ký tự) và tên trường (tối đa 255 ký tự).');
  if (!validSemesterStart(start)) throw new Error('Ngày bắt đầu tuần 1 phải là Thứ Hai, trong năm 2000–2100.');
}
export function validateNewPassword(password) {
  if (password.length < 8 || new TextEncoder().encode(password).length > 72) throw new Error('Mật khẩu cần ít nhất 8 ký tự và tối đa 72 byte UTF-8 (ký tự có dấu có thể chiếm nhiều byte).');
}
export function validateAutoResult(result) {
  const saved = result?.saved === true && result?.status === 'SUCCESS';
  const preview = result?.saved === false && result?.status === 'NOT_SAVED';
  if ((!saved && !preview) || !Array.isArray(result.scheduledClasses) || !result.schoolId || !result.schoolName || !validSemesterStart(result.semesterStartDate)) {
    throw new Error('Máy chủ chưa xác nhận trạng thái và thông tin kết quả. Hãy tải lại lịch để kiểm tra trước khi chạy lại.');
  }
  if (saved && !result.scheduledClasses.length) throw new Error('Máy chủ trả kết quả đã lưu nhưng không có buổi học. Hãy tải lại lịch để kiểm tra.');
  return result;
}

function integer(value, min, max, field) {
  if (!/^\d+$/.test(String(value).trim()) || Number(value) < min || Number(value) > max) throw new Error(`${field}: cần số nguyên từ ${min} đến ${max}.`);
}
function required(value, max, field) {
  if (!String(value ?? '').trim() || String(value).trim().length > max) throw new Error(`${field}: cần 1–${max} ký tự.`);
}
export function validateManual(plans, teachers, rooms, semesterStart) {
  if (!plans.length || !teachers.length || !rooms.length) throw new Error('Cả ba bảng cần ít nhất một dòng.');
  plans.forEach((row, index) => {
    const label = `Kế hoạch, dòng ${index + 1}`;
    required(row.courseCode, 50, `${label}, mã môn`); required(row.courseName, 180, `${label}, tên môn`); required(row.cohort, 50, `${label}, khóa`);
    for (const [key, min, max, field] of [['classSize',1,1000,'sĩ số'],['numClasses',1,500,'số lớp'],['credits',1,30,'tín chỉ'],['theoryPeriods',0,1000,'tổng tiết LT'],['practicePeriods',0,1000,'tổng tiết TH']]) integer(row[key], min, max, `${label}, ${field}`);
    if (Number(row.theoryPeriods) + Number(row.practicePeriods) === 0) throw new Error(`${label}: cần ít nhất một tiết LT hoặc TH.`);
    const validDate = value => /^\d{4}-\d{2}-\d{2}$/.test(value || '') && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
    if (!validDate(row.studyStartDate) || !validDate(row.studyEndDate) || row.studyEndDate < row.studyStartDate) throw new Error(`${label}: ngày bắt đầu/kết thúc không hợp lệ.`);
    if (semesterStart && (row.studyStartDate < semesterStart || (Date.parse(row.studyEndDate) - Date.parse(semesterStart)) / 86400000 >= 700)) throw new Error(`${label}: khoảng học phải nằm trong 100 tuần tính từ mốc tuần 1.`);
  });
  teachers.forEach((row, index) => {
    const label = `Giảng viên, dòng ${index + 1}`;
    required(row.courseCode,50,`${label}, mã môn`); required(row.courseName,255,`${label}, tên môn`); required(row.teacherId,50,`${label}, mã GV`); required(row.teacherName,100,`${label}, họ tên`); required(row.cohort,50,`${label}, khóa`);
    integer(row.theoryWeight,0,10000,`${label}, trọng số LT`); integer(row.practiceWeight,0,10000,`${label}, trọng số TH`);
    if (!plans.some(plan => plan.courseCode.trim() === row.courseCode.trim() && plan.cohort.trim() === row.cohort.trim())) throw new Error(`${label}: mã môn và khóa chưa có trong kế hoạch.`);
  });
  plans.forEach((row, index) => {
    if (!teachers.some(teacher => teacher.courseCode.trim() === row.courseCode.trim() && teacher.cohort.trim() === row.cohort.trim())) throw new Error(`Kế hoạch, dòng ${index + 1}: chưa có giảng viên cho môn và khóa này.`);
  });
  const codes = new Set();
  rooms.forEach((row, index) => {
    const label = `Phòng học, dòng ${index + 1}`;
    required(row.roomCode,100,`${label}, mã phòng`); integer(row.capacity,1,1000,`${label}, sức chứa`);
    if (!['THEORY','PRACTICE'].includes(row.roomType)) throw new Error(`${label}: loại phòng không hợp lệ.`);
    if (codes.has(row.roomCode.trim())) throw new Error(`${label}: mã phòng bị lặp.`);
    codes.add(row.roomCode.trim());
  });
}
