import React, { useEffect, useRef, useState } from 'react';
import { BookOpen, CheckCircle2, Edit3, Layers, List, Plus, Save, Trash2, X } from 'lucide-react';
import { api } from './schedule';
import { postJson } from './catalog';
import FormError from './FormError';

const emptyDepartment = { id: null, code: '', name: '' };
const emptySubject = (departmentId = '') => ({
  id: null,
  departmentId,
  courseCode: '',
  courseName: '',
  theoryRoomType: '',
  practiceRoomType: '',
});
const emptyBatchRow = () => ({
  courseCode: '',
  courseName: '',
  theoryRoomType: '',
  practiceRoomType: '',
});

export default function DepartmentManagementPage() {
  const [schoolId, setSchoolId] = useState('PTIT');
  const [departments, setDepartments] = useState([]);
  const [roomTypes, setRoomTypes] = useState([]);
  const [subjects, setSubjects] = useState([]);
  const [filterDeptId, setFilterDeptId] = useState('ALL');

  const [departmentForm, setDepartmentForm] = useState(emptyDepartment);
  const [subjectForm, setSubjectForm] = useState(() => emptySubject(''));

  const [batchMode, setBatchMode] = useState(false);
  const [batchRows, setBatchRows] = useState([emptyBatchRow(), emptyBatchRow(), emptyBatchRow()]);
  const [busy, setBusy] = useState(false);

  const [error, setError] = useState('');
  const [info, setInfo] = useState('');
  const courseNameInputRef = useRef(null);

  const school = schoolId.trim();

  async function loadDepartments() {
    const [list, types] = await Promise.all([
      api(`/departments?schoolId=${encodeURIComponent(school)}`),
      api(`/room-types?schoolId=${encodeURIComponent(school)}`),
    ]);
    const deptList = Array.isArray(list) ? list : [];
    setDepartments(deptList);
    setRoomTypes(Array.isArray(types) ? types : []);
    return deptList;
  }

  async function loadSubjects(deptFilter = filterDeptId) {
    const query = deptFilter && deptFilter !== 'ALL'
      ? `/subjects?schoolId=${encodeURIComponent(school)}&departmentId=${deptFilter}`
      : `/subjects?schoolId=${encodeURIComponent(school)}`;
    const list = await api(query);
    setSubjects(Array.isArray(list) ? list : []);
  }

  async function suggestCode(departmentId) {
    if (!departmentId) return;
    const next = await api(`/subjects/next-code?departmentId=${departmentId}`);
    setSubjectForm(form => (
      form.id || String(form.departmentId) !== String(departmentId)
        ? form
        : { ...form, courseCode: next.courseCode || '' }
    ));
  }

  useEffect(() => {
    if (!school) return;
    let active = true;
    setError('');
    loadDepartments()
      .then(list => {
        if (!active) return;
        setSubjectForm(prev => {
          const valid = list.some(d => String(d.id) === String(prev.departmentId));
          const nextDeptId = valid ? prev.departmentId : (list[0]?.id ? String(list[0].id) : '');
          return { ...prev, departmentId: nextDeptId };
        });
      })
      .catch(err => { if (active) setError(err.message); });
    return () => { active = false; };
  }, [school]);

  useEffect(() => {
    if (!school) return;
    loadSubjects(filterDeptId).catch(err => setError(err.message));
  }, [school, filterDeptId]);

  useEffect(() => {
    if (!subjectForm.departmentId || subjectForm.id) return;
    suggestCode(subjectForm.departmentId).catch(err => setError(err.message));
  }, [subjectForm.departmentId]);

  async function run(action) {
    setError('');
    setInfo('');
    try { await action(); } catch (err) { setError(err.message); }
  }

  const saveDepartment = event => {
    event.preventDefault();
    run(async () => {
      const saved = await postJson('/departments', { ...departmentForm, schoolCode: school });
      setDepartmentForm(emptyDepartment);
      await loadDepartments();
      setSubjectForm(prev => (
        prev.departmentId ? prev : { ...emptySubject(String(saved.id)) }
      ));
      setInfo(`Đã lưu bộ môn ${saved.code} — ${saved.name}.`);
    });
  };

  const deleteDepartment = department => run(async () => {
    if (!window.confirm(`Xóa bộ môn "${department.name}"?`)) return;
    await api(`/departments/${department.id}`, { method: 'DELETE' });
    const list = await loadDepartments();
    if (String(subjectForm.departmentId) === String(department.id)) {
      setSubjectForm(emptySubject(list[0]?.id ? String(list[0].id) : ''));
    }
    if (String(filterDeptId) === String(department.id)) {
      setFilterDeptId('ALL');
    }
    setInfo(`Đã xóa bộ môn ${department.name}.`);
  });

  function handleSelectDepartmentForSubject(deptId) {
    const idStr = String(deptId);
    setSubjectForm(prev => ({
      ...prev,
      id: null,
      departmentId: idStr,
      courseCode: '',
      courseName: '',
    }));
    setFilterDeptId(idStr);
    setTimeout(() => courseNameInputRef.current?.focus(), 0);
  }

  const saveSubject = event => {
    event.preventDefault();
    if (busy) return;
    run(async () => {
      if (!subjectForm.departmentId) throw new Error('Vui lòng chọn bộ môn.');
      setBusy(true);
      try {
        const targetDeptId = String(subjectForm.departmentId);
        const saved = await postJson('/subjects', {
          id: subjectForm.id,
          departmentId: Number(targetDeptId),
          courseCode: subjectForm.courseCode,
          courseName: subjectForm.courseName,
          theoryRoomType: subjectForm.theoryRoomType || null,
          practiceRoomType: subjectForm.practiceRoomType || null,
        });
        setSubjectForm(prev => ({
          ...emptySubject(targetDeptId),
          theoryRoomType: prev.theoryRoomType,
          practiceRoomType: prev.practiceRoomType,
        }));
        await Promise.all([loadSubjects(filterDeptId), loadDepartments()]);
        await suggestCode(targetDeptId);
        setInfo(`Đã lưu môn ${saved.courseCode} — ${saved.courseName}.`);
        setTimeout(() => courseNameInputRef.current?.focus(), 0);
      } finally {
        setBusy(false);
      }
    });
  };

  function updateBatchRow(idx, field, value) {
    setBatchRows(prev => {
      const next = [...prev];
      next[idx] = { ...next[idx], [field]: value };
      return next;
    });
  }

  function addBatchRows(count = 3) {
    setBatchRows(prev => [...prev, ...Array.from({ length: count }, () => emptyBatchRow())]);
  }

  function removeBatchRow(idx) {
    if (batchRows.length <= 1) return;
    setBatchRows(prev => prev.filter((_, i) => i !== idx));
  }

  const saveBatchSubjects = event => {
    event.preventDefault();
    if (busy) return;
    run(async () => {
      const targetDeptId = String(subjectForm.departmentId);
      if (!targetDeptId) throw new Error('Vui lòng chọn bộ môn.');
      const validRows = batchRows.filter(r => r.courseName.trim() || r.courseCode.trim());
      if (!validRows.length) throw new Error('Vui lòng nhập ít nhất 1 môn học.');
      for (let i = 0; i < validRows.length; i++) {
        if (!validRows[i].courseName.trim()) {
          throw new Error(`Dòng ${i + 1}: Vui lòng nhập tên môn học.`);
        }
      }

      setBusy(true);
      let count = 0;
      try {
        for (const row of validRows) {
          await postJson('/subjects', {
            departmentId: Number(targetDeptId),
            courseCode: row.courseCode.trim() || null,
            courseName: row.courseName.trim(),
            theoryRoomType: row.theoryRoomType || null,
            practiceRoomType: row.practiceRoomType || null,
          });
          count++;
        }
        setBatchRows([emptyBatchRow(), emptyBatchRow(), emptyBatchRow()]);
        await Promise.all([loadSubjects(filterDeptId), loadDepartments()]);
        await suggestCode(targetDeptId);
        setInfo(`Đã lưu ${count} môn học.`);
      } finally {
        setBusy(false);
      }
    });
  };

  const deleteSubject = subject => run(async () => {
    if (!window.confirm(`Xóa môn "${subject.courseName}" (${subject.courseCode})?`)) return;
    await api(`/subjects/${subject.id}`, { method: 'DELETE' });
    await Promise.all([loadSubjects(filterDeptId), loadDepartments()]);
    if (subjectForm.departmentId) await suggestCode(subjectForm.departmentId);
    setInfo(`Đã xóa môn ${subject.courseCode}.`);
  });

  const cancelSubjectEdit = () => {
    const deptId = subjectForm.departmentId;
    setSubjectForm(emptySubject(deptId));
    run(() => suggestCode(deptId));
  };

  const deptName = id => departments.find(d => String(d.id) === String(id))?.name || '—';
  const typeName = code => roomTypes.find(type => type.code === code)?.name || code;
  const theoryTypes = roomTypes.filter(type => type.usageKind === 'THEORY');
  const practiceTypes = roomTypes.filter(type => type.usageKind === 'PRACTICE');

  return (
    <section className="standalone-panel import-panel">
      <div className="panel-title">
        <div>
          <h2>Bộ môn &amp; Môn học</h2>
        </div>
      </div>

      <FormError message={error} />
      {info && <div role="status" className="info-banner catalog-info"><CheckCircle2 size={18} /> {info}</div>}

      {/* BỘ MÔN */}
      <div className="catalog-card">
        <h3><Layers size={18} /> Bộ môn ({departments.length})</h3>
        <form className="catalog-form" onSubmit={saveDepartment}>
          <label>Tên bộ môn
            <input required maxLength={100} placeholder="Công nghệ phần mềm" value={departmentForm.name}
              onChange={event => setDepartmentForm({ ...departmentForm, name: event.target.value })} />
          </label>
          <label>Mã bộ môn
            <input maxLength={20} placeholder="CNPM" value={departmentForm.code}
              onChange={event => setDepartmentForm({ ...departmentForm, code: event.target.value.toUpperCase() })} />
          </label>
          <div className="catalog-actions">
            <button type="submit" className="button primary compact"><Save size={14} /> {departmentForm.id ? 'Lưu bộ môn' : 'Thêm bộ môn'}</button>
            {departmentForm.id && <button type="button" className="button compact" onClick={() => setDepartmentForm(emptyDepartment)}><X size={14} /> Hủy sửa</button>}
          </div>
        </form>

        <div className="table-scroll catalog-table">
          <table>
            <thead><tr><th>Mã</th><th>Tên bộ môn</th><th>Trưởng bộ môn</th><th>Số môn</th><th>Số GV</th><th>Thao tác</th></tr></thead>
            <tbody>
              {!departments.length && <tr><td colSpan={6}>Chưa có bộ môn nào.</td></tr>}
              {departments.map(department => (
                <tr key={department.id} className={String(department.id) === String(subjectForm.departmentId) ? 'catalog-selected' : ''}>
                  <td><strong>{department.code}</strong></td>
                  <td>{department.name}</td>
                  <td>{department.headTeacherName ? `${department.headTeacherName} (${department.headTeacherId})` : <span className="muted-text">Chưa có</span>}</td>
                  <td>{department.subjectCount}</td>
                  <td>{department.teacherCount}</td>
                  <td className="catalog-row-actions">
                    <button type="button" className="button compact" onClick={() => handleSelectDepartmentForSubject(department.id)}>
                      <BookOpen size={14} /> Chọn thêm môn
                    </button>
                    <button type="button" className="icon-button" title="Sửa bộ môn" onClick={() => setDepartmentForm({ id: department.id, code: department.code, name: department.name })}><Edit3 size={14} /></button>
                    <button type="button" className="icon-button" title="Xóa bộ môn" onClick={() => deleteDepartment(department)}><Trash2 size={14} /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* MÔN HỌC */}
      {departments.length > 0 && (
        <div className="catalog-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '0.75rem' }}>
            <h3 style={{ margin: 0 }}><BookOpen size={18} /> Danh mục Môn học ({subjects.length})</h3>
            {!subjectForm.id && (
              <button
                type="button"
                className="button compact"
                onClick={() => setBatchMode(prev => !prev)}
              >
                <List size={14} /> {batchMode ? 'Thêm từng môn' : 'Thêm nhiều môn'}
              </button>
            )}
          </div>

          {!batchMode ? (
            <form className="catalog-form" onSubmit={saveSubject}>
              <label>Bộ môn
                <select
                  required
                  value={subjectForm.departmentId}
                  onChange={event => {
                    const nextId = event.target.value;
                    setSubjectForm(prev => ({ ...prev, departmentId: nextId }));
                    if (filterDeptId !== 'ALL') setFilterDeptId(nextId);
                  }}
                >
                  <option value="">-- Chọn bộ môn --</option>
                  {departments.map(d => (
                    <option key={d.id} value={d.id}>{d.code} — {d.name}</option>
                  ))}
                </select>
              </label>
              <label>Tên môn học
                <input
                  ref={courseNameInputRef}
                  required
                  maxLength={180}
                  placeholder="Cơ sở dữ liệu"
                  value={subjectForm.courseName}
                  onChange={event => setSubjectForm({ ...subjectForm, courseName: event.target.value })}
                />
              </label>
              <label>Mã môn
                <input
                  required
                  maxLength={50}
                  placeholder="61FIT3DBS"
                  value={subjectForm.courseCode}
                  onChange={event => setSubjectForm({ ...subjectForm, courseCode: event.target.value.toUpperCase() })}
                />
              </label>
              <label>Phòng lý thuyết
                <select value={subjectForm.theoryRoomType || ''} onChange={event => setSubjectForm({ ...subjectForm, theoryRoomType: event.target.value })}>
                  <option value="">Phòng lý thuyết bất kỳ</option>
                  {theoryTypes.map(type => <option key={type.code} value={type.code}>{type.name}</option>)}
                </select>
              </label>
              <label>Phòng thực hành
                <select value={subjectForm.practiceRoomType || ''} onChange={event => setSubjectForm({ ...subjectForm, practiceRoomType: event.target.value })}>
                  <option value="">Phòng thực hành bất kỳ</option>
                  {practiceTypes.map(type => <option key={type.code} value={type.code}>{type.name}</option>)}
                </select>
              </label>
              <div className="catalog-actions">
                <button type="submit" className="button primary compact" disabled={busy || !subjectForm.departmentId}>
                  <Plus size={14} /> {subjectForm.id ? 'Lưu môn học' : 'Thêm môn học'}
                </button>
                {subjectForm.id && <button type="button" className="button compact" onClick={cancelSubjectEdit}><X size={14} /> Hủy sửa</button>}
              </div>
            </form>
          ) : (
            <form onSubmit={saveBatchSubjects} style={{ background: '#f8faf9', padding: '0.9rem', borderRadius: '8px', border: '1px solid var(--border)' }}>
              <div style={{ marginBottom: '0.75rem', maxWidth: '280px' }}>
                <label>Bộ môn
                  <select
                    required
                    value={subjectForm.departmentId}
                    onChange={event => setSubjectForm(prev => ({ ...prev, departmentId: event.target.value }))}
                  >
                    <option value="">-- Chọn bộ môn --</option>
                    {departments.map(d => (
                      <option key={d.id} value={d.id}>{d.code} — {d.name}</option>
                    ))}
                  </select>
                </label>
              </div>

              {batchRows.map((row, idx) => (
                <div key={idx} style={{ display: 'grid', gridTemplateColumns: '140px minmax(0, 1.6fr) minmax(0, 1fr) minmax(0, 1fr) 36px', gap: '0.5rem', alignItems: 'end', marginBottom: '0.5rem' }}>
                  <label>{idx === 0 ? 'Mã môn' : ''}
                    <input
                      maxLength={50}
                      placeholder="Mã môn"
                      value={row.courseCode}
                      onChange={e => updateBatchRow(idx, 'courseCode', e.target.value.toUpperCase())}
                    />
                  </label>
                  <label>{idx === 0 ? 'Tên môn học' : ''}
                    <input
                      maxLength={180}
                      placeholder="Tên môn học"
                      value={row.courseName}
                      onChange={e => updateBatchRow(idx, 'courseName', e.target.value)}
                    />
                  </label>
                  <label>{idx === 0 ? 'Phòng lý thuyết' : ''}
                    <select value={row.theoryRoomType} onChange={e => updateBatchRow(idx, 'theoryRoomType', e.target.value)}>
                      <option value="">LT bất kỳ</option>
                      {theoryTypes.map(type => <option key={type.code} value={type.code}>{type.name}</option>)}
                    </select>
                  </label>
                  <label>{idx === 0 ? 'Phòng thực hành' : ''}
                    <select value={row.practiceRoomType} onChange={e => updateBatchRow(idx, 'practiceRoomType', e.target.value)}>
                      <option value="">TH bất kỳ</option>
                      {practiceTypes.map(type => <option key={type.code} value={type.code}>{type.name}</option>)}
                    </select>
                  </label>
                  <button type="button" className="icon-button" onClick={() => removeBatchRow(idx)} disabled={batchRows.length <= 1} title="Xóa dòng">
                    <Trash2 size={15} />
                  </button>
                </div>
              ))}

              <div className="catalog-actions" style={{ marginTop: '0.75rem' }}>
                <button type="button" className="button compact" onClick={() => addBatchRows(3)}>
                  <Plus size={14} /> Thêm 3 dòng
                </button>
                <button type="submit" className="button primary compact" disabled={busy || !subjectForm.departmentId}>
                  <Save size={14} /> {busy ? 'Đang lưu…' : 'Lưu danh sách môn'}
                </button>
              </div>
            </form>
          )}

          <div className="filter-bar" style={{ padding: '0.75rem 0 0' }}>
            <label style={{ display: 'flex', flexDirection: 'row', alignItems: 'center', gap: '0.5rem' }}>
              Bộ môn:
              <select value={filterDeptId} onChange={e => setFilterDeptId(e.target.value)}>
                <option value="ALL">Tất cả bộ môn</option>
                {departments.map(d => (
                  <option key={d.id} value={d.id}>{d.code} — {d.name}</option>
                ))}
              </select>
            </label>
          </div>

          <div className="table-scroll catalog-table">
            <table>
              <thead><tr><th>Mã môn</th><th>Tên môn học</th><th>Bộ môn</th><th>Phòng lý thuyết</th><th>Phòng thực hành</th><th>Thao tác</th></tr></thead>
              <tbody>
                {!subjects.length && <tr><td colSpan={6}>Chưa có môn học nào.</td></tr>}
                {subjects.map(subject => (
                  <tr key={subject.id}>
                    <td><code>{subject.courseCode}</code></td>
                    <td>{subject.courseName}</td>
                    <td>{deptName(subject.departmentId)}</td>
                    <td>{subject.theoryRoomType ? typeName(subject.theoryRoomType) : <span className="muted-text">Bất kỳ</span>}</td>
                    <td>{subject.practiceRoomType ? typeName(subject.practiceRoomType) : <span className="muted-text">Bất kỳ</span>}</td>
                    <td className="catalog-row-actions">
                      <button
                        type="button"
                        className="icon-button"
                        title="Sửa môn học"
                        onClick={() => {
                          setBatchMode(false);
                          setSubjectForm({
                            id: subject.id,
                            departmentId: String(subject.departmentId),
                            courseCode: subject.courseCode,
                            courseName: subject.courseName,
                            theoryRoomType: subject.theoryRoomType || '',
                            practiceRoomType: subject.practiceRoomType || '',
                          });
                        }}
                      >
                        <Edit3 size={14} />
                      </button>
                      <button type="button" className="icon-button" title="Xóa môn học" onClick={() => deleteSubject(subject)}><Trash2 size={14} /></button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}