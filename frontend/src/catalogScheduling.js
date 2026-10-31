export function validateCatalogOptions(options) {
  if (!options?.school?.schoolCode || !/^[a-f0-9]{64}$/.test(options.snapshot || '')
      || !['lecturers', 'rooms', 'assignments', 'periodChoices'].every(key => Array.isArray(options[key]))
      || options.lecturers.some(row => !Number.isInteger(row.lecturer?.id) || typeof row.eligible !== 'boolean')
      || options.periodChoices.some(row => !row.id || !Array.isArray(row.periods) || !row.periods.length))
    throw new Error('Danh mục xếp lịch trả về không hợp lệ. Hãy tải lại dữ liệu.');
  return options;
}

export function catalogConfiguration(options, lecturerIds, roomIds, overrides, periodChoiceId, replaceSchoolSchedule, departmentDraft = false) {
  validateCatalogOptions(options);
  const eligible = new Set(options.lecturers.filter(row => row.eligible).map(row => row.lecturer.id));
  const availableRooms = new Set(options.rooms.map(row => row.id));
  if (!lecturerIds.length || new Set(lecturerIds).size !== lecturerIds.length || lecturerIds.some(id => !eligible.has(id))) throw new Error('Chọn giảng viên đủ điều kiện trong danh mục của trường.');
  if (!roomIds.length || new Set(roomIds).size !== roomIds.length || roomIds.some(id => !availableRooms.has(id))) throw new Error('Chọn phòng đang sử dụng trong danh mục của trường.');
  if (!options.periodChoices.some(row => row.id === periodChoiceId)) throw new Error('Chọn bảng giờ tiết để xếp lịch.');
  if (!departmentDraft && !replaceSchoolSchedule) throw new Error('Xác nhận kế hoạch đầy đủ cho toàn trường trước khi xếp lịch.');
  const weights = options.assignments.filter(row => lecturerIds.includes(row.lecturerId) && overrides[row.id]).map(row => {
    const raw = overrides[row.id];
    const integer = value => String(value).trim() !== '' && Number.isInteger(Number(value)) && Number(value) >= 0 && Number(value) <= 10000;
    if (!integer(raw.theoryWeight) || !integer(raw.practiceWeight)) throw new Error(`Trọng số ${row.courseCode}/${row.cohort} cần số nguyên 0–10000.`);
    return { assignmentId: row.id, theoryWeight: Number(raw.theoryWeight), practiceWeight: Number(raw.practiceWeight) };
  });
  return { snapshot: options.snapshot, lecturerIds, roomIds, weights, periodChoiceId, replaceSchoolSchedule: !departmentDraft };
}

export function resultLecturer(result, teacherId) {
  return result?.catalogContext?.lecturers?.find(row => row.teacherId === teacherId)
    || { teacherId, lecturerCode: teacherId, accountTeacherId: teacherId };
}
