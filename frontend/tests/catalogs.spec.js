import { test, expect } from '@playwright/test';
import ExcelJS from 'exceljs';

test.setTimeout(60000);

async function navigate(page, name) {
  const menu = page.getByRole('button', { name: 'Mở điều hướng' });
  if (await menu.isVisible()) await menu.click();
  await page.getByRole('button', { name, exact: true }).click();
}

async function boot(page, role = 'ADMIN') {
  const state = {
    schools: [{ schoolCode: 'PTIT', name: 'Học viện PTIT', active: true, revision: 0 }],
    departments: { PTIT: [{ id: 1, schoolCode: 'PTIT', departmentCode: 'CNTT', name: 'Công nghệ thông tin', active: true, revision: 0 }] },
    lecturers: { PTIT: [{ id: 1, schoolCode: 'PTIT', lecturerCode: 'GV01', fullName: 'Nguyễn Văn A', departmentId: 1, lecturerType: 'STAFF', accountTeacherId: 'ACCOUNT01', active: true, revision: 0 }] },
    rooms: { PTIT: [{ id: 1, schoolCode: 'PTIT', roomCode: '101-A2', roomName: 'Phòng 101', roomType: 'THEORY_STANDARD', capacity: 60, isLab: false, building: 'A2', active: true, revision: 0 }] },
    assignments: {}, calls: [], accountBatches: [], accountStale: false, previewCalls: 0, commitCalls: 0, previewInvalid: false, staleCommit: false, staleEdit: false, nextId: 10,
    legacy: [{ schoolCode: 'OLD', names: ['Tên cũ A', 'Tên đúng B'], roomCount: 1, registered: false, supportedCode: true,
      lecturers: [{ lecturerCode: 'OLD01', fullName: 'Trần Văn B', accountTeacherId: 'ACCOUNT_OLD01' }] }],
  };
  await page.addInitScript(() => sessionStorage.setItem('campus-access-token', 'catalog-test-token'));
  await page.route('https://fonts.googleapis.com/**', route => route.abort());
  await page.route('**/api/schedule/**', route => {
    const path = new URL(route.request().url()).pathname;
    const body = path.endsWith('/auth/me') ? { role, teacherId: role === 'ADMIN' ? 'ADMIN' : 'ACCOUNT01', username: 'user', fullName: 'Người dùng', semesterStartDate: '2026-08-10' }
      : path.endsWith('/rooms') ? [] : { lessons: [], schools: [], startDate: '2026-08-10', endDate: '2026-08-16' };
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
  });
  await page.route('**/api/admin/institutions**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace('/api/admin/institutions', '');
    const method = request.method();
    state.calls.push({ path, method, authorization: request.headers().authorization, body: request.headers()['content-type']?.includes('application/json') ? request.postDataJSON() : null });
    const respond = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (path === '/legacy') return respond(state.legacy);
    const parts = path.split('/').filter(Boolean);
    const school = parts[0];
    if (parts[1] === 'lecturer-accounts') {
      const input = request.postDataJSON(); state.accountBatches.push(input);
      if (state.accountStale) return respond({ message: 'Danh mục vừa thay đổi.' }, 409);
      const selected = state.lecturers[school].filter(r => input.lecturers.some(t => t.id === r.id));
      selected.forEach(row => Object.assign(row, { accountTeacherId: row.lecturerCode, accountUsername: row.lecturerCode, autoProvisioned: true, revision: row.revision + 1 }));
      return respond({ created: selected.length, lecturers: selected });
    }
    if (parts[1] === 'imports') {
      const kind = parts[2];
      if (parts[3] === 'preview') {
        state.previewCalls++;
        const values = kind === 'rooms' ? { roomCode: 'NEW-ROOM', roomName: 'Phòng từ Excel', roomType: 'THEORY_STANDARD', capacity: 60, active: true }
          : { lecturerCode: 'OLD01', fullName: 'Trần Văn B', lecturerType: 'STAFF', active: true, accountTeacherId: 'ACCOUNT_OLD01' };
        return respond({ schoolCode: school, kind: kind.toUpperCase(), snapshot: (state.previewCalls % 2 ? 'a' : 'b').repeat(64), created: state.previewInvalid ? 0 : 1,
          updated: 0, unchanged: 0, invalid: state.previewInvalid ? 1 : 0, canCommit: !state.previewInvalid,
          rows: [{ rowNumber: 2, action: state.previewInvalid ? 'ERROR' : 'CREATE', values, previous: {}, errors: state.previewInvalid ? ['Sức chứa phải là số nguyên dương.'] : [] }] });
      }
      state.commitCalls++;
      if (state.staleCommit) { state.staleCommit = false; return respond({ message: 'Snapshot changed' }, 409); }
      if (kind === 'rooms') (state.rooms[school] ||= []).push({ id: state.nextId++, schoolCode: school, roomCode: 'NEW-ROOM', roomName: 'Phòng từ Excel', roomType: 'THEORY_STANDARD', capacity: 60, isLab: false, active: true, revision: 0 });
      return respond({ status: 'SUCCESS', created: 1, updated: 0, unchanged: 0 });
    }
    if (!parts.length) {
      if (method === 'GET') return respond(state.schools);
      const input = request.postDataJSON(); const result = { ...input, schoolCode: input.schoolCode.toUpperCase(), revision: 0 };
      state.schools.push(result); return respond(result, 201);
    }
    if (parts.length === 1 && method === 'PUT') {
      const index = state.schools.findIndex(row => row.schoolCode === school);
      state.schools[index] = { ...request.postDataJSON(), revision: state.schools[index].revision + 1 }; return respond(state.schools[index]);
    }
    const kind = parts[1];
    const assignment = parts[3] === 'assignments';
    const rows = assignment ? (state.assignments[parts[2]] ||= []) : ((state[kind] ||= {})[school] ||= []);
    if (method === 'GET') return respond(rows);
    const input = request.postDataJSON(); const id = Number(parts[assignment ? 4 : 2]);
    if (method === 'PUT' && state.staleEdit) {
      state.staleEdit = false; const existing = rows.find(row => row.id === id); existing.fullName = 'Sửa bởi quản lý khác'; existing.revision++;
      return respond({ message: 'Dữ liệu đã thay đổi.' }, 409);
    }
    const result = { ...input, id: method === 'PUT' ? id : state.nextId++, schoolCode: school, revision: method === 'PUT' ? input.revision + 1 : 0 };
    if (kind === 'lecturers') {
      const old = rows.find(r => r.id === id);
      if (old?.accountTeacherId && !result.accountTeacherId) result.accountTeacherId = old.accountTeacherId;
      if (result.active && !result.accountTeacherId) Object.assign(result, { accountTeacherId: result.lecturerCode, accountUsername: result.lecturerCode, autoProvisioned: true });
      else if (old) Object.assign(result, { accountUsername: old.accountUsername, autoProvisioned: old.autoProvisioned });
    }
    if (kind === 'rooms') { result.isLab = input.roomType === 'PRACTICE'; result.roomType = result.isLab ? 'LAB_PRACTICE' : 'THEORY_STANDARD'; }
    if (method === 'PUT') rows[rows.findIndex(row => row.id === id)] = result; else rows.push(result);
    return respond(result, method === 'POST' ? 201 : 200);
  });
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toContainText(role === 'ADMIN' ? 'Xếp lịch toàn trường' : 'Lịch giảng dạy');
  return state;
}

