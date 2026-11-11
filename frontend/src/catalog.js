import { api } from './schedule.js';

export const TEACHER_TYPES = [
  ['TRUONG_BO_MON', 'Trưởng bộ môn'],
  ['GIANG_VIEN', 'Giảng viên'],
  ['TRO_GIANG', 'Trợ giảng'],
  ['THINH_GIANG', 'Thỉnh giảng'],
];
export const teacherTypeLabel = code => TEACHER_TYPES.find(([value]) => value === code)?.[1] || 'Giảng viên';

export const SUBJECT_KINDS = [['PRIMARY', 'Phụ trách'], ['SUBSTITUTE', 'Dạy thay']];
export const subjectKindLabel = code => (code === 'SUBSTITUTE' ? 'Dạy thay' : 'Phụ trách');

export const usageLabel = kind => (kind === 'PRACTICE' ? 'Thực hành' : 'Lý thuyết');

export const postJson = (path, body) => api(path, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

/** "--11-111" -> "3–4, 6–8": các tuần có buổi học, gộp thành khoảng liên tiếp. */
export function weekRanges(weekSchedule) {
  const weeks = String(weekSchedule || '');
  const ranges = [];
  for (let i = 0; i < weeks.length; i++) {
    if (weeks[i] === '-' || weeks[i] === ' ') continue;
    const first = i;
    while (i + 1 < weeks.length && weeks[i + 1] !== '-' && weeks[i + 1] !== ' ') i++;
    ranges.push(first === i ? `${first + 1}` : `${first + 1}–${i + 1}`);
  }
  return ranges.join(', ');
}

export function firstWeek(weekSchedule) {
  const index = String(weekSchedule || '').search(/[^- ]/);
  return index < 0 ? null : index + 1;
}

/** Tải một workbook ExcelJS về máy. */
export async function downloadWorkbook(workbook, filename) {
  const buffer = await workbook.xlsx.writeBuffer();
  const blob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
