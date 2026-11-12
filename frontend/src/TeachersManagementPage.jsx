import React, { useEffect, useState } from 'react';
import { Download, Trash2, Users, Save, Edit3, CheckCircle2, X, Search, List, Plus } from 'lucide-react';
import ExcelJS from 'exceljs';
import { api } from './schedule';
import { TEACHER_TYPES, teacherTypeLabel } from './catalog';
import { isHead } from './permissions';
import FormError from './FormError';

const emptyTeacherForm = (defaultDeptId = '', defaultType = 'GIANG_VIEN') => ({
  teacherId: '',
  teacherName: '',
  departmentId: defaultDeptId,
  teacherType: defaultType,
  selectedCodes: [],
});

const emptyBatchTeacher = () => ({
  teacherId: '',
  teacherName: '',
  teacherType: 'GIANG_VIEN',
});

export default function TeachersManagementPage({ user }) {
  const head = isHead(user);
  const ownDeptId = head && user?.departmentId ? String(user.departmentId) : '';
  const defaultNewType = head ? 'THINH_GIANG' : 'GIANG_VIEN';

  const [schoolId] = useState('PTIT');
  const [departments, setDepartments] = useState([]);
  const [allSubjects, setAllSubjects] = useState([]);
  const [savedTeachers, setSavedTeachers] = useState([]);
  const [filterDeptId, setFilterDeptId] = useState(ownDeptId);
  const [filterCourseCode, setFilterCourseCode] = useState('');
  const [subjectSearch, setSubjectSearch] = useState('');

  const [teacherForm, setTeacherForm] = useState(() => emptyTeacherForm(ownDeptId, defaultNewType));
  const [batchMode, setBatchMode] = useState(false);
  const [batchTeachers, setBatchTeachers] = useState([emptyBatchTeacher(), emptyBatchTeacher(), emptyBatchTeacher()]);

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [infoMessage, setInfoMessage] = useState('');

  const school = schoolId.trim();

  useEffect(() => {
    if (head && ownDeptId) {
      setFilterDeptId(ownDeptId);
      setTeacherForm(prev => ({
        ...prev,
        departmentId: ownDeptId,
        teacherType: savedTeachers.some(t => t.teacherId.toUpperCase() === prev.teacherId.trim().toUpperCase())
          ? prev.teacherType
          : 'THINH_GIANG',
      }));
    }
  }, [head, ownDeptId]);

  useEffect(() => {
    if (!school) return;
    loadData(school);
  }, [school]);

  async function loadData(code) {
    setError('');
    try {
      const deptQuery = head && ownDeptId ? `&departmentId=${encodeURIComponent(ownDeptId)}` : '';
      const [deptsData, subsData, teachersData] = await Promise.all([
        api(`/departments?schoolId=${encodeURIComponent(code)}`),
        api(`/subjects?schoolId=${encodeURIComponent(code)}${deptQuery}`),
        api(`/teacher-subjects?schoolId=${encodeURIComponent(code)}${deptQuery}`),
      ]);
      const deptList = Array.isArray(deptsData) ? deptsData : [];
      setDepartments(deptList);
      setAllSubjects(Array.isArray(subsData) ? subsData : []);
      setSavedTeachers(Array.isArray(teachersData) ? teachersData : []);
      setTeacherForm(prev => {
        if (head && ownDeptId) return { ...prev, departmentId: ownDeptId };
        return prev.departmentId || !deptList.length ? prev : { ...prev, departmentId: String(deptList[0].id) };
      });
    } catch (err) {
      setError(err.message);
    }
  }

  const editingTeacher = savedTeachers.find(
    t => t.teacherId.toUpperCase() === teacherForm.teacherId.trim().toUpperCase()
  );

  const activeDeptId = head && ownDeptId ? ownDeptId : teacherForm.departmentId;
  const departmentSubjects = allSubjects.filter(
    s => String(s.departmentId) === String(activeDeptId)
  );

  const departmentCodes = new Set(departmentSubjects.map(s => s.courseCode.toUpperCase()));

  const visibleSubjectChoices = departmentSubjects.filter(s => {
    if (!subjectSearch.trim()) return true;
    const q = subjectSearch.toLocaleLowerCase('vi');
    return `${s.courseCode} ${s.courseName}`.toLocaleLowerCase('vi').includes(q);
  });

  const selectedInDepartment = teacherForm.selectedCodes.filter(c => departmentCodes.has(c.toUpperCase()));

  function handleDepartmentChange(newDeptId) {
    if (head) return;
    const allowed = new Set(
      allSubjects
        .filter(s => String(s.departmentId) === String(newDeptId))
        .map(s => s.courseCode.toUpperCase())
    );
    setSubjectSearch('');
    setTeacherForm(prev => ({
      ...prev,
      departmentId: newDeptId,
      selectedCodes: prev.selectedCodes.filter(c => allowed.has(c.toUpperCase())),
    }));
  }

  function handleTeacherIdChange(val) {
    const upper = val.toUpperCase();
    const existing = savedTeachers.find(t => t.teacherId.toUpperCase() === upper.trim());
    if (existing) {
      setTeacherForm({
        teacherId: existing.teacherId,
        teacherName: existing.teacherName || '',
        departmentId: existing.departmentId ? String(existing.departmentId) : (ownDeptId || (departments[0]?.id ? String(departments[0].id) : '')),
        teacherType: existing.teacherType || 'GIANG_VIEN',
        selectedCodes: (existing.subjects || []).map(s => s.courseCode.toUpperCase()),
      });
    } else {
      setTeacherForm(prev => ({
        ...prev,
        teacherId: val,
        teacherType: head ? 'THINH_GIANG' : prev.teacherType,
      }));
    }
  }

  function toggleSubjectCode(code) {
    const upper = code.toUpperCase();
    setTeacherForm(prev => {
      const exists = prev.selectedCodes.some(c => c.toUpperCase() === upper);
      return {
        ...prev,
        selectedCodes: exists
          ? prev.selectedCodes.filter(c => c.toUpperCase() !== upper)
          : [...prev.selectedCodes, upper],
      };
    });
  }

  function selectAllVisibleSubjects() {
    const toAdd = visibleSubjectChoices.map(s => s.courseCode.toUpperCase());
    setTeacherForm(prev => {
      const merged = new Set([...prev.selectedCodes.map(c => c.toUpperCase()), ...toAdd]);
      return { ...prev, selectedCodes: [...merged] };
    });
  }

  function clearVisibleSubjects() {
    const toRemove = new Set(visibleSubjectChoices.map(s => s.courseCode.toUpperCase()));
    setTeacherForm(prev => ({
      ...prev,
      selectedCodes: prev.selectedCodes.filter(c => !toRemove.has(c.toUpperCase())),
    }));
  }

  function buildSubjectsPayload() {
    const selectedSet = new Set(selectedInDepartment.map(c => c.toUpperCase()));
    return departmentSubjects
      .filter(s => selectedSet.has(s.courseCode.toUpperCase()))
      .map(s => ({
        id: null,
        courseCode: s.courseCode,
        courseName: s.courseName,
        cohort: '*',
        theoryWeight: 1,
        practiceWeight: 1,
        kind: 'PRIMARY',
      }));
  }

  async function handleSaveTeacher(e) {
    e.preventDefault();
    if (busy) return;
    setError('');
    setInfoMessage('');
    try {
      if (!activeDeptId) throw new Error('Vui lòng chọn bộ môn.');
      if (!selectedInDepartment.length) throw new Error('Vui lòng chọn ít nhất 1 môn học.');

      setBusy(true);
      const selectedSet = new Set(selectedInDepartment.map(c => c.toUpperCase()));
      const subjectsPayload = buildSubjectsPayload();
      const effectiveType = head && !editingTeacher ? 'THINH_GIANG' : teacherForm.teacherType;

      const res = await api('/teacher-subjects', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          teacherId: teacherForm.teacherId,
          teacherName: teacherForm.teacherName,
          teacherType: effectiveType,
          schoolCode: school,
          departmentId: Number(activeDeptId),
          subjects: subjectsPayload,
        }),
      });

      const existing = savedTeachers.find(
        t => t.teacherId.toUpperCase() === teacherForm.teacherId.trim().toUpperCase()
      );
      if (existing?.subjects?.length) {
        const toDelete = existing.subjects.filter(
          s => s.id && departmentCodes.has(s.courseCode.toUpperCase()) && !selectedSet.has(s.courseCode.toUpperCase())
        );
        for (const item of toDelete) {
          await api(`/teacher-subjects/${item.id}`, { method: 'DELETE' });
        }
      }

      if (res.username && res.defaultPassword) {
        setInfoMessage(
          `Đã lưu GV ${res.teacherId} (${res.teacherName}) — TK: ${res.username} | MK mặc định: ${res.defaultPassword}`
        );
      } else {
        setInfoMessage(
          `Đã cập nhật chuyên môn cho GV ${res.teacherId} (${res.teacherName}) — ${selectedInDepartment.length} môn.`
        );
      }
      setSubjectSearch('');
      setTeacherForm(emptyTeacherForm(activeDeptId, defaultNewType));
      await loadData(school);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  function updateBatchTeacher(idx, field, value) {
    setBatchTeachers(prev => {
      const next = [...prev];
      next[idx] = { ...next[idx], [field]: value };
      return next;
    });
  }

  function addBatchTeacherRows(count = 3) {
    setBatchTeachers(prev => [...prev, ...Array.from({ length: count }, () => emptyBatchTeacher())]);
  }

  function removeBatchTeacherRow(idx) {
    if (batchTeachers.length <= 1) return;
    setBatchTeachers(prev => prev.filter((_, i) => i !== idx));
  }

  async function handleSaveBatchTeachers(e) {
    e.preventDefault();
    if (busy || head) return;
    setError('');
    setInfoMessage('');
    try {
      if (!activeDeptId) throw new Error('Vui lòng chọn bộ môn.');
      if (!selectedInDepartment.length) throw new Error('Vui lòng chọn ít nhất 1 môn học bên dưới.');
      const validRows = batchTeachers.filter(r => r.teacherId.trim() || r.teacherName.trim());
      if (!validRows.length) throw new Error('Vui lòng nhập ít nhất 1 giảng viên.');
      for (let i = 0; i < validRows.length; i++) {
        if (!validRows[i].teacherId.trim() || !validRows[i].teacherName.trim()) {
          throw new Error(`Dòng ${i + 1}: Nhập đủ Mã GV và Họ tên.`);
        }
      }

      setBusy(true);
      const subjectsPayload = buildSubjectsPayload();
      let count = 0;
      for (const row of validRows) {
        await api('/teacher-subjects', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            teacherId: row.teacherId.trim().toUpperCase(),
            teacherName: row.teacherName.trim(),
            teacherType: row.teacherType || 'GIANG_VIEN',
            schoolCode: school,
            departmentId: Number(activeDeptId),
            subjects: subjectsPayload,
          }),
        });
        count++;
      }

      setBatchTeachers([emptyBatchTeacher(), emptyBatchTeacher(), emptyBatchTeacher()]);
      setInfoMessage(`Đã lưu ${count} giảng viên.`);
      await loadData(school);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function handleDeleteTeacherAssignment(id) {
    if (!window.confirm('Gỡ môn học này khỏi giảng viên?')) return;
    setError('');
    setInfoMessage('');
    try {
      await api(`/teacher-subjects/${id}`, { method: 'DELETE' });
      setInfoMessage('Đã gỡ môn học.');
      await loadData(school);
    } catch (err) {
      setError(err.message);
    }
  }

  async function exportTeacherAccountsToExcel() {
    if (!visibleTeachers.length || head) return;
    const workbook = new ExcelJS.Workbook();
    const sheet = workbook.addWorksheet('TaiKhoanGiangVien');

    sheet.addRow(['STT', 'Mã GV', 'Họ và tên', 'Bộ môn', 'Loại GV', 'Tài khoản', 'Mật khẩu mặc định', 'Các môn có thể dạy']);
    sheet.getRow(1).font = { bold: true };

    visibleTeachers.forEach((t, idx) => {
      const subList = (t.subjects || [])
        .map(s => `${s.courseCode} - ${s.courseName || ''}`)
        .join('; ');
      sheet.addRow([
        idx + 1,
        t.teacherId,
        t.teacherName,
        t.departmentName || '—',
        teacherTypeLabel(t.teacherType),
        t.username || t.teacherId,
        t.defaultPassword || '',
        subList,
      ]);
    });

    sheet.columns = [{ width: 6 }, { width: 14 }, { width: 24 }, { width: 24 }, { width: 16 }, { width: 16 }, { width: 24 }, { width: 52 }];

    const buffer = await workbook.xlsx.writeBuffer();
    const blob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `danh-sach-tai-khoan-giang-vien-${school}.xlsx`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  const effectiveFilterDept = head && ownDeptId ? ownDeptId : filterDeptId;
  const filterSubjectOptions = effectiveFilterDept
    ? allSubjects.filter(s => String(s.departmentId) === String(effectiveFilterDept))
    : allSubjects;

  const visibleTeachers = savedTeachers.filter(t => {
    if (effectiveFilterDept && String(t.departmentId) !== String(effectiveFilterDept)) return false;
    if (filterCourseCode && !(t.subjects || []).some(s => s.courseCode?.toUpperCase() === filterCourseCode.toUpperCase())) return false;
    return true;
  });

  const showAccountColumn = !head;

  const subjectChecklistBlock = (
    <>
      <div className="subject-picker-header">
        <div>
          <h4 style={{ margin: 0 }}>Các môn có thể dạy ({selectedInDepartment.length}/{departmentSubjects.length})</h4>
        </div>
        {departmentSubjects.length > 0 && (
          <div className="subject-picker-actions">
            <label className="search-field" style={{ background: '#fff', minWidth: '180px' }}>
              <Search size={14} />
              <input
                placeholder="Tìm môn học…"
                value={subjectSearch}
                onChange={e => setSubjectSearch(e.target.value)}
              />
            </label>
            <button type="button" className="button compact" onClick={selectAllVisibleSubjects}>
              Chọn tất cả
            </button>
            <button type="button" className="button compact" onClick={clearVisibleSubjects} disabled={!selectedInDepartment.length}>
              Bỏ chọn
            </button>
          </div>
        )}
      </div>

      {!activeDeptId ? (
        <p className="muted-text" style={{ marginTop: '0.5rem' }}>Chọn bộ môn để hiện danh sách môn.</p>
      ) : !departmentSubjects.length ? (
        <p className="muted-text" style={{ marginTop: '0.5rem' }}>Bộ môn chưa có môn học.</p>
      ) : (
        <div className="subject-checklist">
          {!visibleSubjectChoices.length && (
            <p className="muted-text" style={{ gridColumn: '1 / -1', margin: 0 }}>
              Không tìm thấy môn "{subjectSearch}".
            </p>
          )}
          {visibleSubjectChoices.map(s => {
            const checked = selectedInDepartment.some(c => c.toUpperCase() === s.courseCode.toUpperCase());
            return (
              <label key={s.id} className={`subject-check-item${checked ? ' checked' : ''}`}>
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() => toggleSubjectCode(s.courseCode)}
                />
                <span>
                  <code>{s.courseCode}</code>
                  <strong>{s.courseName}</strong>
                </span>
              </label>
            );
          })}
        </div>
      )}
    </>
  );

  return (
    <section className="standalone-panel import-panel">
      <div className="panel-title">
        <div>
          <h2>Quản lý Giảng viên{head && user?.departmentName ? ` · Bộ môn ${user.departmentName}` : ''}</h2>
        </div>
      </div>

      <FormError message={error} />
      {infoMessage && <div role="status" className="info-banner catalog-info"><CheckCircle2 size={18} /> {infoMessage}</div>}

      <div className="catalog-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
          <h3><Users size={18} /> Danh sách Giảng viên ({visibleTeachers.length})</h3>
          {!head && (
            <div className="catalog-actions">
              <button type="button" className="button compact" onClick={() => setBatchMode(prev => !prev)}>
                <List size={14} /> {batchMode ? 'Thêm từng GV' : 'Thêm nhiều GV'}
              </button>
              <button type="button" className="button primary compact" disabled={!visibleTeachers.length} onClick={exportTeacherAccountsToExcel}>
                <Download size={15} /> Xuất Excel Tài khoản (.xlsx)
              </button>
            </div>
          )}
        </div>

        {head && (
          <p className="muted-text" style={{ marginTop: '0.5rem' }}>
            Trưởng bộ môn được thêm mới hồ sơ <strong>Giảng viên Thỉnh giảng</strong> và cập nhật danh sách môn giảng dạy cho giảng viên thuộc bộ môn.
          </p>
        )}

        {!departments.length && (
          <p className="import-warning">
            Chưa có bộ môn nào. Hãy tạo bộ môn ở trang <strong>Bộ môn &amp; Môn học</strong> trước.
          </p>
        )}

        {!batchMode ? (
          <form onSubmit={handleSaveTeacher} style={{ marginTop: '0.75rem', background: '#f8fafc', padding: '1rem', borderRadius: '8px', border: '1px solid var(--border)' }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.75rem', marginBottom: '1rem' }}>
              <label>
                Mã giảng viên
                <input
                  required
                  readOnly={Boolean(head && editingTeacher)}
                  placeholder={head ? 'TG01' : 'GV02'}
                  value={teacherForm.teacherId}
                  onChange={e => handleTeacherIdChange(e.target.value)}
                />
              </label>
              <label>
                Họ tên giảng viên
                <input
                  required
                  readOnly={Boolean(head && editingTeacher)}
                  placeholder="Nguyễn Văn Minh"
                  value={teacherForm.teacherName}
                  onChange={e => setTeacherForm({ ...teacherForm, teacherName: e.target.value })}
                />
              </label>
              <label>
                Bộ môn
                <select
                  required
                  disabled={head}
                  value={activeDeptId}
                  onChange={e => handleDepartmentChange(e.target.value)}
                >
                  <option value="">-- Chọn bộ môn --</option>
                  {departments.map(d => (
                    <option key={d.id} value={d.id}>{d.code} — {d.name}</option>
                  ))}
                </select>
              </label>
              <label>
                Loại giảng viên
                <select
                  disabled={head}
                  value={head && !editingTeacher ? 'THINH_GIANG' : teacherForm.teacherType}
                  onChange={e => setTeacherForm({ ...teacherForm, teacherType: e.target.value })}
                >
                  {TEACHER_TYPES.map(([val, label]) => (
                    <option key={val} value={val}>{label}</option>
                  ))}
                </select>
              </label>
            </div>

            {subjectChecklistBlock}

            <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.9rem', flexWrap: 'wrap' }}>
              <button type="submit" className="button primary compact" disabled={busy || !departmentSubjects.length || !selectedInDepartment.length}>
                <Save size={14} /> {busy ? 'Đang lưu…' : `Lưu Giảng viên (${selectedInDepartment.length} môn)`}
              </button>
              {(teacherForm.teacherId || selectedInDepartment.length > 0) && (
                <button
                  type="button"
                  className="button compact"
                  onClick={() => {
                    setSubjectSearch('');
                    setTeacherForm(emptyTeacherForm(ownDeptId || (departments[0]?.id ? String(departments[0].id) : ''), defaultNewType));
                  }}
                >
                  <X size={14} /> Làm mới
                </button>
              )}
            </div>
          </form>
        ) : (
          <form onSubmit={handleSaveBatchTeachers} style={{ marginTop: '0.75rem', background: '#f8fafc', padding: '1rem', borderRadius: '8px', border: '1px solid var(--border)' }}>
            <div style={{ marginBottom: '0.75rem', maxWidth: '280px' }}>
              <label>
                Bộ môn
                <select required value={activeDeptId} onChange={e => handleDepartmentChange(e.target.value)}>
                  <option value="">-- Chọn bộ môn --</option>
                  {departments.map(d => (
                    <option key={d.id} value={d.id}>{d.code} — {d.name}</option>
                  ))}
                </select>
              </label>
            </div>

            {batchTeachers.map((row, idx) => (
              <div key={idx} className="batch-teacher-row">
                <label>{idx === 0 ? 'Mã GV' : ''}
                  <input placeholder="GV02" value={row.teacherId} onChange={e => updateBatchTeacher(idx, 'teacherId', e.target.value.toUpperCase())} />
                </label>
                <label>{idx === 0 ? 'Họ tên giảng viên' : ''}
                  <input placeholder="Nguyễn Văn Minh" value={row.teacherName} onChange={e => updateBatchTeacher(idx, 'teacherName', e.target.value)} />
                </label>
                <label>{idx === 0 ? 'Loại giảng viên' : ''}
                  <select value={row.teacherType} onChange={e => updateBatchTeacher(idx, 'teacherType', e.target.value)}>
                    {TEACHER_TYPES.map(([val, label]) => (
                      <option key={val} value={val}>{label}</option>
                    ))}
                  </select>
                </label>
                <button type="button" className="icon-button" onClick={() => removeBatchTeacherRow(idx)} disabled={batchTeachers.length <= 1} title="Xóa dòng">
                  <Trash2 size={15} />
                </button>
              </div>
            ))}

            <div style={{ margin: '0.5rem 0 0.9rem' }}>
              <button type="button" className="button compact" onClick={() => addBatchTeacherRows(3)}>
                <Plus size={14} /> Thêm 3 dòng
              </button>
            </div>

            {subjectChecklistBlock}

            <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.9rem', flexWrap: 'wrap' }}>
              <button type="submit" className="button primary compact" disabled={busy || !departmentSubjects.length || !selectedInDepartment.length}>
                <Save size={14} /> {busy ? 'Đang lưu…' : 'Lưu danh sách Giảng viên'}
              </button>
            </div>
          </form>
        )}

        <div className="filter-bar" style={{ padding: '1rem 0 0', flexWrap: 'wrap' }}>
          {!head && (
            <label style={{ display: 'flex', flexDirection: 'row', alignItems: 'center', gap: '0.5rem' }}>
              Bộ môn:
              <select value={filterDeptId} onChange={e => { setFilterDeptId(e.target.value); setFilterCourseCode(''); }}>
                <option value="">Tất cả bộ môn</option>
                {departments.map(d => (
                  <option key={d.id} value={d.id}>{d.name}</option>
                ))}
              </select>
            </label>
          )}
          <label style={{ display: 'flex', flexDirection: 'row', alignItems: 'center', gap: '0.5rem' }}>
            Môn học:
            <select value={filterCourseCode} onChange={e => setFilterCourseCode(e.target.value)}>
              <option value="">Tất cả môn học</option>
              {filterSubjectOptions.map(s => (
                <option key={s.id} value={s.courseCode}>{s.courseCode} — {s.courseName}</option>
              ))}
            </select>
          </label>
        </div>

        {filterCourseCode && (
          <p className="info-banner" style={{ marginTop: '0.75rem', marginBottom: 0 }}>
            <strong>{filterCourseCode}</strong>: {visibleTeachers.length} giảng viên
            {visibleTeachers.length > 0 && ` (${visibleTeachers.map(t => `${t.teacherName} - ${t.teacherId}`).join(', ')})`}.
          </p>
        )}

        <div className="table-scroll catalog-table">
          <table>
            <thead>
              <tr>
                <th>Mã GV</th>
                <th>Họ tên</th>
                <th>Bộ môn / Loại</th>
                {showAccountColumn && <th>Tài khoản &amp; MK</th>}
                <th>Các môn có thể dạy</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {!visibleTeachers.length && <tr><td colSpan={showAccountColumn ? 6 : 5}>Chưa có giảng viên nào.</td></tr>}
              {visibleTeachers.map(t => (
                <tr key={t.teacherId}>
                  <td><strong>{t.teacherId}</strong></td>
                  <td>{t.teacherName}</td>
                  <td>
                    <div>{t.departmentName || <span className="muted-text">—</span>}</div>
                    <small className="badge muted" style={{ marginTop: '4px' }}>{teacherTypeLabel(t.teacherType)}</small>
                  </td>
                  {showAccountColumn && (
                    <td>
                      <div>TK: <code>{t.username}</code></div>
                      {t.defaultPassword && <div>MK: <code>{t.defaultPassword}</code></div>}
                    </td>
                  )}
                  <td>
                    {(t.subjects || []).map(s => (
                      <div key={s.id || `${s.courseCode}-${s.cohort}`} style={{ fontSize: '0.88rem', marginBottom: '4px' }}>
                        <code>{s.courseCode}</code> — {s.courseName}{' '}
                        {s.id && (
                          <button type="button" className="icon-button" style={{ padding: '2px', display: 'inline-grid' }} onClick={() => handleDeleteTeacherAssignment(s.id)} title="Gỡ môn">
                            <Trash2 size={12} />
                          </button>
                        )}
                      </div>
                    ))}
                    {!(t.subjects || []).length && <span className="muted-text">—</span>}
                  </td>
                  <td>
                    <button
                      type="button"
                      className="button compact"
                      onClick={() => {
                        setBatchMode(false);
                        setSubjectSearch('');
                        setTeacherForm({
                          teacherId: t.teacherId,
                          teacherName: t.teacherName || '',
                          departmentId: t.departmentId ? String(t.departmentId) : (ownDeptId || (departments[0]?.id ? String(departments[0].id) : '')),
                          teacherType: t.teacherType || 'GIANG_VIEN',
                          selectedCodes: (t.subjects || []).map(s => s.courseCode.toUpperCase()),
                        });
                      }}
                    >
                      <Edit3 size={14} /> Sửa môn dạy
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}
