import { test, expect } from '@playwright/test';
import { planWorkbook } from '../src/autoScheduleWorkbook.js';
import ExcelJS from 'exceljs';
import { addDays } from '../src/schedule.js';
import { expectReadable, expectPageFits } from './theme-checks.js';

test.setTimeout(60000);
const anchor = '2026-08-10';
const course = { courseCode: 'NET', courseName: 'Mạng máy tính', cohort: '2024', classSize: 50, numClasses: 1, totalClassesToOpen: 1, credits: 3, studyStartDate: anchor, studyEndDate: '2026-11-29', theoryPeriods: 6, practicePeriods: 0 };
const school = { schoolCode: 'A', name: 'Trường A', active: true, revision: 0 };
const periods = [1, 2, 3].map((p, i) => ({ period: p, start: `0${7 + i}:00`, end: `0${7 + i}:50` }));
const configuration = { snapshot: 'a'.repeat(64), lecturerIds: [1], roomIds: [10], weights: [], periodChoiceId: 'hours', replaceSchoolSchedule: false };
const options = { school, snapshot: configuration.snapshot,
  lecturers: [{ lecturer: { id: 1, lecturerCode: 'GV01', fullName: 'Nguyễn Văn A', departmentId: 1, active: true }, eligible: true }],
  assignments: [{ id: 5, lecturerId: 1, courseCode: 'NET', courseName: course.courseName, cohort: '2024', theoryWeight: 1, practiceWeight: 0, active: true }],
  rooms: [{ id: 10, roomCode: '101', capacity: 60, isLab: false, active: true }], periodChoices: [{ id: 'hours', label: 'Khung giờ của trường', periods }] };
const conflict = { type: 'ROOM_CONFLICT', severity: 'BLOCKING', entryIds: [101], date: anchor, startTime: '07:00', endTime: '09:50', resource: 'GV01 / 101', message: 'Phòng đã được khoa khác sử dụng.' };

function mockBoard(state, plan, version, params) {
  const filters = { week: Number(params.get('week') || 1), view: params.get('view') || 'calendar', groupBy: params.get('groupBy') || 'lecturer', page: Number(params.get('page') || 0),
    lecturerId: params.get('lecturerId') ? Number(params.get('lecturerId')) : null, courseCode: params.get('courseCode') || '', cohort: params.get('cohort') || '', classId: params.get('classId') || '', roomCode: params.get('roomCode') || '',
    sessionType: params.get('sessionType') || '', lecturerType: params.get('lecturerType') || '', conflictsOnly: params.get('conflictsOnly') === 'true', q: params.get('q') || '' };
  const base = { classId: 'NET:LT', parentClassId: 'NET', lecturerId: 1, lecturerCode: 'GV01', lecturerName: 'Nguyễn Văn A', lecturerType: 'STAFF', courseCode: 'NET', courseName: course.courseName,
    cohort: '2024', classGroup: 'Lớp 01', sessionType: 'THEORY', studentCount: 50, roomCode: '101', startTime: '07:00', endTime: '09:50', startPeriod: 1, numPeriods: 3, locked: !!state.adjustmentLocked, conflicts: state.conflict ? [conflict] : [] };
  let all = state.incomplete ? [] : [{ ...base, id: 101, date: anchor }, { ...base, id: 102, date: '2026-08-12' },
    { ...base, id: 103, classId: 'NET:TH', classGroup: 'Lớp 01 - Tổ 1', sessionType: 'PRACTICE', lecturerId: 3, lecturerCode: 'GV03', lecturerName: 'Trần Thị B', lecturerType: 'VISITING', roomCode: 'LAB', date: '2026-08-14' },
    { ...base, id: 104, classId: 'DB:LT', courseCode: 'DB', courseName: 'Cơ sở dữ liệu', cohort: '2025', lecturerId: 3, lecturerCode: 'GV03', lecturerName: 'Trần Thị B', lecturerType: 'VISITING', date: '2026-08-17' }];
  if (state.manySessions) all = Array.from({ length: 26 }, (_, i) => ({ ...base, id: 101 + i, classId: `NET:${i}`, classGroup: `Lớp ${i + 1}`, date: anchor }));
  if (state.manyGroups) all = Array.from({ length: 11 }, (_, i) => ({ ...base, id: 101 + i, classId: `NET:${i}`, lecturerId: 20 + i, lecturerCode: `GV${20 + i}`, classGroup: `Lớp ${i + 1}`, date: anchor }));
  const startDate = addDays(anchor, (filters.week - 1) * 7), endDate = addDays(startDate, 6);
  const matches = e => (!filters.lecturerId || filters.lecturerId === e.lecturerId) && ['courseCode', 'cohort', 'classId', 'sessionType', 'lecturerType'].every(k => !filters[k] || filters[k] === e[k])
    && (!filters.q || `${e.courseCode} ${e.courseName} ${e.classGroup} ${e.lecturerCode} ${e.lecturerName}`.toLowerCase().includes(filters.q.toLowerCase()));
  const rows = all.filter(e => e.date >= startDate && e.date <= endDate && matches(e) && (!filters.roomCode || filters.roomCode === e.roomCode) && (!filters.conflictsOnly || e.conflicts.length));
  const classRows = [...new Set(all.map(e => e.classId))].map(id => { const e = all.find(e => e.classId === id), term = all.filter(e => e.classId === id), week = rows.filter(e => e.classId === id); return { ...e, requiredPeriods: term.length * 3, scheduledPeriods: term.length * 3, missingPeriods: 0, weekPeriods: week.length * 3, weekSessions: week.length, conflictingEntries: week.filter(e => e.conflicts.length).length }; });
  if (state.incomplete) classRows.push({ ...base, requiredPeriods: 6, scheduledPeriods: 0, missingPeriods: 6, weekPeriods: 0, weekSessions: 0, conflictingEntries: 0 });
  const assignments = classRows.filter(a => matches(a) && ((!filters.roomCode && !filters.conflictsOnly) || rows.some(e => e.classId === a.classId)));
  const groups = [...new Set(rows.map(e => filters.groupBy === 'class' ? e.classId : String(e.lecturerId)))].map(id => { const entries = rows.filter(e => (filters.groupBy === 'class' ? e.classId : String(e.lecturerId)) === id); const e = entries[0]; return { id, label: filters.groupBy === 'class' ? e.classGroup : `${e.lecturerCode} · ${e.lecturerName}`, subtitle: e.lecturerType, entries, totalEntries: entries.length, truncated: false }; });
  const choices = key => [...new Set(all.map(e => String(e[key])))].map(value => ({ value, label: value }));
  const totalRows = filters.view === 'calendar' ? groups.length : filters.view === 'assignments' ? assignments.length : rows.length;
  const size = filters.view === 'calendar' ? 10 : 25, slice = values => values.slice(filters.page * size, (filters.page + 1) * size);
  const context = { planId: plan.id, versionId: version.summary.id, versionNumber: version.summary.number, state: state.exportHistory && filters.view === 'export' ? 'HISTORICAL' : version.summary.currentConfirmed ? 'CONFIRMED' : version.summary.currentDraft ? 'DRAFT' : 'HISTORICAL', complete: version.summary.complete, revision: plan.revision,
    name: plan.name, schoolCode: plan.schoolCode, schoolName: plan.schoolName, departmentName: plan.departmentName, semester: plan.semester, semesterStartDate: anchor, updatedAt: '2026-10-31T01:00:00Z', checkedAt: '2026-10-31T12:00:00Z' };
  return { context, filters, startDate, endDate, stats: { sessions: rows.length, weekPeriods: rows.length * 3, lecturers: new Set(rows.map(e => e.lecturerId)).size, classes: assignments.length,
    conflictingEntries: rows.filter(e => e.conflicts.length).length, requiredPeriods: assignments.reduce((n, a) => n + a.requiredPeriods, 0), scheduledPeriods: assignments.reduce((n, a) => n + a.scheduledPeriods, 0), missingPeriods: assignments.reduce((n, a) => n + a.missingPeriods, 0) },
    options: { lecturers: choices('lecturerId'), courses: choices('courseCode'), cohorts: choices('cohort'), classes: choices('classId'), rooms: choices('roomCode'), missingTypeSnapshot: false },
    page: filters.page, pageCount: filters.view === 'export' ? 1 : Math.ceil(totalRows / size), totalRows,
    groups: filters.view === 'calendar' ? slice(groups) : [], entries: filters.view === 'export' ? rows : filters.view === 'sessions' ? slice(rows) : [], assignments: filters.view === 'export' ? assignments : filters.view === 'assignments' ? slice(assignments) : [], warnings: [] };
}

