import test from 'node:test';
import assert from 'node:assert/strict';
import { adjustmentInput, validateAdjustmentPreview } from './scheduleAdjustment.js';
const detail = { plan: { revision: 3, semesterStartDate: '2026-08-10' }, draft: { summary: { id: 100 } }, versions: [{ id: 100 }, { id: 90 }] };
const source = { revision: 3, baseVersionId: 100, snapshot: 'a'.repeat(64), rooms: [{ id: 1 }], classes: [{ classId: 'LT1' }], lecturers: [{ id: 2 }], periods: [1, 2, 3].map(period => ({ period })) };
const form = { action: 'MOVE', entryIds: [9], classIds: ['LT1'], scope: 'entries', roomId: '1', date: '2026-08-11', startPeriod: '1', numPeriods: '3', lecturerId: '', reason: '  Đổi giờ  ' };
test('manual edits send one occurrence and preserve its class and lecturer ownership', () => {
  const input = adjustmentInput(detail, source, form);
  assert.deepEqual(input.edits, [{ entryId: 9, classId: null, date: '2026-08-11', roomId: 1, startPeriod: 1, numPeriods: 3 }]);
  assert.deepEqual(input.classIds, []); assert.deepEqual(input.entryIds, []); assert.equal(input.lecturerId, null); assert.equal(input.reason, 'Đổi giờ');
});
test('automatic scope excludes hidden selection and requires explicit dates and allowed rooms', () => {
  const f = { ...form, action: 'AUTO', scope: 'classes', fromDate: '2026-08-10', toDate: '2026-08-20', roomIds: [1] };
  const input = adjustmentInput(detail, source, f); assert.deepEqual(input.classIds, ['LT1']); assert.deepEqual(input.entryIds, []);
  assert.throws(() => adjustmentInput(detail, source, { ...f, roomIds: [] }));
  assert.throws(() => adjustmentInput(detail, source, { ...f, toDate: '2026-08-01' }));
});
test('locks and restoration require explicit values and a historical version in the same plan', () => {
  assert.throws(() => adjustmentInput(detail, source, { ...form, action: 'LOCK' }));
  const unlocked = adjustmentInput(detail, source, { ...form, action: 'LOCK', locked: false }); assert.equal(unlocked.locked, false);
  const restore = adjustmentInput(detail, source, { ...form, action: 'RESTORE', restoreVersionId: '90', overrideLocks: 'true' }); assert.equal(restore.overrideLocks, false);
  assert.equal(adjustmentInput(detail, source, { ...form, action: 'RESTORE', restoreVersionId: '90', overrideLocks: true }).overrideLocks, true);
  assert.throws(() => adjustmentInput(detail, source, { ...form, action: 'RESTORE', restoreVersionId: 100 }));
});
test('invalid periods dates scopes and stale catalogs cannot produce a request', () => {
  for (const patch of [{ date: '2026-02-30' }, { numPeriods: '4' }, { startPeriod: '1.5' }, { entryIds: [1, 2] }, { roomId: 99 }]) assert.throws(() => adjustmentInput(detail, source, { ...form, ...patch }));
  assert.throws(() => adjustmentInput(detail, { ...source, revision: 2 }, form));
  assert.throws(() => adjustmentInput(detail, source, { ...form, action: 'REASSIGN', classIds: ['outside'], lecturerId: 2 }));
});
test('add uses a new occurrence while delete keeps only the explicitly selected IDs', () => {
  const added = adjustmentInput(detail, source, { ...form, action: 'ADD' }); assert.equal(added.edits[0].entryId, null); assert.equal(added.edits[0].classId, 'LT1');
  const removed = adjustmentInput(detail, source, { ...form, action: 'DELETE', scope: 'classes' }); assert.deepEqual(removed.entryIds, [9]); assert.deepEqual(removed.classIds, []);
});
test('preview must match action revision and version; no solution cannot offer commit', () => {
  const input = adjustmentInput(detail, source, form);
  const p = { revision: 3, baseVersionId: 100, action: 'MOVE', previewToken: 'b'.repeat(64), status: 'NO_SOLUTION', canApply: false, changes: [], conflicts: [], removedBusy: [], addedBusy: [] };
  assert.equal(validateAdjustmentPreview(p, input), p);
  for (const patch of [{ canApply: true }, { action: 'AUTO' }, { revision: 4 }, { baseVersionId: 90 }, { previewToken: '' }]) assert.throws(() => validateAdjustmentPreview({ ...p, ...patch }, input));
});
