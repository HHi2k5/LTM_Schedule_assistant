import { addDays, isMonday, minutes } from './schedule.js';

const localTime = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
});

export function vietnamTime(now = new Date()) {
  const parts = Object.fromEntries(localTime.formatToParts(now).map(part => [part.type, part.value]));
  return { date: `${parts.year}-${parts.month}-${parts.day}`, minute: Number(parts.hour) * 60 + Number(parts.minute) + Number(parts.second) / 60 };
}

export function teachingWeek(date, anchor) {
  if (!isMonday(anchor)) return null;
  return Math.floor((Date.parse(`${date}T00:00:00Z`) - Date.parse(`${anchor}T00:00:00Z`)) / 604800000) + 1;
}

export function lessonStatus(lesson, current) {
  if (lesson.date < current.date || (lesson.date === current.date && minutes(lesson.endTime) <= current.minute)) return 'done';
  if (lesson.date === current.date && minutes(lesson.startTime) <= current.minute) return 'ongoing';
  return 'upcoming';
}

export function summarizeToday(lessons, now = new Date()) {
  const current = vietnamTime(now);
  const day = new Date(`${current.date}T00:00:00Z`).getUTCDay();
  const monday = addDays(current.date, -((day + 6) % 7));
  const sunday = addDays(monday, 6);
  const week = lessons.filter(lesson => lesson.date >= monday && lesson.date <= sunday)
    .slice().sort((a, b) => a.date.localeCompare(b.date) || minutes(a.startTime) - minutes(b.startTime));
  const today = week.filter(lesson => lesson.date === current.date);
  const remaining = week.filter(lesson => lessonStatus(lesson, current) !== 'done');
  const ongoing = today.filter(lesson => lessonStatus(lesson, current) === 'ongoing');
  const next = ongoing[0] || remaining[0] || null;
  const remainingMinutes = remaining.reduce((sum, lesson) => sum + minutes(lesson.endTime) -
    (lessonStatus(lesson, current) === 'ongoing' ? current.minute : minutes(lesson.startTime)), 0);
  return { current, week, today, remaining, ongoing, next, remainingHours: remainingMinutes / 60,
    conflicts: remaining.filter(lesson => lesson.isConflict) };
}

export function timeUntil(lesson, now, ongoing = false) {
  const target = new Date(`${lesson.date}T${ongoing ? lesson.endTime : lesson.startTime}+07:00`);
  const total = Math.max(1, Math.ceil((target - now) / 60000));
  if (total >= 1440) return `${Math.floor(total / 1440)} ngày ${Math.floor(total % 1440 / 60)} giờ`;
  if (total >= 60) return `${Math.floor(total / 60)} giờ${total % 60 ? ` ${total % 60} phút` : ''}`;
  return `${total} phút`;
}
