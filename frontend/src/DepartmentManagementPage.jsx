import React, { useEffect, useState } from 'react';
import { BookOpen, CheckCircle2, Edit3, Layers, Plus, Save, Trash2, X } from 'lucide-react';
import { api } from './schedule';
import { postJson } from './catalog';
import FormError from './FormError';

const emptyDepartment = { id: null, code: '', name: '' };
const emptySubject = { id: null, courseCode: '', courseName: '', theoryRoomType: '', practiceRoomType: '' };

export default function DepartmentManagementPage() {
  const [schoolId, setSchoolId] = useState('PTIT');
  const [departments, setDepartments] = useState([]);
  const [roomTypes, setRoomTypes] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [subjects, setSubjects] = useState([]);
  const [departmentForm, setDepartmentForm] = useState(emptyDepartment);
  const [subjectForm, setSubjectForm] = useState(emptySubject);
  // Mã môn do hệ thống gợi ý cho tới khi người dùng tự sửa.
  const [codeEdited, setCodeEdited] = useState(false);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');

  const school = schoolId.trim();
  const selected = departments.find(department => department.id === selectedId) || null;

  async function loadDepartments() {
    const [list, types] = await Promise.all([
      api(`/departments?schoolId=${encodeURIComponent(school)}`),
      api(`/room-types?schoolId=${encodeURIComponent(school)}`),
    ]);
    setDepartments(Array.isArray(list) ? list : []);
    setRoomTypes(Array.isArray(types) ? types : []);
    return Array.isArray(list) ? list : [];
  }

  async function loadSubjects(departmentId) {
    if (!departmentId) { setSubjects([]); return; }
    const list = await api(`/subjects?schoolId=${encodeURIComponent(school)}&departmentId=${departmentId}`);
    setSubjects(Array.isArray(list) ? list : []);
  }

  async function suggestCode(departmentId) {
    if (!departmentId) return;
    const next = await api(`/subjects/next-code?departmentId=${departmentId}`);
    setSubjectForm(form => (form.id ? form : { ...form, courseCode: next.courseCode || '' }));
    setCodeEdited(false);
  }

  useEffect(() => {
    if (!school) return;
    let active = true;
    setError('');
    loadDepartments()
      .then(list => { if (active) setSelectedId(current => (list.some(d => d.id === current) ? current : list[0]?.id ?? null)); })
      .catch(err => { if (active) setError(err.message); });
    return () => { active = false; };
  }, [school]);

  useEffect(() => {
    setSubjectForm(emptySubject);
    if (!selectedId) { setSubjects([]); return; }
    Promise.all([loadSubjects(selectedId), suggestCode(selectedId)]).catch(err => setError(err.message));
  }, [selectedId]);

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
      setSelectedId(saved.id);
      setInfo(`Đã lưu bộ môn ${saved.code} — ${saved.name}.`);
    });
  };

  const deleteDepartment = department => run(async () => {
    if (!window.confirm(`Xóa bộ môn "${department.name}"?`)) return;
    await api(`/departments/${department.id}`, { method: 'DELETE' });
    const list = await loadDepartments();
    if (selectedId === department.id) setSelectedId(list[0]?.id ?? null);
    setInfo(`Đã xóa bộ môn ${department.name}.`);
  });

  const saveSubject = event => {
    event.preventDefault();
    run(async () => {
      const saved = await postJson('/subjects', {
        ...subjectForm,
        departmentId: selectedId,
        theoryRoomType: subjectForm.theoryRoomType || null,
        practiceRoomType: subjectForm.practiceRoomType || null,
      });
      setSubjectForm(emptySubject);
      await Promise.all([loadSubjects(selectedId), loadDepartments()]);
      await suggestCode(selectedId);
      setInfo(`Đã lưu môn ${saved.courseCode} — ${saved.courseName}.`);
    });
  };

  const deleteSubject = subject => run(async () => {
    if (!window.confirm(`Xóa môn "${subject.courseName}" (${subject.courseCode})?`)) return;
    await api(`/subjects/${subject.id}`, { method: 'DELETE' });
    await Promise.all([loadSubjects(selectedId), loadDepartments()]);
    await suggestCode(selectedId);
    setInfo(`Đã xóa môn ${subject.courseCode}.`);
  });

  const cancelSubjectEdit = () => { setSubjectForm(emptySubject); run(() => suggestCode(selectedId)); };
  const typeName = code => roomTypes.find(type => type.code === code)?.name || code;
  const theoryTypes = roomTypes.filter(type => type.usageKind === 'THEORY');
  const practiceTypes = roomTypes.filter(type => type.usageKind === 'PRACTICE');

  return (
    <section className="standalone-panel import-panel">
      <div className="panel-title">
        <div>
          <h2>Bộ môn & Môn học</h2>
          <p className="muted-text">Tạo các bộ môn của khoa và khai báo môn học thuộc từng bộ môn. Trưởng bộ môn chỉ phân công được các lớp của môn thuộc bộ môn mình.</p>
        </div>
        <label className="catalog-school">Mã Khoa
          <input value={schoolId} onChange={event => setSchoolId(event.target.value)} maxLength={50} />
        </label>
      </div>

      <FormError message={error} />
      {info && <div role="status" className="info-banner catalog-info"><CheckCircle2 size={18} /> {info}</div>}

      <div className="catalog-card">
        <h3><Layers size={18} /> Bộ môn ({departments.length})</h3>
        <form className="catalog-form" onSubmit={saveDepartment}>
          <label>Tên bộ môn
            <input required maxLength={100} placeholder="VD: Công nghệ phần mềm" value={departmentForm.name}
              onChange={event => setDepartmentForm({ ...departmentForm, name: event.target.value })} />
          </label>
          <label>Mã bộ môn
            <input maxLength={20} placeholder="Để trống: tự lấy chữ cái đầu (CNPM)" value={departmentForm.code}
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
              {!departments.length && <tr><td colSpan={6}>Chưa có bộ môn nào. Thêm bộ môn đầu tiên ở form phía trên.</td></tr>}
              {departments.map(department => (
                <tr key={department.id} className={department.id === selectedId ? 'catalog-selected' : ''}>
                  <td><strong>{department.code}</strong></td>
                  <td>{department.name}</td>
                  <td>{department.headTeacherName ? `${department.headTeacherName} (${department.headTeacherId})` : <span className="muted-text">Chưa có</span>}</td>
                  <td>{department.subjectCount}</td>
                  <td>{department.teacherCount}</td>
                  <td className="catalog-row-actions">
                    <button type="button" className="button compact" onClick={() => setSelectedId(department.id)}><BookOpen size={14} /> Môn học</button>
                    <button type="button" className="icon-button" title="Sửa bộ môn" onClick={() => setDepartmentForm({ id: department.id, code: department.code, name: department.name })}><Edit3 size={14} /></button>
                    <button type="button" className="icon-button" title="Xóa bộ môn" onClick={() => deleteDepartment(department)}><Trash2 size={14} /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {selected && (
        <div className="catalog-card">
          <h3><BookOpen size={18} /> Môn học của bộ môn {selected.name} ({subjects.length})</h3>
          <form className="catalog-form" onSubmit={saveSubject}>
            <label>Tên môn học
              <input required maxLength={180} placeholder="VD: Cơ sở dữ liệu" value={subjectForm.courseName}
                onChange={event => setSubjectForm({ ...subjectForm, courseName: event.target.value })} />
            </label>
            <label>Mã môn {!subjectForm.id && !codeEdited && <small className="muted-text">(tự sinh, sửa được)</small>}
              <input required maxLength={50} placeholder="VD: 61FIT3DBS" value={subjectForm.courseCode}
                onChange={event => { setCodeEdited(true); setSubjectForm({ ...subjectForm, courseCode: event.target.value.toUpperCase() }); }} />
            </label>
            <label>Loại phòng cho lớp lý thuyết
              <select value={subjectForm.theoryRoomType || ''} onChange={event => setSubjectForm({ ...subjectForm, theoryRoomType: event.target.value })}>
                <option value="">Phòng lý thuyết bất kỳ</option>
                {theoryTypes.map(type => <option key={type.code} value={type.code}>{type.name}</option>)}
              </select>
            </label>
            <label>Loại phòng cho lớp thực hành
              <select value={subjectForm.practiceRoomType || ''} onChange={event => setSubjectForm({ ...subjectForm, practiceRoomType: event.target.value })}>
                <option value="">Phòng thực hành bất kỳ</option>
                {practiceTypes.map(type => <option key={type.code} value={type.code}>{type.name}</option>)}
              </select>
            </label>
            <div className="catalog-actions">
              <button type="submit" className="button primary compact"><Plus size={14} /> {subjectForm.id ? 'Lưu môn học' : 'Thêm môn học'}</button>
              {subjectForm.id && <button type="button" className="button compact" onClick={cancelSubjectEdit}><X size={14} /> Hủy sửa</button>}
            </div>
          </form>
          <p className="muted-text catalog-hint">Mã môn phải trùng với cột "Mã môn" trong file Kế hoạch mở lớp để lớp được xếp đúng loại phòng và về đúng bộ môn.</p>

          <div className="table-scroll catalog-table">
            <table>
              <thead><tr><th>Mã môn</th><th>Tên môn học</th><th>Phòng lý thuyết</th><th>Phòng thực hành</th><th>Thao tác</th></tr></thead>
              <tbody>
                {!subjects.length && <tr><td colSpan={5}>Bộ môn chưa có môn học nào.</td></tr>}
                {subjects.map(subject => (
                  <tr key={subject.id}>
                    <td><code>{subject.courseCode}</code></td>
                    <td>{subject.courseName}</td>
                    <td>{subject.theoryRoomType ? typeName(subject.theoryRoomType) : <span className="muted-text">Bất kỳ</span>}</td>
                    <td>{subject.practiceRoomType ? typeName(subject.practiceRoomType) : <span className="muted-text">Bất kỳ</span>}</td>
                    <td className="catalog-row-actions">
                      <button type="button" className="icon-button" title="Sửa môn học" onClick={() => { setCodeEdited(true); setSubjectForm({ id: subject.id, courseCode: subject.courseCode, courseName: subject.courseName, theoryRoomType: subject.theoryRoomType || '', practiceRoomType: subject.practiceRoomType || '' }); }}><Edit3 size={14} /></button>
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
