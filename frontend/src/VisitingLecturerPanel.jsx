import React, { useEffect, useRef, useState } from 'react';
import { AlertTriangle, CheckCircle2, Eye, Plus, RefreshCw, Save, Trash2, UserPlus } from 'lucide-react';
import { addDays, clock, dateLabel, dayNames, planApi } from './schedule';
import { busyWindowsFromSlots, initialVisitingForm, visitingInput, validateVisitingPreview } from './visitingLecturer';
import './visitingLecturer.css';

const post = value => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value) });
const weekdays = [{ value: '', label: 'Một ngày' }, ...dayNames.map((label, i) => ({ value: i + 2, label }))];

export default function VisitingLecturerPanel({ detail, disabled, onBusy, onApplied, onDirtyChange }) {
  const [options, setOptions] = useState(null), [form, setForm] = useState(null);
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false), [error, setError] = useState(''), [message, setMessage] = useState('');
  const [reload, setReload] = useState(0), [preview, setPreview] = useState(null), [showProfile, setShowProfile] = useState(false);
  const [classQuery, setClassQuery] = useState(''), [classPage, setClassPage] = useState(0);
  const pendingVisitor = useRef(null), inputAtPreview = useRef(null);
  useEffect(() => {
    const controller = new AbortController(); let active = true; setLoading(true); setError(''); setPreview(null);
    planApi(`/${detail.plan.id}/visiting-options`, { signal: controller.signal }).then(data => {
      if (!active) return;
      if (data.revision !== detail.plan.revision || data.baseVersionId !== detail.draft.summary.id || !['lecturers', 'assignments', 'rooms', 'periods', 'classes', 'busySlots'].every(k => Array.isArray(data[k]))) throw new Error('Nguồn thỉnh giảng đã khác bản nháp đang mở. Mở lại bản lịch để đối chiếu.');
      const chosen = pendingVisitor.current;
      setOptions(data); setForm(previous => {
        if (!previous) return initialVisitingForm(detail, data);
        return chosen ? { ...previous, lecturerId: String(chosen), busySlots: [] } : previous;
      }); pendingVisitor.current = null;
    }).catch(e => { if (active && e.name !== 'AbortError') setError(e.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [detail.plan.id, detail.plan.revision, detail.draft.summary.id, reload]);
  function change(update) { setForm(previous => ({ ...previous, ...update })); setPreview(null); setMessage(''); onDirtyChange(true); }
  function setBusyState(value) { setBusy(value); onBusy(value); }
  function selectVisitor(id) { change({ lecturerId: id, busySlots: busyWindowsFromSlots(options.busySlots, id) }); }
  async function submit(event) {
    event.preventDefault(); if (busy || disabled || loading) return; setError(''); setMessage(''); setPreview(null);
    let input; try { input = visitingInput(detail, options, form); } catch (e) { setError(e.message); return; }
    setBusyState(true);
    try { const result = validateVisitingPreview(await planApi(`/${detail.plan.id}/visiting-lecturers/preview`, post(input)), input); inputAtPreview.current = input; setPreview(result); onDirtyChange(true); }
    catch (e) { setError(e.status === 409 ? 'Bản lịch hoặc danh mục đã thay đổi. Tải lại nguồn hoặc mở lại bản lịch rồi xem trước lại.' : e.message); }
    finally { setBusyState(false); }
  }
  async function apply() {
    if (!preview || busy || disabled) return; setBusyState(true); setError('');
    try { onApplied(await planApi(`/${detail.plan.id}/visiting-lecturers/commit`, post({ input: inputAtPreview.current, previewToken: preview.previewToken }))); }
    catch (e) { setError(e.status === 409 ? 'Dữ liệu đã thay đổi sau khi xem trước. Đầu vào vẫn được giữ; tải lại nguồn hoặc mở lại bản lịch và xem trước lại trước khi lưu.' : e.message); setPreview(null); }
    finally { setBusyState(false); }
  }
  const locked = disabled || busy || loading;
  const visible = (options?.classes || []).filter(c => `${c.courseCode} ${c.courseName} ${c.cohort} ${c.classGroup} ${c.lecturerCodes.join(' ')}`.toLocaleLowerCase('vi').includes(classQuery.toLocaleLowerCase('vi')));
  const page = Math.min(classPage, Math.max(0, Math.ceil(visible.length / 20) - 1));
  const selectedClasses = (options?.classes || []).filter(c => form?.classIds.includes(c.classId));
  const subjects = form?.action === 'NEW' ? [form.newClass] : selectedClasses.length ? selectedClasses : options?.classes || [];
  function editRow(key, index, field, value) { change({ [key]: form[key].map((row, i) => i === index ? { ...row, [field]: value } : row) }); }
  return <section className="catalog-panel visiting-panel" aria-label="Bổ sung thỉnh giảng"><div className="catalog-section-heading"><div><span className="catalog-eyebrow">THỈNH GIẢNG</span><h3><UserPlus size={19}/>Nhận lớp và bổ sung lịch</h3><p className="catalog-muted">Xem trước trên phiên bản {detail.draft.summary.number}. Lịch đang áp dụng được giữ đến khi xác nhận bản nháp mới.</p></div><button className="button" disabled={locked} onClick={() => setReload(v => v + 1)}><RefreshCw size={16}/>Tải lại nguồn thỉnh giảng</button></div>
    {loading && <p role="status">Đang tải nguồn thỉnh giảng…</p>}
    {error && <p className="catalog-error" role="alert"><AlertTriangle size={18}/>{error}</p>}
    {message && <p className="catalog-success" role="status">{message}</p>}
    {options && form && <><div className="catalog-toolbar-actions visiting-profile-toggle"><button className="button" disabled={locked} onClick={() => setShowProfile(v => !v)}><UserPlus size={16}/>{showProfile ? 'Đóng form hồ sơ mới' : 'Tạo hồ sơ thỉnh giảng và phân công'}</button></div>
      {showProfile && <ProfileForm detail={detail} subjects={subjects} disabled={locked} onBusy={setBusyState} onCreated={row => { pendingVisitor.current = row.id; setShowProfile(false); setMessage(`Đã lưu hồ sơ ${row.lecturerCode} và phân công môn. Chưa đổi lịch giảng dạy.`); setPreview(null); onDirtyChange(true); setReload(v => v + 1); }}/>}
      {!options.lecturers.length && <p className="catalog-callout">Chưa có thỉnh giảng đủ điều kiện trong khoa. Tạo hồ sơ và phân công môn ở phía trên trước khi xem trước.</p>}
      <form onSubmit={submit} aria-label="Đề xuất thỉnh giảng"><fieldset className="visiting-fields" disabled={locked}>
        <label>Giảng viên thỉnh giảng<select required aria-label="Giảng viên thỉnh giảng" value={form.lecturerId} onChange={e => selectVisitor(e.target.value)}><option value="">Chọn thỉnh giảng</option>{form.lecturerId && !options.lecturers.some(v => String(v.id) === form.lecturerId) && <option value={form.lecturerId}>GV đã chọn không còn đủ điều kiện</option>}{options.lecturers.map(v => <option key={v.id} value={v.id}>{v.lecturerCode} · {v.fullName}{v.accountTeacherId ? ` · ${v.accountTeacherId}` : ''}</option>)}</select></label>
        <label>Thao tác thỉnh giảng<select aria-label="Thao tác thỉnh giảng" value={form.action} onChange={e => change({ action: e.target.value, sessions: [] })}><option value="RECEIVE">Nhận/chuyển lớp đã có</option><option value="NEW">Bổ sung lớp/nhóm mới</option></select></label>
        {form.action === 'RECEIVE' ? <><label>Cách bố trí giờ thỉnh giảng<select aria-label="Cách bố trí giờ thỉnh giảng" value={form.timing} onChange={e => change({ timing: e.target.value })}><option value="KEEP">Giữ giờ và phòng của lớp hiện tại</option><option value="FIXED">Thay bằng lịch dạy cố định</option></select></label>
          <div className="visiting-wide"><h4>Lớp/nhóm chuyển cho thỉnh giảng ({form.classIds.length})</h4><p className="catalog-muted">Chuyển toàn bộ các buổi của lớp/nhóm được chọn. Các lớp/nhóm khác giữ nguyên.</p><label>Tìm lớp thỉnh giảng<input value={classQuery} onChange={e => { setClassQuery(e.target.value); setClassPage(0); }}/></label>
            <div className="visiting-class-grid">{visible.slice(page * 20, (page + 1) * 20).map(c => <label key={c.classId} className="catalog-pool-choice"><input type="checkbox" aria-label={`Nhận ${c.courseCode} ${c.classGroup} ${c.sessionType}`} checked={form.classIds.includes(c.classId)} onChange={() => change({ classIds: form.classIds.includes(c.classId) ? form.classIds.filter(id => id !== c.classId) : [...form.classIds, c.classId] })}/><span><strong>{c.courseCode} · {c.classGroup} · {c.sessionType === 'PRACTICE' ? 'TH' : 'LT'}</strong><small>{c.cohort} · {c.scheduledPeriods}/{c.requiredPeriods} tiết · GV hiện tại: {c.lecturerCodes.join(', ') || 'chưa phân công'}</small></span></label>)}</div>
            {visible.length > 20 && <div className="catalog-pagination"><span>Trang {page + 1}/{Math.ceil(visible.length / 20)}</span><div><button className="button" type="button" disabled={!page} onClick={() => setClassPage(v => v - 1)}>Lớp thỉnh giảng trang trước</button><button className="button" type="button" disabled={(page + 1) * 20 >= visible.length} onClick={() => setClassPage(v => v + 1)}>Lớp thỉnh giảng trang sau</button></div></div>}
          </div></> : <NewClassFields value={form.newClass} classes={options.classes} onChange={(key, value) => change({ newClass: { ...form.newClass, [key]: value } })}/>}
        {(form.action === 'NEW' || form.timing === 'FIXED') && <div className="visiting-wide"><h4>Lịch dạy cố định thay thế</h4><p className="catalog-muted">Bảng này thay toàn bộ giờ của lớp/nhóm được chọn. Tổng tiết không được vượt nhu cầu; phần còn thiếu được giữ trong nháp.</p>
          <div className="catalog-table-wrap"><table><caption>Buổi cố định của thỉnh giảng</caption><thead><tr>{['Lớp/nhóm', 'Từ ngày', 'Đến ngày', 'Thứ lặp', 'Phòng', 'Tiết đầu', 'Số tiết', 'Xóa'].map(s => <th key={s}>{s}</th>)}</tr></thead><tbody>{form.sessions.map((s, i) => <tr key={i}>
            <td><select aria-label={`Lớp cố định dòng ${i + 1}`} value={s.classId} onChange={e => editRow('sessions', i, 'classId', e.target.value)}>{form.action === 'NEW' ? <option value="$NEW">Lớp/nhóm mới</option> : <><option value="">Chọn lớp</option>{selectedClasses.map(c => <option key={c.classId} value={c.classId}>{c.courseCode} · {c.classGroup} · {c.sessionType === 'PRACTICE' ? 'TH' : 'LT'}</option>)}</>}</select></td>
            {['startDate', 'endDate'].map((key, j) => <td key={key}><input required type="date" aria-label={`${j ? 'Đến ngày' : 'Từ ngày'} cố định dòng ${i + 1}`} value={s[key]} onChange={e => editRow('sessions', i, key, e.target.value)}/></td>)}
            <td><select aria-label={`Thứ cố định dòng ${i + 1}`} value={s.dayOfWeek} onChange={e => editRow('sessions', i, 'dayOfWeek', e.target.value)}>{weekdays.map(d => <option key={d.value} value={d.value}>{d.label}</option>)}</select></td>
            <td><select aria-label={`Phòng cố định dòng ${i + 1}`} required value={s.roomId} onChange={e => editRow('sessions', i, 'roomId', e.target.value)}><option value="">Chọn phòng</option>{options.rooms.map(r => <option key={r.id} value={r.id}>{r.roomCode} · {r.isLab ? 'LAB' : 'LT'} · {r.capacity} chỗ</option>)}</select></td>
            {['startPeriod', 'numPeriods'].map((key, j) => <td key={key}><input required type="number" min={1} max={30} step={1} aria-label={`${j ? 'Số tiết' : 'Tiết đầu'} cố định dòng ${i + 1}`} value={s[key]} onChange={e => editRow('sessions', i, key, e.target.value)}/></td>)}
            <td><button className="button" type="button" aria-label={`Xóa buổi cố định dòng ${i + 1}`} onClick={() => change({ sessions: form.sessions.filter((_, index) => index !== i) })}><Trash2 size={15}/></button></td>
          </tr>)}</tbody></table></div><button className="button" type="button" disabled={form.sessions.length >= 500} onClick={() => change({ sessions: [...form.sessions, { classId: form.action === 'NEW' ? '$NEW' : form.classIds[0] || '', startDate: detail.plan.semesterStartDate, endDate: detail.plan.semesterStartDate, dayOfWeek: '', roomId: String(options.rooms[0]?.id || ''), startPeriod: 1, numPeriods: 3 }] })}><Plus size={16}/>Thêm dòng lịch cố định</button>
          <details><summary>Xem giờ tiết của bản lịch</summary><div className="catalog-period-grid">{options.periods.map(p => <span key={p.period}>Tiết {p.period}: {clock(p.start)}–{clock(p.end)}</span>)}</div></details>
        </div>}
        <div className="visiting-wide"><h4>Lịch bận ngoài trường của GV</h4><p className="catalog-muted">Danh sách này thay lịch bận của GV đã chọn trong bản nháp. Lịch bận chỉ chặn GV; các lịch bận đang áp dụng từ bản lịch khác vẫn được kiểm tra.</p>
          <div className="catalog-table-wrap"><table><caption>Khoảng bận của thỉnh giảng</caption><thead><tr>{['Từ ngày', 'Đến ngày', 'Thứ lặp', 'Từ giờ', 'Đến giờ', 'Lý do', 'Xóa'].map(s => <th key={s}>{s}</th>)}</tr></thead><tbody>{form.busySlots.map((b, i) => <tr key={i}>
            {['startDate', 'endDate'].map((key, j) => <td key={key}><input required type="date" aria-label={`${j ? 'Đến ngày' : 'Từ ngày'} bận dòng ${i + 1}`} value={b[key]} onChange={e => editRow('busySlots', i, key, e.target.value)}/></td>)}
            <td><select aria-label={`Thứ bận dòng ${i + 1}`} value={b.dayOfWeek} onChange={e => editRow('busySlots', i, 'dayOfWeek', e.target.value)}>{weekdays.map(d => <option key={d.value} value={d.value}>{d.label}</option>)}</select></td>
            {['startTime', 'endTime'].map((key, j) => <td key={key}><input required type="time" step={1} aria-label={`${j ? 'Đến giờ' : 'Từ giờ'} bận dòng ${i + 1}`} value={b[key]} onChange={e => editRow('busySlots', i, key, e.target.value)}/></td>)}
            <td><input maxLength={255} aria-label={`Lý do bận dòng ${i + 1}`} value={b.reason} onChange={e => editRow('busySlots', i, 'reason', e.target.value)}/></td><td><button type="button" className="button" aria-label={`Xóa lịch bận dòng ${i + 1}`} onClick={() => change({ busySlots: form.busySlots.filter((_, index) => index !== i) })}><Trash2 size={15}/></button></td>
          </tr>)}</tbody></table></div><button type="button" className="button" disabled={form.busySlots.length >= 100} onClick={() => change({ busySlots: [...form.busySlots, { startDate: detail.plan.semesterStartDate, endDate: detail.plan.semesterStartDate, dayOfWeek: '', startTime: '07:00', endTime: '09:00', reason: '' }] })}><Plus size={16}/>Thêm khoảng bận</button>
        </div>
      </fieldset><div className="catalog-actions"><button type="button" className="button" disabled={locked} onClick={() => { setForm(initialVisitingForm(detail, options)); setPreview(null); setError(''); onDirtyChange(false); }}>Khôi phục đầu vào thỉnh giảng</button><button className="button primary" disabled={locked || !form.lecturerId}><Eye size={16}/>{busy ? 'Đang kiểm tra thỉnh giảng…' : 'Xem trước thay đổi thỉnh giảng'}</button></div></form>
    </>}
    {preview && <div className="visiting-preview" aria-label="Đề xuất thỉnh giảng đã xem trước"><h3>{preview.canConfirm ? <CheckCircle2 size={19}/> : <AlertTriangle size={19}/>}So sánh trước khi lưu nháp</h3><p>{preview.lecturerCode} · {preview.complete ? 'Đủ khối lượng' : 'Còn thiếu tiết'} · {preview.conflicts.length} mục cần xử lý · {preview.unchangedEntries} buổi ngoài phạm vi giữ nguyên.</p>
      <div className="catalog-table-wrap"><table><caption>Khối lượng lớp/nhóm thay đổi</caption><thead><tr><th>Môn / lớp</th><th>GV trước</th><th>GV sau</th><th>Tiết trước</th><th>Tiết sau</th><th>Yêu cầu</th></tr></thead><tbody>{preview.changes.map(c => <tr key={c.classId}><th>{c.courseCode} · {c.classGroup} · {c.sessionType === 'PRACTICE' ? 'TH' : 'LT'}</th><td>{c.beforeLecturers.join(', ') || 'Lớp mới'}</td><td>{c.afterLecturer}</td><td>{c.beforePeriods}</td><td>{c.afterPeriods}</td><td>{c.requiredPeriods}</td></tr>)}</tbody></table></div>
      <PreviewSessions key={`before-${preview.previewToken}`} title="Buổi trước thay đổi" rows={preview.beforeEntries}/><PreviewSessions key={`after-${preview.previewToken}`} title="Buổi sau thay đổi" rows={preview.afterEntries}/>
      {!!preview.conflicts.length && <div className="plan-conflicts" role="status"><strong>Cần xử lý trước khi xác nhận</strong><ul>{preview.conflicts.map((c, i) => <li key={i}><b>{c.resource || c.type}</b>{c.date && ` · ${dateLabel(c.date)} ${clock(c.startTime)}–${clock(c.endTime)}`}<p>{c.message}</p></li>)}</ul></div>}
      <p className="catalog-callout">Có thể lưu bản nháp để tiếp tục xử lý thiếu tiết hoặc xung đột. Lịch cá nhân chỉ cập nhật sau khi xác nhận bản nháp hợp lệ.</p>
      <div className="catalog-actions"><button type="button" className="button primary" disabled={locked} onClick={apply}><Save size={16}/>Lưu đề xuất vào bản nháp</button></div>
    </div>}
  </section>;
}

function NewClassFields({ value, classes, onChange }) {
  const fields = [['courseCode', 'Mã môn lớp mới', 'text', 50], ['courseName', 'Tên môn lớp mới', 'text', 180], ['cohort', 'Khóa lớp mới', 'text', 50], ['classGroup', 'Tên lớp/nhóm mới', 'text', 100],
    ['studentCount', 'Sĩ số lớp mới', 'number', 1000], ['credits', 'Tín chỉ lớp mới', 'number', 30], ['totalPeriods', 'Tổng tiết lớp mới', 'number', 1000], ['studyStartDate', 'Ngày học đầu lớp mới', 'date'], ['studyEndDate', 'Ngày học cuối lớp mới', 'date']];
  const parents = classes.filter(c => c.sessionType === 'THEORY');
  return <div className="visiting-wide visiting-new-class"><h4>Nhu cầu lớp/nhóm mới</h4><div className="visiting-new-grid">{fields.map(([key, label, type, max]) => <label key={key}>{label}<input required type={type} aria-label={label} min={type === 'number' ? 1 : undefined} max={type === 'number' ? max : undefined} maxLength={type === 'text' ? max : undefined} value={value[key]} onChange={e => onChange(key, e.target.value)}/></label>)}
    <label>Loại lớp mới<select value={value.sessionType} onChange={e => onChange('sessionType', e.target.value)}><option value="THEORY">Lý thuyết</option><option value="PRACTICE">Thực hành</option></select></label>
    <label>Lớp cha của nhóm mới<select aria-label="Lớp cha của nhóm mới" disabled={value.sessionType !== 'PRACTICE'} value={value.parentClassId} onChange={e => onChange('parentClassId', e.target.value)}><option value="">Lớp/nhóm độc lập</option>{parents.map(c => <option key={c.classId} value={c.parentClassId}>{c.courseCode} / {c.cohort} · {c.classGroup}</option>)}</select></label></div></div>;
}

function ProfileForm({ detail, subjects, disabled, onBusy, onCreated }) {
  const [form, setForm] = useState({ lecturerCode: '', fullName: '', accountTeacherId: '', theoryWeight: 1, practiceWeight: 1 });
  const [excluded, setExcluded] = useState([]), [error, setError] = useState('');
  const unique = [...new Map(subjects.map(s => [JSON.stringify([s.courseCode, s.cohort]), s])).entries()];
  async function submit(event) {
    event.preventDefault(); setError(''); if (disabled) return;
    const assignments = unique.filter(([key]) => !excluded.includes(key)).map(([, c]) => ({ courseCode: c.courseCode, courseName: c.courseName, cohort: c.cohort, theoryWeight: Number(form.theoryWeight), practiceWeight: Number(form.practiceWeight) }));
    if (!assignments.length || assignments.length > 100) { setError('Chọn từ 1 đến 100 phân công môn.'); return; }
    onBusy(true);
    try { onCreated(await planApi(`/${detail.plan.id}/visiting-lecturers`, post({ revision: detail.plan.revision, lecturerCode: form.lecturerCode.trim(), fullName: form.fullName.trim(), accountTeacherId: form.accountTeacherId.trim() || null, assignments }))); }
    catch (e) { setError(e.message); } finally { onBusy(false); }
  }
  return <form className="catalog-editor" aria-label="Tạo hồ sơ thỉnh giảng" onSubmit={submit}><h4>Tạo hồ sơ và phân công cùng lúc</h4><fieldset disabled={disabled}>
    {[['lecturerCode', 'Mã GV thỉnh giảng mới', 50], ['fullName', 'Họ tên thỉnh giảng mới', 100], ['accountTeacherId', 'Mã tài khoản GV đã có', 50]].map(([key, label, max]) => <label key={key}>{label}<input required={key !== 'accountTeacherId'} maxLength={max} value={form[key]} onChange={e => setForm(p => ({ ...p, [key]: e.target.value }))}/></label>)}
    {['theoryWeight', 'practiceWeight'].map((key, i) => <label key={key}>Trọng số {i ? 'TH' : 'LT'} khi tạo hồ sơ<input type="number" min={0} max={10000} step={1} required value={form[key]} onChange={e => setForm(p => ({ ...p, [key]: e.target.value }))}/></label>)}
    <div className="visiting-wide"><p className="catalog-muted">Để trống mã tài khoản để tự tạo tên đăng nhập MGV, mật khẩu ban đầu là họ tên viết liền, chữ thường, không dấu + MGV. Nếu đã có tài khoản, liên kết đúng mã. Các môn bên dưới được lấy từ lớp đang chọn hoặc nhu cầu lớp mới.</p><div className="visiting-subjects">{unique.map(([key, c]) => <label className="catalog-checkbox" key={key}><input type="checkbox" checked={!excluded.includes(key)} onChange={() => setExcluded(p => p.includes(key) ? p.filter(k => k !== key) : [...p, key])}/>{c.courseCode} · {c.courseName} / {c.cohort}</label>)}</div></div>
  </fieldset>{error && <p role="alert" className="catalog-error">{error}</p>}<div className="catalog-actions"><button className="button primary" disabled={disabled}>Lưu hồ sơ và phân công thỉnh giảng</button></div></form>;
}

function PreviewSessions({ title, rows }) {
  const [page, setPage] = useState(0);
  return <details className="visiting-preview-sessions"><summary>{title} ({rows.length} buổi)</summary><div className="catalog-table-wrap"><table><caption>{title}</caption><thead><tr><th>Môn / lớp</th><th>GV</th><th>Ngày / giờ</th><th>Phòng</th><th>Tiết</th></tr></thead><tbody>{rows.slice(page * 25, (page + 1) * 25).map((s, i) => <tr key={i}><th>{s.courseCode} · {s.classGroup}</th><td>{s.lecturerCode}</td><td>{dateLabel(s.date)}<br/>{clock(s.startTime)}–{clock(s.endTime)}</td><td>{s.roomCode}</td><td>{s.startPeriod} · {s.numPeriods} tiết</td></tr>)}</tbody></table></div>{rows.length > 25 && <div className="catalog-pagination"><span>Trang {page + 1}/{Math.ceil(rows.length / 25)}</span><div><button type="button" className="button" disabled={!page} onClick={() => setPage(v => v - 1)}>Xem trước trang trước</button><button type="button" className="button" disabled={(page + 1) * 25 >= rows.length} onClick={() => setPage(v => v + 1)}>Xem trước trang sau</button></div></div>}</details>;
}
