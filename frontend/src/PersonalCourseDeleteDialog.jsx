import React, { useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, Lock, Trash2, X } from 'lucide-react';
import { api, dayNames } from './schedule';
import FormError from './FormError';

const dayLabel = day => dayNames[day === 8 ? 6 : day - 2] || `Thứ ${day}`;

export default function PersonalCourseDeleteDialog({ teacherId, onClose, onDeleted }) {
  const ref = useRef(null);
  const [courses, setCourses] = useState(null);
  const [checked, setChecked] = useState(() => new Set());
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const dialog = ref.current; const previous = document.activeElement;
    dialog.showModal();
    return () => { dialog.close(); previous?.focus(); };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    api(`/personal-courses?teacherId=${encodeURIComponent(teacherId)}`, { signal: controller.signal })
      .then(data => setCourses(Array.isArray(data) ? data : []))
      .catch(err => { if (err.name !== 'AbortError') { setCourses([]); setError(err.message); } });
    return () => controller.abort();
  }, [teacherId]);

  const groups = useMemo(() => {
    const map = new Map();
    for (const course of courses || []) {
      if (!map.has(course.schoolId)) map.set(course.schoolId, { id: course.schoolId, name: course.schoolName, items: [] });
      map.get(course.schoolId).items.push(course);
    }
    return [...map.values()];
  }, [courses]);

  const personalCount = (courses || []).filter(course => !course.departmentAssigned).length;
  const selected = (courses || []).filter(course => checked.has(course.id) && !course.departmentAssigned);

  function toggle(id) {
    setChecked(previous => { const next = new Set(previous); next.has(id) ? next.delete(id) : next.add(id); return next; });
  }

  function toggleGroup(group, on) {
    setChecked(previous => {
      const next = new Set(previous);
      group.items.filter(course => !course.departmentAssigned).forEach(course => on ? next.add(course.id) : next.delete(course.id));
      return next;
    });
  }

  async function confirmDelete() {
    setBusy(true); setError('');
    try {
      const result = await api('/personal-courses/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ teacherId, courseIds: selected.map(course => course.id) }),
      });
      onDeleted(result.message || `Đã xóa ${selected.length} môn cá nhân.`);
    } catch (err) {
      setError(err.message); setConfirming(false);
    } finally {
      setBusy(false);
    }
  }

  const describe = course => `${dayLabel(course.dayOfWeek)} · Tiết ${course.startPeriod}–${course.startPeriod + course.numPeriods - 1}${course.room ? ` · Phòng ${course.room}` : ''}`;

  return <dialog className="modal delete-course-modal" ref={ref} onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
    <div className="modal-head">
      <h2>{confirming ? 'Xác nhận xóa lịch' : 'Xóa lịch dạy cá nhân'}</h2>
      <button className="icon-button" aria-label="Đóng" disabled={busy} onClick={onClose}><X size={20}/></button>
    </div>
    <FormError message={error}/>
    {courses === null ? <p className="muted-text">Đang tải danh sách môn…</p> : confirming ? <>
      <div role="alert" className="import-warning"><AlertTriangle size={18}/><p>Bạn sắp xóa <strong>{selected.length}</strong> môn khỏi lịch cá nhân. Thao tác này không thể hoàn tác. Lịch do khoa phân vẫn được giữ nguyên.</p></div>
      <ul className="delete-confirm-list">{selected.map(course => <li key={course.id}><strong>{course.courseName}</strong><small>{course.schoolName} · {describe(course)}</small></li>)}</ul>
      <div className="import-actions">
        <button type="button" className="button" disabled={busy} onClick={() => setConfirming(false)}>Quay lại</button>
        <button type="button" className="button danger" disabled={busy} onClick={confirmDelete}><Trash2 size={16}/>{busy ? 'Đang xóa…' : 'Xác nhận xóa'}</button>
      </div>
    </> : <>
      <p className="muted-text">Tích chọn các môn bạn muốn xóa. Môn do khoa phân công được khóa và luôn được giữ lại.</p>
      {!personalCount && <p className="info-banner">Bạn chưa có lịch cá nhân để xóa.</p>}
      <div className="delete-course-list">
        {groups.map(group => {
          const deletable = group.items.filter(course => !course.departmentAssigned);
          const allOn = deletable.length > 0 && deletable.every(course => checked.has(course.id));
          return <section className="delete-course-group" key={group.id}>
            <label className="delete-course-row group-row">
              <input type="checkbox" disabled={!deletable.length} checked={allOn} onChange={event => toggleGroup(group, event.target.checked)}/>
              <span><strong>{group.name}</strong><small>{deletable.length ? `${deletable.length} môn cá nhân · chọn tất cả` : 'Chỉ có lịch khoa phân'}</small></span>
            </label>
            {group.items.map(course => <label key={course.id} className={`delete-course-row${course.departmentAssigned ? ' locked' : ''}`}>
              <input type="checkbox" disabled={course.departmentAssigned} checked={!course.departmentAssigned && checked.has(course.id)} onChange={() => toggle(course.id)}/>
              <span><strong>{course.courseName}</strong><small>{describe(course)}</small></span>
              {course.departmentAssigned && <span className="badge locked"><Lock size={11}/>Khoa phân – giữ nguyên</span>}
            </label>)}
          </section>;
        })}
      </div>
      <div className="import-actions">
        <button type="button" className="button" onClick={onClose}>Hủy</button>
        <button type="button" className="button danger" disabled={!selected.length} onClick={() => { setError(''); setConfirming(true); }}><Trash2 size={16}/>Xóa mục đã chọn ({selected.length})</button>
      </div>
    </>}
  </dialog>;
}