function version(number = 1, complete = true) {
  return { summary: { id: number * 100, number, complete, currentDraft: true, currentConfirmed: false, createdBy: 'ADMIN', confirmedAt: null }, configuration,
    courses: [course], entryCount: complete ? 26 : 0,
    result: { schoolId: 'A', semesterStartDate: anchor, periods, saved: false, complete, unscheduledWarnings: complete ? [] : ['Thiếu phòng đủ sức chứa cho NET'],
      catalogContext: { lecturers: [{ lecturerId: 1, lecturerCode: 'GV01', teacherId: 'DRAFT_L1', accountTeacherId: null }] },
      scheduledClasses: complete ? [{ teacherId: 'DRAFT_L1', teacherName: 'Nguyễn Văn A', courseCode: 'NET', courseName: course.courseName, baseCourseName: course.courseName, classGroup: '01', room: '101', dayOfWeek: 2, startPeriod: 1, numPeriods: 3, weekSchedule: '11', credits: 3 }] : [] } };
}

async function navigate(page) {
  const menu = page.getByRole('button', { name: 'Mở điều hướng' }); if (await menu.isVisible()) await menu.click();
  await page.getByRole('button', { name: 'Bản lịch theo khoa', exact: true }).click();
}
async function boot(page, settings = {}) {
  const state = { empty: false, incomplete: false, conflict: false, stale: false, history: false, calls: [], generated: [], confirms: 0, visitingPreviews: [], visitingCommits: 0, profiles: [], adjustmentPreviews: [], adjustmentCommits: [], ...settings };
  const plan = { id: 1, schoolCode: 'A', schoolName: 'Trường A', departmentId: 1, departmentName: 'Khoa CNTT', name: 'Lịch học kỳ 1', semester: 'HK1-2026', semesterStartDate: anchor, revision: 1, state: 'DRAFT', confirmedVersion: null, draftVersion: state.history ? 2 : 1 };
  let draft = version(state.history ? 2 : 1, !state.incomplete);
  const visitors = state.emptyVisitors ? [] : [{ id: 9, lecturerCode: 'VIS01', fullName: 'Giảng viên thỉnh giảng', lecturerType: 'VISITING', active: true }];
  if (state.initialConfirmed) { plan.state = 'CONFIRMED'; plan.confirmedVersion = draft.summary.number; draft.summary.currentConfirmed = true; draft.result.saved = true; }
  const history = state.history ? [version(1)] : [];
  history.forEach(v => { v.summary.currentDraft = false; v.summary.confirmedAt = '2026-10-01T01:00:00'; });
  const detail = () => ({ plan, draft, versions: [draft, ...history].filter(Boolean).map(v => v.summary) });
  let adjustmentRows = [101, 102].map((id, i) => ({ id, entryKey: `KEY${id}`, classId: 'NET:LT', lecturerId: 1, lecturerName: 'Nguyễn Văn A', lecturerCode: 'GV01', courseCode: 'NET', courseName: course.courseName, classGroup: 'Lớp 01', date: addDays(anchor, i * 7), roomCode: '101', startPeriod: 1, numPeriods: 3, startTime: '07:00', endTime: '09:50', locked: !!state.adjustmentLocked && i === 0 }));
  const adjustmentLogs = [];
  await page.addInitScript(() => sessionStorage.setItem('campus-access-token', 'plan-token'));
  await page.route('https://fonts.googleapis.com/**', route => route.abort());
  await page.route('**/api/schedule/**', route => {
    const path = new URL(route.request().url()).pathname;
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify(path.endsWith('/auth/me') ? { role: 'ADMIN', teacherId: 'ADMIN', fullName: 'Quản lý', semesterStartDate: anchor } : []) });
  });
  await page.route('**/api/admin/institutions**', route => {
    const path = new URL(route.request().url()).pathname;
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify(path.endsWith('/departments') ? [{ id: 1, name: 'Khoa CNTT', active: true }] : path.endsWith('/scheduling-options') ? options : [school]) });
  });
  await page.route('**/api/admin/scheduling-plans**', async route => {
    const request = route.request(); const url = new URL(request.url()); const path = url.pathname.replace('/api/admin/scheduling-plans', '');
    state.calls.push({ path, method: request.method(), authorization: request.headers().authorization, body: request.headers()['content-type']?.includes('application/json') ? request.postDataJSON() : null });
    const respond = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (!path && request.method() === 'GET') return respond(state.empty ? [] : [plan]);
    if (!path) { state.empty = false; Object.assign(plan, request.postDataJSON(), { revision: 0, draftVersion: null }); draft = null; return respond(detail(), 201); }
    if (path.endsWith('/adjustment-options')) return respond({ revision: plan.revision, baseVersionId: draft.summary.id, snapshot: 'e'.repeat(64), lecturers: options.lecturers.map(l => l.lecturer), assignments: options.assignments, rooms: options.rooms, periods, classes: [{ classId: 'NET:LT', courseCode: 'NET', courseName: course.courseName, classGroup: 'Lớp 01', sessionType: 'THEORY', requiredPeriods: 6, scheduledPeriods: 6 }] });
    if (path.endsWith('/adjustment-entries')) return respond({ rows: adjustmentRows, page: 0, pageCount: 1, total: adjustmentRows.length });
    if (path.endsWith('/changes')) return respond({ rows: adjustmentLogs, page: 0, pageCount: 1, total: adjustmentLogs.length });
    if (path.endsWith('/adjustments/preview')) {
      const input = request.postDataJSON(); state.adjustmentPreviews.push(input);
      const picked = adjustmentRows.filter(r => input.entryIds.includes(r.id) || input.edits.some(e => e.entryId === r.id));
      let changes = picked.map(r => ({ entryKey: r.entryKey, before: r, after: input.action === 'DELETE' ? null : { ...r, ...(input.edits[0] || {}), ...(input.action === 'LOCK' ? { locked: input.locked } : {}), ...(input.action === 'AUTO' ? { date: input.fromDate } : {}) } }));
      if (input.action === 'RESTORE') changes = [{ entryKey: 'KEY101', before: adjustmentRows[0], after: { ...adjustmentRows[0], date: anchor, locked: false } }];
      const noSolution = state.adjustmentNoSolution && input.action === 'AUTO';
      state.adjustmentProposal = { previewToken: 'f'.repeat(64), action: input.action, revision: plan.revision, baseVersionId: draft.summary.id, status: noSolution ? 'NO_SOLUTION' : 'READY', message: noSolution ? 'Chưa tìm được phương án. Bản nháp cũ được giữ.' : 'Đề xuất giữ nguyên các buổi ngoài phạm vi.', canApply: !noSolution, complete: input.action !== 'DELETE', canConfirm: input.action !== 'DELETE', unchangedEntries: adjustmentRows.length - changes.length, lockedEntries: input.action === 'LOCK' && input.locked ? 1 : 0, beforeRequiredPeriods: 6, afterRequiredPeriods: 6, afterMissingPeriods: input.action === 'DELETE' ? 3 : 0, configurationChanged: input.action === 'RESTORE', changes: noSolution ? [] : changes, removedBusy: [], addedBusy: [], conflicts: [] };
      return respond(state.adjustmentProposal);
    }
    if (path.endsWith('/adjustments/commit')) {
      const command = request.postDataJSON(); state.adjustmentCommits.push(command); if (state.adjustmentStale) return respond({ message: 'Bản lịch hoặc danh mục đã thay đổi' }, 409);
      const old = draft; old.summary.currentDraft = false; history.unshift(old); draft = version(old.summary.number + 1, state.adjustmentProposal.complete); draft.customized = command.input.action !== 'RESTORE';
      adjustmentRows = adjustmentRows.flatMap(r => { const delta = state.adjustmentProposal.changes.find(d => d.entryKey === r.entryKey); return delta ? delta.after ? [delta.after] : [] : [r]; });
      plan.revision++; plan.state = 'DRAFT'; plan.draftVersion = draft.summary.number;
      adjustmentLogs.unshift({ id: adjustmentLogs.length + 1, action: command.input.action, sourceVersionId: old.summary.id, sourceVersionNumber: old.summary.number, resultVersionId: draft.summary.id, resultVersionNumber: draft.summary.number, actor: 'ADMIN', createdAt: '2026-10-31T15:00:00Z', affectedEntries: state.adjustmentProposal.changes.length, reason: command.input.reason });
      return respond(detail());
    }
    if (path.endsWith('/visiting-options')) return respond({ revision: plan.revision, baseVersionId: draft.summary.id, snapshot: 'c'.repeat(64), lecturers: visitors, assignments: visitors.map(v => ({ id: v.id * 10, lecturerId: v.id, courseCode: 'NET', courseName: course.courseName, cohort: '2024', theoryWeight: 1, practiceWeight: 1 })), rooms: options.rooms, periods,
      classes: [{ classId: 'NET:LT', parentClassId: 'NET', demandIndex: 1, courseCode: 'NET', courseName: course.courseName, cohort: '2024', classGroup: 'Lớp 01', sessionType: 'THEORY', studentCount: 50, credits: 3, studyStartDate: anchor, studyEndDate: course.studyEndDate, requiredPeriods: 6, scheduledPeriods: 6, lecturerCodes: [draft.visitingChange?.lecturerCode || 'GV01'] }], busySlots: draft.busySlots || [] });
    if (path.endsWith('/visiting-lecturers')) {
      const input = request.postDataJSON(); state.profiles.push(input); const row = { id: visitors.length + 10, lecturerCode: input.lecturerCode, fullName: input.fullName, lecturerType: 'VISITING', active: true }; visitors.push(row); return respond(row, 201);
    }
    if (path.endsWith('/visiting-lecturers/preview')) {
      const input = request.postDataJSON(); state.visitingPreviews.push(input); const visitor = visitors.find(v => v.id === input.lecturerId);
      const before = input.action === 'NEW' ? [] : [0, 7].map(offset => ({ classId: 'NET:LT', courseCode: 'NET', classGroup: 'Lớp 01', lecturerCode: 'GV01', roomCode: '101', date: addDays(anchor, offset), startTime: '07:00', endTime: '09:50', startPeriod: 1, numPeriods: 3 }));
      const after = input.timing === 'KEEP' ? before.map(b => ({ ...b, lecturerCode: visitor.lecturerCode })) : input.sessions.map(s => ({ ...s, classId: input.action === 'NEW' ? 'NEW:LT' : s.classId, courseCode: input.newClass?.courseCode || 'NET', classGroup: input.newClass?.classGroup || 'Lớp 01', lecturerCode: visitor.lecturerCode, roomCode: '101', startTime: '07:00', endTime: '09:50' }));
      const total = after.reduce((sum, s) => sum + s.numPeriods, 0), required = input.newClass?.totalPeriods || 6;
      const conflicts = input.busySlots.length ? [{ ...conflict, type: 'BUSY_CONFLICT', resource: visitor.lecturerCode, message: 'Buổi dạy trùng lịch bận của giảng viên.' }] : [];
      state.latestVisitingPreview = { previewToken: 'd'.repeat(64), revision: plan.revision, baseVersionId: draft.summary.id, lecturerCode: visitor.lecturerCode, action: input.action, timing: input.timing, complete: total === required, canConfirm: total === required && !conflicts.length, unchangedEntries: input.action === 'NEW' ? 2 : 0,
        changes: [{ classId: input.action === 'NEW' ? 'NEW:LT' : 'NET:LT', courseCode: input.newClass?.courseCode || 'NET', classGroup: input.newClass?.classGroup || 'Lớp 01', sessionType: input.newClass?.sessionType || 'THEORY', beforeLecturers: input.action === 'NEW' ? [] : ['GV01'], afterLecturer: visitor.lecturerCode, beforePeriods: input.action === 'NEW' ? 0 : 6, afterPeriods: total, requiredPeriods: required }], beforeEntries: before, afterEntries: after,
        busySlots: input.busySlots.map(b => ({ lecturerId: visitor.id, date: b.startDate, startTime: b.startTime, endTime: b.endTime, reason: b.reason })), conflicts };
      return respond(state.latestVisitingPreview);
    }
    if (path.endsWith('/visiting-lecturers/commit')) {
      state.visitingCommits++; if (state.visitingStale) return respond({ message: 'Lịch ngoài khoa vừa đổi' }, 409);
      const { input } = request.postDataJSON(), preview = state.latestVisitingPreview; draft.summary.currentDraft = false; history.unshift(draft);
      draft = version(draft.summary.number + 1, preview.complete); draft.visitingChange = { sourceVersionId: input.baseVersionId, lecturerId: input.lecturerId, lecturerCode: preview.lecturerCode, action: input.action, timing: input.timing }; draft.busySlots = preview.busySlots;
      draft.result.unscheduledWarnings = preview.complete ? [] : ['Lớp thỉnh giảng còn thiếu tiết']; plan.revision++; plan.state = 'DRAFT'; plan.draftVersion = draft.summary.number; return respond(detail());
    }
    if (path.endsWith('/options')) return respond(options);
    if (path.endsWith('/generate-preview')) {
      const raw = request.postDataBuffer().toString('latin1');
      const parts = raw.split('--' + request.headers()['content-type'].split('boundary=')[1]);
      const bytes = name => { const part = parts.find(p => p.includes(`name="${name}"`)); return part ? Buffer.from(part.slice(part.indexOf('\r\n\r\n') + 4, part.lastIndexOf('\r\n')), 'latin1') : null; };
      state.generated.push({ configuration: JSON.parse(bytes('configuration').toString('utf8')), revision: bytes('revision').toString(), plan: bytes('planFile'), teacherFile: bytes('teacherFile') });
      if (state.stale) return respond({ message: 'Revision changed' }, 409);
      if (draft) { draft.summary.currentDraft = false; history.unshift(draft); }
      draft = version((plan.draftVersion || 0) + 1, !state.incomplete); plan.draftVersion = draft.summary.number; plan.revision++; plan.state = 'DRAFT'; return respond(detail());
    }
    if (path.endsWith('/validate')) return respond(state.conflict ? [conflict] : []);
    if (path.endsWith('/confirm')) {
      state.confirms++;
      if (state.conflict) return respond({ message: 'Bản nháp chưa thể xác nhận.', conflicts: [conflict] }, 409);
      draft.summary.currentConfirmed = true; draft.summary.confirmedAt = '2026-10-31T10:00:00'; draft.result.saved = true; plan.state = 'CONFIRMED'; plan.confirmedVersion = draft.summary.number; plan.revision++; return respond(detail());
    }
    if (path.endsWith('/overview')) {
      if (state.boardFailure) return respond({ message: 'Không tải được lịch toàn khoa' }, 500);
      if (state.delayWeekOne && url.searchParams.get('week') === '1') await new Promise(resolve => setTimeout(resolve, 500));
      const current = [draft, ...history].find(v => v.summary.id === Number(path.split('/')[3]));
      return respond(mockBoard(state, plan, current, url.searchParams));
    }
    if (path.endsWith('/entries')) {
      const current = [draft, ...history].find(v => v.summary.id === Number(path.split('/')[3]));
      const pageIndex = Number(url.searchParams.get('page'));
      return respond({ rows: current.entryCount ? Array.from({ length: pageIndex ? 1 : 25 }, (_, i) => ({ id: (pageIndex * 25) + i + 101, date: anchor, startTime: '07:00', endTime: '09:50', courseCode: 'NET', courseName: course.courseName, cohort: '2024', classGroup: `Lớp ${pageIndex * 25 + i + 1}`, lecturerCode: 'GV01', lecturerName: 'Nguyễn Văn A', roomCode: '101', sessionType: 'THEORY', numPeriods: 3 })) : [], page: pageIndex, pageCount: current.entryCount ? 2 : 0, total: current.entryCount });
    }
    if (path.includes('/versions/')) return respond([draft, ...history].find(v => v.summary.id === Number(path.split('/')[3])));
    return respond(detail());
  });
  await page.goto('/'); await expect(page.getByRole('heading', { level: 1 })).toContainText('Xếp lịch toàn trường'); await navigate(page);
  if (!state.empty) { await page.getByLabel('Bản lịch đã lưu', { exact: true }).selectOption('1'); await expect(page.getByText('Chuẩn bị bản nháp tiếp theo', { exact: true })).toBeVisible(); await expect(page.getByLabel('Mã môn kế hoạch dòng 1', { exact: true })).toHaveValue('NET'); }
  return state;
}

