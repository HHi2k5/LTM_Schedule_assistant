import React, { useEffect, useRef, useState } from 'react';
import { GraduationCap, LayoutDashboard, CalendarDays, BookOpen, Clock3, Settings, ChevronLeft, ChevronRight, Search, Upload, Download, List, Calendar, Menu, HelpCircle, X, CheckCircle2, AlertTriangle, RefreshCw, Sparkles, LogOut, Trash2 } from 'lucide-react';
import { api, dayNames, minutes, clock, dateLabel, addDays, currentWeek, layoutLessons, calendarFile } from './schedule';
import ImportSchedule from './ImportSchedule';
import AutoSchedulePage from './AutoSchedulePage';
import AuthPage from './AuthPage';
import { getAccessToken, setAccessToken } from './authSession';
import { canImport, canSchedule, canVisit, defaultPage } from './permissions';

const nav = [
  ['overview', 'Tổng quan', LayoutDashboard],
  ['schedule', 'Lịch giảng dạy', CalendarDays],
  ['courses', 'Môn dạy trong tuần', BookOpen],
  ['import', 'Nhập lịch dạy', Upload],
  ['autoschedule', 'Xếp lịch toàn trường', Sparkles],
];
const titles = Object.fromEntries([...nav, ['settings', 'Cài đặt'], ['help', 'Trợ giúp']].map(([id, label]) => [id, label]));

function stored(key, fallback) {
  try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; }
}