async function roomFile() {
  const book = new ExcelJS.Workbook(); const sheet = book.addWorksheet('DanhMuc');
  sheet.addRow(['Mã phòng', 'Loại phòng', 'Sức chứa']); sheet.addRow(['NEW-ROOM', 'THEORY', 60]);
  return { name: 'rooms.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: Buffer.from(await book.xlsx.writeBuffer()) };
}

async function capture(page, testInfo, name) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true });
}

test('automatic accounts: saving an active lecturer shows its login without sending a password', async ({ page }, testInfo) => {
  const state = await boot(page); await navigate(page, 'Danh mục nhà trường');
  await page.getByRole('button', { name: 'Thêm giảng viên', exact: true }).click();
  await page.getByLabel('Mã GV trong trường', { exact: false }).fill('AUTO01'); await page.getByLabel('Họ và tên', { exact: false }).fill('Đặng Thị Mỹ Lệ');
  await expect(page.getByRole('form', { name: 'Thêm giảng viên', exact: true })).toContainText(/mật khẩu ban đầu/i);
  await page.getByRole('button', { name: 'Lưu giảng viên', exact: true }).click();
  const row = page.getByRole('row').filter({ hasText: 'Đặng Thị Mỹ Lệ' }); await expect(row).toContainText('AUTO01');
  await expect(page.getByRole('columnheader', { name: 'Tên đăng nhập', exact: true })).toBeVisible();
  const sent = state.calls.find(c => c.path === '/PTIT/lecturers' && c.method === 'POST'); expect(sent.body.accountTeacherId).toBeNull(); expect(sent.body.password).toBeUndefined();
  expect(state.lecturers.PTIT.find(r => r.lecturerCode === 'AUTO01').accountUsername).toBe('AUTO01');
  await capture(page, testInfo, 'automatic-lecturer-account');
});

