import React, { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { AlertTriangle } from 'lucide-react';
import { clock, dateLabel, minutes } from './schedule.js';

export default function CalendarLesson({ lesson, start, color, onSelect }) {
  const button = useRef(null);
  const popup = useRef(null);
  const closeTimer = useRef(null);
  const openFrame = useRef(null);
  const hovered = useRef(false);
  const focused = useRef(false);
  const tooltipId = useId();
  const [width, setWidth] = useState(Infinity);
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState(null);
  const height = Math.max(2, (minutes(lesson.endTime) - minutes(lesson.startTime)) * 1.1 - 4);
  const compact = height < 100;
  const narrow = width < 120;
  const tiny = height < 36 || (narrow && height < 48);
  const titleLines = compact ? Math.max(1, Math.min(2, Math.floor((height - 10 - (narrow ? 24 : 12)) / 13))) : Math.max(1, Math.min(6, Math.floor((height - (narrow ? 73 : 61)) / 15)));
  const time = `${clock(lesson.startTime)} – ${clock(lesson.endTime)}`;
  const periods = lesson.startPeriod === lesson.endPeriod ? `Tiết ${lesson.startPeriod}` : `Tiết ${lesson.startPeriod}–${lesson.endPeriod}`;
  const warning = lesson.conflictReason || 'Buổi dạy bị trùng giờ.';

  useLayoutEffect(() => {
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width));
    observer.observe(button.current);
    return () => observer.disconnect();
  }, []);

  useLayoutEffect(() => {
    if (!open) return;
    const card = button.current.getBoundingClientRect();
    const detail = popup.current.getBoundingClientRect();
    const left = Math.max(12, Math.min(card.left, window.innerWidth - detail.width - 12));
    const below = card.bottom + 8;
    const top = below + detail.height <= window.innerHeight - 12 ? below : Math.max(12, card.top - detail.height - 8);
    setPosition({ left, top });
  }, [open, lesson]);

  useEffect(() => {
    if (!open) return;
    function dismiss(event) {
      if (event?.type === 'scroll' && event.target instanceof Node && popup.current?.contains(event.target)) return;
      cancelAnimationFrame(openFrame.current);
      setOpen(false);
    }
    function escape(event) {
      if (event.key === 'Escape') { event.preventDefault(); dismiss(); }
    }
    window.addEventListener('scroll', dismiss, true);
    window.addEventListener('resize', dismiss);
    document.addEventListener('keydown', escape);
    return () => {
      window.removeEventListener('scroll', dismiss, true);
      window.removeEventListener('resize', dismiss);
      document.removeEventListener('keydown', escape);
    };
  }, [open]);

  useEffect(() => () => { clearTimeout(closeTimer.current); cancelAnimationFrame(openFrame.current); }, []);

  function show() {
    clearTimeout(closeTimer.current);
    if (!open) { setPosition(null); setOpen(true); }
  }
  function showAfterScroll() {
    cancelAnimationFrame(openFrame.current);
    // Focusing or hovering an off-screen card first scrolls its container into view.
    // Open after those scroll events so they do not immediately dismiss the popup.
    openFrame.current = requestAnimationFrame(() => {
      openFrame.current = requestAnimationFrame(() => {
        if ((hovered.current || focused.current) && !document.querySelector('dialog[open]')) show();
      });
    });
  }
  function leave() {
    hovered.current = false;
    clearTimeout(closeTimer.current);
    closeTimer.current = setTimeout(() => {
      if (!hovered.current && !focused.current) setOpen(false);
    }, 160);
  }
  function enter(event) {
    if (event.pointerType === 'touch') return;
    hovered.current = true;
    if (open) clearTimeout(closeTimer.current);
    else showAfterScroll();
  }

  return <>
    <button
      ref={button}
      type="button"
      className={`session-card imported-session lesson-card${compact ? ' is-compact' : ''}${narrow ? ' is-narrow' : ''}${tiny ? ' is-tiny' : ''}${lesson.isConflict ? ' has-conflict' : ''}`}
      style={{ top: (minutes(lesson.startTime) - start) * 1.1 + 2, height, left: `calc(${lesson.lane * 100 / lesson.lanes}% + 3px)`, width: `calc(${100 / lesson.lanes}% - 6px)`, right: 'auto', '--school-color': color, '--lesson-title-lines': titleLines }}
      aria-label={`${time} · ${lesson.courseName} · ${lesson.schoolName} · Phòng ${lesson.room} · ${periods}${lesson.isConflict ? ` · Trùng giờ: ${warning}` : ''}. Bấm để xem chi tiết.`}
      aria-describedby={open ? tooltipId : undefined}
      onPointerEnter={enter}
      onPointerLeave={leave}
      onFocus={() => {
        focused.current = true;
        button.current.scrollIntoView({ block: 'nearest', inline: 'nearest' });
        showAfterScroll();
      }}
      onBlur={() => {
        focused.current = false;
        hovered.current = false;
        clearTimeout(closeTimer.current);
        cancelAnimationFrame(openFrame.current);
        setOpen(false);
      }}
      onClick={() => { clearTimeout(closeTimer.current); cancelAnimationFrame(openFrame.current); setOpen(false); onSelect(lesson); }}
    >
      <span className="lesson-card-meta"><span className="session-time">{time}</span><span className="lesson-card-room">{lesson.room}</span></span>
      <strong className="lesson-card-title">{lesson.courseName}</strong>
      <span className="session-code lesson-card-school">{lesson.schoolName}</span>
      <span className="session-room lesson-card-periods">{periods}</span>
      {lesson.isConflict && <AlertTriangle className="lesson-conflict-icon" size={13} aria-hidden="true"/>}
    </button>
    {open && createPortal(<div
      ref={popup}
      id={tooltipId}
      role="tooltip"
      className="lesson-tooltip"
      style={{ left: position?.left ?? 12, top: position?.top ?? 12, visibility: position ? 'visible' : 'hidden', '--school-color': color }}
      onPointerEnter={enter}
      onPointerLeave={leave}
    >
      <strong>{lesson.courseName}</strong>
      <p className="lesson-tooltip-school">{lesson.schoolName}</p>
      <dl>
        <div><dt>Ngày</dt><dd>{dateLabel(lesson.date)}</dd></div>
        <div><dt>Giờ học</dt><dd>{time}</dd></div>
        <div><dt>Tiết</dt><dd>{periods.replace('Tiết ', '')}</dd></div>
        <div><dt>Phòng</dt><dd>{lesson.room}</dd></div>
      </dl>
      {lesson.isConflict && <p className="lesson-tooltip-warning"><AlertTriangle size={14} aria-hidden="true"/>{warning}</p>}
      <p className="lesson-tooltip-hint">Bấm vào buổi dạy để mở chi tiết</p>
    </div>, document.body)}
  </>;
}
