import React, { useEffect, useState } from 'react';
import { Upload, Download, Calendar, CheckCircle2, AlertTriangle, Trash2, Search, UserPlus } from 'lucide-react';
import { api, addDays } from './schedule';
import { planWorkbook } from './autoScheduleWorkbook';
import { scheduledWorkbook } from './scheduleExport';
import { firstActiveWeek, validateAutoResult, validateFile, validateSchool } from './workflow';
import FormError from './FormError';

function partitionPracticePeriods(total) {
  const n = Number(total);
  if (!Number.isInteger(n) || n < 3) return null;
  for (let n4 = Math.floor(n / 4); n4 >= 0; n4--) {
    const rem = n - 4 * n4;
    if (rem % 3 === 0) {
      const n3 = rem / 3;
      return [...Array(n4).fill(4), ...Array(n3).fill(3)];
    }
  }
  return null;
}

export default function AutoSchedulePage({ semesterStart, onScheduled, onViewTeacherSchedule }) {
  const [schoolId, setSchoolId] = useState('PTIT');
  const [schoolName, setSchoolName] = useState('Khoa Công nghệ Thông tin - PTIT');
  const [startDate, setStartDate] = useState(semesterStart || '2026-08-10');

  const [planFile, setPlanFile] = useState(null);
  const [savedTeachers, setSavedTeachers] = useState([]);
  const [savedRooms, setSavedRooms] = useState([]);

  // Form Thỉnh giảng hỗ trợ nhiều môn / nhiều lớp cùng lúc
  const [visitingForm, setVisitingForm] = useState({
    teacherId: '',
    teacherName: '',
    assignments: [
      { courseCode: '', courseName: '', cohort: '2024', classGroup: 'Lớp 01', sessionType: 'THEORY', studentCount: 60, requiredPeriods: 30 }
    ],
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
    loadCatalog(schoolId.trim());
  }, [schoolId]);

  async function loadCatalog(code) {
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

  async function handleClearDepartmentSchedule() {
    if (!window.confirm(`Bạn có chắc chắn muốn XÓA TOÀN BỘ lịch đã xếp của khoa "${schoolName}" (${schoolId})?`)) {
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

  function toggleBusyDay(day) {
    setVisitingForm(prev => {
      const exists = prev.busyDaysOfWeek.includes(day);
      return {
        ...prev,
        busyDaysOfWeek: exists ? prev.busyDaysOfWeek.filter(d => d !== day) : [...prev.busyDaysOfWeek, day]
      };
    });
  }

  function addVisitingAssignment() {
    setVisitingForm(prev => ({
      ...prev,
      assignments: [...prev.assignments, { courseCode: '', courseName: '', cohort: '2024', classGroup: 'Lớp 02', sessionType: 'THEORY', studentCount: 60, requiredPeriods: 30 }]
    }));
  }

  function removeVisitingAssignment(idx) {
    if (visitingForm.assignments.length === 1) return;
    setVisitingForm(prev => ({
      ...prev,
      assignments: prev.assignments.filter((_, i) => i !== idx)
    }));
  }

  function updateVisitingAssignment(idx, field, value) {
    setVisitingForm(prev => {
      const next = [...prev.assignments];
      next[idx] = { ...next[idx], [field]: value };
      return { ...prev, assignments: next };
    });
  }

  async function handleVisitingSubmit(e) {
    e.preventDefault();
    if (busy) return;
    setError('');
    setInfoMessage('');

    for (let i = 0; i < visitingForm.assignments.length; i++) {
      const a = visitingForm.assignments[i];
      if (a.sessionType === 'PRACTICE' && !partitionPracticePeriods(a.requiredPeriods)) {
        setError(`Môn ${a.courseCode || i + 1}: Tổng tiết thực hành (${a.requiredPeriods}) không thể chia thành các buổi 3–4 tiết.`);
        return;
      }
    }

    setBusy(true);
    try {
      const response = await api('/visiting-schedule', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...visitingForm,
          schoolId: schoolId.trim(),
          schoolName: schoolName.trim(),
          semesterStartDate: startDate,
          assignments: visitingForm.assignments.map(a => ({
            ...a,
            studentCount: Number(a.studentCount),
            requiredPeriods: Number(a.requiredPeriods)
          }))
        })
      });
      setResult(response);
      if (response.saved) {
        setInfoMessage(response.message);
        await loadCatalog(schoolId.trim());
      } else {
        setError(response.message || 'Chưa lưu lịch thỉnh giảng.');
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
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
      if (!savedTeachers.length) throw new Error('Khoa chưa có giảng viên nào. Vui lòng thêm giảng viên ở trang Quản lý Giảng viên & Phòng học.');
      if (!savedRooms.length) throw new Error('Khoa chưa có phòng học nào. Vui lòng thêm phòng học ở trang Quản lý Giảng viên & Phòng học.');
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
            Chính sách: rải đều toàn học kỳ, mỗi lớp học tối đa 1 ca/tuần; LT tối đa 3 tiết/ngày; TH chia 3–4 tiết cách 14/7 ngày.
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

      <form onSubmit={handleScheduleSubmit}>
        <fieldset disabled={busy || exporting} className="import-fields">
          <label>Mã Khoa <input value={schoolId} onChange={e => setSchoolId(e.target.value)} required maxLength={50} /></label>
          <label>Tên Khoa / Đơn vị <input value={schoolName} onChange={e => setSchoolName(e.target.value)} required maxLength={255} /></label>
          <label>Ngày Thứ Hai của Tuần 1 <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} required /></label>

          <div className="file-field" style={{ gridColumn: '1 / -1' }}>
            <label htmlFor="auto-plan-file"><strong>File Danh sách mở lớp của Khoa (.xlsx)</strong></label>
            <input id="auto-plan-file" type="file" accept=".xlsx" onChange={e => setPlanFile(e.target.files?.[0] || null)} required />
            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', marginTop: '0.4rem' }}>
              <button type="button" className="button compact" onClick={downloadPlanTemplate}><Download size={15} /> Tải mẫu Mở lớp</button>
              <small>Đang lưu sẵn trong hệ thống: <strong>{savedTeachers.length} giảng viên</strong> · <strong>{savedRooms.length} phòng học</strong></small>
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

      {/* FORM THỈNH GIẢNG ĐA MÔN / ĐA LỚP */}
      <details open style={{ marginTop: '1.5rem', border: '1px solid #bae6fd', borderRadius: '10px', padding: '0.85rem', background: '#f0f9ff' }}>
        <summary style={{ cursor: 'pointer', fontWeight: 700, color: '#0369a1' }}>
          <UserPlus size={17} style={{ verticalAlign: 'middle' }} /> Xếp lịch bổ sung cho Giảng viên Thỉnh giảng (Đảm nhận nhiều môn, nhiều lớp)
        </summary>
        <p className="muted-text" style={{ marginTop: '0.4rem' }}>
          Thêm danh sách các môn học / lớp học phân công cho giảng viên thỉnh giảng. Hệ thống tự động xếp lịch rải đều, tránh trùng phòng và giờ bận.
        </p>

        <form onSubmit={handleVisitingSubmit} style={{ marginTop: '0.75rem' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.6rem', marginBottom: '0.75rem' }}>
            <label>Mã GV thỉnh giảng <input required placeholder="VD: GV_TG01" value={visitingForm.teacherId} onChange={e => setVisitingForm({ ...visitingForm, teacherId: e.target.value })} /></label>
            <label>Tên GV thỉnh giảng <input required placeholder="VD: PGS.TS Trần Văn B" value={visitingForm.teacherName} onChange={e => setVisitingForm({ ...visitingForm, teacherName: e.target.value })} /></label>
            <label>Từ ngày học <input type="date" required value={visitingForm.studyStartDate} onChange={e => setVisitingForm({ ...visitingForm, studyStartDate: e.target.value })} /></label>
            <label>Đến ngày học <input type="date" required value={visitingForm.studyEndDate} onChange={e => setVisitingForm({ ...visitingForm, studyEndDate: e.target.value })} /></label>
          </div>

          <h4>Danh sách các môn học / lớp học phân công cho giảng viên này</h4>
          {visitingForm.assignments.map((asg, idx) => (
            <div key={idx} style={{ display: 'grid', gridTemplateColumns: '1fr 1.5fr 75px 1.1fr 1.1fr 75px 85px 40px', gap: '0.4ynth', alignItems: 'end', marginBottom: '0.5rem', background: '#fff', padding: '0.5rem', borderRadius: '6px', border: '1px solid #e2e8f0' }}>
              <label>Mã môn <input required placeholder="61FIT3DBS" value={asg.courseCode} onChange={e => updateVisitingAssignment(idx, 'courseCode', e.target.value)} /></label>
              <label>Tên môn <input required placeholder="Cơ sở dữ liệu" value={asg.courseName} onChange={e => updateVisitingAssignment(idx, 'courseName', e.target.value)} /></label>
              <label>Khóa <input required placeholder="2024" value={asg.cohort} onChange={e => updateVisitingAssignment(idx, 'cohort', e.target.value)} /></label>
              <label>Lớp / Nhóm <input required placeholder="Lớp 01" value={asg.classGroup} onChange={e => updateVisitingAssignment(idx, 'classGroup', e.target.value)} /></label>
              <label>
                Loại
                <select value={asg.sessionType} onChange={e => updateVisitingAssignment(idx, 'sessionType', e.target.value)}>
                  <option value="THEORY">Lý thuyết</option>
                  <option value="PRACTICE">Thực hành</option>
                </select>
              </label>
              <label>Sĩ số <input type="number" min={1} required value={asg.studentCount} onChange={e => updateVisitingAssignment(idx, 'studentCount', e.target.value)} /></label>
              <label>Tổng tiết <input type="number" min={1} required value={asg.requiredPeriods} onChange={e => updateVisitingAssignment(idx, 'requiredPeriods', e.target.value)} /></label>
              <button type="button" className="icon-button" onClick={() => removeVisitingAssignment(idx)} disabled={visitingForm.assignments.length === 1} title="Xóa môn này"><Trash2 size={16} /></button>
            </div>
          ))}

          <button type="button" className="button compact" onClick={addVisitingAssignment} style={{ marginBottom: '1rem' }}>
            <Plus size={14} /> + Thêm môn / lớp khác cho GV thỉnh giảng này
          </button>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.75rem', alignItems: 'center' }}>
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
              Hoặc ngày bận cụ thể (YYYY-MM-DD, cách nhau dấu phẩy)
              <input
                placeholder="VD: 2026-08-18, 2026-08-25"
                value={visitingForm.busyDatesText}
                onChange={e => setVisitingForm({ ...visitingForm, busyDatesText: e.target.value })}
              />
            </label>
            <button type="submit" className="button primary" disabled={busy}>
              <UserPlus size={16} /> Xếp phòng & Lưu toàn bộ lịch Thỉnh giảng
            </button>
          </div>
        </form>
      </details>

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
                  <th>Mã GV</th><th>Giảng viên</th><th>Mã môn</th><th>Học phần & Nhóm</th><th>Loại lớp</th><th>Sĩ số</th><th>Phòng học</th><th>Thứ</th><th>Tiết học</th><th>Ghi chú</th><th>Xem TKB</th>
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
                    <td><small className="muted-text">{slot.phaseNote || '—'}</small></td>
                    <td>
                      {onViewTeacherSchedule && (
                        <button type="button" className="button compact" disabled={!saved} onClick={() => onViewTeacherSchedule(slot.teacherId, firstActiveWeek(result.scheduledClasses, slot.teacherId))}>
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