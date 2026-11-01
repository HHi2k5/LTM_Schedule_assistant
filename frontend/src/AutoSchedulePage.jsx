import React, { useEffect, useState } from 'react';
import { Upload, Download, Calendar, CheckCircle2, AlertTriangle, Plus, Trash2, Search, Building2, Users, Save, Edit3, UserPlus } from 'lucide-react';
import ExcelJS from 'exceljs';
import { api, dateLabel, addDays } from './schedule';
import { planWorkbook } from './autoScheduleWorkbook';
import { scheduledWorkbook } from './scheduleExport';
import { firstActiveWeek, validateAutoResult, validateFile, validateSchool } from './workflow';
import FormError from './FormError';

const emptyTeacherForm = {
  teacherId: '',
  teacherName: '',
  subjects: [
    { id: null, courseCode: '', courseName: '', cohort: '*', theoryWeight: 1, practiceWeight: 1 }
  ]
};

export default function AutoSchedulePage({ semesterStart, onScheduled, onViewTeacherSchedule }) {
  const [schoolId, setSchoolId] = useState('PTIT');
  const [schoolName, setSchoolName] = useState('Khoa Công nghệ Thông tin - PTIT');
  const [startDate, setStartDate] = useState(semesterStart || '2026-08-10');

  const [planFile, setPlanFile] = useState(null);
  const [savedTeachers, setSavedTeachers] = useState([]);
  const [savedRooms, setSavedRooms] = useState([]);

  const [teacherForm, setTeacherForm] = useState(emptyTeacherForm);
  const [roomForm, setRoomForm] = useState({
    id: null, roomCode: '', roomType: 'THEORY', capacity: 60, building: 'A2'
  });

  // Form Xếp lịch Giảng viên Thỉnh giảng (sau khi xếp lịch khoa xong)
  const [visitingForm, setVisitingForm] = useState({
    teacherId: '',
    teacherName: '',
    courseCode: '',
    courseName: '',
    cohort: '2024',
    classGroup: 'Lớp 01',
    sessionType: 'THEORY',
    studentCount: 60,
    requiredPeriods: 30,
    periodsPerSession: 3,
    studyStartDate: startDate,
    studyEndDate: addDays(startDate, 105),
    busyDaysOfWeek: [],
    busyDatesText: ''
  });

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [infoMessage, setInfoMessage] = useState('');
  const [result, setResult] = useState(null);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState('');

  const [filterText, setFilterText] = useState('');
  const [sessionFilter, setSessionFilter] = useState('ALL');

  const samplePlans = [
    { courseCode: '61FIT3DBS', courseName: 'Cơ sở dữ liệu', classType: 'LT', classSize: 60, cohort: '2024', numClasses: 2, studyStartDate: startDate, studyEndDate: addDays(startDate, 105), credits: 3, totalPeriods: 30 },
    { courseCode: '61FIT3DBS', courseName: 'Cơ sở dữ liệu', classType: 'TH', classSize: 30, cohort: '2024', numClasses: 4, studyStartDate: startDate, studyEndDate: addDays(startDate, 105), credits: 3, totalPeriods: 15 },
    { courseCode: '61FIT3CGR', courseName: 'Đồ họa máy tính', classType: 'LT', classSize: 90, cohort: '2024', numClasses: 1, studyStartDate: startDate, studyEndDate: addDays(startDate, 105), credits: 3, totalPeriods: 30 },
    { courseCode: '61FIT3CGR', courseName: 'Đồ họa máy tính', classType: 'TH', classSize: 30, cohort: '2024', numClasses: 3, studyStartDate: startDate, studyEndDate: addDays(startDate, 105), credits: 3, totalPeriods: 15 }
  ];

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

  // ==================== LƯU & XÓA GIẢNG VIÊN ====================
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

  // ==================== XUẤT EXCEL TÀI KHOẢN GỬI GIẢNG VIÊN ====================
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

  // ==================== XÓA TOÀN BỘ LỊCH CŨ CỦA KHOA ====================
  async function handleClearDepartmentSchedule() {
    if (!window.confirm(`Bạn có chắc chắn muốn XÓA TOÀN BỘ lịch đã xếp của khoa "${schoolName}" (${schoolId})? Lịch chỉ bị xóa khi bạn xác nhận thao tác này.`)) {
      return;
    }
    setBusy(true);
    setError('');
    setInfoMessage('');
    try {
      const res = await api(`/department-schedule?schoolId=${encodeURIComponent(schoolId.trim())}`, {
        method: 'DELETE'
      });
      setResult(null);
      setInfoMessage(res.message || 'Đã xóa toàn bộ lịch cũ của khoa!');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  // ==================== XẾP LỊCH GIẢNG VIÊN THỈNH GIẢNG ====================
  function toggleBusyDay(day) {
    setVisitingForm(prev => {
      const exists = prev.busyDaysOfWeek.includes(day);
      return {
        ...prev,
        busyDaysOfWeek: exists ? prev.busyDaysOfWeek.filter(d => d !== day) : [...prev.busyDaysOfWeek, day]
      };
    });
  }

  async function handleVisitingSubmit(e) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    setInfoMessage('');
    try {
      const response = await api('/visiting-schedule', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...visitingForm,
          schoolId: schoolId.trim(),
          schoolName: schoolName.trim(),
          semesterStartDate: startDate,
          studentCount: Number(visitingForm.studentCount),
          requiredPeriods: Number(visitingForm.requiredPeriods),
          periodsPerSession: Number(visitingForm.periodsPerSession)
        })
      });
      setResult(response);
      setInfoMessage(response.message);
      await loadSchoolCatalog(schoolId.trim());
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  // ==================== LƯU & XÓA PHÒNG HỌC ====================
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

  async function downloadPlanTemplate() {
    const workbook = planWorkbook(samplePlans);
    const buffer = await workbook.xlsx.writeBuffer();
    const blob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'mau-ke-hoach-mo-lop-khoa.xlsx';
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  async function handleScheduleSubmit(event) {
    event.preventDefault();
    if (busy || exporting) return;
    try {
      validateSchool(schoolId, schoolName, startDate);
      validateFile(planFile, 'Kế hoạch mở lớp');
      if (!savedTeachers.length) throw new Error('Khoa chưa có giảng viên nào. Vui lòng thêm giảng viên trước khi xếp lịch.');
      if (!savedRooms.length) throw new Error('Khoa chưa có phòng học nào. Vui lòng thêm phòng học trước khi xếp lịch.');
    } catch (err) {
      setError(err.message);
      return;
    }

    setBusy(true);
    setError('');
    setInfoMessage('');
    setResult(null);

    try {
      const formData = new FormData();
      formData.append('planFile', planFile);
      formData.append('schoolId', schoolId.trim());
      formData.append('schoolName', schoolName.trim());
      formData.append('semesterStartDate', startDate);

      const response = await api('/auto-generate-3files', {
        method: 'POST',
        body: formData,
      });

      validateAutoResult(response);
      setResult(response);
      if (response.saved) onScheduled?.(response);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function exportResultToExcel() {
    if (busy || exporting || !result?.scheduledClasses?.length) return;
    setExporting(true);
    setExportError('');
    try {
      const { workbook, filename } = scheduledWorkbook(result);
      const buffer = await workbook.xlsx.writeBuffer();
      const blob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (err) {
      setExportError(err.message);
    } finally {
      setExporting(false);
    }
  }

  const saved = result?.saved === true && result?.status === 'SUCCESS';
  const filteredSlots = (result?.scheduledClasses || []).filter(slot => {
    if (sessionFilter !== 'ALL' && slot.sessionType !== sessionFilter) return false;
    if (!filterText.trim()) return true;
    const q = filterText.toLocaleLowerCase('vi');
    return `${slot.teacherId} ${slot.teacherName} ${slot.courseCode} ${slot.courseName} ${slot.room}`.toLocaleLowerCase('vi').includes(q);
  });

  return (
    <section className="standalone-panel import-panel auto-panel" aria-busy={busy}>
      <div className="panel-title">
        <div>
          <h2>Xếp lịch giảng dạy của Khoa</h2>
          <p className="muted-text">
            Lịch toàn khoa sau khi xếp được lưu cố định trong cơ sở dữ liệu (chỉ xóa khi Admin bấm nút Xóa lịch cũ). Hỗ trợ quản lý 1 giảng viên dạy nhiều môn và xếp lịch bổ sung cho giảng viên thỉnh giảng.
          </p>
        </div>
        <button
          type="button"
          className="button"
          style={{ color: '#dc2626', borderColor: '#fca5a5' }}
          disabled={busy}
          onClick={handleClearDepartmentSchedule}
        >
          <Trash2 size={16} /> Xóa toàn bộ lịch cũ của Khoa
        </button>
      </div>

      {/* ==================== PHẦN 1: XẾP LỊCH TOÀN KHOA TỪ 1 FILE MỞ LỚP ==================== */}
      <form onSubmit={handleScheduleSubmit}>
        <fieldset disabled={busy || exporting} className="import-fields">
          <label>Mã Khoa <input value={schoolId} onChange={e => setSchoolId(e.target.value)} required maxLength={50} /></label>
          <label>Tên Khoa / Đơn vị <input value={schoolName} onChange={e => setSchoolName(e.target.value)} required maxLength={255} /></label>
          <label>Ngày Thứ Hai của Tuần 1 <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} required /></label>

          <div className="file-field" style={{ gridColumn: '1 / -1' }}>
            <label htmlFor="auto-plan-file"><strong>File Danh sách mở lớp của Khoa (.xlsx) — Có sẵn cột Loại lớp (LT / TH)</strong></label>
            <input id="auto-plan-file" type="file" accept=".xlsx" onChange={e => setPlanFile(e.target.files?.[0] || null)} required />
            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', marginTop: '0.4rem' }}>
              <button type="button" className="button compact" onClick={downloadPlanTemplate}><Download size={15} /> Tải mẫu Mở lớp</button>
              <small>Đang lưu sẵn: <strong>{savedTeachers.length} giảng viên</strong> · <strong>{savedRooms.length} phòng học</strong></small>
            </div>
          </div>
        </fieldset>

        <div className="import-actions" style={{ marginTop: '0.75rem' }}>
          <button type="submit" className="button primary" disabled={busy || !planFile}>
            <Upload size={16} /> {busy ? 'Đang xếp lịch khoa…' : 'Tải File Mở Lớp & Chạy Xếp Lịch Khoa'}
          </button>
        </div>
      </form>

      <FormError message={error} />
      {infoMessage && <div role="status" className="info-banner" style={{ marginTop: '0.75rem' }}><CheckCircle2 size={18} /> {infoMessage}</div>}

      {/* ==================== PHẦN 2: THÊM & XẾP LỊCH GIẢNG VIÊN THỈNH GIẢNG ==================== */}
      <details open style={{ marginTop: '1.5rem', border: '1px solid #bae6fd', borderRadius: '10px', padding: '0.85rem', background: '#f0f9ff' }}>
        <summary style={{ cursor: 'pointer', fontWeight: 700, color: '#0369a1' }}>
          <UserPlus size={17} style={{ verticalAlign: 'middle' }} /> Xếp lịch bổ sung cho Giảng viên Thỉnh giảng (Khi môn học thiếu GV sau khi xếp lịch khoa)
        </summary>
        <p className="muted-text" style={{ marginTop: '0.4rem' }}>
          Nhập Mã GV, Tên GV thỉnh giảng, môn học & lớp học được giao cùng các ngày bận. Hệ thống tự động cấp tài khoản cho GV thỉnh giảng, chọn phòng trống phù hợp và tự động điều chỉnh lịch cũ nếu cần thiết.
        </p>

        <form onSubmit={handleVisitingSubmit} style={{ marginTop: '0.75rem' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(155px, 1fr))', gap: '0.6rem' }}>
            <label>Mã GV thỉnh giảng <input required placeholder="VD: GV_TG01" value={visitingForm.teacherId} onChange={e => setVisitingForm({ ...visitingForm, teacherId: e.target.value })} /></label>
            <label>Tên GV thỉnh giảng <input required placeholder="VD: PGS.TS Trần Văn B" value={visitingForm.teacherName} onChange={e => setVisitingForm({ ...visitingForm, teacherName: e.target.value })} /></label>
            <label>Mã môn học <input required placeholder="VD: 61FIT3DBS" value={visitingForm.courseCode} onChange={e => setVisitingForm({ ...visitingForm, courseCode: e.target.value })} /></label>
            <label>Tên môn học <input required placeholder="VD: Cơ sở dữ liệu" value={visitingForm.courseName} onChange={e => setVisitingForm({ ...visitingForm, courseName: e.target.value })} /></label>
            <label>Khóa <input required placeholder="2024" value={visitingForm.cohort} onChange={e => setVisitingForm({ ...visitingForm, cohort: e.target.value })} /></label>
            <label>Lớp / Nhóm cần xếp <input required placeholder="VD: Lớp 03 hoặc Tổ 2" value={visitingForm.classGroup} onChange={e => setVisitingForm({ ...visitingForm, classGroup: e.target.value })} /></label>
            <label>
              Loại lớp
              <select value={visitingForm.sessionType} onChange={e => setVisitingForm({ ...visitingForm, sessionType: e.target.value })}>
                <option value="THEORY">Lý thuyết (LT)</option>
                <option value="PRACTICE">Thực hành (TH / LAB)</option>
              </select>
            </label>
            <label>Sĩ số SV <input type="number" min={1} max={500} required value={visitingForm.studentCount} onChange={e => setVisitingForm({ ...visitingForm, studentCount: e.target.value })} /></label>
            <label>Tổng số tiết cần xếp <input type="number" min={1} max={300} required value={visitingForm.requiredPeriods} onChange={e => setVisitingForm({ ...visitingForm, requiredPeriods: e.target.value })} /></label>
            <label>Số tiết / buổi <input type="number" min={1} max={5} required value={visitingForm.periodsPerSession} onChange={e => setVisitingForm({ ...visitingForm, periodsPerSession: e.target.value })} /></label>
            <label>Từ ngày <input type="date" required value={visitingForm.studyStartDate} onChange={e => setVisitingForm({ ...visitingForm, studyStartDate: e.target.value })} /></label>
            <label>Đến ngày <input type="date" required value={visitingForm.studyEndDate} onChange={e => setVisitingForm({ ...visitingForm, studyEndDate: e.target.value })} /></label>
          </div>

          <div style={{ marginTop: '0.75rem', display: 'flex', flexWrap: 'wrap', gap: '0.75rem', alignItems: 'center' }}>
            <strong>Các thứ bận cố định trong tuần:</strong>
            {[2, 3, 4, 5, 6, 7].map(d => (
              <label key={d} style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={visitingForm.busyDaysOfWeek.includes(d)}
                  onChange={() => toggleBusyDay(d)}
                />
                Thứ {d}
              </label>
            ))}
          </div>

          <div style={{ marginTop: '0.6rem', display: 'flex', gap: '0.75rem', alignItems: 'end', flexWrap: 'wrap' }}>
            <label style={{ flex: 1, minWidth: '260px' }}>
              Hoặc các ngày bận cụ thể (YYYY-MM-DD, cách nhau dấu phẩy)
              <input
                placeholder="VD: 2026-08-18, 2026-08-25"
                value={visitingForm.busyDatesText}
                onChange={e => setVisitingForm({ ...visitingForm, busyDatesText: e.target.value })}
              />
            </label>
            <button type="submit" className="button primary" disabled={busy}>
              <UserPlus size={16} /> Xếp phòng & Lưu lịch Giảng viên Thỉnh giảng
            </button>
          </div>
        </form>
      </details>

      {/* ==================== PHẦN 3: QUẢN LÝ GIẢNG VIÊN CỦA KHOA (1 GV DẠY NHIỀU MÔN) ==================== */}
      <details open style={{ marginTop: '1.5rem' }}>
        <summary style={{ cursor: 'pointer', fontWeight: 600 }}>
          <Users size={16} style={{ verticalAlign: 'middle' }} /> Quản lý Giảng viên Cơ hữu & Nhiều Môn dạy của Khoa ({savedTeachers.length} giảng viên)
        </summary>

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '0.6rem', flexWrap: 'wrap', gap: '0.5rem' }}>
          <small className="muted-text">Mỗi giảng viên có thể thêm nhiều môn học cùng lúc. Hệ thống tự tạo Tài khoản = Mã GV, Mật khẩu = Tên không dấu + Mã GV.</small>
          <button type="button" className="button primary compact" disabled={!savedTeachers.length} onClick={exportTeacherAccountsToExcel}>
            <Download size={15} /> Xuất Excel Tài khoản gửi GV (.xlsx)
          </button>
        </div>

        <form onSubmit={handleSaveTeacher} style={{ marginTop: '0.75rem', background: '#f8fafc', padding: '1rem', borderRadius: '8px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.75rem', marginBottom: '1rem' }}>
            <label>Mã giảng viên <input required placeholder="VD: GV01" value={teacherForm.teacherId} onChange={e => handleTeacherIdChange(e.target.value)} /></label>
            <label>Họ tên giảng viên <input required placeholder="VD: Đào Ngọc Phong" value={teacherForm.teacherName} onChange={e => setTeacherForm({ ...teacherForm, teacherName: e.target.value })} /></label>
          </div>

          <h4>Danh sách các môn học giảng viên này phụ trách</h4>
          {teacherForm.subjects.map((sub, idx) => (
            <div key={idx} style={{ display: 'grid', gridTemplateColumns: '1fr 1.5fr 90px 90px 40px', gap: '0.5rem', alignItems: 'end', marginBottom: '0.5rem' }}>
              <label>Mã môn <input required placeholder="61FIT3DBS" value={sub.courseCode} onChange={e => updateSubject(idx, 'courseCode', e.target.value)} /></label>
              <label>Tên môn <input required placeholder="Cơ sở dữ liệu" value={sub.courseName} onChange={e => updateSubject(idx, 'courseName', e.target.value)} /></label>
              <label>Trọng số LT <input type="number" min={0} required value={sub.theoryWeight} onChange={e => updateSubject(idx, 'theoryWeight', Number(e.target.value))} /></label>
              <label>Trọng số TH <input type="number" min={0} required value={sub.practiceWeight} onChange={e => updateSubject(idx, 'practiceWeight', Number(e.target.value))} /></label>
              <button type="button" className="icon-button" onClick={() => removeSubjectRow(idx)} disabled={teacherForm.subjects.length === 1} title="Xóa dòng môn này"><Trash2 size={16} /></button>
            </div>
          ))}

          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.75rem' }}>
            <button type="button" className="button compact" onClick={addSubjectRow}><Plus size={14} /> + Thêm môn học khác cho GV này</button>
            <button type="submit" className="button primary compact"><Save size={14} /> Lưu Giảng viên & Danh sách Môn dạy</button>
          </div>
        </form>

        <div className="table-scroll" style={{ marginTop: '0.75rem', maxHeight: '280px' }}>
          <table>
            <thead>
              <tr>
                <th>Mã GV</th>
                <th>Tên giảng viên</th>
                <th>Tài khoản</th>
                <th>Mật khẩu mặc định</th>
                <th>Các môn học phụ trách (Mã - Tên - Trọng số LT/TH)</th>
                <th>Thao tác</th>
              </tr>
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
                        <button type="button" className="icon-button" style={{ padding: '2px' }} onClick={() => handleDeleteTeacherAssignment(s.id)} title="Xóa môn này"><Trash2 size={12} /></button>
                      </div>
                    ))}
                  </td>
                  <td>
                    <button type="button" className="button compact" onClick={() => setTeacherForm({ teacherId: t.teacherId, teacherName: t.teacherName, subjects: (t.subjects || []).map(s => ({ ...s })) })} title="Sửa hoặc thêm môn cho GV này">
                      <Edit3 size={14} /> Sửa / +Môn
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>

      {/* ==================== PHẦN 4: QUẢN LÝ PHÒNG HỌC ==================== */}
      <details open style={{ marginTop: '1.25rem' }}>
        <summary style={{ cursor: 'pointer', fontWeight: 600 }}>
          <Building2 size={16} style={{ verticalAlign: 'middle' }} /> Quản lý Phòng học của Khoa ({savedRooms.length} phòng)
        </summary>
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
      </details>

      {/* ==================== BẢNG KẾT QUẢ XẾP LỊCH ==================== */}
      {exportError && <p role="alert" className="error-text">{exportError}</p>}
      {result && (
        <div className="import-preview" style={{ marginTop: '1.5rem' }}>
          <div className="panel-title">
            <div>
              <h2>{saved ? <CheckCircle2 size={18} /> : <AlertTriangle size={18} />} {saved ? 'Đã lưu lịch vào Cơ sở dữ liệu' : 'Bản xem trước — chưa lưu'}</h2>
              <p>{result.message}</p>
              {result.unscheduledWarnings?.length > 0 && (
                <div className="import-warning" style={{ marginTop: '0.5rem' }}>
                  <AlertTriangle size={16} />
                  <span><strong>Cảnh báo môn/lớp còn thiếu giảng viên hoặc phòng:</strong> {result.unscheduledWarnings.join(' | ')}</span>
                </div>
              )}
            </div>
            <div className="template-actions">
              <button type="button" className="button primary" onClick={exportResultToExcel}><Download size={16} /> Xuất Excel</button>
            </div>
          </div>

          <div className="filter-bar" style={{ marginTop: '0.75rem' }}>
            <label className="search-field">
              <Search size={17} />
              <input placeholder="Tìm theo giảng viên, môn học, phòng…" value={filterText} onChange={e => setFilterText(e.target.value)} />
            </label>
            <select value={sessionFilter} onChange={e => setSessionFilter(e.target.value)}>
              <option value="ALL">Tất cả (Lý thuyết + Thực hành)</option>
              <option value="THEORY">Lớp Lý thuyết</option>
              <option value="PRACTICE">Lớp Thực hành</option>
            </select>
          </div>

          <div className="table-scroll" style={{ marginTop: '0.75rem' }}>
            <table>
              <thead>
                <tr>
                  <th>Mã GV</th>
                  <th>Giảng viên</th>
                  <th>Mã môn</th>
                  <th>Học phần & Nhóm</th>
                  <th>Loại lớp</th>
                  <th>Sĩ số</th>
                  <th>Phòng học</th>
                  <th>Thứ</th>
                  <th>Tiết học</th>
                  <th>Xem TKB</th>
                </tr>
              </thead>
              <tbody>
                {filteredSlots.map((slot, index) => (
                  <tr key={index}>
                    <td><strong>{slot.teacherId}</strong></td>
                    <td>{slot.teacherName}</td>
                    <td><code>{slot.courseCode}</code></td>
                    <td>{slot.courseName}</td>
                    <td><span className="badge" style={{ background: slot.sessionType === 'PRACTICE' ? '#0284c7' : '#16a34a', color: '#fff' }}>{slot.sessionType === 'PRACTICE' ? 'TH' : 'LT'}</span></td>
                    <td>{slot.studentCount} SV</td>
                    <td><strong>{slot.room}</strong></td>
                    <td>Thứ {slot.dayOfWeek}</td>
                    <td>Tiết {slot.startPeriod}–{slot.startPeriod + slot.numPeriods - 1}</td>
                    <td>
                      {onViewTeacherSchedule && (
                        <button type="button" className="button compact" onClick={() => onViewTeacherSchedule(slot.teacherId, firstActiveWeek(result.scheduledClasses, slot.teacherId))}>
                          <Calendar size={14} /> Xem TKB
                        </button>
                      )}
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