export default function App() {
  const [authUser, setAuthUser] = useState(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [page, setPage] = useState('schedule');
  const [mobileOpen, setMobileOpen] = useState(false);
  const [teacherId, setTeacherId] = useState('');
  const [teacherInput, setTeacherInput] = useState(teacherId);
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
  const requestId = useRef(0);

  function navigate(next) { setPage(canVisit(authUser, next) ? next : defaultPage(authUser)); setMobileOpen(false); setSelected(null); }

  function acceptUser(user) {
    setAuthUser(user); setPage(defaultPage(user)); setWeek(1); setSchoolFilter('all'); setQuery('');
    setTeacherId(canImport(user) ? user.teacherId : '');
    setTeacherInput(canImport(user) ? user.teacherId : '');
    setRevision(v => v + 1);
  }

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    function expired() {
      setAuthUser(null); setSchedule(null); setSelected(null); setToast(''); setTeacherId(''); setTeacherInput('');
    }
    window.addEventListener('campus-auth-expired', expired);
    try { localStorage.removeItem('campus-auth-user'); } catch { /* Legacy UI state is not authentication. */ }
    if (!getAccessToken()) setAuthLoading(false);
    else api('/auth/me', { signal: controller.signal })
      .then(user => { if (active) acceptUser(user); })
      .catch(error => { if (error.name !== 'AbortError' && active) { setAccessToken(''); expired(); } })
      .finally(() => { if (active) setAuthLoading(false); });
    return () => { active = false; controller.abort(); window.removeEventListener('campus-auth-expired', expired); };
  }, []);

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
    const id = ++requestId.current;
    const controller = new AbortController();
    setSchedule(null); setSelected(null); setError('');
    if (!authUser || !teacherId || !['schedule', 'overview', 'courses', 'settings'].includes(page)) { setLoading(false); return () => controller.abort(); }
    setLoading(true);
    api(`/week/${week}?teacherId=${encodeURIComponent(teacherId)}`, { signal: controller.signal })
      .then(data => {
        if (!data || !Array.isArray(data.lessons)) throw new Error('Dữ liệu lịch tuần trả về không hợp lệ.');
        if (id === requestId.current) setSchedule(data);
      })
      .catch(err => { if (err.name !== 'AbortError' && id === requestId.current) setError(err.message); })
      .finally(() => { if (id === requestId.current) setLoading(false); });
    return () => controller.abort();
  }, [authUser, teacherId, week, revision, page]);

  // Nếu chưa đăng nhập -> Hiển thị màn hình Đăng nhập / Đăng ký
  if (authLoading) return <Empty text="Đang kiểm tra phiên đăng nhập…"/>;
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
  const periodConfigs = configs[teacherId] || {};
  const totalHours = lessons.reduce((sum, lesson) => sum + minutes(lesson.endTime) - minutes(lesson.startTime), 0) / 60;
  const conflictCount = lessons.filter(lesson => lesson.isConflict).length;

  function exportCalendar() {
    const url = URL.createObjectURL(new Blob([calendarFile(visible)], { type: 'text/calendar;charset=utf-8' }));
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = `lich-day-tuan-${week}.ics`; anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function useTeacher(event) {
    event.preventDefault();
    const id = teacherInput.trim();
    if (!id) return;
    setTeacherId(id); setWeek(1); setSchoolFilter('all'); setQuery(''); setRevision(value => value + 1);
  }

  function onImported(payload) {
    setConfigs(previous => ({ ...previous, [payload.teacherId]: { ...previous[payload.teacherId], [payload.school.schoolId]: payload.school } }));
    setTeacherId(payload.teacherId); setTeacherInput(payload.teacherId);
    setWeek(Math.min(...payload.courses.map(course => course.weekSchedule.search(/[^- ]/) + 1)));
    setSchoolFilter('all'); setQuery(''); setRevision(value => value + 1); navigate('schedule');
    setToast('Đã lưu lịch vào máy chủ. Đang tải lịch tuần.');
  }

  async function handleDeleteSchool(schoolIdToDelete, schoolNameLabel) {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa toàn bộ lịch cũ của trường "${schoolNameLabel}" (${schoolIdToDelete}) cho giảng viên ${teacherId}?`)) return;
    try {
      await api(`/school?teacherId=${encodeURIComponent(teacherId)}&schoolId=${encodeURIComponent(schoolIdToDelete)}`, {
        method: 'DELETE',
      });
      setSchoolFilter('all');
      setRevision(v => v + 1);
      setToast(`Đã xóa lịch của trường ${schoolNameLabel}!`);
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

  return <div className="app-shell">
    {mobileOpen && <button className="sidebar-backdrop" aria-label="Đóng điều hướng" onClick={() => setMobileOpen(false)}/>}
    <aside className={`sidebar ${mobileOpen ? 'open' : ''}`}>
      <a className="brand" href="#" onClick={event => { event.preventDefault(); navigate(defaultPage(authUser)); }}><span className="brand-mark"><GraduationCap size={25}/></span><span>campus<span className="brand-dot">.</span><small>TEACHING WORKSPACE</small></span></a>
      <div className="workspace"><span className="school-icon"><GraduationCap size={19}/></span><div><strong>{canSchedule(authUser) ? 'Quản lý đào tạo' : 'Lịch dạy cá nhân'}</strong><small>{canSchedule(authUser) ? 'Xếp lịch toàn trường' : 'Tổng hợp từ các trường'}</small></div></div>
      <span className="nav-label">KHÔNG GIAN LÀM VIỆC</span>
      <nav>{nav.filter(([id]) => canVisit(authUser, id)).map(([id, label, Icon]) => <button key={id} onClick={() => navigate(id)} className={`nav-item ${page === id ? 'active' : ''}`}><Icon size={20}/><span>{label}</span></button>)}</nav>
      <div className="sidebar-bottom">
        <div className="semester-card"><CalendarDays size={20}/><strong>Lịch có sẵn, luôn bên bạn.</strong><p>{canSchedule(authUser) ? 'Xếp lịch toàn trường và tải kết quả Excel.' : 'Nhập lịch từ Excel và theo dõi lịch của bạn.'}</p></div>
        <button className="nav-item" onClick={() => navigate('settings')}><Settings size={19}/>Cài đặt</button>
        <button className="nav-item" onClick={() => navigate('help')}><HelpCircle size={19}/>Trợ giúp & hỗ trợ</button>
        <button className="nav-item" onClick={handleLogout}><LogOut size={19}/>Đăng xuất</button>
        <div className="sidebar-user"><span className="avatar"><GraduationCap size={20}/></span><div><strong>{authUser.fullName || teacherId}</strong><small>Mã: {authUser.teacherId} · {authUser.role}</small></div></div>
      </div>
    </aside>
    <div className="main-shell"><header className="topbar"><div className="breadcrumb"><button className="icon-button mobile-menu" aria-label="Mở điều hướng" onClick={() => setMobileOpen(true)}><Menu size={22}/></button><span>{canSchedule(authUser) ? 'Không gian quản lý' : 'Không gian giảng viên'}</span><ChevronRight size={14}/><strong>{titles[page]}</strong></div><span className="today-label">{new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'full' }).format(new Date())}</span></header>
    <main><div className="page-heading"><div><div className="eyebrow"><span/> {canSchedule(authUser) ? 'QUẢN LÝ ĐÀO TẠO' : 'LỊCH GIẢNG DẠY CỦA BẠN'}</div><h1>{titles[page]}<span className="heading-dot">.</span></h1><p>{canSchedule(authUser) ? 'Phân công, xếp lịch và xuất Excel toàn trường.' : 'Nhập lịch đã được phân công. Theo dõi từng tuần, từng trường.'}</p></div><div style={{ display: 'flex', gap: '8px' }}>{canImport(authUser) && page !== 'import' && <button className="button" onClick={() => navigate('import')}><Upload size={18}/>Nhập lịch cá nhân</button>}{canSchedule(authUser) && page !== 'autoschedule' && <button className="button primary" onClick={() => navigate('autoschedule')}><Sparkles size={18}/>Xếp lịch toàn trường</button>}</div></div>
    {storageError && <div role="alert" className="info-banner">Không lưu được lựa chọn trên trình duyệt. Lịch đã nhập vẫn được lưu ở máy chủ.</div>}
    {page !== 'import' && page !== 'autoschedule' && page !== 'help' && <form className="teacher-picker" onSubmit={useTeacher}><label>Mã giảng viên<input readOnly={!canSchedule(authUser)} value={teacherInput} onChange={event => setTeacherInput(event.target.value)} placeholder="Ví dụ: GV01" required maxLength={50}/></label><button className="button" disabled={loading}><RefreshCw size={15}/>Tải lịch</button><span className="muted-text">{semesterStart ? `Tuần 1 bắt đầu ${dateLabel(semesterStart)}` : 'Nhập mã để xem lịch đã lưu trên máy chủ.'}</span></form>}
    {(page === 'schedule' || page === 'overview') && <>
      <div className="stats-grid"><Stat icon={CalendarDays} color="green" label="Buổi dạy trong tuần" value={schedule ? lessons.length : '—'} note="Từ lịch đã nhập"/><Stat icon={Clock3} color="blue" label="Tổng giờ giảng" value={schedule ? Number(totalHours.toFixed(1)) : '—'} suffix="giờ" note="Theo giờ thực tế của trường"/><Stat icon={GraduationCap} color="purple" label="Trường trong tuần" value={schedule ? schools.length : '—'} note="Lịch tổng hợp nhiều trường"/><Stat icon={AlertTriangle} color="orange" label="Buổi trùng giờ" value={schedule ? conflictCount : '—'} note="Kiểm tra trước khi giảng dạy"/></div>
      <div className="content-grid"><section className="calendar-panel"><div className="calendar-toolbar"><div><h2>Lịch tuần <span className="badge muted">Tuần {week}</span></h2><p>{schedule ? `${dateLabel(schedule.startDate)} – ${dateLabel(schedule.endDate)}` : 'Chọn giảng viên để tải lịch'}</p></div><div className="calendar-controls"><button className="button compact" disabled={!semesterStart || loading} onClick={() => setWeek(currentWeek(semesterStart))}>Hôm nay</button><div className="arrow-group"><button aria-label="Tuần trước" disabled={week <= 1 || loading} onClick={() => setWeek(value => value - 1)}><ChevronLeft size={18}/></button><button aria-label="Tuần sau" disabled={!teacherId || loading} onClick={() => setWeek(value => value + 1)}><ChevronRight size={18}/></button></div><div className="view-switch"><button aria-label="Lịch tuần" aria-pressed={view === 'week'} className={view === 'week' ? 'selected' : ''} onClick={() => setView('week')}><Calendar size={17}/></button><button aria-label="Danh sách" aria-pressed={view === 'list'} className={view === 'list' ? 'selected' : ''} onClick={() => setView('list')}><List size={18}/></button></div></div></div>
      <div className="filter-bar"><label className="search-field"><Search size={17}/><input aria-label="Tìm môn hoặc phòng" placeholder="Tìm môn, phòng hoặc trường…" value={query} onChange={event => setQuery(event.target.value)}/></label><select aria-label="Lọc theo trường" value={schoolFilter} onChange={event => setSchoolFilter(event.target.value)}><option value="all">Tất cả trường</option>{schools.map(school => <option key={school.id} value={school.id}>{school.name}</option>)}</select><button className="icon-button export-button" type="button" aria-label="Xuất lịch .ics" disabled={!visible.length || loading} onClick={exportCalendar}><Download size={18}/></button></div>
      {loading ? <Empty text="Đang tải lịch từ máy chủ…"/> : error ? <div role="alert" className="load-error"><AlertTriangle size={28}/><p>{error}</p><p className="muted-text">{canSchedule(authUser) ? 'Chọn mã giảng viên đã có lịch hoặc vào Xếp lịch toàn trường.' : 'Nếu chưa có lịch, hãy nhập file Excel của bạn.'}</p><button className="button" onClick={() => setRevision(value => value + 1)}>Thử lại</button></div> : !schedule ? <Empty text={canSchedule(authUser) ? 'Chọn mã giảng viên để xem lịch.' : 'Chọn Nhập lịch để bắt đầu.'}/> : !visible.length ? <Empty text={lessons.length ? 'Không có buổi dạy phù hợp với bộ lọc.' : 'Tuần này chưa có buổi dạy. Chuyển tuần để xem lịch khác.'}/> : view === 'week' ? <WeekCalendar lessons={visible} startDate={schedule.startDate} configs={periodConfigs} onSelect={setSelected}/> : <div className="session-list">{visible.map(lesson => <button key={lesson.id} className="session-row" onClick={() => setSelected(lesson)}><span className="school-color" style={{ background: safeColor(lesson.colorCode) }}/><div><strong>{lesson.courseName}</strong><small>{dateLabel(lesson.date)} · {clock(lesson.startTime)} – {clock(lesson.endTime)} · {lesson.schoolName}</small>{lesson.isConflict && <small className="error-text">{lesson.conflictReason}</small>}</div><span className="row-room">{lesson.room}</span><ChevronRight size={18}/></button>)}</div>}
      <div className="calendar-footer"><span><CheckCircle2 size={14}/>Lịch từ máy chủ</span><span>Giờ thực tế theo từng trường</span></div></section>
      <aside className="right-rail">
        <section className="today-card">
          <div className="rail-title"><h2>Trường trong tuần</h2></div>
          {schools.length ? schools.map(school => (
            <div key={school.id} style={{ display: 'flex', alignItems: 'center', gap: '4px', marginBottom: '6px' }}>
              <button className="school-filter" style={{ flex: 1 }} onClick={() => setSchoolFilter(school.id)}>
                {school.name}<ChevronRight size={15}/>
              </button>
              {canImport(authUser) && <button
                type="button"
                className="icon-button"
                title={`Xóa lịch trường ${school.name}`}
                onClick={() => handleDeleteSchool(school.id, school.name)}
              >
                <Trash2 size={15} />
              </button>}
            </div>
          )) : <p className="rail-subtitle">Chưa có dữ liệu tuần này.</p>}
        </section>
        <section className="notice-card"><h2>Kiểm tra trùng giờ</h2><p className="rail-subtitle">{conflictCount ? `${conflictCount} buổi dạy trùng giờ. Mở buổi dạy để xem chi tiết.` : 'Các cảnh báo được tính từ giờ thực tế, kể cả giữa các trường.'}</p></section>
        {canImport(authUser) && <section className="tip-card"><Upload size={21}/><h3>Đổi hoặc thêm lịch trường</h3><p>Bạn có thể bấm biểu tượng thùng rác cạnh tên trường để xóa lịch cũ hoặc tải file Excel mới đè lên.</p><button onClick={() => navigate('import')}>Nhập lịch <ChevronRight size={16}/></button></section>}
      </aside></div>
    </>}
    {canImport(authUser) && page === 'import' && <ImportSchedule teacherId={authUser.teacherId} semesterStart={semesterStart} onImported={onImported}/>}
    {canSchedule(authUser) && page === 'autoschedule' && (
      <AutoSchedulePage
        semesterStart={semesterStart}
        onScheduled={res => {
          setRevision(v => v + 1);
          setToast(`Đã xếp lịch tự động thành công (${res?.totalClassesScheduled || 0} ca học)!`);
        }}
        onViewTeacherSchedule={selectedTeacherId => {
          setTeacherId(selectedTeacherId);
          setTeacherInput(selectedTeacherId);
          setWeek(2); // Tuần 2 có đầy đủ cả Lý thuyết và Thực hành
          setSchoolFilter('all');
          setQuery('');
          setRevision(v => v + 1);
          navigate('schedule');
          setToast(`Đang xem thời khóa biểu của giảng viên ${selectedTeacherId}`);
        }}
      />
    )}
    {page === 'courses' && <section className="standalone-panel"><h2>Môn dạy trong tuần {week}</h2>{loading ? <Empty text="Đang tải…"/> : error ? <p role="alert" className="error-text">{error}</p> : !lessons.length ? <Empty text="Chưa có môn dạy trong tuần này."/> : <div className="session-list">{lessons.map(lesson => <button className="session-row" key={lesson.id} onClick={() => setSelected(lesson)}><BookOpen size={20}/><div><strong>{lesson.courseName}</strong><small>{lesson.schoolName} · {dateLabel(lesson.date)} · Phòng {lesson.room}</small></div><span>Tiết {lesson.startPeriod}–{lesson.endPeriod}</span></button>)}</div>}</section>}
    {page === 'settings' && <section className="standalone-panel"><h2>Tài khoản</h2><p>{authUser.fullName} · {authUser.username} · {canSchedule(authUser) ? 'Quản lý' : 'Giảng viên'}</p><p className="muted-text">{canSchedule(authUser) ? 'Xếp lịch toàn trường và tải Excel kết quả.' : 'Nhập, xem và quản lý lịch của bạn.'}</p></section>}
    {page === 'help' && <section className="standalone-panel help-panel"><h2>Hướng dẫn sử dụng</h2>{canImport(authUser) ? <p>Nhập file Excel lịch của bạn. Nhập lại cùng mã trường sẽ thay thế lịch trường đó.</p> : <p>Xếp lịch toàn trường bằng file phân công hoặc bảng nhập nhanh. Sau khi xếp, chọn Tải Excel lịch đã xếp để lưu kết quả có mã và tên giảng viên.</p>}</section>}
    <footer className="page-footer"><span>Campus · Không gian giảng dạy của bạn</span><span><i/>Hệ thống quản lý & xếp lịch tự động</span></footer></main></div>
    {selected && <LessonModal lesson={selected} onClose={() => setSelected(null)}/>}
    {toast && <div className="toast" role="status"><CheckCircle2 size={19}/>{toast}<button aria-label="Đóng thông báo" onClick={() => setToast('')}><X size={16}/></button></div>}
  </div>;
}

function Stat({ icon: Icon, color, label, value, suffix, note }) { return <section className="stat-card"><div className="stat-top"><span>{label}</span><span className={`stat-icon ${color}`}><Icon size={19}/></span></div><div className="stat-value">{value}<small>{suffix}</small></div><p>{note}</p></section>; }
function Empty({ text }) { return <div className="empty-state" role="status"><CalendarDays size={36}/><p>{text}</p></div>; }
function safeColor(color) { return /^#[\da-f]{6}$/i.test(color || '') ? color : '#177c62'; }
function WeekCalendar({ lessons, startDate, configs, onSelect }) {
  const schools = [...new Map(lessons.map(lesson => [lesson.schoolId, lesson.schoolName])).entries()];
  const [reference, setReference] = useState('');
  const referenceId = schools.some(([id]) => id === reference) ? reference : schools[0]?.[0];
  const referenceLessons = lessons.filter(lesson => lesson.schoolId === referenceId);
  const configured = configs[referenceId]?.periods;
  const periods = configured?.length ? configured : [...new Map(referenceLessons.map(lesson => [lesson.startPeriod, { period: lesson.startPeriod, start: lesson.startTime, end: lesson.endTime }])).values()];
  const start = Math.floor(Math.min(420, ...lessons.map(lesson => minutes(lesson.startTime)), ...periods.map(period => minutes(period.start))) / 60) * 60;
  const end = Math.ceil(Math.max(1020, ...lessons.map(lesson => minutes(lesson.endTime)), ...periods.map(period => minutes(period.end))) / 60) * 60;
  const positioned = layoutLessons(lessons);
  return <><div className="axis-reference"><label>Tiết tham chiếu <select value={referenceId} onChange={event => setReference(event.target.value)}>{schools.map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select></label><small>{configured?.length ? 'Vị trí buổi dạy tính theo giờ thực tế.' : 'Hiện số tiết đầu từ lịch tuần.'}</small></div><div className="calendar-scroll"><div className="week-calendar"><div className="day-header"><div className="timezone">Tiết</div>{dayNames.map((day, index) => <div className={`day ${index > 4 ? 'weekend' : ''}`} key={day}><span>{day}</span><b>{addDays(startDate, index).slice(8)}</b></div>)}</div><div className="calendar-body imported-calendar" style={{ height: (end - start) * 1.1 }}><div className="time-axis">{periods.map(period => <span key={period.period} title={`${clock(period.start)} – ${clock(period.end)}`} style={{ top: (minutes(period.start) - start) * 1.1 + 3 }}>{period.period}</span>)}</div>{dayNames.map((day, index) => <div key={day} className={`day-column ${index > 4 ? 'weekend' : ''}`}>{positioned.filter(lesson => lesson.dayOfWeek === index + 2).map(lesson => <button key={lesson.id} className={`session-card imported-session ${lesson.isConflict ? 'has-conflict' : ''}`} style={{ top: (minutes(lesson.startTime) - start) * 1.1 + 2, height: Math.max(2, (minutes(lesson.endTime) - minutes(lesson.startTime)) * 1.1 - 4), left: `calc(${lesson.lane * 100 / lesson.lanes}% + 3px)`, width: `calc(${100 / lesson.lanes}% - 6px)`, right: 'auto', '--school-color': safeColor(lesson.colorCode) }} onClick={() => onSelect(lesson)} title={`${lesson.courseName} · ${lesson.schoolName} · ${clock(lesson.startTime)}–${clock(lesson.endTime)}${lesson.isConflict ? ` · ${lesson.conflictReason}` : ''}`}><span className="session-time">{clock(lesson.startTime)} – {clock(lesson.endTime)}</span><strong>{lesson.courseName}</strong><span className="session-code">{lesson.schoolName}</span><span className="session-room">{lesson.room} · Tiết {lesson.startPeriod}–{lesson.endPeriod}</span>{lesson.isConflict && <span className="conflict-label"><AlertTriangle size={12}/>Trùng giờ</span>}</button>)}</div>)}</div></div></div></>;
}
function LessonModal({ lesson, onClose }) {
  const ref = useRef(null);
  useEffect(() => { const dialog = ref.current; const previous = document.activeElement; dialog.showModal(); return () => { dialog.close(); previous?.focus(); }; }, []);
  return <dialog className="modal" ref={ref} onCancel={onClose}><div className="modal-head"><h2>Chi tiết buổi dạy</h2><button className="icon-button" aria-label="Đóng" onClick={onClose}><X size={20}/></button></div><h3>{lesson.courseName}</h3><div className="detail-grid">{[['Trường', lesson.schoolName], ['Ngày', dateLabel(lesson.date)], ['Thời gian', `${clock(lesson.startTime)} – ${clock(lesson.endTime)}`], ['Tiết', `${lesson.startPeriod}–${lesson.endPeriod}`], ['Phòng', lesson.room]].map(([label, value]) => <div key={label}><small>{label}</small><strong>{value}</strong></div>)}</div>{lesson.isConflict && <div role="alert" className="import-warning"><AlertTriangle size={18}/>{lesson.conflictReason || 'Buổi dạy bị trùng giờ.'}</div>}</dialog>;
}
