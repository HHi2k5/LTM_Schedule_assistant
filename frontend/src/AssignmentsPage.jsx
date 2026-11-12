import React, { useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, BookOpen, Calendar, CalendarDays, CheckCircle2, ChevronLeft, ChevronRight, RefreshCw, Search, Send, Sparkles, UserCheck, Users, X } from 'lucide-react';
import { api, addDays, clock, currentWeek, dateLabel } from './schedule';
import { firstWeek, postJson, teacherTypeLabel, weekRanges } from './catalog';
import { isHead } from './permissions';
import { clampWeek } from './workflow';
import SchoolWideCalendar from './SchoolWideCalendar';
import VisitingSchedulePanel from './VisitingSchedulePanel';
import FormError from './FormError';

const TEACHER_PALETTE = [
  '#2563eb', '#16a34a', '#9333ea', '#ea580c', '#0284c7', '#db2777',
  '#4f46e5', '#0d9488', '#d97706', '#dc2626', '#7c3aed', '#059669',
];

export default function AssignmentsPage({ user, scheduleRevision = 0, onViewTeacherSchedule }) {
  const head = isHead(user);
  const [schoolId] = useState('PTIT');
  const [departmentId, setDepartmentId] = useState(String(user?.departmentId || ''));
  const [overview, setOverview] = useState(null);
  const [periods, setPeriods] = useState([]);
  const [loading, setLoading] = useState(false);
  const [busyKey, setBusyKey] = useState('');
  const [autoResult, setAutoResult] = useState(null);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');

  const [calendarWeek, setCalendarWeek] = useState(1);
  const [selectedLesson, setSelectedLesson] = useState(null);

  const [subjectFilter, setSubjectFilter] = useState('ALL');
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  const school = schoolId.trim() || 'PTIT';

  useEffect(() => {
    setDepartmentId(String(user?.departmentId || ''));
  }, [user?.departmentId]);

  async function loadOverview() {
    if (!head) return;
    setLoading(true);
    setError('');
    try {
      const deptParam = departmentId ? `&departmentId=${encodeURIComponent(departmentId)}` : '';
      const [data, periodList] = await Promise.all([
        api(`/assignments?schoolId=${encodeURIComponent(school)}${deptParam}`),
        api(`/period-templates?schoolId=${encodeURIComponent(school)}`).catch(() => []),
      ]);
      setOverview(data);
      setPeriods(Array.isArray(periodList) ? periodList : []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadOverview();
  }, [school, departmentId, scheduleRevision, head]);

  async function handleAssign(classId, teacherId) {
    setBusyKey(classId);
    setError('');
    setInfo('');
    try {
      const res = await postJson('/assignments/assign', {
        schoolId: school,
        classId,
        teacherId: teacherId || null,
      });
      setInfo(res.message);
      await loadOverview();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyKey('');
    }
  }

  async function handleAutoAssign() {
    if (!departmentId) {
      setError('Tài khoản chưa được gắn bộ môn.');
      return;
    }
    setBusyKey('__AUTO__');
    setError('');
    setInfo('');
    setAutoResult(null);
    try {
      const res = await postJson('/assignments/auto', {
        schoolId: school,
        departmentId: Number(departmentId),
      });
      setAutoResult(res);
      setInfo(res.message);
      await loadOverview();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyKey('');
    }
  }

  async function handleSubmitToAdmin() {
    if (busyKey) return;
    const msg = unassignedCount > 0
      ? `Hiện vẫn còn ${unassignedCount} lớp chưa phân công giảng viên. Bạn vẫn muốn gửi kết quả phân công hiện tại lên Admin?`
      : 'Xác nhận gửi toàn bộ kết quả phân công của bộ môn lên Admin?';
    if (!window.confirm(msg)) return;

    setBusyKey('__SUBMIT__');
    setError('');
    setInfo('');
    try {
      const res = await postJson('/assignments/submit', { schoolId: school });
      setInfo(res.message);
      await loadOverview();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyKey('');
    }
  }

  const classes = overview?.classes || [];
  const teachers = overview?.teachers || [];
  const workflowStatus = overview?.workflowStatus || 'EMPTY';
  const isSubmitted = workflowStatus === 'SUBMITTED_TO_ADMIN';
  const isEditable = workflowStatus === 'SENT_TO_DEPT';
  const unassignedCount = classes.filter(c => !c.teacherId).length;
  const semesterStart = overview?.semesterStartDate || user?.semesterStartDate || '2026-08-10';
  const weekStartDate = addDays(semesterStart, (calendarWeek - 1) * 7);
  const weekEndDate = addDays(weekStartDate, 6);

  // Tổng hợp các lớp của bộ môn theo từng môn học
  const subjectSummaries = useMemo(() => {
    const map = new Map();
    for (const row of classes) {
      const code = row.courseCode || '';
      if (!map.has(code)) {
        map.set(code, {
          courseCode: code,
          courseName: row.courseName || code,
          theoryCount: 0,
          practiceCount: 0,
          assignedCount: 0,
          unassignedCount: 0,
          totalPeriods: 0,
        });
      }
      const item = map.get(code);
      if (row.sessionType === 'PRACTICE') item.practiceCount += 1;
      else item.theoryCount += 1;
      if (row.teacherId) item.assignedCount += 1;
      else item.unassignedCount += 1;
      item.totalPeriods += Number(row.totalPeriods || 0);
    }
    return [...map.values()].sort((a, b) => a.courseCode.localeCompare(b.courseCode));
  }, [classes]);

  // Dựng dữ liệu bảng lịch tuần của bộ môn (giống bảng của Admin)
  const calendarLessons = useMemo(() => {
    const periodMap = new Map(periods.map(p => [Number(p.period), p]));
    const colorMap = new Map();
    teachers.forEach((t, idx) => {
      colorMap.set(t.teacherId, TEACHER_PALETTE[idx % TEACHER_PALETTE.length]);
    });

    const list = [];
    const seen = new Set();
    for (const row of classes) {
      if (subjectFilter !== 'ALL' && row.courseCode !== subjectFilter) continue;
      const color = row.teacherId ? (colorMap.get(row.teacherId) || '#177c62') : '#94a3b8';
      const teacherDisplay = row.teacherId ? `${row.teacherName} (${row.teacherId})` : 'Chưa phân công';

      for (const s of row.sessions || []) {
        const weeks = String(s.weekSchedule || '');
        const ch = weeks[calendarWeek - 1];
        if (!ch || ch === '-' || ch === ' ') continue;

        const key = `${row.classId}-${s.dayOfWeek}-${s.startPeriod}-${s.numPeriods}-${s.room}`;
        if (seen.has(key)) continue;
        seen.add(key);

        const endPeriod = s.startPeriod + s.numPeriods - 1;
        const pStart = periodMap.get(s.startPeriod);
        const pEnd = periodMap.get(endPeriod);
        const startTime = pStart?.start || `${String(s.startPeriod + 6).padStart(2, '0')}:00`;
        const endTime = pEnd?.end || `${String(endPeriod + 6).padStart(2, '0')}:50`;
        const offsetDays = s.dayOfWeek === 8 ? 6 : (s.dayOfWeek - 2);
        const date = addDays(weekStartDate, offsetDays);

        list.push({
          id: `${key}-w${calendarWeek}`,
          schoolId: school,
          schoolName: user?.departmentName || 'Bộ môn',
          colorCode: color,
          courseName: `[${teacherDisplay}] ${row.label}`,
          teacherName: teacherDisplay,
          room: s.room,
          dayOfWeek: s.dayOfWeek,
          date,
          startPeriod: s.startPeriod,
          endPeriod,
          startTime,
          endTime,
          isConflict: false,
        });
      }
    }
    return list;
  }, [classes, periods, teachers, calendarWeek, weekStartDate, school, subjectFilter, user?.departmentName]);

  const visibleClasses = classes.filter(row => {
    if (subjectFilter !== 'ALL' && row.courseCode !== subjectFilter) return false;
    if (typeFilter !== 'ALL' && row.sessionType !== typeFilter) return false;
    if (statusFilter === 'UNASSIGNED' && row.teacherId) return false;
    if (statusFilter === 'ASSIGNED' && !row.teacherId) return false;
    if (!query.trim()) return true;
    const q = query.toLocaleLowerCase('vi');
    return `${row.courseCode} ${row.courseName} ${row.label} ${row.teacherName || ''} ${row.teacherId || ''}`
      .toLocaleLowerCase('vi')
      .includes(q);
  });

  if (!head) {
    return (
      <section className="standalone-panel import-panel">
        <div className="import-warning">
          <AlertTriangle size={18} />
          <p>Chức năng phân công giảng viên chỉ dành cho <strong>Trưởng bộ môn</strong>.</p>
        </div>
      </section>
    );
  }

  return (
    <section className="standalone-panel import-panel auto-panel">
      <div className="panel-title">
        <div>
          <h2>Phân công giảng viên{user?.departmentName ? ` · Bộ môn ${user.departmentName}` : ''}</h2>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'end' }}>
          <button type="button" className="button" disabled={loading} onClick={loadOverview}>
            <RefreshCw size={15} /> Làm mới
          </button>
          <button
            type="button"
            className="button"
            disabled={!isEditable || busyKey === '__AUTO__' || !departmentId || !unassignedCount}
            onClick={handleAutoAssign}
          >
            <Sparkles size={15} /> {busyKey === '__AUTO__' ? 'Đang phân công…' : `Tự phân công (${unassignedCount})`}
          </button>
          {classes.length > 0 && !isSubmitted && (
            <button
              type="button"
              className="button primary"
              disabled={!isEditable || busyKey === '__SUBMIT__'}
              onClick={handleSubmitToAdmin}
            >
              <Send size={15} /> {busyKey === '__SUBMIT__' ? 'Đang gửi…' : 'Gửi kết quả lên Admin'}
            </button>
          )}
        </div>
      </div>

      <FormError message={error} />
      {info && <div role="status" className="info-banner catalog-info"><CheckCircle2 size={18} /> {info}</div>}

      {isSubmitted && (
        <div className="info-banner catalog-info" style={{ marginBottom: '1rem' }}>
          <CheckCircle2 size={18} />
          <span>Bộ môn đã gửi kết quả phân công lên Admin. Nếu cần chỉnh sửa thêm, hãy liên hệ Admin mở lại.</span>
        </div>
      )}

      {!loading && classes.length === 0 && (
        <div className="import-warning">
          <AlertTriangle size={18} />
          <p>Bộ môn chưa có lớp nào được Admin chuyển về. Khi Admin xếp lịch và bấm <strong>Chuyển về Bộ môn</strong>, danh sách lớp sẽ hiển thị tại đây.</p>
        </div>
      )}

      {autoResult?.remaining?.length > 0 && (
        <div className="import-warning">
          <AlertTriangle size={18} />
          <div>
            <strong>Còn {autoResult.remaining.length} lớp chưa phân công được:</strong>
            <ul style={{ margin: '6px 0 0', paddingLeft: '18px' }}>
              {autoResult.remaining.map(item => (
                <li key={item.classId}>{item.label}: {item.reason}</li>
              ))}
            </ul>
          </div>
        </div>
      )}

      {/* BẢNG 1: TỔNG HỢP CÁC LỚP CỦA BỘ MÔN */}
      <div className="catalog-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
          <h3 style={{ margin: 0 }}>
            <BookOpen size={18} /> Tổng hợp các lớp của bộ môn ({subjectSummaries.length} môn · {classes.length} lớp)
          </h3>
          {subjectFilter !== 'ALL' && (
            <button type="button" className="button compact" onClick={() => setSubjectFilter('ALL')}>
              Hiện tất cả môn ({subjectSummaries.length})
            </button>
          )}
        </div>
        <div className="table-scroll catalog-table">
          <table>
            <thead>
              <tr>
                <th>Mã môn</th>
                <th>Tên môn học</th>
                <th>Lớp LT</th>
                <th>Lớp TH</th>
                <th>Tổng lớp</th>
                <th>Đã phân công</th>
                <th>Chưa phân công</th>
                <th>Tổng tiết</th>
                <th>Lọc nhanh</th>
              </tr>
            </thead>
            <tbody>
              {!subjectSummaries.length && (
                <tr><td colSpan={9}>{loading ? 'Đang tải…' : 'Chưa có lớp nào.'}</td></tr>
              )}
              {subjectSummaries.map(sub => {
                const selected = subjectFilter === sub.courseCode;
                return (
                  <tr key={sub.courseCode} className={selected ? 'catalog-selected' : ''}>
                    <td><code>{sub.courseCode}</code></td>
                    <td><strong>{sub.courseName}</strong></td>
                    <td>{sub.theoryCount}</td>
                    <td>{sub.practiceCount}</td>
                    <td><strong>{sub.theoryCount + sub.practiceCount}</strong></td>
                    <td>
                      <span className="badge" style={{ background: '#dcfce7', color: '#166534' }}>
                        {sub.assignedCount} lớp
                      </span>
                    </td>
                    <td>
                      {sub.unassignedCount > 0 ? (
                        <span className="badge" style={{ background: '#fef3c7', color: '#92400e' }}>
                          {sub.unassignedCount} trống
                        </span>
                      ) : (
                        <span className="badge muted">Đủ</span>
                      )}
                    </td>
                    <td>{sub.totalPeriods} tiết</td>
                    <td>
                      <button
                        type="button"
                        className={`button compact${selected ? ' primary' : ''}`}
                        onClick={() => setSubjectFilter(selected ? 'ALL' : sub.courseCode)}
                      >
                        {selected ? 'Đang lọc' : 'Xem lớp'}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* BẢNG 2: LỊCH GIẢNG DẠY CỦA BỘ MÔN THEO TUẦN (GIỐNG BẢNG CỦA ADMIN) */}
      {classes.length > 0 && (
        <div className="catalog-card" style={{ padding: 0, overflow: 'hidden' }}>
          <div className="calendar-toolbar">
            <div>
              <h2>
                <CalendarDays size={18} /> Lịch giảng dạy của Bộ môn <span className="badge muted">Tuần {calendarWeek}</span>
              </h2>
              <p>{dateLabel(weekStartDate)} – {dateLabel(weekEndDate)}</p>
            </div>
            <div className="calendar-controls">
              <button
                type="button"
                className="button compact"
                onClick={() => setCalendarWeek(clampWeek(currentWeek(semesterStart)))}
              >
                Hôm nay
              </button>
              <div className="arrow-group">
                <button type="button" aria-label="Tuần trước" disabled={calendarWeek <= 1} onClick={() => setCalendarWeek(w => w - 1)}>
                  <ChevronLeft size={18} />
                </button>
                <button type="button" aria-label="Tuần sau" disabled={calendarWeek >= 100} onClick={() => setCalendarWeek(w => clampWeek(w + 1))}>
                  <ChevronRight size={18} />
                </button>
              </div>
            </div>
          </div>

          {calendarLessons.length > 0 ? (
            <SchoolWideCalendar lessons={calendarLessons} startDate={weekStartDate} onSelect={setSelectedLesson} />
          ) : (
            <p className="muted-text" style={{ padding: '1.25rem 20px' }}>Tuần {calendarWeek} chưa có buổi học nào của bộ môn.</p>
          )}
        </div>
      )}

      {/* BẢNG 3: KHỐI LƯỢNG GIẢNG DẠY */}
      <div className="catalog-card">
        <h3><Users size={18} /> Khối lượng giảng dạy ({teachers.length} GV)</h3>
        <div className="table-scroll catalog-table">
          <table>
            <thead>
              <tr><th>Mã GV</th><th>Họ tên</th><th>Loại GV</th><th>Số lớp</th><th>Tổng tiết</th><th>Lịch tuần</th></tr>
            </thead>
            <tbody>
              {!teachers.length && <tr><td colSpan={6}>Chưa có giảng viên trong bộ môn.</td></tr>}
              {teachers.map(t => (
                <tr key={t.teacherId}>
                  <td><strong>{t.teacherId}</strong></td>
                  <td>{t.teacherName}</td>
                  <td>{teacherTypeLabel(t.teacherType)}</td>
                  <td><strong>{t.classes}</strong> lớp</td>
                  <td>{t.periods} tiết</td>
                  <td>
                    {onViewTeacherSchedule && (
                      <button type="button" className="button compact" onClick={() => onViewTeacherSchedule(t.teacherId, 1)}>
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

      {/* BẢNG 4: CHI TIẾT TỪNG LỚP ĐỂ PHÂN CÔNG */}
      <div className="catalog-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
          <h3><UserCheck size={18} /> Chi tiết lớp học phần ({visibleClasses.length}/{classes.length} · trống {unassignedCount})</h3>
        </div>

        <div className="filter-bar" style={{ padding: '0.75rem 0 0', flexWrap: 'wrap' }}>
          <label className="search-field">
            <Search size={17} />
            <input placeholder="Tìm mã môn, tên môn, lớp, giảng viên…" value={query} onChange={e => setQuery(e.target.value)} />
          </label>
          <select value={subjectFilter} onChange={e => setSubjectFilter(e.target.value)}>
            <option value="ALL">Tất cả môn ({subjectSummaries.length})</option>
            {subjectSummaries.map(s => (
              <option key={s.courseCode} value={s.courseCode}>{s.courseCode} — {s.courseName}</option>
            ))}
          </select>
          <select value={typeFilter} onChange={e => setTypeFilter(e.target.value)}>
            <option value="ALL">Cả LT &amp; TH</option>
            <option value="THEORY">Lý thuyết (LT)</option>
            <option value="PRACTICE">Thực hành (TH)</option>
          </select>
          <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
            <option value="ALL">Tất cả trạng thái</option>
            <option value="UNASSIGNED">Chưa phân công ({unassignedCount})</option>
            <option value="ASSIGNED">Đã phân công ({classes.length - unassignedCount})</option>
          </select>
        </div>

        <div className="table-scroll catalog-table" style={{ maxHeight: '480px' }}>
          <table>
            <thead>
              <tr>
                <th>Môn học &amp; Lớp</th>
                <th>Lịch phòng</th>
                <th>Giảng viên</th>
                <th>Phân công</th>
              </tr>
            </thead>
            <tbody>
              {!visibleClasses.length && (
                <tr><td colSpan={4}>{loading ? 'Đang tải…' : 'Không có lớp phù hợp.'}</td></tr>
              )}
              {visibleClasses.map(row => {
                const isBusy = busyKey === row.classId;
                const firstActive = row.sessions?.length
                  ? Math.min(...row.sessions.map(s => firstWeek(s.weekSchedule)).filter(Boolean))
                  : 1;
                const uniqueSessions = [];
                const seenKeys = new Set();
                for (const s of row.sessions || []) {
                  const k = `${s.dayOfWeek}-${s.startPeriod}-${s.numPeriods}-${s.room}-${s.weekSchedule}`;
                  if (!seenKeys.has(k)) {
                    seenKeys.add(k);
                    uniqueSessions.push(s);
                  }
                }
                return (
                  <tr key={row.classId} className={!row.teacherId ? 'assign-row-unassigned' : ''}>
                    <td style={{ whiteSpace: 'normal', minWidth: '220px' }}>
                      <div>
                        <code>{row.courseCode}</code>{' '}
                        <span className="badge" style={{ background: row.sessionType === 'PRACTICE' ? '#0284c7' : '#16a34a', color: '#fff' }}>
                          {row.sessionType === 'PRACTICE' ? 'TH' : 'LT'}
                        </span>
                      </div>
                      <strong style={{ display: 'block', marginTop: '4px' }}>{row.courseName} — {row.label}</strong>
                      <small className="muted-text">{row.totalPeriods} tiết</small>
                    </td>
                    <td style={{ whiteSpace: 'normal', minWidth: '250px' }}>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                        {uniqueSessions.map((s, idx) => (
                          <span key={idx} className="badge muted" style={{ whiteSpace: 'normal', textAlign: 'left', lineHeight: 1.5 }}>
                            {s.dayOfWeek === 8 ? 'CN' : `Thứ ${s.dayOfWeek}`} · Tiết {s.startPeriod}–{s.startPeriod + s.numPeriods - 1} · Phòng <strong>{s.room}</strong> · Tuần {weekRanges(s.weekSchedule)}
                          </span>
                        ))}
                      </div>
                    </td>
                    <td>
                      {row.teacherId ? (
                        <div>
                          <strong>{row.teacherName}</strong> ({row.teacherId})
                          {onViewTeacherSchedule && (
                            <div style={{ marginTop: '4px' }}>
                              <button type="button" className="button compact" onClick={() => onViewTeacherSchedule(row.teacherId, firstActive || 1)}>
                                <Calendar size={13} /> Xem TKB
                              </button>
                            </div>
                          )}
                        </div>
                      ) : (
                        <span className="badge muted">Chưa phân công</span>
                      )}
                    </td>
                    <td style={{ minWidth: '260px' }}>
                      <select
                        disabled={!isEditable || isBusy}
                        value={row.teacherId || ''}
                        onChange={e => handleAssign(row.classId, e.target.value)}
                        style={{ width: '100%' }}
                      >
                        <option value="">-- Chưa phân công --</option>
                        {(row.candidates || []).map(c => {
                          const disabled = Boolean(c.blockedReason) && c.teacherId !== row.teacherId;
                          return (
                            <option key={c.teacherId} value={c.teacherId} disabled={disabled}>
                              {c.teacherName} ({c.teacherId}) · {teacherTypeLabel(c.teacherType)}
                              {c.blockedReason ? ` — [${c.blockedReason}]` : ''}
                            </option>
                          );
                        })}
                      </select>
                      {!row.candidates?.length && (
                        <small className="error-text" style={{ display: 'block', marginTop: '4px' }}>
                          Chưa có GV dạy môn {row.courseCode}.
                        </small>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* BẢNG 5: LỊCH BẬN & PHÂN LỚP ĐÃ MỞ CHO GIẢNG VIÊN THỈNH GIẢNG */}
      <div style={{ marginTop: '1.5rem' }}>
        <VisitingSchedulePanel
          schoolId={school}
          visitingTeachers={teachers.filter(t => t.teacherType === 'THINH_GIANG')}
          defaultOpen={teachers.some(t => t.teacherType === 'THINH_GIANG')}
        />
      </div>

      {selectedLesson && <LessonDetailModal lesson={selectedLesson} onClose={() => setSelectedLesson(null)} />}
    </section>
  );
}

function LessonDetailModal({ lesson, onClose }) {
  const ref = useRef(null);
  useEffect(() => {
    const dialog = ref.current;
    const previous = document.activeElement;
    dialog.showModal();
    return () => { dialog.close(); previous?.focus(); };
  }, []);
  return (
    <dialog className="modal" ref={ref} onCancel={onClose}>
      <div className="modal-head">
        <h2>Chi tiết buổi dạy</h2>
        <button className="icon-button" aria-label="Đóng" onClick={onClose}><X size={20} /></button>
      </div>
      <h3>{lesson.courseName}</h3>
      <div className="detail-grid">
        {[
          ['Giảng viên', lesson.teacherName],
          ['Ngày', dateLabel(lesson.date)],
          ['Thời gian', `${clock(lesson.startTime)} – ${clock(lesson.endTime)}`],
          ['Tiết', `${lesson.startPeriod}–${lesson.endPeriod}`],
          ['Phòng', lesson.room],
        ].map(([label, value]) => (
          <div key={label}><small>{label}</small><strong>{value}</strong></div>
        ))}
      </div>
    </dialog>
  );
}
