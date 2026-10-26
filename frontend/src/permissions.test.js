import { test } from 'node:test';
import assert from 'node:assert/strict';
import { canImport, canSchedule, canVisit, defaultPage } from './permissions.js';

test('teacher and manager have different entry points and cannot navigate to each others forms', () => {
  const teacher = { role: 'TEACHER' }, manager = { role: 'ADMIN' };
  assert.equal(defaultPage(teacher), 'schedule'); assert.equal(defaultPage(manager), 'autoschedule');
  assert.equal(canImport(teacher), true); assert.equal(canSchedule(teacher), false);
  assert.equal(canVisit(teacher, 'autoschedule'), false); assert.equal(canVisit(manager, 'import'), false);
  assert.equal(canVisit(teacher, 'import'), true); assert.equal(canVisit(manager, 'autoschedule'), true);
  assert.equal(canVisit(manager, 'schedule'), true); assert.equal(canVisit(null, 'schedule'), false);
  assert.equal(canVisit({ role: 'unknown' }, 'overview'), false);
});