test('theme: faculty legend and disabled actions remain readable with focus and forced colors', async ({ page }, testInfo) => {
  await boot(page, { initialConfirmed: true, adjustmentLocked: true, conflict: true });
  const legend = page.getByRole('list', { name: 'Chú giải bảng lịch' });
  for (const label of ['LT · Lý thuyết', 'TH · Thực hành', 'Thỉnh giảng', 'Trùng lịch', 'Đã khóa']) {
    const item = legend.getByRole('listitem').filter({ hasText: label }); await expect(item).toBeVisible(); await expectReadable(item);
  }
  const confirm = page.getByRole('button', { name: 'Xác nhận bản nháp', exact: true }); await expect(confirm).toBeDisabled(); await expectReadable(confirm);
  const field = page.getByLabel('Tuần toàn khoa', { exact: true }); await field.focus();
  expect(await field.evaluate(e => parseFloat(getComputedStyle(e).outlineWidth))).toBeGreaterThanOrEqual(3);
  await expectPageFits(page); await page.screenshot({ path: testInfo.outputPath('theme-faculty-board.png'), fullPage: true });
  await page.emulateMedia({ forcedColors: 'active' }); await expect(legend).toBeVisible();
  await expect(legend.getByText('Trùng lịch', { exact: true })).toBeVisible(); await expect(legend.getByText('Đã khóa', { exact: true })).toBeVisible();
  expect(await confirm.evaluate(e => parseFloat(getComputedStyle(e).borderTopWidth))).toBeGreaterThanOrEqual(1);
  await page.setViewportSize({ width: 320, height: 844 }); await expectPageFits(page);
});

