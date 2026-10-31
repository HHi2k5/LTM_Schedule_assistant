import { addDays } from './schedule.js';

export const adjustmentActions = { MOVE: 'Sửa buổi', ADD: 'Thêm buổi', DELETE: 'Bỏ buổi', LOCK: 'Khóa / mở khóa', REASSIGN: 'Đổi giảng viên', AUTO: 'Xếp lại phạm vi', RESTORE: 'Khôi phục phiên bản' };
const number = (v, name) => { const n = Number(v); if (!String(v ?? '').trim() || !Number.isSafeInteger(n) || n < 1) throw new Error(`${name} cần số nguyên dương.`); return n; };
const validDate = v => /^\d{4}-\d{2}-\d{2}$/.test(v || '') && !Number.isNaN(Date.parse(`${v}T12:00:00Z`)) && new Date(`${v}T12:00:00Z`).toISOString().slice(0, 10) === v;
export function adjustmentInput(detail, source, form) {
  if (!source || source.revision !== detail.plan.revision || source.baseVersionId !== detail.draft?.summary.id || !/^[a-f0-9]{64}$/.test(source.snapshot || '')) throw new Error('Nguồn điều chỉnh đã cũ. Mở lại bản lịch.');
  if (!adjustmentActions[form.action]) throw new Error('Chọn thao tác điều chỉnh.');
  const input = { revision: source.revision, baseVersionId: source.baseVersionId, snapshot: source.snapshot, action: form.action, entryIds: [], classIds: [], edits: [], lecturerId: null, locked: null, fromDate: null, toDate: null, roomIds: null, restoreVersionId: null, overrideLocks: false, reason: String(form.reason || '').trim() };
  if (input.reason.length > 255) throw new Error('Lý do tối đa 255 ký tự.');
  const classes = () => {
    const ids = form.classIds || [];
    if (!ids.length || ids.length > 100 || new Set(ids).size !== ids.length || ids.some(id => !source.classes.some(c => c.classId === id))) throw new Error('Chọn 1–100 lớp/nhóm trong bản nháp.');
    return [...ids];
  };
  const entries = () => {
    const ids = (form.entryIds || []).map(v => number(v, 'Buổi'));
    if (!ids.length || ids.length > 2000 || new Set(ids).size !== ids.length) throw new Error('Chọn 1–2000 buổi khác nhau.');
    return ids;
  };
  const date = v => { if (!validDate(v) || v < detail.plan.semesterStartDate || v > addDays(detail.plan.semesterStartDate, 699)) throw new Error('Chọn ngày trong 100 tuần của bản lịch.'); return v; };
  if (['MOVE', 'ADD'].includes(form.action)) {
    const roomId = number(form.roomId, 'Phòng'), startPeriod = number(form.startPeriod, 'Tiết bắt đầu'), numPeriods = number(form.numPeriods, 'Số tiết');
    if (!source.rooms.some(r => r.id === roomId)) throw new Error('Chọn phòng đang hoạt động trong trường.');
    const periods = new Set(source.periods.map(p => p.period));
    if (numPeriods > 30 || !Array.from({ length: numPeriods }, (_, i) => startPeriod + i).every(p => periods.has(p))) throw new Error('Các tiết phải có trong bảng giờ của bản lịch.');
    const classId = form.action === 'ADD' ? classes()[0] : null;
    if (form.action === 'ADD' && form.classIds.length !== 1) throw new Error('Thêm buổi cho một lớp/nhóm mỗi lần.');
    if (form.action === 'MOVE' && entries().length !== 1) throw new Error('Chọn một buổi cần sửa.');
    input.edits = [{ entryId: form.action === 'MOVE' ? entries()[0] : null, classId, date: date(form.date), roomId, startPeriod, numPeriods }];
  }
  if (['LOCK', 'AUTO', 'DELETE', 'REASSIGN'].includes(form.action)) {
    if (form.action === 'REASSIGN' || form.scope === 'classes' && form.action !== 'DELETE') input.classIds = classes(); else input.entryIds = entries();
  }
  if (form.action === 'LOCK') { if (typeof form.locked !== 'boolean') throw new Error('Chọn trạng thái khóa.'); input.locked = form.locked; }
  if (form.action === 'REASSIGN' || form.action === 'ADD' && form.lecturerId) {
    input.lecturerId = number(form.lecturerId, 'Giảng viên');
    if (!source.lecturers.some(l => l.id === input.lecturerId)) throw new Error('Chọn giảng viên thuộc khoa.');
  }
  if (form.action === 'AUTO') {
    input.fromDate = date(form.fromDate); input.toDate = date(form.toDate);
    if (input.toDate < input.fromDate) throw new Error('Ngày kết thúc phải từ ngày bắt đầu.');
    input.roomIds = (form.roomIds || []).map(v => number(v, 'Phòng'));
    if (!input.roomIds.length || new Set(input.roomIds).size !== input.roomIds.length || input.roomIds.some(id => !source.rooms.some(r => r.id === id))) throw new Error('Chọn tập phòng cho phép xếp lại.');
  }
  if (form.action === 'RESTORE') {
    input.restoreVersionId = number(form.restoreVersionId, 'Phiên bản');
    if (input.restoreVersionId === source.baseVersionId || !detail.versions.some(v => v.id === input.restoreVersionId)) throw new Error('Chọn phiên bản lịch sử thuộc bản lịch.');
    input.overrideLocks = form.overrideLocks === true;
  }
  return input;
}
export function validateAdjustmentPreview(result, input) {
  if (result?.revision !== input.revision || result.baseVersionId !== input.baseVersionId || result.action !== input.action || !/^[a-f0-9]{64}$/.test(result.previewToken || '') || !['READY', 'NO_CHANGE', 'NO_SOLUTION'].includes(result.status) || !['changes', 'conflicts', 'removedBusy', 'addedBusy'].every(k => Array.isArray(result[k])) || result.canApply !== (result.status === 'READY')) throw new Error('Đề xuất trả về không khớp yêu cầu. Xem trước lại.');
  return result;
}
