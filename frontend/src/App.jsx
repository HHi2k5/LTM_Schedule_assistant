import React, { useEffect, useRef, useState } from 'react';
import {
  GraduationCap, CalendarDays, BookOpen, Clock3, Settings, ChevronLeft, ChevronRight,
  Search, Upload, Download, List, Calendar, Menu, HelpCircle, X, CheckCircle2,
  AlertTriangle, RefreshCw, Sparkles, LogOut, Trash2, Building2, Layers, Users, UserCheck
} from 'lucide-react';
import { api, dayNames, minutes, clock, dateLabel, addDays, currentWeek, layoutLessons, calendarFile } from './schedule';
import ImportSchedule from './ImportSchedule';
import AuthPage from './AuthPage';
import TeacherIdentity from './TeacherIdentity';
import ChangePasswordForm from './ChangePasswordForm';
import TopbarClock from './TopbarClock';
import CalendarLesson from './CalendarLesson';
import SchoolWideCalendar from './SchoolWideCalendar';
import TodayDashboard from './TodayDashboard';
import AutoSchedulePage from './AutoSchedulePage';
import PeriodTemplatesPage from './PeriodTemplatesPage';
import DepartmentManagementPage from './DepartmentManagementPage';
import TeachersManagementPage from './TeachersManagementPage';
import RoomsManagementPage from './RoomsManagementPage';
import AssignmentsPage from './AssignmentsPage';
import PersonalCourseDeleteDialog from './PersonalCourseDeleteDialog';
import { clampWeek } from './workflow';
import { getAccessToken, setAccessToken } from './authSession';
import { canAssign, canImport, canManageTeachers, canSchedule, canVisit, defaultPage, roleLabel } from './permissions';
import { subscribeScheduleUpdates } from './scheduleRealtime';

const nav = [
  ['overview', 'Hôm nay', Clock3],
  ['schedule', 'Lịch giảng dạy', CalendarDays],
  ['courses', 'Môn dạy trong tuần', BookOpen],
  ['import', 'Nhập lịch dạy', Upload],
  ['autoschedule', 'Xếp lịch của khoa', Sparkles],
  ['periods', 'Khung giờ tiết học', Clock3],
  ['departments', 'Bộ môn & Môn học', Layers],
  ['teachers', 'Quản lý Giảng viên', Users],
  ['rooms', 'Loại phòng & Phòng học', Building2],
  ['assignments', 'Phân công giảng viên', UserCheck],
];
const titles = Object.fromEntries([...nav, ['settings', 'Cài đặt'], ['help', 'Trợ giúp']].map(([id, label]) => [id, label]));

function stored(key, fallback) {
  try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; }
}

