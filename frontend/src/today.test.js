import { test } from 'node:test';
import assert from 'node:assert/strict';
import { lessonStatus, summarizeToday, teachingWeek, timeUntil, vietnamTime } from './today.js';

const lesson = (date, startTime, endTime, extra = {}) => ({ date, startTime, endTime, courseName: 'Lập trình mạng', ...extra });

test('Vietnam midnight and Sunday rollover use the local date and actual semester week', () => {
  assert.equal(vietnamTime(new Date('2026-10-27T16:59:59Z')).date, '2026-10-27');
  assert.equal(vietnamTime(new Date('2026-10-27T17:00:00Z')).date, '2026-10-28');
  assert.equal(teachingWeek(vietnamTime(new Date('2026-11-01T16:59:59Z')).date, '2026-08-10'), 12);
  assert.equal(teachingWeek(vietnamTime(new Date('2026-11-01T17:00:00Z')).date, '2026-08-10'), 13);
  assert.equal(teachingWeek('2026-08-09', '2026-08-10'), 0);
  assert.equal(teachingWeek('2028-07-10', '2026-08-10'), 101);
  assert.equal(teachingWeek('2026-10-27', '2026-08-11'), null);
});

test('today summary excludes other weeks and ended lessons and counts only unfinished teaching time', () => {
  const now = new Date('2026-10-27T02:30:00Z'); // 09:30 in Vietnam.
  const current = lesson('2026-10-27', '09:00', '10:00');
  const entries = [lesson('2026-10-19', '07:00', '08:00'), lesson('2026-10-26', '07:00', '08:00', { isConflict: true }),
    current, lesson('2026-10-27', '13:00', '14:00', { isConflict: true }),
    lesson('2026-10-27', '13:30', '14:30', { isConflict: true }), lesson('2026-10-28', '07:00', '08:00'),
    lesson('2026-11-02', '07:00', '08:00')];
  const result = summarizeToday(entries, now);
  assert.equal(result.week.length, 5);
  assert.equal(result.today.length, 3);
  assert.equal(result.remaining.length, 4);
  assert.equal(result.remainingHours, 3.5);
  assert.equal(result.conflicts.length, 2);
  assert.equal(result.next, current);
  assert.equal(timeUntil(current, now, true), '30 phút');
  assert.equal(lessonStatus(current, vietnamTime(new Date('2026-10-27T02:00:00Z'))), 'ongoing');
  assert.equal(lessonStatus(current, vietnamTime(new Date('2026-10-27T03:00:00Z'))), 'done');
});

test('simultaneous lessons, empty days and the end of the week have explicit summaries', () => {
  const entries = [lesson('2026-10-27', '09:00', '10:00', { isConflict: true }), lesson('2026-10-27', '09:15', '10:15', { isConflict: true })];
  const active = summarizeToday(entries, new Date('2026-10-27T02:30:00Z'));
  assert.equal(active.ongoing.length, 2);
  assert.equal(active.remainingHours, 1.25);
  const after = summarizeToday(entries, new Date('2026-10-28T02:30:00Z'));
  assert.equal(after.today.length, 0);
  assert.equal(after.remaining.length, 0);
  assert.equal(after.next, null);
  assert.equal(after.conflicts.length, 0);
  const empty = summarizeToday([], new Date('2026-10-27T02:30:00Z'));
  assert.equal(empty.remainingHours, 0);
  const next = lesson('2026-10-28', '07:00', '08:00');
  assert.equal(summarizeToday([next], new Date('2026-10-27T02:30:00Z')).next, next);
});
