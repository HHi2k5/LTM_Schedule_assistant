import React, { useEffect, useState } from 'react';
import { Upload, Download, Calendar, CheckCircle2, AlertTriangle, Search, Trash2, Clock3, Send, RotateCcw, Layers } from 'lucide-react';
import { api, addDays, clock } from './schedule';
import { postJson } from './catalog';
import { planWorkbook } from './autoScheduleWorkbook';
import { scheduledWorkbook } from './scheduleExport';
import { firstActiveWeek, isSavedAutoResult, unscheduledClasses, validateAutoResult, validateFile, validateSchool } from './workflow';
import FormError from './FormError';

const STORAGE_KEY = 'campus-last-autoschedule-meta';

function readCachedMeta(schoolCode) {
  try {
    const raw = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || 'null');
    return raw && raw.schoolId === schoolCode ? raw : null;
  } catch {
    return null;
  }
}

function writeCachedMeta(data) {
  try {
    if (!data) sessionStorage.removeItem(STORAGE_KEY);
    else sessionStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  } catch {
    /* bỏ qua */
  }
}

function buildResultFromOverview(overview, periods, schoolId, schoolName, fallbackStart, cachedMeta) {
  const classes = Array.isArray(overview?.classes) ? overview.classes : [];
  const cachedUnscheduled = Array.isArray(cachedMeta?.unscheduledClasses) ? cachedMeta.unscheduledClasses : [];
  const slotMetaMap = cachedMeta?.slotMetaByClassId || {};
  if (!classes.length && !cachedUnscheduled.length) return null;

  const scheduledClasses = [];
  for (const row of classes) {
    const meta = slotMetaMap[row.classId] || {};
    const sessions = Array.isArray(row.sessions) ? row.sessions : [];
    for (const s of sessions) {
      scheduledClasses.push({
        classId: row.classId,
        courseCode: row.courseCode,
        baseCourseName: row.courseName,
        courseName: meta.courseName || `${row.courseName} — ${row.label}`,
        classGroup: row.label,
        credits: meta.credits ?? null,
        sessionType: row.sessionType,
        teacherId: row.teacherId || null,
        teacherName: row.teacherName || null,
        studentCount: meta.studentCount ?? null,
        room: s.room,
        dayOfWeek: s.dayOfWeek,
        startPeriod: s.startPeriod,
        numPeriods: s.numPeriods,
        weekSchedule: s.weekSchedule,
        phaseNote: meta.phaseNote || row.departmentName || '',
      });
    }
  }

  const saved = scheduledClasses.length > 0;
  return {
    schoolId,
    schoolName,
    semesterStartDate: overview?.semesterStartDate || cachedMeta?.semesterStartDate || fallbackStart,
    periods,
    saved,
    status: saved ? (cachedUnscheduled.length ? 'PARTIAL_SUCCESS' : 'SUCCESS') : 'NOT_SAVED',
    message: cachedMeta?.message || `Đang lưu ${classes.length} lớp/nhóm đã xếp phòng trong hệ thống.`,
    scheduledClasses,
    unscheduledClasses: cachedUnscheduled,
    catalogWarnings: Array.isArray(cachedMeta?.catalogWarnings) ? cachedMeta.catalogWarnings : [],
    unscheduledWarnings: Array.isArray(cachedMeta?.unscheduledWarnings) ? cachedMeta.unscheduledWarnings : [],
  };
}

function statusBadge(status) {
  if (status === 'SUBMITTED_TO_ADMIN') {
    return <span className="badge" style={{ background: '#dcfce7', color: '#166534' }}>Trưởng BM đã gửi kết quả</span>;
  }
  if (status === 'SENT_TO_DEPT') {
    return <span className="badge" style={{ background: '#dbeafe', color: '#1e40af' }}>Đã chuyển về Bộ môn</span>;
  }
  if (status === 'DRAFT') {
    return <span className="badge" style={{ background: '#fef3c7', color: '#92400e' }}>Chờ chuyển về Bộ môn</span>;
  }
  return <span className="badge muted">Chưa có lớp mở</span>;
}

