import React, { useEffect, useState } from 'react';
import { Upload, Download, Calendar, CheckCircle2, AlertTriangle, Plus, Trash2, Search, Building2, Users, Save, Edit3 } from 'lucide-react';
import { api, dateLabel, addDays } from './schedule';
import { planWorkbook } from './autoScheduleWorkbook';
import { scheduledWorkbook } from './scheduleExport';
import { firstActiveWeek, validateAutoResult, validateFile, validateSchool } from './workflow';
import FormError from './FormError';

export default function AutoSchedulePage({ semesterStart, onScheduled, onViewTeacherSchedule }) {
  const [schoolId, setSchoolId] = useState('PTIT');
  const [schoolName, setSchoolName] = useState('Học viện Công nghệ Bưu chính Viễn thông');
  const [startDate, setStartDate] = useState(semesterStart || '2026-08-10');

  // Chỉ cần 1 file Kế hoạch mở lớp (có sẵn cột Loại lớp LT / TH)
  const [planFile, setPlanFile] = useState(null);

  // Danh sách Giảng viên - Môn học và Phòng học lưu sẵn trong MySQL
  const [savedTeachers, setSavedTeachers] = useState([]);
  const [savedRooms, setSavedRooms] = useState([]);
  const [loadingCatalog, setLoadingCatalog] = useState(false);

  // Form thêm/sửa Giảng viên (1 GV có thể thêm nhiều môn)
  const [teacherForm, setTeacherForm] = useState({
    id: null, teacherId: '', teacherName: '', courseCode: '', courseName: '', cohort: '*', theoryWeight: 1, practiceWeight: 1
  });

  // Form thêm/sửa Phòng học
  const [roomForm, setRoomForm] = useState({
    id: null, roomCode: '', roomType: 'THEORY', capacity: 60, building: 'A2'
  });

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState('');

  // Bộ lọc bảng kết quả
  const [filterText, setFilterText] = useState('');
  const [sessionFilter, setSessionFilter] = useState('ALL');
  const [weekFilter, setWeekFilter] = useState('');

  // Dữ liệu mẫu để xuất file Excel Kế hoạch mở lớp (có sẵn lớp LT và lớp TH)
  const samplePlans = [
    { courseCode: '61FIT3DBS', courseName: 'Cơ sở dữ liệu', classType: 'LT', classSize: 60, cohort: '2024', numClasses: 2, studyStartDate: startDate, studyEndDate: addDays(startDate, 105), credits: 3, totalPeriods: 30 },
    { courseCode: '61FIT3DBS', courseName: 'Cơ sở dữ liệu', classType: 'TH', classSize: 30, cohort: '2024', numClasses: 4, studyStartDate: startDate, studyEndDate: addDays(startDate, 105), credits: 3, totalPeriods: 15 },
    { courseCode: '61FIT3CGR', courseName: 'Đồ họa máy tính', classType: 'LT', classSize: 90, cohort: '2024', numClasses: 1, studyStartDate: startDate, studyEndDate: addDays(startDate, 105), credits: 3, totalPeriods: 30 },
    { courseCode: '61FIT3CGR', courseName: 'Đồ họa máy tính', classType: 'TH', classSize: 30, cohort: '2024', numClasses: 3, studyStartDate: startDate, studyEndDate: addDays(startDate, 105), credits: 3, totalPeriods: 15 }
  ];

  // Tải danh sách Giảng viên & Phòng học đã lưu của trường từ MySQL
  useEffect(() => {
    if (!schoolId.trim()) return;
    loadSchoolCatalog(schoolId.trim());
  }, [schoolId]);

  async function loadSchoolCatalog(code) {
    setLoadingCatalog(true);
    try {
      const [roomsData, teachersData] = await Promise.all([
        api(`/rooms?schoolId=${encodeURIComponent(code)}`),
        api(`/teacher-subjects?schoolId=${encodeURIComponent(code)}`)
      ]);
      setSavedRooms(Array.isArray(roomsData) ? roomsData : []);
      setSavedTeachers(Array.isArray(teachersData) ? teachersData : []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoadingCatalog(false);
    }
  }

  // ==================== CRUD GIẢNG VIÊN - MÔN HỌC ====================
  async function handleSaveTeacher(e) {
    e.preventDefault();
    setError('');
    try {
      await api('/teacher-subjects', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...teacherForm, schoolCode: schoolId.trim() })
      });
      setTeacherForm({ id: null, teacherId: '', teacherName: '', courseCode: '', courseName: '', cohort: '*', theoryWeight: 1, practiceWeight: 1 });
      await loadSchoolCatalog(schoolId.trim());
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleDeleteTeacher(id) {
    try {
      await api(`/teacher-subjects/${id}`, { method: 'DELETE' });
      await loadSchoolCatalog(schoolId.trim());
    } catch (err) {
      setError(err.message);
    }
  }

  // ==================== CRUD PHÒNG HỌC ====================
  async function handleSaveRoom(e) {
    e.preventDefault();
    setError('');
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
      setRoomForm({ id: null, roomCode: '', roomType: 'THEORY', capacity: 60, building: 'A2' });
      await loadSchoolCatalog(schoolId.trim());
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleDeleteRoom(id) {
    try {
      await api(`/rooms/${id}`, { method: 'DELETE' });
      await loadSchoolCatalog(schoolId.trim());
    } catch (err) {
      setError(err.message);
    }
  }

  // ==================== TẢI MẪU & CHẠY XẾP LỊCH TỪ 1 FILE ====================
  async function downloadPlanTemplate() {
    const workbook = planWorkbook(samplePlans);
    const buffer = await workbook.xlsx.writeBuffer();
    const blob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'mau-ke-hoach-mo-lop-LT-TH.xlsx';
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  async function handleScheduleSubmit(event) {
    event.preventDefault();
    if (busy || exporting) return;
    try {
      validateSchool(schoolId, schoolName, startDate);
      validateFile(planFile, 'Kế hoạch mở lớp');
      if (!savedTeachers.length) throw new Error('Hệ thống chưa có giảng viên nào. Vui lòng thêm giảng viên ở mục Quản lý bên dưới trước khi xếp lịch.');
      if (!savedRooms.length) throw new Error('Hệ thống chưa có phòng học nào. Vui lòng thêm phòng học ở mục Quản lý bên dưới trước khi xếp lịch.');
    } catch (err) {
      setError(err.message);
      return;
    }

    setBusy(true);
    setError('');
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
    if (weekFilter && (!slot.weekSchedule?.[Number(weekFilter) - 1] || ['-', ' '].includes(slot.weekSchedule[Number(weekFilter) - 1]))) return false;
    if (!filterText.trim()) return true;
    const q = filterText.toLocaleLowerCase('vi');
    return `${slot.teacherId} ${slot.teacherName} ${slot.courseCode} ${slot.courseName} ${slot.room}`.toLocaleLowerCase('vi').includes(q);
  });

  return (
    <section className="standalone-panel import-panel auto-panel" aria-busy={busy}>
      <div className="panel-title">
        <div>
          <h2>Xếp lịch toàn trường (1 File Kế hoạch mở lớp + Dữ liệu GV & Phòng lưu sẵn)</h2>
          <p className="muted-text">
            Hệ thống lưu sẵn danh mục Giảng viên (1 giảng viên dạy nhiều môn) và Phòng học (Lý thuyết / LAB). Khi xếp lịch chỉ cần tải lên 1 file Kế hoạch mở lớp có ghi rõ lớp Lý thuyết (LT) hay Thực hành (TH).
          </p>
        </div>
      </div>

      {/* ==================== PHẦN 1: TẢI FILE KẾ HOẠCH MỞ LỚP ĐỂ XẾP LỊCH ==================== */}
      <form onSubmit={handleScheduleSubmit}>
        <fieldset disabled={busy || exporting} className="import-fields">
          <label>
            Mã trường
            <input value={schoolId} onChange={e => setSchoolId(e.target.value)} required maxLength={50} />
          </label>
          <label>
            Tên trường
            <input value={schoolName} onChange={e => setSchoolName(e.target.value)} required maxLength={255} />
          </label>
          <label>
            Ngày Thứ Hai của Tuần 1
            <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} required />
          </label>

          <div className="file-field" style={{ gridColumn: '1 / -1' }}>
            <label htmlFor="auto-plan-file">
              <strong>File Kế hoạch mở lớp (.xlsx) — Đã có cột phân loại Lớp Lý thuyết (LT) / Lớp Thực hành (TH)</strong>
            </label>
            <input
              id="auto-plan-file"
              type="file"
              accept=".xlsx"
              onChange={e => setPlanFile(e.target.files?.[0] || null)}
              required
            />
            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', marginTop: '0.4rem' }}>
              <button type="button" className="button compact" onClick={downloadPlanTemplate}>
                <Download size={15} /> Tải mẫu Kế hoạch mở lớp (Có cột LT/TH)
              </button>
              <small>
                Trạng thái dữ liệu lưu sẵn của trường <strong>{schoolId}</strong>:{' '}
                <strong>{savedTeachers.length} phân công GV-Môn</strong> · <strong>{savedRooms.length} phòng học</strong>
              </small>
            </div>
          </div>
        </fieldset>

        <div className="import-actions" style={{ marginTop: '0.75rem' }}>
          <button type="submit" className="button primary" disabled={busy || !planFile}>
            <Upload size={16} /> {busy ? 'Đang xếp lịch tự động…' : 'Tải File Mở Lớp & Chạy Xếp Lịch Ngay'}
          </button>
        </div>
      </form>

      <FormError message={error} />

      {/* ==================== PHẦN 2: QUẢN LÝ GIẢNG VIÊN & MÔN DẠY LƯU SẴN ==================== */}
      <details open style={{ marginTop: '1.5rem' }}>
        <summary style={{ cursor: 'pointer', fontWeight: 600 }}>
          <Users size={16} style={{ verticalAlign: 'middle' }} /> Quản lý Giảng viên & Môn giảng dạy của trường ({savedTeachers.length} dòng — 1 GV có thể dạy nhiều môn)
        </summary>

        <form onSubmit={handleSaveTeacher} style={{ marginTop: '0.75rem', display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(135px, 1fr))', gap: '0.5rem', alignItems: 'end' }}>
          <label>Mã GV <input required placeholder="VD: GV01" value={teacherForm.teacherId} onChange={e => setTeacherForm({ ...teacherForm, teacherId: e.target.value })} /></label>
          <label>Họ tên GV <input required placeholder="Đào Ngọc Phong" value={teacherForm.teacherName} onChange={e => setTeacherForm({ ...teacherForm, teacherName: e.target.value })} /></label>
          <label>Mã môn dạy <input required placeholder="61FIT3DBS" value={teacherForm.courseCode} onChange={e => setTeacherForm({ ...teacherForm, courseCode: e.target.value })} /></label>
          <label>Tên môn <input placeholder="Cơ sở dữ liệu" value={teacherForm.courseName} onChange={e => setTeacherForm({ ...teacherForm, courseName: e.target.value })} /></label>
          <label>Khóa (* = mọi khóa) <input required placeholder="* hoặc 2024" value={teacherForm.cohort} onChange={e => setTeacherForm({ ...teacherForm, cohort: e.target.value })} /></label>
          <label>Trọng số LT <input type="number" min={0} required value={teacherForm.theoryWeight} onChange={e => setTeacherForm({ ...teacherForm, theoryWeight: Number(e.target.value) })} /></label>
          <label>Trọng số TH <input type="number" min={0} required value={teacherForm.practiceWeight} onChange={e => setTeacherForm({ ...teacherForm, practiceWeight: Number(e.target.value) })} /></label>
          <div style={{ display: 'flex', gap: '0.4rem' }}>
            <button type="submit" className="button primary compact">
              <Save size={14} /> {teacherForm.id ? 'Cập nhật GV' : 'Thêm GV - Môn'}
            </button>
            {teacherForm.id && (
              <button type="button" className="button compact" onClick={() => setTeacherForm({ id: null, teacherId: '', teacherName: '', courseCode: '', courseName: '', cohort: '*', theoryWeight: 1, practiceWeight: 1 })}>
                Hủy
              </button>
            )}
          </div>
        </form>

        <div className="table-scroll" style={{ marginTop: '0.75rem', maxHeight: '260px' }}>
          <table>
            <thead>
              <tr>
                <th>Mã GV</th>
                <th>Họ tên giảng viên</th>
                <th>Mã môn</th>
                <th>Tên môn</th>
                <th>Khóa</th>
                <th>Trọng số LT</th>
                <th>Trọng số TH</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {!savedTeachers.length && <tr><td colSpan={8}>Chưa có giảng viên nào. Hãy thêm giảng viên ở biểu mẫu trên.</td></tr>}
              {savedTeachers.map(t => (
                <tr key={t.id}>
                  <td><strong>{t.teacherId}</strong></td>
                  <td>{t.teacherName}</td>
                  <td><code>{t.courseCode}</code></td>
                  <td>{t.courseName || '—'}</td>
                  <td>{t.cohort === '*' ? 'Tất cả (*)' : t.cohort}</td>
                  <td>{t.theoryWeight}</td>
                  <td>{t.practiceWeight}</td>
                  <td style={{ display: 'flex', gap: '0.4rem' }}>
                    <button type="button" className="button compact" onClick={() => setTeacherForm(t)} title="Chỉnh sửa">
                      <Edit3 size={14} /> Sửa
                    </button>
                    <button type="button" className="icon-button" onClick={() => handleDeleteTeacher(t.id)} title="Xóa">
                      <Trash2 size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>

      {/* ==================== PHẦN 3: QUẢN LÝ PHÒNG HỌC LƯU SẴN ==================== */}
      <details open style={{ marginTop: '1.25rem' }}>
        <summary style={{ cursor: 'pointer', fontWeight: 600 }}>
          <Building2 size={16} style={{ verticalAlign: 'middle' }} /> Quản lý Danh mục Phòng học lưu sẵn ({savedRooms.length} phòng)
        </summary>

        <form onSubmit={handleSaveRoom} style={{ marginTop: '0.75rem', display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: '0.5rem', alignItems: 'end' }}>
          <label>Mã phòng <input required placeholder="VD: 101-A2 hoặc 601-LAB" value={roomForm.roomCode} onChange={e => setRoomForm({ ...roomForm, roomCode: e.target.value })} /></label>
          <label>
            Loại phòng
            <select value={roomForm.roomType} onChange={e => setRoomForm({ ...roomForm, roomType: e.target.value })}>
              <option value="THEORY">Phòng Lý thuyết (THEORY)</option>
              <option value="PRACTICE">Phòng Thực hành máy (LAB)</option>
            </select>
          </label>
          <label>Sức chứa (Chỗ) <input type="number" min={1} max={1000} required value={roomForm.capacity} onChange={e => setRoomForm({ ...roomForm, capacity: e.target.value })} /></label>
          <label>Tòa nhà <input required placeholder="A2" value={roomForm.building} onChange={e => setRoomForm({ ...roomForm, building: e.target.value })} /></label>
          <div style={{ display: 'flex', gap: '0.4rem' }}>
            <button type="submit" className="button primary compact">
              <Plus size={14} /> {roomForm.id ? 'Cập nhật Phòng' : 'Lưu Phòng học'}
            </button>
            {roomForm.id && (
              <button type="button" className="button compact" onClick={() => setRoomForm({ id: null, roomCode: '', roomType: 'THEORY', capacity: 60, building: 'A2' })}>
                Hủy
              </button>
            )}
          </div>
        </form>

        <div className="table-scroll" style={{ marginTop: '0.75rem', maxHeight: '260px' }}>
          <table>
            <thead>
              <tr>
                <th>Mã phòng</th>
                <th>Loại phòng</th>
                <th>Sức chứa</th>
                <th>Tòa nhà</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {!savedRooms.length && <tr><td colSpan={5}>Chưa có phòng học nào trong CSDL của trường.</td></tr>}
              {savedRooms.map(r => (
                <tr key={r.id}>
                  <td><strong>{r.roomCode}</strong></td>
                  <td>{r.lab ? 'Thực hành (LAB)' : 'Lý thuyết'}</td>
                  <td>{r.capacity} chỗ</td>
                  <td>{r.building}</td>
                  <td style={{ display: 'flex', gap: '0.4rem' }}>
                    <button type="button" className="button compact" onClick={() => setRoomForm({ id: r.id, roomCode: r.roomCode, roomType: r.lab ? 'PRACTICE' : 'THEORY', capacity: r.capacity, building: r.building })}>
                      <Edit3 size={14} /> Sửa
                    </button>
                    <button type="button" className="icon-button" onClick={() => handleDeleteRoom(r.id)}>
                      <Trash2 size={14} />
                    </button>
                  </td>
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
              <h2>{saved ? <CheckCircle2 size={18} /> : <AlertTriangle size={18} />} {saved ? 'Đã xếp và lưu lịch thành công' : 'Bản xem trước — chưa lưu'}</h2>
              <p>{result.message}</p>
            </div>
            <div className="template-actions">
              <button type="button" className="button primary" onClick={exportResultToExcel}>
                <Download size={16} /> Xuất kết quả Excel (.xlsx)
              </button>
            </div>
          </div>

          <div className="filter-bar" style={{ marginBottom: '0.75rem' }}>
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

          <div className="table-scroll">
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
                  <th>Xem lịch GV</th>
                </tr>
              </thead>
              <tbody>
                {filteredSlots.map((slot, index) => {
                  const isPractice = slot.sessionType === 'PRACTICE';
                  return (
                    <tr key={index}>
                      <td><strong>{slot.teacherId}</strong></td>
                      <td>{slot.teacherName}</td>
                      <td><code>{slot.courseCode}</code></td>
                      <td>{slot.courseName}</td>
                      <td>
                        <span className="badge" style={{ background: isPractice ? '#0284c7' : '#16a34a', color: '#fff' }}>
                          {isPractice ? 'Thực hành (TH)' : 'Lý thuyết (LT)'}
                        </span>
                      </td>
                      <td>{slot.studentCount} SV</td>
                      <td><strong>{slot.room}</strong> ({slot.roomCapacity} chỗ)</td>
                      <td>{slot.dayOfWeek === 8 ? 'CN' : `Thứ ${slot.dayOfWeek}`}</td>
                      <td>Tiết {slot.startPeriod}–{slot.startPeriod + slot.numPeriods - 1}</td>
                      <td>
                        {onViewTeacherSchedule && (
                          <button type="button" className="button compact" disabled={!saved} onClick={() => onViewTeacherSchedule(slot.teacherId, firstActiveWeek(result.scheduledClasses, slot.teacherId))}>
                            <Calendar size={14} /> Xem TKB
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}