export default function App() {
  const [authUser, setAuthUser] = useState(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [restoreError, setRestoreError] = useState('');
  const [authRevision, setAuthRevision] = useState(0);
  const [page, setPage] = useState('schedule');
  const [mobileOpen, setMobileOpen] = useState(false);
  const [teacherId, setTeacherId] = useState('ALL');
  const [teacherInput, setTeacherInput] = useState('');
  const [week, setWeek] = useState(1);
  const [view, setView] = useState('week');
  const [query, setQuery] = useState('');
  const [schoolFilter, setSchoolFilter] = useState('all');
  const [schedule, setSchedule] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const [selected, setSelected] = useState(null);
  const [toast, setToast] = useState('');
  const [configs, setConfigs] = useState(() => stored('campus-period-configs', {}));
  const [storageError, setStorageError] = useState(false);
  const [liveStatus, setLiveStatus] = useState('connecting');
  const [deleteOpen, setDeleteOpen] = useState(false);
  const requestId = useRef(0);
  const loadedScope = useRef(null);

  function navigate(next) {
    setPage(canVisit(authUser, next) ? next : defaultPage(authUser));
    setMobileOpen(false);
    setSelected(null);
  }

  function acceptUser(user) {
    setAuthUser(user); setPage(defaultPage(user)); setWeek(1); setSchoolFilter('all'); setQuery('');
    const initialTarget = canSchedule(user) ? 'ALL' : user.teacherId;
    setTeacherId(initialTarget);
    setTeacherInput(canSchedule(user) ? '' : user.teacherId);
    setRevision(v => v + 1);
  }

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    function expired() {
      setRestoreError('');
      setAuthUser(null); setSchedule(null); setSelected(null); setToast(''); setTeacherId(''); setTeacherInput('');
    }
    window.addEventListener('campus-auth-expired', expired);
    setAuthLoading(true); setRestoreError('');
    try { localStorage.removeItem('campus-auth-user'); } catch { /* ignore */ }
    if (!getAccessToken()) setAuthLoading(false);
    else api('/auth/me', { signal: controller.signal })
      .then(user => { if (active) acceptUser(user); })
      .catch(error => {
        if (error.name !== 'AbortError' && active) {
          if (error.status === 401) expired();
          else setRestoreError(error.message);
        }
      })
      .finally(() => { if (active) setAuthLoading(false); });
    return () => { active = false; controller.abort(); window.removeEventListener('campus-auth-expired', expired); };
  }, [authRevision]);

  useEffect(() => {
    try {
      localStorage.setItem('campus-teacher-id', JSON.stringify(teacherId));
      localStorage.setItem('campus-period-configs', JSON.stringify(configs));
    } catch { setStorageError(true); }
  }, [authUser, teacherId, configs]);

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(''), 5000);
    return () => clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    if (!authUser) return;
    let refreshTimer, changed = false;
    const stop = subscribeScheduleUpdates({
      onStatus: setLiveStatus,
      onChange: event => {
        changed ||= event.kind === 'changed';
        window.clearTimeout(refreshTimer);
        refreshTimer = window.setTimeout(() => {
          setSelected(null);
          setRevision(value => value + 1);
          if (changed) setToast('Lịch giảng dạy vừa thay đổi. Đã cập nhật dữ liệu mới.');
          changed = false;
        }, 150);
      },
    });
    return () => { stop(); window.clearTimeout(refreshTimer); };
  }, [authUser]);

  useEffect(() => {
    const id = ++requestId.current;
    const controller = new AbortController();
    setSelected(null); setError('');
    const targetId = teacherId || (canSchedule(authUser) ? 'ALL' : authUser?.teacherId || '');
    if (!authUser || !targetId || !['schedule', 'courses', 'settings'].includes(page)) {
      loadedScope.current = null; setSchedule(null); setLoading(false); return () => controller.abort();
    }
    const scope = `${authUser.teacherId}/${targetId}/${week}`;
    if (loadedScope.current !== scope) { setSchedule(null); setLoading(true); }
    api(`/week/${week}?teacherId=${encodeURIComponent(targetId)}`, { signal: controller.signal })
      .then(data => {
        if (!data || !Array.isArray(data.lessons)) throw new Error('Dữ liệu lịch tuần trả về không hợp lệ.');
        if (id === requestId.current) { loadedScope.current = scope; setSchedule(data); }
      })
      .catch(err => { if (err.name !== 'AbortError' && id === requestId.current) setError(err.message); })
      .finally(() => { if (id === requestId.current) setLoading(false); });
    return () => controller.abort();
  }, [authUser, teacherId, week, revision, page]);

  if (authLoading) return <Empty text="Đang kiểm tra phiên đăng nhập…" />;
  if (!authUser && restoreError) return <div className="auth-recovery"><section className="standalone-panel"><h2>Chưa khôi phục được phiên đăng nhập</h2><p role="alert">{restoreError}</p><p>Phiên của bạn vẫn được giữ. Thử lại khi kết nối hoạt động.</p><div className="template-actions"><button className="button primary" onClick={() => setAuthRevision(value => value + 1)}>Thử khôi phục lại</button><button className="button" onClick={() => { setAccessToken(''); setRestoreError(''); }}>Đăng nhập tài khoản khác</button></div></section></div>;
  if (!authUser) {
    return (
      <AuthPage
        onAuthenticated={user => {
          acceptUser(user);
          setToast(`Xin chào ${user.fullName || user.teacherId}!`);
        }}
      />
    );
  }

  const lessons = (schedule?.lessons || []).map((lesson, index) => ({ ...lesson, id: `${lesson.schoolId}-${lesson.date}-${index}` }));
  const schools = [...new Map(lessons.map(lesson => [lesson.schoolId, { id: lesson.schoolId, name: lesson.schoolName }])).values()];
  const visible = lessons.filter(lesson => (schoolFilter === 'all' || lesson.schoolId === schoolFilter) && `${lesson.courseName} ${lesson.room} ${lesson.schoolName}`.toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi')));
  const semesterStart = schedule?.startDate ? addDays(schedule.startDate, -(week - 1) * 7) : authUser.semesterStartDate || '2026-08-10';
  const periodConfigs = Array.isArray(schedule?.schools)
    ? Object.fromEntries(schedule.schools.map(school => [school.schoolId, school]))
    : configs[teacherId] || {};
  const totalHours = lessons.reduce((sum, lesson) => sum + minutes(lesson.endTime) - minutes(lesson.startTime), 0) / 60;
  const conflictCount = lessons.filter(lesson => lesson.isConflict).length;

  function exportCalendar() {
    const url = URL.createObjectURL(new Blob([calendarFile(visible)], { type: 'text/calendar;charset=utf-8' }));
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = `lich-giang-day-khoa-tuan-${week}.ics`; anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function useTeacher(event) {
    event.preventDefault();
    const id = teacherInput.trim() || (canSchedule(authUser) ? 'ALL' : authUser.teacherId);
    if (!id) return;
    setTeacherId(id); setWeek(1); setSchoolFilter('all'); setQuery(''); setRevision(value => value + 1);
  }

  function showDefaultSchedule() {
    if (canSchedule(authUser)) {
      setTeacherInput('');
      setTeacherId('ALL');
    } else {
      setTeacherInput(authUser.teacherId);
      setTeacherId(authUser.teacherId);
    }
    setSchoolFilter('all');
    setQuery('');
    setRevision(value => value + 1);
  }

  function onImported(payload) {
    setAuthUser(previous => previous && ({ ...previous, semesterStartDate: payload.semesterStartDate }));
    setConfigs(previous => ({ ...previous, [payload.teacherId]: { ...previous[payload.teacherId], [payload.school.schoolId]: payload.school } }));
    setTeacherId(payload.teacherId); setTeacherInput(payload.teacherId);
    setWeek(Math.min(...payload.courses.map(course => course.weekSchedule.search(/[^- ]/) + 1)));
    setSchoolFilter('all'); setQuery(''); setRevision(value => value + 1); navigate('schedule');
    setToast('Đã lưu lịch vào máy chủ. Đang tải lịch tuần.');
  }

  async function handleDeleteSchool(schoolIdToDelete, schoolNameLabel) {
    const isDept = canSchedule(authUser) && teacherId === 'ALL';
    const confirmMsg = isDept
      ? `Xóa toàn bộ lịch đã xếp của khoa (${schoolNameLabel || schoolIdToDelete || 'PTIT'})?`
      : `Xóa toàn bộ lịch cũ của "${schoolNameLabel}" (${schoolIdToDelete}) cho giảng viên ${teacherId}?`;
    if (!window.confirm(confirmMsg)) return;
    try {
      if (isDept) {
        await api(`/department-schedule?schoolId=${encodeURIComponent(schoolIdToDelete || 'PTIT')}`, {
          method: 'DELETE',
        });
      } else {
        await api(`/school?teacherId=${encodeURIComponent(teacherId)}&schoolId=${encodeURIComponent(schoolIdToDelete || 'PTIT')}`, {
          method: 'DELETE',
        });
      }
      setSchoolFilter('all');
      setRevision(v => v + 1);
      setToast(`Đã xóa lịch cũ của ${schoolNameLabel || schoolIdToDelete || 'khoa'}.`);
    } catch (err) {
      setError(err.message);
    }
  }

  function handleLogout() {
    setAccessToken('');
    setAuthUser(null);
    setTeacherId(''); setTeacherInput(''); setSchedule(null); setSelected(null); setToast('');
    setMobileOpen(false);
  }

  const viewTeacherFromPage = (selectedTeacherId, firstWeek) => {
    setTeacherId(selectedTeacherId);
    setTeacherInput(selectedTeacherId);
    setWeek(clampWeek(firstWeek));
    setSchoolFilter('all');
    setQuery('');
    setRevision(v => v + 1);
    navigate('schedule');
    setToast(`Đang xem thời khóa biểu của giảng viên ${selectedTeacherId}`);
  };

  const canPickTeacher = canSchedule(authUser) || canAssign(authUser);
  const showTeacherPicker = ['schedule', 'courses'].includes(page);

  return <div className="app-shell">
    {mobileOpen && <button className="sidebar-backdrop" aria-label="Đóng điều hướng" onClick={() => setMobileOpen(false)} />}
    <aside className={`sidebar ${mobileOpen ? 'open' : ''}`}>
      <a className="brand" href="#" onClick={event => { event.preventDefault(); navigate(defaultPage(authUser)); }}><span className="brand-mark"><GraduationCap size={25} /></span><span>campus<span className="brand-dot">.</span><small>TEACHING WORKSPACE</small></span></a>
      <div className="workspace"><span className="school-icon"><GraduationCap size={19} /></span><div><strong>{roleLabel(authUser)}</strong><small>{canSchedule(authUser) ? 'Quản trị & xếp lịch khoa' : canAssign(authUser) ? 'Phân công bộ môn & lịch dạy' : 'Lịch giảng dạy'}</small></div></div>
      <span className="nav-label">KHÔNG GIAN LÀM VIỆC</span>
      <nav>{nav.filter(([id]) => canVisit(authUser, id)).map(([id, label, Icon]) => <button key={id} onClick={() => navigate(id)} className={`nav-item ${page === id ? 'active' : ''}`}><Icon size={20} /><span>{label}</span></button>)}</nav>
      <div className="sidebar-bottom">
        <div className="semester-card"><CalendarDays size={20} /><strong>Lịch có sẵn, luôn bên bạn.</strong><p>{canSchedule(authUser) ? 'Xếp lịch khoa và quản lý danh mục.' : canAssign(authUser) ? 'Phân công lớp của bộ môn và xem lịch.' : 'Nhập lịch từ Excel và theo dõi lịch dạy.'}</p></div>
        <button className="nav-item" onClick={() => navigate('settings')}><Settings size={19} />Cài đặt</button>
        <button className="nav-item" onClick={() => navigate('help')}><HelpCircle size={19} />Trợ giúp &amp; hỗ trợ</button>
        <button className="nav-item" onClick={handleLogout}><LogOut size={19} />Đăng xuất</button>
        <div className="sidebar-user"><span className="avatar"><GraduationCap size={20} /></span><div><strong>{authUser.fullName || teacherId}</strong><small>Mã: {authUser.teacherId} · {roleLabel(authUser)}</small></div></div>
      </div>
    </aside>
    <div className="main-shell"><header className="topbar"><div className="breadcrumb"><button className="icon-button mobile-menu" aria-label="Mở điều hướng" onClick={() => setMobileOpen(true)}><Menu size={22} /></button><span>{roleLabel(authUser)}</span><ChevronRight size={14} /><strong>{titles[page]}</strong></div><TopbarClock /></header>
    <main>
      <div className="page-heading">
        <div>
          <div className="eyebrow"><span /> {canSchedule(authUser) ? 'QUẢN LÝ ĐÀO TẠO KHOA' : canAssign(authUser) ? 'QUẢN LÝ BỘ MÔN & LỊCH DẠY' : 'LỊCH GIẢNG DẠY CỦA BẠN'}</div>
          <h1>{titles[page]}<span className="heading-dot">.</span></h1>
          <span className={`schedule-live-status ${liveStatus === 'live' ? 'connected' : ''}`} role="status">
            {liveStatus === 'live' ? 'Đang nhận cập nhật lịch trực tiếp' : liveStatus === 'unavailable'
              ? 'Kết nối cập nhật trực tiếp chưa sẵn sàng' : 'Đang kết nối cập nhật lịch…'}
          </span>
        </div>
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          {canImport(authUser) && page !== 'import' && (
            <button className="button" onClick={() => navigate('import')}>
              <Upload size={18} />Nhập lịch cá nhân
            </button>
          )}
          {canImport(authUser) && page === 'schedule' && teacherId === authUser.teacherId && (
            <button
              type="button"
              className="button"
              style={{ color: '#dc2626', borderColor: '#fca5a5' }}
              onClick={() => setDeleteOpen(true)}
            >
              <Trash2 size={17} /> Xóa lịch dạy cá nhân
            </button>
          )}
          {canSchedule(authUser) && page === 'schedule' && (
            <button
              type="button"
              className="button"
              style={{ color: '#dc2626', borderColor: '#fca5a5' }}
              onClick={() => handleDeleteSchool(schools[0]?.id || 'PTIT', teacherId === 'ALL' ? 'Toàn khoa' : teacherId)}
            >
              <Trash2 size={17} /> {teacherId === 'ALL' ? 'Xóa lịch cũ của khoa' : `Xóa lịch của ${teacherId}`}
            </button>
          )}
          {canSchedule(authUser) && page !== 'periods' && (
            <button className="button" onClick={() => navigate('periods')}>
              <Clock3 size={18} />Khung giờ tiết học
            </button>
          )}
          {canAssign(authUser) && page !== 'assignments' && (
            <button className="button" onClick={() => navigate('assignments')}>
              <UserCheck size={18} />Phân công giảng viên
            </button>
          )}
          {canSchedule(authUser) && page !== 'autoschedule' && (
            <button className="button primary" onClick={() => navigate('autoschedule')}>
              <Sparkles size={18} />Xếp lịch của khoa
            </button>
          )}
        </div>
      </div>

    {storageError && <div role="alert" className="info-banner">Không lưu được lựa chọn trên trình duyệt. Lịch đã nhập vẫn được lưu ở máy chủ.</div>}
    {showTeacherPicker && (
      <form className="teacher-picker" onSubmit={useTeacher}>
        <label>
          {canSchedule(authUser) ? 'Mã giảng viên (trống = Toàn khoa)' : 'Mã giảng viên'}
          <input
            readOnly={!canPickTeacher}
            value={teacherInput}
            onChange={event => setTeacherInput(event.target.value)}
            placeholder={canSchedule(authUser) ? 'Toàn khoa (hoặc GV02)' : 'GV02'}
            maxLength={50}
          />
        </label>
        <button className="button" disabled={loading}><RefreshCw size={15} />{canSchedule(authUser) && !teacherInput.trim() ? 'Tải lịch toàn khoa' : 'Tải lịch'}</button>
        {canSchedule(authUser) && teacherId !== 'ALL' && (
          <button type="button" className="button primary" onClick={showDefaultSchedule}>
            Xem lịch toàn khoa
          </button>
        )}
        {!canSchedule(authUser) && canAssign(authUser) && teacherId !== authUser.teacherId && (
          <button type="button" className="button primary" onClick={showDefaultSchedule}>
            Về lịch của tôi
          </button>
        )}
        <span className="muted-text">
          {canSchedule(authUser)
            ? (teacherId === 'ALL' ? `Toàn khoa · Tuần 1: ${dateLabel(semesterStart)}` : `GV ${teacherId} · Tuần 1: ${dateLabel(semesterStart)}`)
            : `GV ${teacherId} · Tuần 1: ${dateLabel(semesterStart)}`}
        </span>
      </form>
    )}
    {page === 'overview' && canImport(authUser) && <TodayDashboard user={authUser} scheduleRevision={revision} onSelect={setSelected} onImport={() => navigate('import')} onViewWeek={targetWeek => { setWeek(clampWeek(targetWeek)); setTeacherId(authUser.teacherId); setTeacherInput(authUser.teacherId); setSchoolFilter('all'); setQuery(''); setView('week'); navigate('schedule'); }} />}
    {page === 'schedule' && <>
      <div className="stats-grid"><Stat icon={CalendarDays} color="green" label="Buổi dạy trong tuần" value={schedule ? lessons.length : '—'} note={canSchedule(authUser) && teacherId === 'ALL' ? 'Tổng ca học toàn khoa' : 'Từ lịch đã nhập'} /><Stat icon={Clock3} color="blue" label="Tổng giờ giảng" value={schedule ? Number(totalHours.toFixed(1)) : '—'} suffix="giờ" note="Theo giờ thực tế" /><Stat icon={GraduationCap} color="purple" label={canSchedule(authUser) ? 'Đơn vị / Khoa' : 'Trường trong tuần'} value={schedule ? schools.length : '—'} note={canSchedule(authUser) ? 'Lịch đào tạo khoa' : 'Tổng hợp nhiều trường'} /><Stat icon={AlertTriangle} color="orange" label="Buổi trùng giờ" value={schedule ? conflictCount : '—'} note="Kiểm tra trùng ca" /></div>
      <div className={`content-grid${teacherId === 'ALL' ? ' school-wide-layout' : ''}`}><section className="calendar-panel"><div className="calendar-toolbar"><div><h2>{canSchedule(authUser) && teacherId === 'ALL' ? 'Lịch giảng dạy toàn khoa' : `Lịch tuần (${teacherId})`} <span className="badge muted">Tuần {week}</span></h2><p>{schedule ? `${dateLabel(schedule.startDate)} – ${dateLabel(schedule.endDate)}` : 'Đang tải dữ liệu lịch'}</p></div><div className="calendar-controls"><button className="button compact" aria-label="Về tuần hiện tại" disabled={!semesterStart || loading} onClick={() => setWeek(clampWeek(currentWeek(semesterStart)))}>Hôm nay</button><div className="arrow-group"><button aria-label="Tuần trước" disabled={week <= 1 || loading} onClick={() => setWeek(value => value - 1)}><ChevronLeft size={18} /></button><button aria-label="Tuần sau" disabled={!teacherId || loading || week >= 100} onClick={() => setWeek(value => clampWeek(value + 1))}><ChevronRight size={18} /></button></div><div className="view-switch"><button aria-label="Lịch tuần" aria-pressed={view === 'week'} className={view === 'week' ? 'selected' : ''} onClick={() => setView('week')}><Calendar size={17} /></button><button aria-label="Danh sách" aria-pressed={view === 'list'} className={view === 'list' ? 'selected' : ''} onClick={() => setView('list')}><List size={18} /></button></div></div></div>
      <div className="filter-bar"><label className="search-field"><Search size={17} /><input aria-label="Tìm môn hoặc phòng" placeholder="Tìm môn, giảng viên, phòng học…" value={query} onChange={event => setQuery(event.target.value)} /></label><select aria-label="Lọc theo đơn vị" value={schoolFilter} onChange={event => setSchoolFilter(event.target.value)}><option value="all">Tất cả đơn vị</option>{schools.map(school => <option key={school.id} value={school.id}>{school.name}</option>)}</select><button className="icon-button export-button" type="button" aria-label="Xuất lịch .ics" disabled={!visible.length || loading} onClick={exportCalendar}><Download size={18} /></button></div>
      {loading ? <Empty text="Đang tải lịch từ máy chủ…" /> : error ? <div role="alert" className="load-error"><AlertTriangle size={28} /><p>{error}</p><button className="button" onClick={() => setRevision(value => value + 1)}>Thử lại</button></div> : !schedule ? <Empty text={canSchedule(authUser) ? 'Chưa có dữ liệu lịch khoa.' : 'Chọn Nhập lịch để bắt đầu.'} /> : !visible.length ? <Empty text={lessons.length ? 'Không có buổi dạy phù hợp với bộ lọc.' : 'Tuần này chưa có buổi dạy.'} /> : view === 'week' ? (teacherId === 'ALL' ? <SchoolWideCalendar lessons={visible} startDate={schedule.startDate} onSelect={setSelected} /> : <WeekCalendar lessons={visible} startDate={schedule.startDate} configs={periodConfigs} onSelect={setSelected} />) : <div className="session-list">{visible.map(lesson => <button key={lesson.id} className="session-row" onClick={() => setSelected(lesson)}><span className="school-color" style={{ background: safeColor(lesson.colorCode) }} /><div><strong>{lesson.courseName}</strong><small>{dateLabel(lesson.date)} · {clock(lesson.startTime)} – {clock(lesson.endTime)} · {lesson.schoolName}</small>{lesson.isConflict && <small className="error-text">{lesson.conflictReason}</small>}</div><span className="row-room">{lesson.room}</span><ChevronRight size={18} /></button>)}</div>}
      <div className="calendar-footer"><span><CheckCircle2 size={14} />Lịch được lưu trên máy chủ</span><span>Mỗi giảng viên hiển thị một màu riêng</span></div></section>
      <aside className="right-rail">
        <section className="today-card">
          <div className="rail-title"><h2>{canSchedule(authUser) ? 'Khoa / Đơn vị trong tuần' : 'Trường trong tuần'}</h2></div>
          {schools.length ? schools.map(school => (
            <div key={school.id} style={{ display: 'flex', alignItems: 'center', gap: '4px', marginBottom: '6px' }}>
              <button className="school-filter" style={{ flex: 1 }} onClick={() => setSchoolFilter(school.id)}>
                {school.name}<ChevronRight size={15} />
              </button>
              {canSchedule(authUser) && <button
                type="button"
                className="icon-button"
                title={`Xóa lịch ${school.name}`}
                onClick={() => handleDeleteSchool(school.id, school.name)}
              >
                <Trash2 size={15} />
              </button>}
            </div>
          )) : <p className="rail-subtitle">Chưa có dữ liệu tuần này.</p>}
        </section>
        <section className="notice-card"><h2>Kiểm tra trùng giờ</h2><p className="rail-subtitle">{conflictCount ? `${conflictCount} buổi dạy trùng giờ.` : 'Không có buổi trùng giờ.'}</p></section>
        {canImport(authUser) && <section className="tip-card"><Upload size={21} /><h3>Cập nhật lịch cá nhân</h3><p>Nhập file Excel mới hoặc xóa môn cá nhân đã chọn.</p><button onClick={() => navigate('import')}>Nhập lịch <ChevronRight size={16} /></button></section>}
        {canAssign(authUser) && (
          <section className="tip-card">
            <UserCheck size={21} />
            <h3>Phân công &amp; Thỉnh giảng</h3>
            <p>Gán giảng viên cho các lớp đã xếp phòng hoặc xếp lịch thỉnh giảng.</p>
            <button onClick={() => navigate('assignments')}>Mở Phân công <ChevronRight size={16} /></button>
          </section>
        )}
      </aside></div>
    </>}
    {canImport(authUser) && page === 'import' && <ImportSchedule user={authUser} teacherId={authUser.teacherId} teacherName={authUser.fullName} semesterStart={semesterStart} onImported={onImported} />}
    {canSchedule(authUser) && page === 'periods' && <PeriodTemplatesPage />}
    {canSchedule(authUser) && page === 'departments' && <DepartmentManagementPage />}
    {canManageTeachers(authUser) && page === 'teachers' && <TeachersManagementPage user={authUser} />}
    {canSchedule(authUser) && page === 'rooms' && <RoomsManagementPage />}
    {canAssign(authUser) && page === 'assignments' && (
      <AssignmentsPage user={authUser} scheduleRevision={revision} onViewTeacherSchedule={viewTeacherFromPage} />
    )}
    {canSchedule(authUser) && page === 'autoschedule' && (
      <AutoSchedulePage
        semesterStart={semesterStart}
        scheduleRevision={revision}
        onNavigatePeriods={() => navigate('periods')}
        onScheduled={res => {
          setTeacherId('ALL');
          setTeacherInput('');
          setWeek(1);
          setSchoolFilter('all');
          setQuery('');
          setRevision(v => v + 1);
          setToast(res?.message || `Đã xếp phòng cho ${res?.totalClassesScheduled || 0} lớp/nhóm.`);
        }}
        onViewTeacherSchedule={viewTeacherFromPage}
      />
    )}
    {page === 'courses' && canImport(authUser) && (
      <section className="standalone-panel">
        <h2>Môn dạy trong tuần {week}</h2>
        {loading ? <Empty text="Đang tải…" /> : error ? <p role="alert" className="error-text">{error}</p> : !lessons.length ? <Empty text="Chưa có môn dạy trong tuần này." /> : (
          <div className="session-list">
            {lessons.map(lesson => (
              <button className="session-row" key={lesson.id} onClick={() => setSelected(lesson)}>
                <BookOpen size={20} />
                <div>
                  <strong>{lesson.courseName}</strong>
                  <small>{lesson.schoolName} · {dateLabel(lesson.date)} · Phòng {lesson.room}</small>
                </div>
                <span>Tiết {lesson.startPeriod}–{lesson.endPeriod}</span>
              </button>
            ))}
          </div>
        )}
      </section>
    )}
    {page === 'settings' && <section className="standalone-panel account-panel"><h2>Tài khoản</h2><p>{authUser.fullName} · {authUser.username} · {roleLabel(authUser)}</p><TeacherIdentity teacherId={authUser.teacherId} /><ChangePasswordForm username={authUser.username} /></section>}
    {page === 'help' && <section className="standalone-panel help-panel"><h2>Hướng dẫn sử dụng</h2>{canSchedule(authUser) ? <><p>1. Cấu hình <strong>Khung giờ tiết học</strong>, <strong>Bộ môn &amp; Môn học</strong>, <strong>Loại phòng &amp; Phòng học</strong> và <strong>Quản lý Giảng viên</strong>.</p><p>2. Vào <strong>Xếp lịch của khoa</strong> để xếp phòng, sau đó Trưởng bộ môn vào <strong>Phân công giảng viên</strong> để chia lớp.</p></> : <><p>Tải mẫu Excel trong mục <strong>Nhập lịch dạy</strong>, điền sheet <code>MonHoc</code> và <code>TietHoc</code> rồi lưu.</p>{canAssign(authUser) && <p>Trưởng bộ môn vào <strong>Phân công giảng viên</strong> để chia lớp cho giảng viên trong bộ môn.</p>}</>}</section>}
    <footer className="page-footer"><span>Campus · Không gian giảng dạy của bạn</span><span><i />Hệ thống quản lý &amp; xếp lịch tự động của khoa</span></footer></main></div>
    {selected && <LessonModal lesson={selected} onClose={() => setSelected(null)} />}
    {deleteOpen && canImport(authUser) && <PersonalCourseDeleteDialog
      teacherId={authUser.teacherId}
      onClose={() => setDeleteOpen(false)}
      onDeleted={message => { setDeleteOpen(false); setRevision(value => value + 1); setToast(message); }}
    />}
    {toast && <div className="toast" role="status"><CheckCircle2 size={19} />{toast}<button aria-label="Đóng thông báo" onClick={() => setToast('')}><X size={16} /></button></div>}
  </div>;
}

function Stat({ icon: Icon, color, label, value, suffix, note }) { return <section className="stat-card"><div className="stat-top"><span>{label}</span><span className={`stat-icon ${color}`}><Icon size={19} /></span></div><div className="stat-value">{value}<small>{suffix}</small></div><p>{note}</p></section>; }
function Empty({ text }) { return <div className="empty-state" role="status"><CalendarDays size={36} /><p>{text}</p></div>; }
function safeColor(color) { return /^#[\da-f]{6}$/i.test(color || '') ? color : '#177c62'; }
function WeekCalendar({ lessons, startDate, configs, onSelect }) {
  const schools = [...new Map(lessons.map(lesson => [lesson.schoolId, lesson.schoolName])).entries()];
  const [reference, setReference] = useState('');
  const referenceId = schools.some(([id]) => id === reference) ? reference : schools[0]?.[0];
  const referenceLessons = lessons.filter(lesson => lesson.schoolId === referenceId);
  const configured = configs[referenceId]?.periods;
  const periods = [...(configured?.length ? configured : new Map(referenceLessons.map(lesson => [lesson.startPeriod, { period: lesson.startPeriod, start: lesson.startTime, end: lesson.endTime }])).values())].sort((a, b) => a.period - b.period);
  const start = Math.floor(Math.min(420, ...lessons.map(lesson => minutes(lesson.startTime)), ...periods.map(period => minutes(period.start))) / 60) * 60;
  const end = Math.ceil(Math.max(1020, ...lessons.map(lesson => minutes(lesson.endTime)), ...periods.map(period => minutes(period.end))) / 60) * 60;
  const positioned = layoutLessons(lessons);
  return <><div className="axis-reference"><label>Tiết tham chiếu <select value={referenceId} onChange={event => setReference(event.target.value)}>{schools.map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select></label><small>{configured?.length ? 'Vị trí buổi dạy tính theo giờ thực tế.' : 'Hiện số tiết đầu từ lịch tuần.'}</small></div><div className="calendar-scroll"><div className="week-calendar"><div className="day-header"><div className="timezone">Tiết</div>{dayNames.map((day, index) => <div className={`day ${index > 4 ? 'weekend' : ''}`} key={day}><span>{day}</span><b>{addDays(startDate, index).slice(8)}</b></div>)}</div><div className="calendar-body imported-calendar" style={{ height: (end - start) * 1.1 }}><div className="time-axis">{periods.map(period => <span key={period.period} title={`${clock(period.start)} – ${clock(period.end)}`} style={{ top: (minutes(period.start) - start) * 1.1 + 3 }}>{period.period}</span>)}</div>{dayNames.map((day, index) => <div key={day} className={`day-column ${index > 4 ? 'weekend' : ''}`}>{positioned.filter(lesson => lesson.dayOfWeek === index + 2).map(lesson => <CalendarLesson key={lesson.id} lesson={lesson} start={start} color={safeColor(lesson.colorCode)} onSelect={onSelect} />)}</div>)}</div></div></div></>;
}
function LessonModal({ lesson, onClose }) {
  const ref = useRef(null);
  useEffect(() => { const dialog = ref.current; const previous = document.activeElement; dialog.showModal(); return () => { dialog.close(); previous?.focus(); }; }, []);
  return <dialog className="modal" ref={ref} onCancel={onClose}><div className="modal-head"><h2>Chi tiết buổi dạy</h2><button className="icon-button" aria-label="Đóng" onClick={onClose}><X size={20} /></button></div><h3>{lesson.courseName}</h3><div className="detail-grid">{[['Đơn vị / Khoa', lesson.schoolName], ['Ngày', dateLabel(lesson.date)], ['Thời gian', `${clock(lesson.startTime)} – ${clock(lesson.endTime)}`], ['Tiết', `${lesson.startPeriod}–${lesson.endPeriod}`], ['Phòng', lesson.room]].map(([label, value]) => <div key={label}><small>{label}</small><strong>{value}</strong></div>)}</div>{lesson.isConflict && <div role="alert" className="import-warning"><AlertTriangle size={18} />{lesson.conflictReason || 'Buổi dạy bị trùng giờ.'}</div>}</dialog>;
}