export default function AutoSchedulePage({
  semesterStart,
  scheduleRevision = 0,
  onScheduled,
  onViewTeacherSchedule,
  onNavigatePeriods,
}) {
  const [schoolId] = useState('PTIT');
  const [schoolName] = useState('Khoa Công nghệ Thông tin - PTIT');
  const [startDate, setStartDate] = useState(semesterStart || '2026-08-10');

  const [planFile, setPlanFile] = useState(null);
  const [savedRooms, setSavedRooms] = useState([]);
  const [savedSubjects, setSavedSubjects] = useState([]);
  const [periodTemplates, setPeriodTemplates] = useState([]);
  const [departmentStatuses, setDepartmentStatuses] = useState([]);

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [infoMessage, setInfoMessage] = useState('');
  const [result, setResult] = useState(null);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState('');

  const [filterText, setFilterText] = useState('');
  const [sessionFilter, setSessionFilter] = useState('ALL');

  const firstSub = savedSubjects[0] || { courseCode: 'INT1313', courseName: 'Cơ sở dữ liệu' };
  const secondSub = savedSubjects[1] || { courseCode: 'INT13165', courseName: 'Lập trình mạng' };

  const samplePlans = [
    { courseCode: firstSub.courseCode, courseName: firstSub.courseName, classType: 'LT', classSize: 60, cohort: '2024', numClasses: 2, studyStartDate: startDate, studyEndDate: addDays(startDate, 105), credits: 3, totalPeriods: 30 },
    { courseCode: firstSub.courseCode, courseName: firstSub.courseName, classType: 'TH', classSize: 30, cohort: '2024', numClasses: 4, studyStartDate: startDate, studyEndDate: addDays(startDate, 105), credits: 3, totalPeriods: 15 },
    { courseCode: secondSub.courseCode, courseName: secondSub.courseName, classType: 'LT', classSize: 90, cohort: '2024', numClasses: 1, studyStartDate: startDate, studyEndDate: addDays(startDate, 105), credits: 3, totalPeriods: 30 },
    { courseCode: secondSub.courseCode, courseName: secondSub.courseName, classType: 'TH', classSize: 30, cohort: '2024', numClasses: 3, studyStartDate: startDate, studyEndDate: addDays(startDate, 105), credits: 3, totalPeriods: 15 },
  ];

  useEffect(() => {
    if (!schoolId.trim()) return;
    loadCatalog(schoolId.trim());
  }, [schoolId, scheduleRevision]);

  async function loadCatalog(code) {
    try {
      const [roomsData, subjectsData, periodsData, overviewData] = await Promise.all([
        api(`/rooms?schoolId=${encodeURIComponent(code)}`),
        api(`/subjects?schoolId=${encodeURIComponent(code)}`),
        api(`/period-templates?schoolId=${encodeURIComponent(code)}`),
        api(`/assignments?schoolId=${encodeURIComponent(code)}`).catch(() => null),
      ]);
      const roomsList = Array.isArray(roomsData) ? roomsData : [];
      const subjectsList = Array.isArray(subjectsData) ? subjectsData : [];
      const periodsList = Array.isArray(periodsData) ? periodsData : [];

      setSavedRooms(roomsList);
      setSavedSubjects(subjectsList);
      setPeriodTemplates(periodsList);
      setDepartmentStatuses(Array.isArray(overviewData?.departmentStatuses) ? overviewData.departmentStatuses : []);

      if (overviewData?.semesterStartDate) {
        setStartDate(overviewData.semesterStartDate);
      }

      const cachedMeta = readCachedMeta(code);
      const restored = buildResultFromOverview(
        overviewData,
        periodsList,
        code,
        schoolName,
        overviewData?.semesterStartDate || startDate,
        cachedMeta
      );
      if (restored) {
        setResult(restored);
      }
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleDispatch(departmentId = null) {
    if (busy) return;
    setBusy(true);
    setError('');
    setInfoMessage('');
    try {
      const res = await postJson('/assignments/dispatch', {
        schoolId: schoolId.trim(),
        departmentId,
      });
      setInfoMessage(res.message);
      await loadCatalog(schoolId.trim());
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function handleReopen(departmentId) {
    if (busy || !departmentId) return;
    setBusy(true);
    setError('');
    setInfoMessage('');
    try {
      const res = await postJson('/assignments/reopen', {
        schoolId: schoolId.trim(),
        departmentId,
      });
      setInfoMessage(res.message);
      await loadCatalog(schoolId.trim());
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function handleClearDepartmentSchedule() {
    if (!window.confirm(`Xóa toàn bộ lịch đã xếp của "${schoolName}" (${schoolId})?`)) {
      return;
    }
    setBusy(true);
    setError('');
    setInfoMessage('');
    try {
      const res = await api(`/department-schedule?schoolId=${encodeURIComponent(schoolId.trim())}`, {
        method: 'DELETE',
      });
      writeCachedMeta(null);
      setResult(null);
      setInfoMessage(res.message || 'Đã xóa toàn bộ lịch cũ của khoa.');
      await loadCatalog(schoolId.trim());
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
      if (!periodTemplates.length) {
        throw new Error('Chưa cấu hình khung giờ tiết học. Vui lòng thêm tiết ở trang Khung giờ tiết học.');
      }
      validateFile(planFile, 'Kế hoạch mở lớp');
      if (!savedRooms.length) {
        throw new Error('Chưa có phòng học nào. Hãy thêm phòng ở trang Loại phòng & Phòng học.');
      }
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

      const slotMetaByClassId = {};
      for (const slot of response.scheduledClasses || []) {
        if (slot.classId && !slotMetaByClassId[slot.classId]) {
          slotMetaByClassId[slot.classId] = {
            studentCount: slot.studentCount ?? null,
            credits: slot.credits ?? null,
            phaseNote: slot.phaseNote || '',
            courseName: slot.courseName || '',
          };
        }
      }

      writeCachedMeta({
        schoolId: schoolId.trim(),
        semesterStartDate: response.semesterStartDate,
        message: response.message,
        slotMetaByClassId,
        unscheduledClasses: unscheduledClasses(response),
        catalogWarnings: response.catalogWarnings || [],
        unscheduledWarnings: response.unscheduledWarnings || [],
      });
      await loadCatalog(schoolId.trim());
      if (response.saved) onScheduled?.(response);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function exportResultToExcel() {
    if (busy || exporting || !result) return;
    setExporting(true);
    setExportError('');
    try {
      const normalized = {
        ...result,
        periods: result.periods?.length ? result.periods : periodTemplates,
        scheduledClasses: (result.scheduledClasses || []).map(slot => ({
          ...slot,
          teacherId: slot.teacherId || 'CHUA_PHAN_CONG',
          teacherName: slot.teacherName || 'Chưa phân công',
        })),
      };
      const { workbook, filename } = scheduledWorkbook(normalized);
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

  const saved = isSavedAutoResult(result);
  const unassigned = unscheduledClasses(result);
  const canExport = Boolean(result?.scheduledClasses?.length || unassigned.length);
  const draftDepartmentsCount = departmentStatuses.filter(d => d.workflowStatus === 'DRAFT').length;

  const filteredSlots = (result?.scheduledClasses || []).filter(slot => {
    if (sessionFilter !== 'ALL' && slot.sessionType !== sessionFilter) return false;
    if (!filterText.trim()) return true;
    const q = filterText.toLocaleLowerCase('vi');
    return `${slot.teacherId || 'chưa phân công'} ${slot.teacherName || ''} ${slot.courseCode} ${slot.courseName} ${slot.room}`
      .toLocaleLowerCase('vi')
      .includes(q);
  });

  const periodRangeText = periodTemplates.length
    ? `${periodTemplates.length} tiết (${clock(periodTemplates[0].start)} – ${clock(periodTemplates[periodTemplates.length - 1].end)})`
    : 'Chưa cấu hình khung giờ';

  return (
    <section className="standalone-panel import-panel auto-panel" aria-busy={busy}>
      <div className="panel-title">
        <div>
          <h2>Xếp lịch của Khoa</h2>
        </div>
        <div className="catalog-actions">
          {onNavigatePeriods && (
            <button type="button" className="button" onClick={onNavigatePeriods}>
              <Clock3 size={16} /> Khung giờ ({periodRangeText})
            </button>
          )}
          <button
            type="button"
            className="button"
            style={{ color: '#dc2626', borderColor: '#fca5a5' }}
            disabled={busy}
            onClick={handleClearDepartmentSchedule}
          >
            <Trash2 size={16} /> Xóa lịch cũ của Khoa
          </button>
        </div>
      </div>

      <form onSubmit={handleScheduleSubmit}>
        <fieldset disabled={busy || exporting} className="import-fields" style={{ gridTemplateColumns: 'minmax(0, 320px)' }}>
          <label>Ngày Thứ Hai của Tuần 1 <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} required /></label>

          <div className="file-field" style={{ gridColumn: '1 / -1' }}>
            <label htmlFor="auto-plan-file"><strong>File Kế hoạch mở lớp (.xlsx)</strong></label>
            <input id="auto-plan-file" type="file" accept=".xlsx" onChange={e => setPlanFile(e.target.files?.[0] || null)} required />
            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', marginTop: '0.4rem', flexWrap: 'wrap' }}>
              <button type="button" className="button compact" onClick={downloadPlanTemplate}><Download size={15} /> Tải mẫu Mở lớp</button>
              <small><strong>{savedSubjects.length}</strong> môn · <strong>{savedRooms.length}</strong> phòng · Khung giờ: <strong>{periodRangeText}</strong></small>
            </div>
          </div>
        </fieldset>

        <div className="import-actions" style={{ marginTop: '0.75rem' }}>
          <button type="submit" className="button primary" disabled={busy || !planFile}>
            <Upload size={16} /> {busy ? 'Đang xếp lịch…' : 'Chạy xếp lịch'}
          </button>
        </div>
      </form>

      <FormError message={error} />
      {infoMessage && <div role="status" className="info-banner catalog-info" style={{ marginTop: '0.75rem' }}><CheckCircle2 size={18} /> {infoMessage}</div>}

      {/* BẢNG BÀN GIAO LỊCH VỀ TỪNG BỘ MÔN */}
      {departmentStatuses.length > 0 && (
        <div className="catalog-card" style={{ marginTop: '1.25rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
            <h3 style={{ margin: 0 }}>
              <Layers size={18} /> Bàn giao lịch về các Bộ môn
            </h3>
            <button
              type="button"
              className="button primary compact"
              disabled={busy || draftDepartmentsCount === 0}
              onClick={() => handleDispatch(null)}
            >
              <Send size={14} /> Chuyển tất cả về các Bộ môn ({draftDepartmentsCount})
            </button>
          </div>

          <div className="table-scroll catalog-table">
            <table>
              <thead>
                <tr>
                  <th>Mã BM</th>
                  <th>Bộ môn</th>
                  <th>Trưởng bộ môn</th>
                  <th>Số lớp đã xếp</th>
                  <th>Tiến độ phân công</th>
                  <th>Trạng thái bàn giao</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {departmentStatuses.map(dept => (
                  <tr key={dept.departmentId}>
                    <td><strong>{dept.departmentCode}</strong></td>
                    <td>{dept.departmentName}</td>
                    <td>
                      {dept.headTeacherName
                        ? `${dept.headTeacherName} (${dept.headTeacherId})`
                        : <span className="muted-text">Chưa gán TBM</span>}
                    </td>
                    <td><strong>{dept.totalClasses}</strong> lớp</td>
                    <td>{dept.assignedClasses}/{dept.totalClasses} lớp</td>
                    <td>{statusBadge(dept.workflowStatus)}</td>
                    <td>
                      {dept.workflowStatus === 'DRAFT' && (
                        <button
                          type="button"
                          className="button primary compact"
                          disabled={busy}
                          onClick={() => handleDispatch(dept.departmentId)}
                        >
                          <Send size={13} /> Chuyển về Bộ môn
                        </button>
                      )}
                      {dept.workflowStatus === 'SUBMITTED_TO_ADMIN' && (
                        <button
                          type="button"
                          className="button compact"
                          disabled={busy}
                          onClick={() => handleReopen(dept.departmentId)}
                        >
                          <RotateCcw size={13} /> Mở lại cho BM
                        </button>
                      )}
                      {dept.workflowStatus === 'SENT_TO_DEPT' && (
                        <span className="muted-text">Đang chờ TBM gửi lại</span>
                      )}
                      {dept.workflowStatus === 'EMPTY' && (
                        <span className="muted-text">—</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {exportError && <p role="alert" className="error-text">{exportError}</p>}
      {result && (
        <div className="import-preview" style={{ marginTop: '1.5rem' }}>
          <div className="panel-title">
            <div>
              <h2>{saved ? <CheckCircle2 size={18} /> : <AlertTriangle size={18} />} {saved ? 'Đã lưu kết quả xếp lịch' : 'Kết quả xếp lịch — chưa lưu'}</h2>
              <p>{result.message}</p>
              {result.catalogWarnings?.length > 0 && (
                <div className="import-warning" style={{ marginTop: '0.5rem' }}>
                  <AlertTriangle size={16} />
                  <span>{result.catalogWarnings.join(' | ')}</span>
                </div>
              )}
              {result.unscheduledWarnings?.length > 0 && !unassigned.length && (
                <div className="import-warning" style={{ marginTop: '0.5rem' }}>
                  <AlertTriangle size={16} />
                  <span>{result.unscheduledWarnings.join(' | ')}</span>
                </div>
              )}
            </div>
            <div className="template-actions">
              <button type="button" className="button primary" disabled={busy || exporting || !canExport} onClick={exportResultToExcel}><Download size={16} /> {exporting ? 'Đang xuất Excel…' : 'Xuất Excel kết quả'}</button>
            </div>
          </div>

          {unassigned.length > 0 && (
            <section aria-label="Các lớp chưa xếp được" style={{ marginTop: '1rem' }}>
              <div role="alert" className="import-warning">
                <AlertTriangle size={18} />
                <span>Còn <strong>{unassigned.length} lớp/nhóm chưa xếp được</strong>.</span>
              </div>
              <div className="table-scroll">
                <table>
                  <thead><tr><th>Mã môn</th><th>Học phần</th><th>Khóa</th><th>Lớp/nhóm</th><th>Loại</th><th>Tiết còn thiếu</th><th>Lý do</th></tr></thead>
                  <tbody>{unassigned.map((row, index) => (
                    <tr key={row.classId || index}>
                      <td>{row.courseCode}</td><td>{row.courseName || '—'}</td><td>{row.cohort}</td>
                      <td>{row.classGroup}</td><td>{row.sessionType === 'PRACTICE' ? 'TH' : 'LT'}</td>
                      <td>{row.missingPeriods}/{row.requiredPeriods}</td>
                      <td>{row.reason || 'Chưa xếp đủ lịch.'}</td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            </section>
          )}

          <div className="filter-bar" style={{ marginTop: '0.75rem' }}>
            <label className="search-field">
              <Search size={17} />
              <input placeholder="Tìm theo giảng viên, môn học, phòng…" value={filterText} onChange={e => setFilterText(e.target.value)} />
            </label>
            <select value={sessionFilter} onChange={e => setSessionFilter(e.target.value)}>
              <option value="ALL">Tất cả (LT + TH)</option>
              <option value="THEORY">Lý thuyết</option>
              <option value="PRACTICE">Thực hành</option>
            </select>
          </div>

          <div className="table-scroll" style={{ marginTop: '0.75rem' }}>
            <table>
              <thead>
                <tr>
                  <th>Giảng viên</th><th>Mã môn</th><th>Học phần &amp; Nhóm</th><th>Loại</th><th>Sĩ số</th><th>Phòng</th><th>Thứ</th><th>Tiết</th><th>Ghi chú</th><th>Xem TKB</th>
                </tr>
              </thead>
              <tbody>
                {filteredSlots.map((slot, index) => (
                  <tr key={index}>
                    <td>
                      {slot.teacherId
                        ? <><strong>{slot.teacherId}</strong> · {slot.teacherName}</>
                        : <span className="badge muted">Chưa phân công</span>}
                    </td>
                    <td><code>{slot.courseCode}</code></td>
                    <td>{slot.courseName}</td>
                    <td><span className="badge" style={{ background: slot.sessionType === 'PRACTICE' ? '#0284c7' : '#16a34a', color: '#fff' }}>{slot.sessionType === 'PRACTICE' ? 'TH' : 'LT'}</span></td>
                    <td>{slot.studentCount != null ? `${slot.studentCount} SV` : '—'}</td>
                    <td><strong>{slot.room}</strong></td>
                    <td>{slot.dayOfWeek === 8 ? 'CN' : `Thứ ${slot.dayOfWeek}`}</td>
                    <td>Tiết {slot.startPeriod}–{slot.startPeriod + slot.numPeriods - 1}</td>
                    <td><small className="muted-text">{slot.phaseNote || '—'}</small></td>
                    <td>
                      {slot.teacherId && onViewTeacherSchedule ? (
                        <button type="button" className="button compact" disabled={!saved} onClick={() => onViewTeacherSchedule(slot.teacherId, firstActiveWeek(result.scheduledClasses, slot.teacherId))}>
                          <Calendar size={14} /> Xem TKB
                        </button>
                      ) : '—'}
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
