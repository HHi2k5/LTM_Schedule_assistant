export const catalogKinds = {
  institutions: { singular: 'trường', title: 'Trường', key: 'schoolCode', fields: [
    ['schoolCode', 'Mã trường', 'code', true, 50], ['name', 'Tên trường', 'text', true, 255],
  ] },
  departments: { singular: 'khoa', title: 'Khoa', key: 'departmentCode', fields: [
    ['departmentCode', 'Mã khoa', 'code', true, 50], ['name', 'Tên khoa', 'text', true, 255],
  ] },
  lecturers: { singular: 'giảng viên', title: 'Giảng viên', key: 'lecturerCode', fields: [
    ['lecturerCode', 'Mã GV trong trường', 'code', true, 50], ['fullName', 'Họ và tên', 'text', true, 100],
    ['departmentId', 'Khoa', 'department'], ['lecturerType', 'Loại giảng viên', 'lecturerType', true],
    ['accountTeacherId', 'Mã tài khoản GV đã có', 'text', false, 50],
    ['existingScheduleTeacherId', 'Mã hồ sơ lịch cũ (nếu có)', 'text', false, 50],
  ] },
  rooms: { singular: 'phòng học', title: 'Phòng học', key: 'roomCode', fields: [
    ['roomCode', 'Mã phòng', 'code', true, 50], ['roomName', 'Tên phòng', 'text', true, 100],
    ['roomType', 'Loại phòng', 'roomType', true], ['capacity', 'Sức chứa', 'number', true, 10000, 1],
    ['building', 'Tòa nhà', 'text', false, 50],
  ] },
  assignments: { singular: 'phân công', title: 'Phân công môn', key: 'courseCode', fields: [
    ['courseCode', 'Mã môn', 'code', true, 50], ['courseName', 'Tên môn', 'text', true, 180],
    ['cohort', 'Khóa sinh viên', 'code', true, 50], ['theoryWeight', 'Trọng số LT', 'number', true, 10000, 0],
    ['practiceWeight', 'Trọng số TH', 'number', true, 10000, 0],
  ] },
};

export function catalogForm(kind, row) {
  const defaults = { active: true, lecturerType: 'STAFF', roomType: 'THEORY', capacity: 60, theoryWeight: 1, practiceWeight: 1 };
  const form = { ...defaults, ...row };
  catalogKinds[kind].fields.forEach(([key]) => { form[key] ??= ''; });
  if (kind === 'lecturers') form.existingScheduleTeacherId = row?.accountTeacherId ? '' : row?.scheduleTeacherId || '';
  if (kind === 'rooms') form.roomType = row?.isLab || row?.roomType === 'LAB_PRACTICE' ? 'PRACTICE' : 'THEORY';
  return form;
}

export function catalogPayload(kind, form, editing = false) {
  const input = Object.fromEntries(catalogKinds[kind].fields.map(([key, , type]) => [key,
    type === 'number' || type === 'department' ? (form[key] === '' ? null : Number(form[key]))
      : String(form[key] ?? '').trim() || null,
  ]));
  input.active = Boolean(form.active);
  if (kind === 'lecturers' && !input.existingScheduleTeacherId) delete input.existingScheduleTeacherId;
  if (editing) input.revision = form.revision;
  return input;
}

export function catalogPath(kind, schoolCode, lecturerId) {
  if (kind === 'institutions') return '';
  const school = `/${encodeURIComponent(schoolCode)}`;
  if (kind === 'assignments') {
    if (!lecturerId) throw new Error('Chọn giảng viên trước khi quản lý phân công.');
    return `${school}/lecturers/${encodeURIComponent(lecturerId)}/assignments`;
  }
  return `${school}/${kind}`;
}

export const catalogLabels = {
  accountUsername: 'Tên đăng nhập',
  accountAction: 'Tài khoản sau lưu',
  lecturerCode: 'Mã GV', fullName: 'Họ tên', departmentId: 'Khoa', lecturerType: 'Loại GV', accountTeacherId: 'Mã tài khoản',
  existingScheduleTeacherId: 'Hồ sơ lịch đã đối chiếu',
  roomCode: 'Mã phòng', roomName: 'Tên phòng', roomType: 'Loại phòng', capacity: 'Sức chứa', building: 'Tòa nhà',
  departmentCode: 'Mã khoa', name: 'Tên', courseCode: 'Mã môn', courseName: 'Tên môn', cohort: 'Khóa SV',
  theoryWeight: 'Trọng số LT', practiceWeight: 'Trọng số TH', active: 'Trạng thái',
};

export function catalogValue(key, value, departments = []) {
  if (value == null || value === '') return '—';
  if (key === 'active') return value ? 'Đang sử dụng' : 'Ngừng sử dụng';
  if (key === 'departmentId') return departments.find(row => String(row.id) === String(value))?.name || `Khoa #${value}`;
  if (key === 'lecturerType') return value === 'VISITING' ? 'Thỉnh giảng' : 'Cơ hữu';
  if (key === 'roomType') return ['PRACTICE', 'LAB_PRACTICE'].includes(value) ? 'Thực hành / LAB' : 'Lý thuyết';
  return String(value);
}
