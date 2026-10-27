import React, { useEffect, useState } from 'react';
import { AlertTriangle, ArrowRight, BookOpen, CalendarDays, Clock3, MapPin, RefreshCw } from 'lucide-react';
import { api, clock, dateLabel } from './schedule.js';
import { lessonStatus, summarizeToday, teachingWeek, timeUntil, vietnamTime } from './today.js';

const dateFormat = new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'full' });
const statusLabels = { done: 'Đã kết thúc', ongoing: 'Đang dạy', upcoming: 'Chưa bắt đầu' };

export default function TodayDashboard({ user, onSelect, onViewWeek, onImport }) {
  const [now, setNow] = useState(() => new Date());
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState(null);
  const current = vietnamTime(now);
  const week = teachingWeek(current.date, user.semesterStartDate);
  const inRange = week !== null && week >= 1 && week <= 100;
  const key = `${user.teacherId}/${current.date}/${week}/${revision}`;
  const loading = inRange && result?.key !== key;

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1000);
    function refresh() {
      if (document.visibilityState === 'hidden') return;
      setNow(new Date()); setRevision(value => value + 1);
    }
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', refresh);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener('focus', refresh);
      document.removeEventListener('visibilitychange', refresh);
    };
  }, []);

  useEffect(() => {
    if (!inRange) return;
    let active = true;
    const controller = new AbortController();
    api(`/week/${week}?teacherId=${encodeURIComponent(user.teacherId)}`, { signal: controller.signal })
      .then(data => {
        if (!Array.isArray(data.lessons) || data.teacherId !== user.teacherId || data.weekIndex !== week) throw new Error('Dữ liệu lịch tuần trả về không hợp lệ.');
        if (active) setResult({ key, lessons: data.lessons });
      })
      .catch(error => { if (active && error.name !== 'AbortError') setResult({ key, error: error.message }); });
    return () => { active = false; controller.abort(); };
  }, [key, inRange, week, user.teacherId]);

  const summary = summarizeToday(result?.key === key ? result.lessons || [] : [], now);
  const next = summary.next;
  const ongoing = next && lessonStatus(next, current) === 'ongoing';

  return <section className="today-dashboard" aria-label="Tổng hợp lịch hôm nay">
    <div className="today-toolbar">
      <div><time dateTime={current.date}>{dateFormat.format(now)}</time><p>{inRange ? `Tuần ${week} · Giờ Việt Nam` : 'Giờ Việt Nam'}</p></div>
      <div className="today-actions">
        <button className="button" disabled={!inRange} onClick={() => onViewWeek(week)}><CalendarDays size={16}/>Xem lịch tuần</button>
        <button className="icon-button" aria-label="Làm mới Hôm nay" disabled={!inRange || loading} onClick={() => { setNow(new Date()); setRevision(value => value + 1); }}><RefreshCw size={17}/></button>
      </div>
    </div>
    {!inRange ? <div className="today-empty standalone-panel"><CalendarDays size={28}/><h2>{week !== null && week < 1 ? 'Học kỳ chưa bắt đầu' : 'Ngày hiện tại ngoài phạm vi lịch'}</h2><p>{week !== null && week < 1 ? `Tuần 1 bắt đầu ${dateLabel(user.semesterStartDate)}.` : 'Kiểm tra mốc học kỳ trong lịch đã nhập. Hệ thống hỗ trợ tối đa 100 tuần.'}</p><button className="button" onClick={onImport}>Nhập lịch cá nhân</button></div>
      : loading ? <div className="today-empty standalone-panel" role="status"><RefreshCw size={26}/><p>Đang tải lịch tuần hiện tại…</p></div>
      : result.error ? <div className="load-error standalone-panel" role="alert"><AlertTriangle size={26}/><p>{result.error}</p><button className="button" onClick={() => setRevision(value => value + 1)}>Thử tải lại Hôm nay</button></div>
      : <>
        <div className="today-summary" aria-label="Tóm tắt tuần hiện tại">
          <Summary label="Buổi dạy hôm nay" value={summary.today.length}/>
          <Summary label="Buổi còn lại trong tuần" value={summary.remaining.length}/>
          <Summary label="Giờ còn lại trong tuần" value={Number(summary.remainingHours.toFixed(1))}/>
          <Summary label="Buổi trùng giờ còn lại" value={summary.conflicts.length} warning={summary.conflicts.length > 0}/>
        </div>
        <div className="today-columns">
          <div className="today-main">
            {next ? <section className="today-next-panel">
              <div className="today-section-heading"><h2>{ongoing ? 'Buổi đang dạy' : 'Buổi tiếp theo'}</h2><span>Trong tuần hiện tại</span></div>
              <button className="today-next-card" onClick={() => onSelect(next)}>
                <span className={`today-state ${ongoing ? 'ongoing' : ''}`}><Clock3 size={14}/>{ongoing ? `Đang dạy · còn ${timeUntil(next, now, true)}` : `Bắt đầu sau ${timeUntil(next, now)}`}</span>
                <strong>{next.courseName}</strong>
                <span>{dateLabel(next.date)} · {clock(next.startTime)} – {clock(next.endTime)}</span>
                <span className="today-location"><MapPin size={16}/>Phòng {next.room} · {next.schoolName}</span>
                {summary.ongoing.length > 1 && <span className="today-simultaneous"><AlertTriangle size={15}/>{summary.ongoing.length} buổi đang diễn ra đồng thời. Kiểm tra trùng giờ.</span>}
                <span className="today-card-link">Xem chi tiết <ArrowRight size={15}/></span>
              </button>
            </section> : <section className="today-empty standalone-panel"><CalendarDays size={28}/><h2>{summary.week.length ? 'Đã hoàn tất lịch tuần này' : 'Tuần này chưa có lịch dạy'}</h2><p>{summary.week.length ? 'Bạn đã kết thúc các buổi dạy trong tuần hiện tại.' : 'Bạn có thể xem tuần khác hoặc nhập lịch cá nhân.'}</p>{!summary.week.length && <button className="button" onClick={onImport}>Nhập lịch cá nhân</button>}</section>}
            <section className="today-agenda standalone-panel">
              <div className="today-section-heading"><h2>Lịch hôm nay</h2><span>{summary.today.length} buổi</span></div>
              {summary.today.length ? <ol className="today-lesson-list">{summary.today.map((lesson, index) => {
                const status = lessonStatus(lesson, current);
                return <li key={`${lesson.schoolId}-${lesson.startTime}-${index}`}><button className={`today-lesson ${status}`} onClick={() => onSelect(lesson)}>
                  <span className="today-lesson-time">{clock(lesson.startTime)}<small>{clock(lesson.endTime)}</small></span>
                  <span className="today-lesson-info"><strong>{lesson.courseName}</strong><span>Phòng {lesson.room} · {lesson.schoolName}</span><span className="today-state">{statusLabels[status]}{lesson.isConflict ? ' · Trùng giờ' : ''}</span></span>
                  <ArrowRight size={16}/>
                </button></li>;
              })}</ol> : <div className="today-no-lessons"><BookOpen size={26}/><p>Hôm nay không có lịch dạy.</p></div>}
            </section>
          </div>
          <aside className="today-attention standalone-panel">
            <h2><AlertTriangle size={18}/>Việc cần chú ý</h2>
            {summary.conflicts.length ? <><p>{summary.conflicts.length} buổi trùng giờ chưa kết thúc trong tuần này.</p><ul>{summary.conflicts.map((lesson, index) => <li key={`${lesson.date}-${lesson.startTime}-${index}`}><button onClick={() => onViewWeek(week)} aria-label={`Xem lịch trùng giờ: ${lesson.courseName}, ${dateLabel(lesson.date)}`}><strong>{lesson.courseName}</strong><span>{dateLabel(lesson.date)} · {clock(lesson.startTime)} – {clock(lesson.endTime)}</span><span>{lesson.conflictReason || 'Có buổi học trùng giờ. Mở lịch tuần để đối chiếu.'}</span><span className="today-card-link">Mở lịch tuần <ArrowRight size={14}/></span></button></li>)}</ul></>
              : <p>Không có buổi trùng giờ còn lại trong tuần.</p>}
            <p className="today-scope-note">Tổng hợp từ lịch đã lưu trong tuần hiện tại. Buổi đang dạy chỉ tính phần thời gian còn lại.</p>
          </aside>
        </div>
      </>}
  </section>;
}

function Summary({ label, value, warning = false }) {
  return <div className={`today-summary-card${warning ? ' warning' : ''}`}><span>{label}</span><strong>{value}</strong></div>;
}
