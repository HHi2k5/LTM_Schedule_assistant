import { buildUpload, validateScheduleRows } from './scheduleImport.js';

const isObject = value => value !== null && typeof value === 'object' && !Array.isArray(value);

function text(object, key, path, maxLength = 255, preserveSpaces = false) {
  if (typeof object[key] !== 'string' || !object[key].trim() || object[key].length > maxLength) {
    throw new Error(`JSON: ${path}.${key} phải là chuỗi không rỗng, tối đa ${maxLength} ký tự.`);
  }
  return preserveSpaces ? object[key] : object[key].trim();
}

function integer(object, key, path) {
  if (!Number.isSafeInteger(object[key]) || object[key] < 1) {
    throw new Error(`JSON: ${path}.${key} phải là số nguyên dương.`);
  }
  return object[key];
}

export function parseJsonSchedule(source, knownStart) {
  let data;
  try { 
    data = JSON.parse(source.replace(/^\uFEFF/, '')); 
  } catch { 
    throw new Error('File JSON sai cú pháp. Kiểm tra dấu ngoặc, dấu phẩy và dấu nháy kép.'); 
  }

  if (!isObject(data) || !isObject(data.school)) {
    throw new Error('JSON cần một đối tượng lịch có school và courses theo file mẫu.');
  }
  if (!Array.isArray(data.school.periods) || !data.school.periods.length || data.school.periods.length > 30) {
    throw new Error('JSON: school.periods phải có từ 1 đến 30 tiết.');
  }
  if (!Array.isArray(data.courses) || !data.courses.length || data.courses.length > 5000) {
    throw new Error('JSON: courses phải có từ 1 đến 5.000 dòng lịch.');
  }

  const periodRows = data.school.periods.map((period, index) => {
    const path = `school.periods[${index}]`;
    if (!isObject(period)) throw new Error(`JSON: ${path} phải là đối tượng tiết học.`);
    return { 
      period: integer(period, 'period', path), 
      start: text(period, 'start', path, 8), 
      end: text(period, 'end', path, 8), 
      row: index + 1 
    };
  });

  const courseRows = data.courses.map((course, index) => {
    const path = `courses[${index}]`;
    if (!isObject(course)) throw new Error(`JSON: ${path} phải là đối tượng môn học.`);
    const normalized = {
      courseCode: text(course, 'courseCode', path, 50), 
      courseName: text(course, 'courseName', path), 
      room: text(course, 'room', path),
      dayOfWeek: integer(course, 'dayOfWeek', path), 
      startPeriod: integer(course, 'startPeriod', path), 
      numPeriods: integer(course, 'numPeriods', path),
      weekSchedule: text(course, 'weekSchedule', path, 104, true), 
      row: index + 1,
    };
    return normalized;
  });

  const metadata = {
    teacherId: text(data, 'teacherId', 'lịch', 50), 
    teacherName: text(data, 'teacherName', 'lịch', 150),
    semesterStartDate: text(data, 'semesterStartDate', 'lịch', 10),
    schoolId: text(data.school, 'schoolId', 'school', 50), 
    schoolName: text(data.school, 'schoolName', 'school', 150),
    colorCode: text(data.school, 'colorCode', 'school', 7),
  };

  try { 
    return buildUpload(validateScheduleRows(periodRows, courseRows), metadata, knownStart); 
  } catch (error) { 
    throw new Error(`JSON: ${error.message.replaceAll('Sheet TietHoc', 'school.periods').replaceAll('Sheet MonHoc', 'courses')}`); 
  }
}

export function templateJson() {
  return {
    teacherId: '',
    teacherName: '',
    semesterStartDate: '',
    school: {
      schoolId: '',
      schoolName: '',
      colorCode: '#2563eb',
      periods: [
        { period: 1, start: '07:00', end: '07:50' },
        { period: 2, start: '08:00', end: '08:50' },
        { period: 3, start: '09:00', end: '09:50' },
        { period: 4, start: '10:00', end: '10:50' },
        { period: 5, start: '11:00', end: '11:50' }
      ],
    },
    courses: [
      {
        courseCode: '',
        courseName: '',
        room: '',
        dayOfWeek: 2,
        startPeriod: 1,
        numPeriods: 2,
        weekSchedule: ''
      }
    ],
  };
}