test('create, generate one-file preview and reopen its saved courses without confirmation', async ({ page }, testInfo) => {
  const state = await boot(page, { empty: true });
  await page.getByRole('button', { name: 'Tạo bản lịch', exact: true }).click();
  await page.getByLabel('Trường của bản lịch').selectOption('A'); await page.getByLabel('Khoa của bản lịch').selectOption('1');
  await page.getByLabel('Tên bản lịch', { exact: true }).fill('Kế hoạch mới'); await page.getByLabel('Học kỳ / đợt').fill('HK1-2026');
  await page.getByRole('button', { name: 'Lưu bản lịch mới' }).click();
  await page.getByLabel('Kế hoạch mở lớp từ Excel').setInputFiles({ name: 'plan.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: Buffer.from(await planWorkbook([course]).xlsx.writeBuffer()) });
  await page.getByRole('button', { name: 'Xếp và lưu bản nháp mới' }).click();
  await expect(page.getByLabel('Mã môn kế hoạch dòng 1', { exact: true })).toHaveValue('NET');
  expect(state.generated).toHaveLength(1); expect(state.generated[0].configuration.replaceSchoolSchedule).toBe(false); expect(state.generated[0].revision).toBe('0'); expect(state.generated[0].teacherFile).toBeNull(); expect(state.confirms).toBe(0);
  await page.reload(); await navigate(page); await page.getByLabel('Bản lịch đã lưu', { exact: true }).selectOption('1');
  await expect(page.getByLabel('Tổng tiết LT kế hoạch dòng 1', { exact: true })).toHaveValue('6');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('department-draft.png'), fullPage: true });
});