test('automatic accounts: bulk creation includes active missing lecturers outside the display filter', async ({ page }) => {
  const state = await boot(page);
  state.lecturers.PTIT.push(...[{ id: 42, lecturerCode: 'AUTO42', fullName: 'Đặng Thị Mỹ Lệ', active: true }, { id: 43, lecturerCode: 'OFF43', fullName: 'Ngừng sử dụng', active: false }, { id: 44, lecturerCode: 'AUTO44', fullName: 'Nguyễn Văn An', active: true }].map(r => ({ ...r, revision: 3, lecturerType: 'STAFF', accountTeacherId: null })));
  await navigate(page, 'Danh mục nhà trường'); await page.getByLabel('Tìm trong danh mục').fill('Đặng');
  await page.getByRole('button', { name: 'Tạo tài khoản còn thiếu (2)', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã tạo 2 tài khoản giảng viên.' })).toBeVisible();
  expect(state.accountBatches).toEqual([{ lecturers: [{ id: 42, revision: 3 }, { id: 44, revision: 3 }] }]);
  await expect(page.getByRole('button', { name: 'Tạo tài khoản còn thiếu (0)', exact: true })).toBeDisabled();
  expect(state.lecturers.PTIT.find(r => r.id === 43).accountTeacherId).toBeNull();
});

test('automatic accounts: a stale bulk request reports the error without retrying or clearing the roster', async ({ page }) => {
  const state = await boot(page); state.accountStale = true;
  state.lecturers.PTIT.push({ id: 42, lecturerCode: 'AUTO42', fullName: 'Nguyễn Văn An', active: true, revision: 2, accountTeacherId: null, lecturerType: 'STAFF' });
  await navigate(page, 'Danh mục nhà trường'); await page.getByRole('button', { name: 'Tạo tài khoản còn thiếu (1)', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Tải lại danh mục'); expect(state.accountBatches).toHaveLength(1);
  await expect(page.getByRole('row').filter({ hasText: 'AUTO42' })).toBeVisible(); expect(state.lecturers.PTIT.find(r => r.id === 42).accountTeacherId).toBeNull();
});

test('admin manages faculty, lecturer, room and assignment and reloads records from server', async ({ page }, testInfo) => {
  const state = await boot(page); await navigate(page, 'Danh mục nhà trường');
  await expect(page.getByLabel('Trường đang quản lý')).toHaveValue('PTIT');
  await expect(page.getByRole('tab', { name: 'Giảng viên', exact: true })).toBeVisible();
  await expect(page.locator('.teacher-picker')).toHaveCount(0);
  await page.getByRole('tab', { name: 'Khoa', exact: true }).click();
  await page.getByRole('button', { name: 'Thêm khoa', exact: true }).click();
  let form = page.getByRole('form', { name: 'Thêm khoa', exact: true });
  await form.getByLabel('Mã khoa').fill('MATH'); await form.getByLabel('Tên khoa').fill('Toán ứng dụng');
  await form.getByRole('button', { name: 'Lưu khoa', exact: true }).click();
  await expect(page.locator('.catalog-panel table')).toContainText('Toán ứng dụng');

  await page.getByRole('tab', { name: 'Giảng viên', exact: true }).click();
  await page.getByRole('button', { name: 'Thêm giảng viên', exact: true }).click();
  form = page.getByRole('form', { name: 'Thêm giảng viên', exact: true });
  await form.getByLabel('Mã GV trong trường').fill('TG01'); await form.getByLabel('Họ và tên').fill('Lê Thị C');
  await form.getByRole('combobox', { name: 'Khoa', exact: true }).selectOption({ label: 'Toán ứng dụng' });
  await form.getByLabel('Loại giảng viên').selectOption('VISITING');
  await form.getByRole('button', { name: 'Lưu giảng viên', exact: true }).click();
  await expect(page.locator('.catalog-panel table')).toContainText('Lê Thị C');
  await page.getByLabel('Lọc khoa').selectOption({ label: 'Toán ứng dụng' });
  await expect(page.locator('.catalog-panel table')).not.toContainText('Nguyễn Văn A');
  await expect(page.locator('.catalog-panel table')).toContainText('Thỉnh giảng');

  await page.getByRole('tab', { name: 'Phòng học', exact: true }).click();
  await page.getByRole('button', { name: 'Thêm phòng học', exact: true }).click();
  form = page.getByRole('form', { name: 'Thêm phòng học', exact: true });
  await form.getByLabel('Mã phòng').fill('LAB01'); await form.getByLabel('Tên phòng').fill('Phòng thực hành mới');
  await form.getByLabel('Loại phòng').selectOption('PRACTICE'); await form.getByLabel('Sức chứa').fill('30');
  await form.getByRole('button', { name: 'Lưu phòng học', exact: true }).click();
  await expect(page.locator('.catalog-panel table')).toContainText('LAB01');
  await page.getByRole('button', { name: 'Sửa LAB01', exact: true }).click();
  form = page.getByRole('form', { name: 'Sửa phòng học', exact: true });
  await form.getByRole('checkbox', { name: 'Đang sử dụng' }).uncheck();
  await form.getByRole('button', { name: 'Lưu phòng học', exact: true }).click();
  await page.getByLabel('Lọc trạng thái danh mục').selectOption('inactive');
  await expect(page.locator('.catalog-panel table')).toContainText('LAB01');
  await expect(page.locator('.catalog-panel table')).not.toContainText('101-A2');

  await page.getByRole('tab', { name: 'Phân công môn', exact: true }).click();
  await page.getByLabel('Giảng viên được phân công').selectOption({ label: 'Lê Thị C (TG01)' });
  await page.getByRole('button', { name: 'Thêm phân công', exact: true }).click();
  form = page.getByRole('form', { name: 'Thêm phân công', exact: true });
  await form.getByLabel('Mã môn').fill('NET'); await form.getByLabel('Tên môn').fill('Mạng máy tính'); await form.getByLabel('Khóa sinh viên').fill('2024');
  await form.getByLabel('Trọng số LT').fill('2'); await form.getByLabel('Trọng số TH').fill('8');
  await form.getByRole('button', { name: 'Lưu phân công', exact: true }).click();
  await expect(page.locator('.catalog-panel table')).toContainText('Mạng máy tính');
  await page.reload(); await navigate(page, 'Danh mục nhà trường');
  await expect(page.locator('.catalog-panel table')).toContainText('Lê Thị C');
  expect(state.lecturers.PTIT.find(row => row.lecturerCode === 'TG01').accountUsername).toBe('TG01');
  expect(state.calls.every(call => call.authorization === 'Bearer catalog-test-token')).toBe(true);
  await capture(page, testInfo, 'catalog-directory');

  await page.getByRole('button', { name: 'Thêm trường', exact: true }).click();
  form = page.getByRole('form', { name: 'Thêm trường', exact: true });
  await form.getByLabel('Mã trường').fill('NEW'); await form.getByLabel('Tên trường').fill('Trường mới');
  await form.getByRole('button', { name: 'Lưu trường', exact: true }).click();
  await expect(page.getByLabel('Trường đang quản lý')).toHaveValue('NEW');
  await expect(page.locator('.catalog-panel table')).not.toContainText('Lê Thị C');
});

test('Excel import requires preview, reports row errors and saves only after explicit confirmation', async ({ page }, testInfo) => {
  const state = await boot(page); state.previewInvalid = true;
  await navigate(page, 'Danh mục nhà trường'); await page.getByRole('tab', { name: 'Phòng học', exact: true }).click();
  await page.getByRole('button', { name: 'Nhập Excel', exact: true }).click();
  await page.getByLabel('File danh mục .xlsx').setInputFiles(await roomFile());
  await page.getByRole('button', { name: 'Xem trước', exact: true }).click();
  await expect(page.getByText('1 dòng lỗi', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Xác nhận lưu danh mục' })).toBeDisabled();
  expect(state.commitCalls).toBe(0);
  await capture(page, testInfo, 'import-errors');
  state.previewInvalid = false;
  await page.getByLabel('File danh mục .xlsx').setInputFiles(await roomFile());
  await page.getByRole('button', { name: 'Xem trước', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Xác nhận lưu danh mục' })).toBeEnabled();
  expect(state.commitCalls).toBe(0);
  await page.getByRole('button', { name: 'Xác nhận lưu danh mục' }).click();
  await expect(page.locator('.catalog-panel > .catalog-table-wrap')).toContainText('NEW-ROOM');
  expect(state.commitCalls).toBe(1);
  await expect(page.getByRole('button', { name: 'Xác nhận lưu danh mục' })).toHaveCount(0);
});

test('stale import requires a fresh preview and never retries a commit automatically', async ({ page }) => {
  const state = await boot(page); state.staleCommit = true;
  await navigate(page, 'Danh mục nhà trường'); await page.getByRole('tab', { name: 'Phòng học', exact: true }).click();
  await page.getByRole('button', { name: 'Nhập Excel', exact: true }).click();
  await page.getByLabel('File danh mục .xlsx').setInputFiles(await roomFile());
  await page.getByRole('button', { name: 'Xem trước', exact: true }).click();
  await page.getByRole('button', { name: 'Xác nhận lưu danh mục' }).click();
  await expect(page.getByRole('alert')).toContainText('Bấm Xem trước');
  expect(state.previewCalls).toBe(1); expect(state.commitCalls).toBe(1);
  await expect(page.getByRole('button', { name: 'Xác nhận lưu danh mục' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Xem trước', exact: true }).click();
  await page.getByRole('button', { name: 'Xác nhận lưu danh mục' }).click();
  await expect(page.locator('.catalog-panel > .catalog-table-wrap')).toContainText('NEW-ROOM');
  expect(state.previewCalls).toBe(2); expect(state.commitCalls).toBe(2);
});

test('stale edit preserves the entered name until user closes and reloads', async ({ page }) => {
  const state = await boot(page); state.staleEdit = true;
  await navigate(page, 'Danh mục nhà trường'); await page.getByRole('button', { name: 'Sửa GV01', exact: true }).click();
  const form = page.getByRole('form', { name: 'Sửa giảng viên', exact: true });
  await form.getByLabel('Họ và tên').fill('Nội dung đang sửa'); await form.getByRole('button', { name: 'Lưu giảng viên', exact: true }).click();
  await expect(form.getByRole('alert')).toContainText('Nội dung bạn nhập vẫn được giữ');
  await expect(form.getByLabel('Họ và tên')).toHaveValue('Nội dung đang sửa');
  await form.getByRole('button', { name: 'Đóng và tải lại' }).click();
  await expect(page.locator('.catalog-panel table')).toContainText('Sửa bởi quản lý khác');
  expect(state.calls.filter(call => call.path.endsWith('/lecturers/1') && call.method === 'PUT')).toHaveLength(1);
});

test('legacy reconciliation requires the school name and prepares a reviewable lecturer file', async ({ page }, testInfo) => {
  const state = await boot(page); await navigate(page, 'Danh mục nhà trường');
  await page.getByRole('button', { name: 'Đối chiếu dữ liệu cũ' }).click();
  await expect(page.getByRole('button', { name: 'Tạo danh mục cho OLD' })).toBeDisabled();
  await page.getByLabel('Chọn tên trường OLD').selectOption('Tên đúng B');
  await page.getByRole('button', { name: 'Tạo danh mục cho OLD' }).click();
  await expect(page.getByLabel('Trường đang quản lý')).toHaveValue('OLD');
  await page.getByRole('button', { name: 'Chuẩn bị nhập GV OLD' }).click();
  await expect(page.getByText('giang-vien-cu-OLD.xlsx', { exact: true })).toBeVisible();
  expect(state.previewCalls).toBe(0); expect(state.commitCalls).toBe(0);
  await page.getByRole('button', { name: 'Xem trước', exact: true }).click();
  await expect(page.locator('.catalog-preview')).toContainText('Trần Văn B');
  expect(state.commitCalls).toBe(0);
  await capture(page, testInfo, 'legacy-preview');
});

test('teacher navigation never exposes administration catalogs', async ({ page }) => {
  const state = await boot(page, 'TEACHER');
  const menu = page.getByRole('button', { name: 'Mở điều hướng' }); if (await menu.isVisible()) await menu.click();
  await expect(page.getByRole('button', { name: 'Danh mục nhà trường', exact: true })).toHaveCount(0);
  expect(state.calls).toHaveLength(0);
});
