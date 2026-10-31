import React, { useEffect, useRef, useState } from 'react';
import { AlertTriangle, CalendarDays, CheckCircle2, ChevronLeft, ChevronRight, Download, List, Lock, RefreshCw, Users, X } from 'lucide-react';
import { addDays, clock, currentWeek, dateLabel, dayNames, planApi } from './schedule';
import { boardDefaults, boardQuery, boardStateLabel, lecturerTypeLabel, restoreBoard, validateBoard } from './facultyBoard';
import './facultyBoard.css';

export default function FacultyScheduleBoard({ planId, versionId, disabled = false, onBusy, onAdjust }) {
  const key = `campus-board-${planId}-${versionId}`;
  const [filters, setFilters] = useState(() => { try { return restoreBoard(localStorage.getItem(key)); } catch { return { ...boardDefaults }; } });
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const [selected, setSelected] = useState(null);
  const [exporting, setExporting] = useState(false);
  const [exportMessage, setExportMessage] = useState('');
  const query = boardQuery(filters);
  useEffect(() => {
    const controller = new AbortController(); let active = true;
    setLoading(true); setError(''); setData(null); setSelected(null); setExportMessage('');
    planApi(`/${planId}/versions/${versionId}/overview?${query}`, { signal: controller.signal })
      .then(result => validateBoard(result, planId, versionId, filters)).then(result => { if (active) setData(result); })
      .catch(e => { if (active && e.name !== 'AbortError') setError(e.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [planId, versionId, query, revision]);
  useEffect(() => { try { localStorage.setItem(key, JSON.stringify(filters)); } catch { /* Board preferences are optional. */ } }, [key, filters]);
  function change(key, value) { setFilters(previous => ({ ...previous, [key]: value, page: 0 })); }
  async function download() {
    setExporting(true); onBusy?.(true); setError(''); setExportMessage('');
    try {
      const fresh = await planApi(`/${planId}/versions/${versionId}/overview?${boardQuery(filters, 'export')}`);
      validateBoard(fresh, planId, versionId, { ...filters, view: 'export' });
      const { facultyBoardWorkbook } = await import('./facultyBoardWorkbook');
      const { workbook, filename } = facultyBoardWorkbook(fresh);
      const url = URL.createObjectURL(new Blob([await workbook.xlsx.writeBuffer()], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }));
      const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
      setExportMessage(`Đã xuất phiên bản ${fresh.context.versionNumber} · ${boardStateLabel(fresh.context.state)} · tuần ${fresh.filters.week}: ${fresh.entries.length} buổi, ${fresh.assignments.length} lớp/nhóm.`);
    } catch (e) { setError(e.message); } finally { setExporting(false); onBusy?.(false); }
  }
  const locked = disabled || exporting;
  return <section className="faculty-board" aria-label="Bảng lịch toàn khoa">
    <div className="catalog-section-heading"><div><h3><CalendarDays size={19}/>Toàn khoa</h3><p className="catalog-muted">Lịch tuần và phân công của phiên bản đã lưu.</p></div><button className="button" disabled={locked || loading} onClick={() => setRevision(v => v + 1)}><RefreshCw size={15}/>Tải lại bảng toàn khoa</button></div>
    <div className="board-week-nav"><div className="catalog-toolbar-actions"><button className="button" aria-label="Toàn khoa tuần trước" disabled={locked || filters.week <= 1} onClick={() => change('week', filters.week - 1)}><ChevronLeft size={16}/></button>
      <label>Tuần toàn khoa<select aria-label="Tuần toàn khoa" value={filters.week} disabled={locked} onChange={e => change('week', Number(e.target.value))}>{Array.from({ length: 100 }, (_, i) => <option key={i} value={i + 1}>Tuần {i + 1}</option>)}</select></label>
      <button className="button" aria-label="Toàn khoa tuần sau" disabled={locked || filters.week >= 100} onClick={() => change('week', filters.week + 1)}><ChevronRight size={16}/></button>
      <button className="button" disabled={locked || !data} onClick={() => change('week', Math.min(100, currentWeek(data.context.semesterStartDate)))}>Tuần hiện tại</button></div>
      {data && <div className="board-context"><span className={`catalog-badge ${data.context.state === 'CONFIRMED' ? 'active' : 'visiting'}`}>{boardStateLabel(data.context.state)} · v{data.context.versionNumber}</span><span>{dateLabel(data.startDate)} – {dateLabel(data.endDate)}</span><small>{data.context.schoolName} · {data.context.departmentName} · {data.context.semester}</small><small>Cập nhật bản lịch: {new Date(data.context.updatedAt).toLocaleString('vi-VN')}</small></div>}
    </div>
    <div className="catalog-tabs" role="tablist" aria-label="Dạng bảng toàn khoa">{[['calendar', 'Lịch tuần toàn khoa', CalendarDays], ['assignments', 'Phân công toàn khoa', Users], ['sessions', 'Danh sách buổi', List]].map(([id, label, Icon]) => <button key={id} role="tab" aria-selected={filters.view === id} disabled={locked} onClick={() => change('view', id)}><Icon size={16}/>{label}</button>)}</div>
    <ul className="board-legend" aria-label="Chú giải bảng lịch"><li className="theory">LT · Lý thuyết</li><li className="practice">TH · Thực hành</li><li className="visiting">Thỉnh giảng</li><li className="conflict"><AlertTriangle size={14}/>Trùng lịch</li><li className="locked"><Lock size={13}/>Đã khóa</li></ul>
    <fieldset className="board-filters" disabled={locked}><legend>Bộ lọc toàn khoa</legend>
      <Choice label="Giảng viên" value={filters.lecturerId} options={data?.options.lecturers} onChange={v => change('lecturerId', v)}/>
      <Choice label="Môn" value={filters.courseCode} options={data?.options.courses} onChange={v => change('courseCode', v)}/>
      <Choice label="Khóa" value={filters.cohort} options={data?.options.cohorts} onChange={v => change('cohort', v)}/>
      <Choice label="Lớp/nhóm" value={filters.classId} options={data?.options.classes} onChange={v => change('classId', v)}/>
      <Choice label="Phòng" value={filters.roomCode} options={data?.options.rooms} onChange={v => change('roomCode', v)}/>
      <Choice label="LT/TH" value={filters.sessionType} options={[{ value: 'THEORY', label: 'Lý thuyết' }, { value: 'PRACTICE', label: 'Thực hành' }]} onChange={v => change('sessionType', v)}/>
      <Choice label="Loại giảng viên" value={filters.lecturerType} options={['STAFF', 'VISITING', 'UNKNOWN'].map(value => ({ value, label: lecturerTypeLabel(value) }))} onChange={v => change('lecturerType', v)}/>
      <label>Tìm môn, lớp hoặc GV<input aria-label="Tìm toàn khoa" maxLength={200} value={filters.q} onChange={e => change('q', e.target.value)}/></label>
      <label className="catalog-checkbox board-conflict-filter"><input type="checkbox" checked={filters.conflictsOnly} onChange={e => change('conflictsOnly', e.target.checked)}/>Chỉ buổi có xung đột</label>
      <button type="button" className="button" onClick={() => setFilters({ ...boardDefaults, week: filters.week, view: filters.view, groupBy: filters.groupBy })}>Xóa bộ lọc toàn khoa</button>
    </fieldset>
    {loading && <p role="status">Đang tải bảng toàn khoa…</p>}
    {error && <p className="catalog-error" role="alert"><AlertTriangle size={17}/>{error}<button className="button" disabled={locked} onClick={() => setRevision(v => v + 1)}>Thử tải bảng lại</button></p>}
    {data && <><div className="board-stats" aria-label="Số liệu toàn khoa"><Stat label="Buổi trong tuần" value={data.stats.sessions} tone="blue"/><Stat label="Tiết trong tuần" value={data.stats.weekPeriods} tone="teal"/><Stat label="Lớp/nhóm LT/TH" value={data.stats.classes} tone="violet"/><Stat label="Buổi có xung đột" value={data.stats.conflictingEntries} tone="amber"/></div>
      <p className="catalog-muted">Theo bộ lọc: {data.stats.lecturers} GV có buổi trong tuần. Tổng kỳ của lớp/nhóm đã chọn: {data.stats.scheduledPeriods}/{data.stats.requiredPeriods} tiết, thiếu {data.stats.missingPeriods} tiết.</p>
      {data.options.missingTypeSnapshot && <p className="catalog-callout">Phiên bản cũ chưa lưu loại giảng viên. Các GV này được đánh dấu “Chưa lưu loại GV”; tạo phiên bản mới để lưu đầy đủ.</p>}
      {data.warnings.map((w, i) => <p className="catalog-callout" key={i}><AlertTriangle size={15}/>{w}</p>)}
      {filters.view === 'calendar' && <><label className="board-group-choice">Hàng lịch theo<select aria-label="Hàng lịch theo" value={filters.groupBy} disabled={locked} onChange={e => change('groupBy', e.target.value)}><option value="lecturer">Giảng viên</option><option value="class">Lớp/nhóm</option></select></label><WeekCalendar data={data} onSelect={setSelected}/></>}
      {filters.view === 'assignments' && <AssignmentTable rows={data.assignments}/>}
      {filters.view === 'sessions' && <SessionTable rows={data.entries} count={data.totalRows} onSelect={setSelected}/>}
      {!data.totalRows && <div className="catalog-empty"><CalendarDays size={25}/><p>{filters.view === 'assignments' ? 'Không có phân công khớp bộ lọc.' : 'Tuần này không có buổi học khớp bộ lọc.'}</p></div>}
      {data.pageCount > 1 && <div className="catalog-pagination"><span>Trang {filters.page + 1}/{data.pageCount} · {data.totalRows} {filters.view === 'calendar' ? 'hàng lịch, tối đa 10 hàng/trang' : filters.view === 'assignments' ? 'lớp/nhóm, 25 dòng/trang' : 'buổi, 25 buổi/trang'}</span><div><button className="button" disabled={locked || filters.page <= 0} onClick={() => setFilters(p => ({ ...p, page: p.page - 1 }))}>{filters.view === 'sessions' ? 'Buổi học trang trước' : 'Toàn khoa trang trước'}</button><button className="button" disabled={locked || filters.page + 1 >= data.pageCount} onClick={() => setFilters(p => ({ ...p, page: p.page + 1 }))}>{filters.view === 'sessions' ? 'Buổi học trang sau' : 'Toàn khoa trang sau'}</button></div></div>}
      <div className="board-footer"><small>Đối chiếu lịch đang áp dụng lúc {new Date(data.context.checkedAt).toLocaleString('vi-VN')}. Khi xác nhận, máy chủ kiểm tra lại toàn bộ bản nháp.</small><button className="button" disabled={locked || loading || (!data.stats.sessions && !data.stats.classes)} onClick={download}><Download size={16}/>{exporting ? 'Đang xuất toàn khoa…' : 'Xuất toàn khoa theo bộ lọc'}</button></div>
    </>}
    {exportMessage && <p className="catalog-success" role="status"><CheckCircle2 size={16}/>{exportMessage}</p>}
    {selected && <EntryDetail entry={selected} warnings={data?.warnings || []} onClose={() => setSelected(null)} onAdjust={onAdjust}/>}
  </section>;
}

function Choice({ label, options = [], value, onChange }) {
  return <label>{label}<select aria-label={`Lọc ${label.toLowerCase()} toàn khoa`} value={value} onChange={e => onChange(e.target.value)}><option value="">Tất cả</option>{value && !options.some(o => o.value === value) && <option value={value}>{value}</option>}{options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select></label>;
}
function Stat({ label, value, tone }) { return <div className={`catalog-stat ${tone}`}><span>{label}</span><strong>{value}</strong></div>; }
function Lesson({ entry: e, onSelect }) {
  return <button className={`board-lesson ${e.sessionType === 'PRACTICE' ? 'practice' : 'theory'} ${e.conflicts.length ? 'conflict' : ''}`} onClick={() => onSelect(e)} aria-label={`Chi tiết ${e.courseCode} ${e.classGroup} ${dateLabel(e.date)} ${clock(e.startTime)}`}>
    <strong>{clock(e.startTime)}–{clock(e.endTime)} <span>{e.sessionType === 'PRACTICE' ? 'TH' : 'LT'}</span></strong><b>{e.courseCode} · {e.courseName}</b><span>{e.classGroup} · {e.cohort}</span><span>{e.lecturerCode} · Phòng {e.roomCode}</span><small>Tiết {e.startPeriod}–{e.startPeriod + e.numPeriods - 1}</small>
    {!!e.conflicts.length && <span className="board-conflict-label"><AlertTriangle size={13}/>Trùng lịch</span>}
    {e.lecturerType === 'VISITING' && <span className="board-visiting">Thỉnh giảng</span>}
    {e.locked && <span className="board-locked"><Lock size={12}/>Đã khóa</span>}
  </button>;
}
function WeekCalendar({ data, onSelect }) {
  const days = Array.from({ length: 7 }, (_, i) => addDays(data.startDate, i));
  return <><div className="board-calendar board-desktop"><table><caption>Lịch tuần toàn khoa · tuần {data.filters.week}</caption><thead><tr><th>{data.filters.groupBy === 'class' ? 'Lớp/nhóm' : 'Giảng viên'}</th>{days.map((d, i) => <th key={d}>{dayNames[i]}<small>{dateLabel(d)}</small></th>)}</tr></thead><tbody>{data.groups.map(g => <tr key={g.id}><th scope="row">{g.label}<small>{g.subtitle === 'STAFF' || g.subtitle === 'VISITING' || g.subtitle === 'UNKNOWN' ? lecturerTypeLabel(g.subtitle) : g.subtitle}</small>{g.truncated && <small className="board-truncated">Hiện 200/{g.totalEntries} buổi. Lọc hẹp hơn hoặc dùng danh sách.</small>}</th>{days.map(d => <td key={d}>{g.entries.filter(e => e.date === d).map(e => <Lesson key={e.id} entry={e} onSelect={onSelect}/>)}</td>)}</tr>)}</tbody></table></div>
    <div className="board-mobile">{data.groups.map(g => <section className="board-mobile-group" key={g.id}><h4>{g.label}</h4>{g.truncated && <p>Hiện 200/{g.totalEntries} buổi. Lọc hẹp hơn hoặc dùng danh sách.</p>}{days.filter(d => g.entries.some(e => e.date === d)).map(d => <div key={d}><p className="board-date">{dayNames[days.indexOf(d)]} · {dateLabel(d)}</p>{g.entries.filter(e => e.date === d).map(e => <Lesson key={e.id} entry={e} onSelect={onSelect}/>)}</div>)}</section>)}</div></>;
}
function AssignmentTable({ rows }) {
  return <div className="catalog-table-wrap board-assignment-table"><table><caption>Phân công toàn khoa — tổng kỳ và phần tuần theo bộ lọc</caption><thead><tr>{['Môn / lớp', 'GV / loại', 'LT/TH', 'Yêu cầu tổng kỳ', 'Đã xếp tổng kỳ', 'Thiếu tổng kỳ', 'Tiết tuần', 'Buổi tuần', 'Buổi trùng'].map(h => <th key={h}>{h}</th>)}</tr></thead><tbody>{rows.map(a => <tr key={a.classId}><th>{a.courseCode} · {a.courseName}<small>{a.cohort} / {a.classGroup}</small></th><td>{a.lecturerCode} · {a.lecturerName}<small>{lecturerTypeLabel(a.lecturerType)}</small></td><td><span className={`catalog-badge ${a.sessionType === 'PRACTICE' ? 'active' : 'staff'}`}>{a.sessionType === 'PRACTICE' ? 'TH' : 'LT'}</span></td><td>{a.requiredPeriods}</td><td>{a.scheduledPeriods}</td><td><span className={a.missingPeriods ? 'board-missing' : ''}>{a.missingPeriods}</span></td><td>{a.weekPeriods}</td><td>{a.weekSessions}</td><td>{a.conflictingEntries}</td></tr>)}</tbody></table></div>;
}
function SessionTable({ rows, count, onSelect }) {
  return <div className="catalog-table-wrap board-session-table"><table><caption>Buổi học của phiên bản đã lưu ({count} buổi theo tuần và bộ lọc)</caption><thead><tr><th>Ngày / giờ</th><th>Môn / lớp</th><th>Giảng viên</th><th>Phòng</th><th>Loại / tiết</th><th>Chi tiết</th></tr></thead><tbody>{rows.map(e => <tr key={e.id}><th>{dateLabel(e.date)}<br/>{clock(e.startTime)}–{clock(e.endTime)}</th><td>{e.courseCode} · {e.courseName}<small>{e.cohort} / {e.classGroup}</small></td><td>{e.lecturerCode} · {e.lecturerName}</td><td>{e.roomCode}</td><td>{e.sessionType === 'PRACTICE' ? 'TH' : 'LT'} · {e.numPeriods} tiết</td><td><button className="button" onClick={() => onSelect(e)}>{e.conflicts.length ? 'Trùng lịch · Chi tiết' : 'Chi tiết buổi'}</button></td></tr>)}</tbody></table></div>;
}
function EntryDetail({ entry: e, warnings, onClose, onAdjust }) {
  const close = useRef(null);
  useEffect(() => { const previous = document.activeElement; close.current?.focus(); return () => { if (previous?.isConnected) previous.focus(); }; }, []);
  return <div className="board-dialog-backdrop" onClick={event => { if (event.target === event.currentTarget) onClose(); }}><section className="board-dialog" role="dialog" aria-modal="true" aria-labelledby="board-lesson-title" onKeyDown={event => { if (event.key === 'Escape') onClose(); if (event.key === 'Tab') { const buttons = [...event.currentTarget.querySelectorAll('button:not(:disabled)')]; const first = buttons[0], last = buttons[buttons.length - 1]; if (event.shiftKey && document.activeElement === first || !event.shiftKey && document.activeElement === last) { event.preventDefault(); (event.shiftKey ? last : first)?.focus(); } } }}>
    <div className="catalog-section-heading"><h3 id="board-lesson-title">Chi tiết buổi · {e.courseCode}</h3><button ref={close} className="button" aria-label="Đóng chi tiết buổi" onClick={onClose}><X size={18}/></button></div>
    <h4>{e.courseName}</h4><dl>{[['Ngày', dateLabel(e.date)], ['Giờ thực tế', `${clock(e.startTime)}–${clock(e.endTime)}`], ['Tiết', `${e.startPeriod}–${e.startPeriod + e.numPeriods - 1} (${e.numPeriods} tiết)`], ['Giảng viên', `${e.lecturerCode} · ${e.lecturerName}`], ['Loại GV', lecturerTypeLabel(e.lecturerType)], ['Lớp / khóa', `${e.classGroup} / ${e.cohort}`], ['Phòng / sĩ số', `${e.roomCode} / ${e.studentCount}`], ['LT/TH', e.sessionType === 'PRACTICE' ? 'Thực hành' : 'Lý thuyết']].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
    {warnings.map((w, i) => <p key={i} className="catalog-callout">{w}</p>)}
    <p><span className="board-locked">{e.locked ? 'Đã khóa' : 'Buổi đang mở'}</span></p>
    {onAdjust && <div className="catalog-actions">{[['MOVE', 'Sửa buổi này'], ['LOCK', e.locked ? 'Mở khóa buổi này' : 'Khóa buổi này'], ['AUTO', 'Xếp lại buổi này']].map(([action, label]) => <button key={action} className="button" disabled={e.locked && action !== 'LOCK'} onClick={() => { onClose(); onAdjust({ action, entry: e }); }}>{label}</button>)}</div>}
    {e.conflicts.length ? <div className="plan-conflicts"><strong>Xung đột tại thời điểm tải bảng</strong><ul>{e.conflicts.map(c => <li key={c.type}>{c.message}</li>)}</ul></div> : <p className={warnings.length ? 'catalog-callout' : 'catalog-success'}>Chưa phát hiện xung đột thời gian của buổi này trong dữ liệu đã đối chiếu.</p>}
  </section></div>;
}
