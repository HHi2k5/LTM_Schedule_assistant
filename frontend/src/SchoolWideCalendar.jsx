import React from 'react';
import { AlertTriangle } from 'lucide-react';
import { addDays, clock, dateLabel, dayNames, minutes } from './schedule';

// Group by actual time, not by period number: different units may use different clocks.
function timeRows(lessons) {
  const rows = new Map();
  for (const lesson of lessons) {
    const key = `${minutes(lesson.startTime)}-${minutes(lesson.endTime)}`;
    if (!rows.has(key)) rows.set(key, { key, start: lesson.startTime, end: lesson.endTime, days: Array.from({ length: 7 }, () => []) });
    rows.get(key).days[lesson.dayOfWeek - 2]?.push(lesson);
  }
  return [...rows.values()].sort((a, b) => minutes(a.start) - minutes(b.start) || minutes(a.end) - minutes(b.end));
}

function lessonLabels(lesson) {
  // Hỗ trợ cả định dạng "[GV01 - Tên GV] ..." lẫn "[Chưa phân công] ..." từ backend.
  const prefixMatch = lesson.courseName?.match(/^\[([^\]]+)\]\s*/);
  const teacher = lesson.teacherName || (lesson.schoolName?.startsWith('GV: ') ? lesson.schoolName.slice(4) : prefixMatch?.[1]);
  return { title: prefixMatch ? lesson.courseName.slice(prefixMatch[0].length) : lesson.courseName, teacher };
}

export default function SchoolWideCalendar({ lessons, startDate, onSelect }) {
  const rows = timeRows(lessons);
  return <div className="department-calendar">
    <div className="department-calendar-note"><span><i />Mỗi giảng viên một màu (màu xám là lớp chưa phân công)</span><span>Các lớp cùng giờ được xếp thành từng hàng</span></div>
    <div className="department-calendar-scroll" tabIndex={0} role="region" aria-label="Bảng lịch toàn khoa, cuộn ngang để xem đủ các ngày">
      <table className="department-timetable">
        <caption className="department-caption">Lịch toàn khoa từ {dateLabel(startDate)} đến {dateLabel(addDays(startDate, 6))}</caption>
        <colgroup><col className="department-time-col" />{dayNames.map(day => <col key={day} />)}</colgroup>
        <thead><tr><th scope="col" className="department-corner">Khung giờ</th>{dayNames.map((day, index) => {
          const date = addDays(startDate, index);
          return <th scope="col" className={index > 4 ? 'department-weekend' : ''} key={day}><span>{day}</span><time dateTime={date}>{date.slice(8)}/{date.slice(5, 7)}</time></th>;
        })}</tr></thead>
        <tbody>{rows.map(row => <tr key={row.key}>
          <th scope="row" className="department-time"><time>{clock(row.start)}</time><span>–</span><time>{clock(row.end)}</time></th>
          {row.days.map((dayLessons, index) => <td key={index} className={index > 4 ? 'department-weekend' : ''}>
            <div className="department-cell">{dayLessons.length ? dayLessons.map(lesson => {
              const { title, teacher } = lessonLabels(lesson);
              const color = /^#[\da-f]{6}$/i.test(lesson.colorCode || '') ? lesson.colorCode : '#177c62';
              const periods = lesson.startPeriod === lesson.endPeriod ? `Tiết ${lesson.startPeriod}` : `Tiết ${lesson.startPeriod}–${lesson.endPeriod}`;
              return <button type="button" key={lesson.id} className={`department-lesson${lesson.isConflict ? ' has-conflict' : ''}`} style={{ '--teacher-color': color }} onClick={() => onSelect(lesson)}>
                <strong>{title}</strong>
                {teacher && <span className="department-teacher">{teacher}</span>}
                <span className="department-lesson-meta"><span>Phòng <b>{lesson.room || 'Chưa có'}</b></span><span>{periods}</span></span>
                {lesson.isConflict && <span className="department-conflict"><AlertTriangle size={13} />{lesson.conflictReason || 'Trùng giờ'}</span>}
              </button>;
            }) : <span className="department-vacant" aria-label="Không có lớp">—</span>}</div>
          </td>)}
        </tr>)}</tbody>
      </table>
    </div>
  </div>;
}