import React, { useEffect, useState } from 'react';
import { Download, Plus, Trash2, Building2, Users, Save, Edit3, CheckCircle2 } from 'lucide-react';
import ExcelJS from 'exceljs';
import { api } from './schedule';
import FormError from './FormError';

const emptyTeacherForm = {
  teacherId: '',
  teacherName: '',
  subjects: [
    { id: null, courseCode: '', courseName: '', cohort: '*', theoryWeight: 1, practiceWeight: 1 }
  ]
};

export default function SchoolResourcesPage({ semesterStart }) {
  const [schoolId, setSchoolId] = useState('PTIT');
  const [savedTeachers, setSavedTeachers] = useState([]);
  const [savedRooms, setSavedRooms] = useState([]);

  const [teacherForm, setTeacherForm] = useState(emptyTeacherForm);
  const [roomForm, setRoomForm] = useState({
    id: null, roomCode: '', roomType: 'THEORY', capacity: 60, building: 'A2'
  });

  const [error, setError] = useState('');
  const [infoMessage, setInfoMessage] = useState('');

  useEffect(() => {
    if (!schoolId.trim()) return;
    loadSchoolCatalog(schoolId.trim());
  }, [schoolId]);

  async function loadSchoolCatalog(code) {
    try {
      const [roomsData, teachersData] = await Promise.all([
        api(`/rooms?schoolId=${encodeURIComponent(code)}`),
        api(`/teacher-subjects?schoolId=${encodeURIComponent(code)}`)
      ]);
      setSavedRooms(Array.isArray(roomsData) ? roomsData : []);
      setSavedTeachers(Array.isArray(teachersData) ? teachersData : []);
    } catch (err) {
      setError(err.message);
    }
  }

  function handleTeacherIdChange(val) {
    const upper = val.toUpperCase();
    const existing = savedTeachers.find(t => t.teacherId.toUpperCase() === upper.trim());
    if (existing) {
      setTeacherForm(prev => ({
        ...prev,
        teacherId: val,
        teacherName: prev.teacherName || existing.teacherName
      }));
    } else {
      setTeacherForm(prev => ({ ...prev, teacherId: val }));
    }
  }

  function addSubjectRow() {
    setTeacherForm(prev => ({
      ...prev,
      subjects: [...prev.subjects, { id: null, courseCode: '', courseName: '', cohort: '*', theoryWeight: 1, practiceWeight: 1 }]
    }));
  }

  function removeSubjectRow(idx) {
    if (teacherForm.subjects.length === 1) return;
    setTeacherForm(prev => ({
      ...prev,
      subjects: prev.subjects.filter((_, i) => i !== idx)
    }));
  }

  function updateSubject(idx, field, value) {
    setTeacherForm(prev => {
      const next = [...prev.subjects];
      next[idx] = { ...next[idx], [field]: value };
      return { ...prev, subjects: next };
    });
  }

  async function handleSaveTeacher(e) {
    e.preventDefault();
    setError('');
    setInfoMessage('');
    try {
      const res = await api('/teacher-subjects', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...teacherForm,
          schoolCode: schoolId.trim()
        })
      });
      setInfoMessage(
        `Đã lưu GV ${res.teacherId || teacherForm.teacherId.toUpperCase()} (${res.teacherName || teacherForm.teacherName}) — Tài khoản: ${res.username || teacherForm.teacherId.toUpperCase()} | Mật khẩu mặc định: ${res.defaultPassword || ''}`
      );
      setTeacherForm(emptyTeacherForm);
      await loadSchoolCatalog(schoolId.trim());
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleDeleteTeacherAssignment(id) {
    setError('');
    try {
      await api(`/teacher-subjects/${id}`, { method: 'DELETE' });
      await loadSchoolCatalog(schoolId.trim());
    } catch (err) {
      setError(err.message);
    }
  }

  async function exportTeacherAccountsToExcel() {
    if (!savedTeachers.length) return;
    const workbook = new ExcelJS.Workbook();
    const sheet = workbook.addWorksheet('TaiKhoanGiangVien');

    sheet.addRow(['STT', 'Mã giảng viên', 'Họ và tên giảng viên', 'Tài khoản đăng nhập', 'Mật khẩu mặc định', 'Các môn học phụ trách']);
    sheet.getRow(1).font = { bold: true };

    let stt = 1;
    savedTeachers.forEach(t => {
      const subList = (t.subjects || []).map(s => `${s.courseCode} - ${s.courseName || ''} (LT:${s.theoryWeight}, TH:${s.practiceWeight})`).join('; ');
      sheet.addRow([stt++, t.teacherId, t.teacherName, t.username, t.defaultPassword, subList]);
    });

    sheet.columns = [{ width: 8 }, { width: 16 }, { width: 26 }, { width: 20 }, { width: 26 }, { width: 50 }];

    const buffer = await workbook.xlsx.writeBuffer();
    const blob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `danh-sach-tai-khoan-giang-vien-${schoolId.trim()}.xlsx`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  async function handleSaveRoom(e) {
    e.preventDefault();
    setError('');
    setInfoMessage('');
    try {
      await api('/rooms', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...roomForm,
          capacity: Number(roomForm.capacity),
          lab: roomForm.roomType === 'PRACTICE',
          schoolCode: schoolId.trim()
        })
      });
      setInfoMessage(`Đã lưu phòng học ${roomForm.roomCode}.`);
      setRoomForm({ id: null, roomCode: '', roomType: 'THEORY', capacity: 60, building: 'A2' });
      await loadSchoolCatalog(schoolId.trim());
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleDeleteRoom(id) {
    setError('');
    try {
      await api(`/rooms/${id}`, { method: 'DELETE' });
      await loadSchoolCatalog(schoolId.trim());
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <section className="standalone-panel import-panel">
      <div className="panel-title">
        <div>
          <h2>Quản lý Giảng viên & Phòng học của Khoa</h2>
          <p className="muted-text">Cấu hình danh sách giảng viên cơ hữu, nhiều môn dạy và danh mục phòng học cho đơn vị.</p>
        </div>
        <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 600 }}>
          Mã Khoa:
          <input value={schoolId} onChange={e => setSchoolId(e.target.value)} style={{ width: '120px' }} maxLength={50} />
        </label>
      </div>

      <FormError message={error} />
      {infoMessage && <div role="status" className="info-banner" style={{ marginTop: '0.75rem' }}><CheckCircle2 size={18} /> {infoMessage}</div>}

      {/* QUẢN LÝ GIẢNG VIÊN */}
      <div style={{ marginTop: '1rem', border: '1px solid #e2e8f0', borderRadius: '8px', padding: '1rem', background: '#fff' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
          <h3><Users size={18} style={{ verticalAlign: 'middle' }} /> Giảng viên cơ hữu ({savedTeachers.length} giảng viên)</h3>
          <button type="button" className="button primary compact" disabled={!savedTeachers.length} onClick={exportTeacherAccountsToExcel}>
            <Download size={15} /> Xuất Excel Tài khoản gửi GV (.xlsx)
          </button>
        </div>

        <form onSubmit={handleSaveTeacher} style={{ marginTop: '0.75rem', background: '#f8fafc', padding: '1rem', borderRadius: '8px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.75rem', marginBottom: '1rem' }}>
            <label>Mã giảng viên <input required placeholder="VD: GV01" value={teacherForm.teacherId} onChange={e => handleTeacherIdChange(e.target.value)} /></label>
            <label>Họ tên giảng viên <input required placeholder="VD: Đinh Văn B" value={teacherForm.teacherName} onChange={e => setTeacherForm({ ...teacherForm, teacherName: e.target.value })} /></label>
          </div>

          <h4>Danh sách các môn học giảng viên này phụ trách</h4>
          {teacherForm.subjects.map((sub, idx) => (
            <div key={idx} style={{ display: 'grid', gridTemplateColumns: '1fr 1.5fr 90px 90px 40px', gap: '0.5rem', alignItems: 'end', marginBottom: '0.5rem' }}>
              <label>Mã môn <input required placeholder="61FIT3DBS" value={sub.courseCode} onChange={e => updateSubject(idx, 'courseCode', e.target.value)} /></label>
              <label>Tên môn <input required placeholder="Cơ sở dữ liệu" value={sub.courseName} onChange={e => updateSubject(idx, 'courseName', e.target.value)} /></label>
              <label>Trọng số LT <input type="number" min={0} required value={sub.theoryWeight} onChange={e => updateSubject(idx, 'theoryWeight', Number(e.target.value))} /></label>
              <label>Trọng số TH <input type="number" min={0} required value={sub.practiceWeight} onChange={e => updateSubject(idx, 'practiceWeight', Number(e.target.value))} /></label>
              <button type="button" className="icon-button" onClick={() => removeSubjectRow(idx)} disabled={teacherForm.subjects.length === 1} title="Xóa dòng"><Trash2 size={16} /></button>
            </div>
          ))}

          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.75rem' }}>
            <button type="button" className="button compact" onClick={addSubjectRow}><Plus size={14} /> + Thêm môn học khác</button>
            <button type="submit" className="button primary compact"><Save size={14} /> Lưu Giảng viên & Môn dạy</button>
          </div>
        </form>

        <div className="table-scroll" style={{ marginTop: '0.75rem', maxHeight: '280px' }}>
          <table>
            <thead>
              <tr><th>Mã GV</th><th>Tên giảng viên</th><th>Tài khoản</th><th>Mật khẩu</th><th>Các môn học phụ trách</th><th>Thao tác</th></tr>
            </thead>
            <tbody>
              {!savedTeachers.length && <tr><td colSpan={6}>Chưa có giảng viên nào.</td></tr>}
              {savedTeachers.map(t => (
                <tr key={t.teacherId}>
                  <td><strong>{t.teacherId}</strong></td>
                  <td>{t.teacherName}</td>
                  <td><code>{t.username}</code></td>
                  <td><code>{t.defaultPassword}</code></td>
                  <td>
                    {(t.subjects || []).map(s => (
                      <div key={s.id || s.courseCode} style={{ fontSize: '0.9rem', marginBottom: '3px' }}>
                        <code>{s.courseCode}</code> — {s.courseName} (LT: {s.theoryWeight}, TH: {s.practiceWeight}){' '}
                        <button type="button" className="icon-button" style={{ padding: '2px' }} onClick={() => handleDeleteTeacherAssignment(s.id)} title="Xóa môn"><Trash2 size={12} /></button>
                      </div>
                    ))}
                  </td>
                  <td>
                    <button type="button" className="button compact" onClick={() => setTeacherForm({ teacherId: t.teacherId, teacherName: t.teacherName, subjects: (t.subjects || []).map(s => ({ ...s })) })}>
                      <Edit3 size={14} /> Sửa / +Môn
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* QUẢN LÝ PHÒNG HỌC */}
      <div style={{ marginTop: '1.5rem', border: '1px solid #e2e8f0', borderRadius: '8px', padding: '1rem', background: '#fff' }}>
        <h3><Building2 size={18} style={{ verticalAlign: 'middle' }} /> Phòng học của Khoa ({savedRooms.length} phòng)</h3>
        <form onSubmit={handleSaveRoom} style={{ marginTop: '0.75rem', display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: '0.5rem', alignItems: 'end' }}>
          <label>Mã phòng <input required placeholder="VD: 101-A2" value={roomForm.roomCode} onChange={e => setRoomForm({ ...roomForm, roomCode: e.target.value })} /></label>
          <label>Loại phòng <select value={roomForm.roomType} onChange={e => setRoomForm({ ...roomForm, roomType: e.target.value })}><option value="THEORY">Lý thuyết</option><option value="PRACTICE">Thực hành (LAB)</option></select></label>
          <label>Sức chứa <input type="number" min={1} required value={roomForm.capacity} onChange={e => setRoomForm({ ...roomForm, capacity: e.target.value })} /></label>
          <label>Tòa nhà <input required value={roomForm.building} onChange={e => setRoomForm({ ...roomForm, building: e.target.value })} /></label>
          <button type="submit" className="button primary compact"><Plus size={14} /> Lưu Phòng</button>
        </form>

        <div className="table-scroll" style={{ marginTop: '0.75rem', maxHeight: '220px' }}>
          <table>
            <thead><tr><th>Mã phòng</th><th>Loại</th><th>Sức chứa</th><th>Tòa</th><th>Xóa</th></tr></thead>
            <tbody>
              {savedRooms.map(r => (
                <tr key={r.id}>
                  <td><strong>{r.roomCode}</strong></td>
                  <td>{r.lab ? 'LAB' : 'LT'}</td>
                  <td>{r.capacity} chỗ</td>
                  <td>{r.building}</td>
                  <td><button type="button" className="icon-button" onClick={() => handleDeleteRoom(r.id)}><Trash2 size={14} /></button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}