test('manual adjustment previews one occurrence and saves a new draft while keeping the published version', async ({ page }, testInfo) => {
  const state = await boot(page, { initialConfirmed: true });
  const lesson = page.getByRole('button', { name: /^Chi tiết NET Lớp 01 10\/08\/2026/ }).filter({ visible: true });
  await lesson.click(); await page.getByRole('button', { name: 'Sửa buổi này', exact: true }).click();
  const panel = page.getByRole('region', { name: 'Điều chỉnh bản nháp', exact: true });
  await expect(panel.getByLabel('Ngày buổi học')).toHaveValue(anchor);
  await panel.getByLabel('Ngày buổi học').fill('2026-08-11'); await panel.getByLabel('Lý do điều chỉnh').fill('Đổi lịch theo khoa');
  await panel.getByRole('button', { name: 'Xem trước điều chỉnh', exact: true }).click();
  await expect(panel.getByLabel('So sánh điều chỉnh')).toContainText('6 → 6 tiết'); expect(state.adjustmentCommits).toHaveLength(0);
  await expect(page.getByRole('button', { name: 'Xác nhận bản nháp', exact: true })).toBeDisabled();
  await page.screenshot({ path: testInfo.outputPath('manual-adjustment.png'), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  await panel.getByRole('button', { name: 'Lưu điều chỉnh vào nháp', exact: true }).click();
  await expect(page.getByText('Lịch cá nhân đang dùng phiên bản 1.', { exact: false })).toBeVisible();
  await expect(page.getByLabel('Phiên bản đang xem')).toHaveValue('200'); expect(state.confirms).toBe(0);
  expect(state.adjustmentCommits[0].input.edits).toEqual([{ entryId: 101, classId: null, date: '2026-08-11', roomId: 10, startPeriod: 1, numPeriods: 3 }]);
  await page.getByText('Nhật ký thay đổi bản lịch', { exact: true }).click();
  await expect(page.getByRole('table', { name: 'Người sửa và phiên bản kết quả' })).toContainText('Đổi lịch theo khoa');
});

test('locking an explicit occurrence keeps the selected scope and changing input discards the proposal', async ({ page }) => {
  const state = await boot(page); await page.getByRole('button', { name: 'Điều chỉnh lịch', exact: true }).click();
  const panel = page.getByRole('region', { name: 'Điều chỉnh bản nháp', exact: true });
  await panel.getByLabel('Thao tác điều chỉnh').selectOption('LOCK'); await panel.getByLabel('Chọn buổi 101', { exact: true }).check();
  await panel.getByRole('button', { name: 'Xem trước điều chỉnh', exact: true }).click();
  await expect(panel.getByLabel('So sánh điều chỉnh')).toContainText('Đã khóa');
  expect(state.adjustmentPreviews[0].entryIds).toEqual([101]); expect(state.adjustmentPreviews[0].classIds).toEqual([]);
  await panel.getByLabel('Trạng thái buổi').selectOption('false'); await expect(panel.getByLabel('So sánh điều chỉnh')).toHaveCount(0);
  await panel.getByRole('button', { name: 'Xem trước điều chỉnh', exact: true }).click();
  await panel.getByRole('button', { name: 'Lưu điều chỉnh vào nháp', exact: true }).click(); await expect(page.getByLabel('Phiên bản đang xem')).toHaveValue('200');
  expect(state.adjustmentCommits[0].input.locked).toBe(false); expect(state.confirms).toBe(0);
});

test('automatic repair with no solution keeps the old draft and cannot offer saving', async ({ page }, testInfo) => {
  const state = await boot(page, { adjustmentNoSolution: true }); await page.getByRole('button', { name: 'Điều chỉnh lịch', exact: true }).click();
  const panel = page.getByRole('region', { name: 'Điều chỉnh bản nháp', exact: true });
  await panel.getByLabel('Thao tác điều chỉnh').selectOption('AUTO'); await panel.getByLabel('Chọn phạm vi').selectOption('classes');
  await panel.getByLabel('NET · Lớp 01 · LT', { exact: false }).check(); await panel.getByLabel('Xếp lại từ ngày').fill('2026-08-17');
  await panel.getByRole('button', { name: 'Xem trước điều chỉnh', exact: true }).click();
  await expect(panel.getByLabel('So sánh điều chỉnh')).toContainText('Bản nháp cũ được giữ'); await expect(panel.getByRole('button', { name: 'Lưu điều chỉnh vào nháp', exact: true })).toBeDisabled();
  expect(state.adjustmentPreviews[0].classIds).toEqual(['NET:LT']); expect(state.adjustmentPreviews[0].roomIds).toEqual([10]); expect(state.adjustmentPreviews[0].entryIds).toEqual([]);
  expect(state.adjustmentCommits).toHaveLength(0); await expect(page.getByLabel('Phiên bản đang xem')).toHaveValue('100');
  await page.screenshot({ path: testInfo.outputPath('partial-no-solution.png'), fullPage: true });
});

test('historical restore requires a preview and stale save retains the reason without retrying', async ({ page }) => {
  const state = await boot(page, { history: true, adjustmentStale: true });
  await page.getByLabel('Phiên bản đang xem').selectOption('100'); await page.getByRole('button', { name: 'Khôi phục phiên bản đang xem', exact: true }).click();
  const panel = page.getByRole('region', { name: 'Điều chỉnh bản nháp', exact: true });
  await expect(panel.getByLabel('Phiên bản cần khôi phục')).toHaveValue('100'); await panel.getByLabel('Cho phép khôi phục cả những buổi hiện đang khóa').check(); await panel.getByLabel('Lý do điều chỉnh').fill('Trở lại lịch đã duyệt');
  await panel.getByRole('button', { name: 'Xem trước điều chỉnh', exact: true }).click(); await panel.getByRole('button', { name: 'Lưu điều chỉnh vào nháp', exact: true }).click();
  await expect(panel.getByRole('alert')).toContainText('Đầu vào được giữ'); await expect(panel.getByLabel('Lý do điều chỉnh')).toHaveValue('Trở lại lịch đã duyệt'); await expect(panel.getByLabel('So sánh điều chỉnh')).toHaveCount(0);
  expect(state.adjustmentCommits).toHaveLength(1); expect(state.adjustmentCommits[0].input.restoreVersionId).toBe(100); expect(state.adjustmentCommits[0].input.baseVersionId).toBe(200); expect(state.adjustmentCommits[0].input.overrideLocks).toBe(true);
  expect(state.confirms).toBe(0);
});

test('locked lesson disables moving and traps keyboard focus while offering an explicit unlock', async ({ page }) => {
  const state = await boot(page, { adjustmentLocked: true });
  await page.getByRole('button', { name: /^Chi tiết NET Lớp 01 10\/08\/2026/ }).filter({ visible: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Chi tiết buổi · NET' });
  await expect(dialog.getByRole('button', { name: 'Sửa buổi này', exact: true })).toBeDisabled(); await expect(dialog.getByRole('button', { name: 'Xếp lại buổi này', exact: true })).toBeDisabled();
  await page.keyboard.press('Shift+Tab'); await expect(dialog.getByRole('button', { name: 'Mở khóa buổi này', exact: true })).toBeFocused();
  await page.keyboard.press('Tab'); await expect(dialog.getByRole('button', { name: 'Đóng chi tiết buổi', exact: true })).toBeFocused();
  await dialog.getByRole('button', { name: 'Mở khóa buổi này', exact: true }).click();
  const panel = page.getByRole('region', { name: 'Điều chỉnh bản nháp', exact: true }); await expect(panel.getByLabel('Trạng thái buổi')).toHaveValue('false');
  await panel.getByRole('button', { name: 'Xem trước điều chỉnh', exact: true }).click(); expect(state.adjustmentPreviews[0].entryIds).toEqual([101]); expect(state.adjustmentPreviews[0].locked).toBe(false);
});

test('restoring historical content creates a new draft and journal without confirming automatically', async ({ page }) => {
  const state = await boot(page, { history: true });
  await page.getByLabel('Phiên bản đang xem').selectOption('100'); await page.getByRole('button', { name: 'Khôi phục phiên bản đang xem', exact: true }).click();
  const panel = page.getByRole('region', { name: 'Điều chỉnh bản nháp', exact: true });
  await panel.getByRole('button', { name: 'Xem trước điều chỉnh', exact: true }).click();
  await expect(panel.getByLabel('So sánh điều chỉnh')).toContainText('chế độ lập lịch'); await panel.getByRole('button', { name: 'Lưu điều chỉnh vào nháp', exact: true }).click();
  await expect(page.getByLabel('Phiên bản đang xem')).toHaveValue('300'); await page.getByText('Nhật ký thay đổi bản lịch', { exact: true }).click();
  await expect(page.getByRole('table', { name: 'Người sửa và phiên bản kết quả' })).toContainText('Khôi phục phiên bản');
  expect(state.adjustmentCommits).toHaveLength(1); expect(state.adjustmentCommits[0].input.overrideLocks).toBe(false); expect(state.confirms).toBe(0);
});

test('fresh validation and confirmation show room conflicts and preserve the draft until a deliberate retry', async ({ page }, testInfo) => {
  const state = await boot(page, { conflict: true });
  await page.getByRole('button', { name: 'Kiểm tra bản nháp đã lưu' }).click();
  await expect(page.getByLabel('Kết quả kiểm tra bản nháp')).toContainText('Phòng đã được khoa khác sử dụng.');
  await page.getByRole('button', { name: 'Xác nhận bản nháp', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('chưa thể xác nhận'); expect(state.confirms).toBe(1);
  await expect(page.getByText('Chưa có phiên bản nào đưa vào lịch cá nhân.', { exact: false })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('department-conflicts.png'), fullPage: true });
  state.conflict = false; await page.getByRole('button', { name: 'Xác nhận bản nháp', exact: true }).click();
  await expect(page.getByText('Lịch cá nhân đang dùng phiên bản 1.', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Xác nhận bản nháp', exact: true })).toBeDisabled(); expect(state.confirms).toBe(2);
  expect(state.calls.filter(c => c.path.endsWith('/confirm')).every(c => c.body.revision === 1 && c.authorization === 'Bearer plan-token')).toBe(true);
});

test('editing inputs blocks confirmation; historical versions cannot be confirmed', async ({ page }) => {
  await boot(page, { history: true });
  const confirm = page.getByRole('button', { name: 'Xác nhận bản nháp', exact: true }); await expect(confirm).toBeEnabled();
  await page.getByLabel('Tổng tiết LT kế hoạch dòng 1', { exact: true }).fill('9'); await expect(confirm).toBeDisabled();
  await expect(page.getByText('Nội dung nhập đã thay đổi.', { exact: false })).toBeVisible();
  await page.getByLabel('Tổng tiết LT kế hoạch dòng 1', { exact: true }).fill('6'); await expect(confirm).toBeEnabled();
  await page.getByLabel('Phiên bản đang xem').selectOption('100'); await expect(confirm).toBeDisabled();
  await expect(page.getByText('Đang xem lịch sử.', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Kiểm tra bản nháp đã lưu' })).toBeDisabled();
});

test('saved occurrences paginate and exported workbook identifies draft version', async ({ page }) => {
  await boot(page, { manySessions: true });
  await page.getByRole('tab', { name: 'Danh sách buổi', exact: true }).click();
  await expect(page.getByRole('table', { name: 'Buổi học của phiên bản đã lưu', exact: false })).toContainText('Lớp 25');
  await page.getByRole('button', { name: 'Buổi học trang sau' }).click();
  await expect(page.getByRole('table', { name: 'Buổi học của phiên bản đã lưu', exact: false })).toContainText('Lớp 26');
  const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Xuất Excel phiên bản' }).click()]);
  expect(download.suggestedFilename()).toContain('nhap-lich-su-v1');
  const stream = await download.createReadStream(); const chunks = []; for await (const c of stream) chunks.push(c);
  const book = new ExcelJS.Workbook(); await book.xlsx.load(Buffer.concat(chunks));
  expect(book.getWorksheet('BanLich').getCell('B4').value).toBe('Bản nháp'); expect(book.getWorksheet('MonHoc').rowCount).toBe(2);
});

test('incomplete and stale drafts remain saved without offering confirmation or automatic resubmission', async ({ page }) => {
  const state = await boot(page, { incomplete: true, stale: true });
  await expect(page.getByRole('button', { name: 'Xác nhận bản nháp', exact: true })).toBeDisabled();
  await expect(page.getByText('Thiếu phòng đủ sức chứa cho NET')).toBeVisible();
  await page.getByRole('button', { name: 'Xếp và lưu bản nháp mới' }).click();
  await expect(page.getByRole('alert')).toContainText('Bản lịch hoặc danh mục đã thay đổi');
  expect(state.generated).toHaveLength(1); expect(state.confirms).toBe(0);
  await page.getByRole('button', { name: 'Mở lại bản lịch', exact: true }).click();
  await expect(page.getByLabel('Mã môn kế hoạch dòng 1', { exact: true })).toHaveValue('NET');
  expect(state.generated).toHaveLength(1);
});

test('faculty board shares week and combined filters between calendar and assignment tabs and restores preferences', async ({ page }, testInfo) => {
  await boot(page); const board = page.getByRole('region', { name: 'Bảng lịch toàn khoa' });
  await expect(board.getByLabel('Số liệu toàn khoa')).toContainText('9');
  await page.getByLabel('Lọc loại giảng viên toàn khoa', { exact: true }).selectOption('VISITING');
  await page.getByLabel('Lọc lt/th toàn khoa', { exact: true }).selectOption('PRACTICE');
  await page.getByLabel('Lọc phòng toàn khoa', { exact: true }).selectOption('LAB');
  await expect(board.getByLabel('Số liệu toàn khoa')).toContainText('3');
  await page.getByRole('tab', { name: 'Phân công toàn khoa', exact: true }).click();
  await expect(board.getByRole('table', { name: 'Phân công toàn khoa', exact: false })).toContainText('GV03');
  await expect(board.getByRole('table', { name: 'Phân công toàn khoa', exact: false }).locator('tbody tr')).toHaveCount(1);
  await page.reload(); await navigate(page); await page.getByLabel('Bản lịch đã lưu', { exact: true }).selectOption('1');
  await expect(page.getByRole('tab', { name: 'Phân công toàn khoa', exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByLabel('Lọc phòng toàn khoa', { exact: true })).toHaveValue('LAB');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('faculty-assignment-filtered.png'), fullPage: true });
});

test('faculty lesson details work by keyboard and conflict-only filter keeps reports visible', async ({ page }, testInfo) => {
  await boot(page, { conflict: true }); const board = page.getByRole('region', { name: 'Bảng lịch toàn khoa' });
  await page.getByRole('checkbox', { name: 'Chỉ buổi có xung đột' }).check();
  const lesson = board.getByRole('button', { name: /Chi tiết NET Lớp 01 10\/08\/2026/ });
  await lesson.focus(); await page.keyboard.press('Enter');
  const dialog = page.getByRole('dialog', { name: 'Chi tiết buổi · NET' });
  await expect(dialog).toContainText('Phòng đã được khoa khác sử dụng.'); await expect(dialog).toContainText('07:00–09:50');
  await page.keyboard.press('Escape'); await expect(dialog).toHaveCount(0); await expect(lesson).toBeFocused();
  await page.screenshot({ path: testInfo.outputPath('faculty-week-conflicts.png'), fullPage: true });
});

test('filtered faculty Excel fetches fresh status and includes every matched occurrence and matching workload', async ({ page }) => {
  const state = await boot(page); state.exportHistory = true;
  await page.getByLabel('Lọc giảng viên toàn khoa', { exact: true }).selectOption('1'); await page.getByLabel('Lọc môn toàn khoa', { exact: true }).selectOption('NET');
  const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Xuất toàn khoa theo bộ lọc', exact: true }).click()]);
  expect(download.suggestedFilename()).toContain('v1-historical-tuan-1');
  const chunks = []; for await (const chunk of await download.createReadStream()) chunks.push(chunk);
  const book = new ExcelJS.Workbook(); await book.xlsx.load(Buffer.concat(chunks));
  expect(book.getWorksheet('LichToanKhoa').rowCount).toBe(3); expect(book.getWorksheet('PhanCongToanKhoa').rowCount).toBe(2);
  expect(book.getWorksheet('ThongTin').getCell('B7').value).toBe('Phiên bản lịch sử');
  expect(book.getWorksheet('ThongTin').getCell('B14').value).toBe(6);
  expect(state.calls.some(c => c.path.endsWith('/overview'))).toBe(true);
});

test('faculty board paginates groups, changes grouping and handles empty weeks and transient errors', async ({ page }) => {
  const state = await boot(page, { manyGroups: true }); const board = page.getByRole('region', { name: 'Bảng lịch toàn khoa' });
  await expect(board.getByText('11 hàng lịch', { exact: false })).toBeVisible(); await page.getByRole('button', { name: 'Toàn khoa trang sau', exact: true }).click();
  await expect(board.getByText('Trang 2/2', { exact: false })).toBeVisible();
  await page.getByLabel('Hàng lịch theo', { exact: true }).selectOption('class'); await expect(board.getByText('Trang 1/2', { exact: false })).toBeVisible();
  await page.getByLabel('Tuần toàn khoa', { exact: true }).selectOption('100'); await expect(board.getByText('Tuần này không có buổi học khớp bộ lọc.', { exact: true })).toBeVisible();
  state.boardFailure = true; await page.getByRole('button', { name: 'Tải lại bảng toàn khoa' }).click(); await expect(board.getByRole('alert')).toContainText('Không tải được lịch toàn khoa');
  state.boardFailure = false; await page.getByRole('button', { name: 'Thử tải bảng lại' }).click(); await expect(board.getByRole('alert')).toHaveCount(0);
});

test('a delayed old-week response cannot replace the newly selected faculty week', async ({ page }) => {
  const state = await boot(page); const board = page.getByRole('region', { name: 'Bảng lịch toàn khoa' });
  await expect(board.getByLabel('Số liệu toàn khoa')).toBeVisible(); state.delayWeekOne = true;
  await page.getByRole('button', { name: 'Tải lại bảng toàn khoa' }).click();
  await page.getByLabel('Tuần toàn khoa', { exact: true }).selectOption('2');
  await expect(board.getByText('17/08/2026 – 23/08/2026', { exact: true })).toBeVisible();
  await expect(board.getByLabel('Số liệu toàn khoa')).toContainText('3');
  await expect(board.getByText('17/08/2026 – 23/08/2026', { exact: true })).toBeVisible();
});

async function openVisiting(page) {
  await page.getByRole('button', { name: 'Bổ sung thỉnh giảng', exact: true }).click();
  const panel = page.getByRole('region', { name: 'Bổ sung thỉnh giảng', exact: true });
  await expect(panel.getByLabel('Giảng viên thỉnh giảng', { exact: true })).toBeVisible(); return panel;
}

test('visiting teacher receives a class only after preview and saved draft keeps the published version', async ({ page }, testInfo) => {
  const state = await boot(page, { initialConfirmed: true }); const panel = await openVisiting(page);
  await panel.getByRole('checkbox', { name: 'Nhận NET Lớp 01 THEORY', exact: true }).check();
  await panel.getByRole('button', { name: 'Xem trước thay đổi thỉnh giảng' }).click();
  const preview = panel.getByLabel('Đề xuất thỉnh giảng đã xem trước'); await expect(preview).toContainText('6'); expect(state.visitingCommits).toBe(0);
  await expect(preview.getByRole('table', { name: 'Khối lượng lớp/nhóm thay đổi' })).toContainText('GV01'); await expect(preview).toContainText('VIS01');
  expect(state.visitingPreviews[0]).toMatchObject({ action: 'RECEIVE', timing: 'KEEP', classIds: ['NET:LT'], newClass: null });
  await page.screenshot({ path: testInfo.outputPath('visiting-transfer-preview.png'), fullPage: true });
  await panel.getByRole('button', { name: 'Lưu đề xuất vào bản nháp' }).click();
  await expect(page.getByText('Lịch cá nhân đang dùng phiên bản 1.', { exact: false })).toBeVisible();
  await expect(page.getByText('Bản nháp có phân công thỉnh giảng hoặc lịch cố định.', { exact: false })).toBeVisible();
  expect(state.visitingCommits).toBe(1); expect(state.confirms).toBe(0);
});

test('creating a visiting profile saves assignments without provisioning an account and selects the new roster entry', async ({ page }) => {
  const state = await boot(page, { emptyVisitors: true }); const panel = await openVisiting(page);
  await panel.getByRole('checkbox', { name: 'Nhận NET Lớp 01 THEORY', exact: true }).check();
  await panel.getByRole('button', { name: 'Tạo hồ sơ thỉnh giảng và phân công', exact: true }).click();
  const form = panel.getByRole('form', { name: 'Tạo hồ sơ thỉnh giảng', exact: true });
  await form.getByLabel('Mã GV thỉnh giảng mới', { exact: true }).fill('VISNEW'); await form.getByLabel('Họ tên thỉnh giảng mới', { exact: true }).fill('Nguyễn Thỉnh Giảng');
  await form.getByRole('button', { name: 'Lưu hồ sơ và phân công thỉnh giảng' }).click();
  await expect(panel.getByLabel('Giảng viên thỉnh giảng', { exact: true })).toHaveValue('10');
  expect(state.profiles).toHaveLength(1); expect(state.profiles[0].assignments).toMatchObject([{ courseCode: 'NET', cohort: '2024', theoryWeight: 1, practiceWeight: 1 }]);
  expect(state.profiles[0].password).toBeUndefined(); expect(state.profiles[0].role).toBeUndefined();
  await panel.getByRole('button', { name: 'Xem trước thay đổi thỉnh giảng' }).click(); await expect(panel.getByLabel('Đề xuất thỉnh giảng đã xem trước')).toContainText('VISNEW');
});

test('fixed visiting classes expand a weekly range and changed input invalidates the reviewed proposal', async ({ page }) => {
  const state = await boot(page); const panel = await openVisiting(page);
  await panel.getByLabel('Thao tác thỉnh giảng', { exact: true }).selectOption('NEW'); await panel.getByLabel('Tổng tiết lớp mới', { exact: true }).fill('6');
  await panel.getByRole('button', { name: 'Thêm dòng lịch cố định' }).click();
  await panel.getByLabel('Từ ngày cố định dòng 1', { exact: true }).fill('2026-08-16'); await panel.getByLabel('Đến ngày cố định dòng 1', { exact: true }).fill('2026-08-23');
  await panel.getByLabel('Thứ cố định dòng 1', { exact: true }).selectOption('8'); await panel.getByRole('button', { name: 'Xem trước thay đổi thỉnh giảng' }).click();
  await expect(panel.getByRole('button', { name: 'Lưu đề xuất vào bản nháp' })).toBeVisible();
  expect(state.visitingPreviews[0]).toMatchObject({ action: 'NEW', timing: 'FIXED', classIds: [], newClass: { totalPeriods: 6 } });
  expect(state.visitingPreviews[0].sessions.map(s => s.date)).toEqual(['2026-08-16', '2026-08-23']);
  await panel.getByLabel('Tên lớp/nhóm mới', { exact: true }).fill('Lớp TG02'); await expect(panel.getByRole('button', { name: 'Lưu đề xuất vào bản nháp' })).toHaveCount(0);
  expect(state.visitingCommits).toBe(0);
});

test('a busy visiting proposal can be kept as a draft with conflicts and never publishes automatically', async ({ page }, testInfo) => {
  const state = await boot(page, { initialConfirmed: true }); const panel = await openVisiting(page);
  await panel.getByRole('checkbox', { name: 'Nhận NET Lớp 01 THEORY', exact: true }).check(); await panel.getByRole('button', { name: 'Thêm khoảng bận' }).click();
  await panel.getByRole('button', { name: 'Xem trước thay đổi thỉnh giảng' }).click();
  await expect(panel.getByText('Buổi dạy trùng lịch bận của giảng viên.', { exact: true })).toBeVisible();
  await expect(panel.getByRole('button', { name: 'Lưu đề xuất vào bản nháp' })).toBeEnabled();
  expect(state.latestVisitingPreview.canConfirm).toBe(false);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('visiting-busy-preview.png'), fullPage: true });
  await panel.getByRole('button', { name: 'Lưu đề xuất vào bản nháp' }).click(); expect(state.confirms).toBe(0);
  await expect(page.getByText('Lịch cá nhân đang dùng phiên bản 1.', { exact: false })).toBeVisible();
});

test('a stale visiting commit keeps entered selections and requires another preview instead of retrying', async ({ page }) => {
  const state = await boot(page, { visitingStale: true }); const panel = await openVisiting(page);
  const checkbox = panel.getByRole('checkbox', { name: 'Nhận NET Lớp 01 THEORY', exact: true }); await checkbox.check();
  await panel.getByRole('button', { name: 'Xem trước thay đổi thỉnh giảng' }).click(); await panel.getByRole('button', { name: 'Lưu đề xuất vào bản nháp' }).click();
  await expect(panel.getByRole('alert')).toContainText('Dữ liệu đã thay đổi sau khi xem trước'); await expect(checkbox).toBeChecked(); expect(state.visitingCommits).toBe(1);
  await expect(panel.getByRole('button', { name: 'Lưu đề xuất vào bản nháp' })).toHaveCount(0);
  state.visitingStale = false; await panel.getByRole('button', { name: 'Tải lại nguồn thỉnh giảng' }).click(); await expect(checkbox).toBeChecked();
  await panel.getByRole('button', { name: 'Xem trước thay đổi thỉnh giảng' }).click(); await expect(panel.getByRole('button', { name: 'Lưu đề xuất vào bản nháp' })).toBeVisible();
  expect(state.visitingCommits).toBe(1);
});
