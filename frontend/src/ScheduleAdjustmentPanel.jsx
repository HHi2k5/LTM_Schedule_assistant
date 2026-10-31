import React, { useEffect, useRef, useState } from 'react';
import { planApi, dateLabel, addDays, clock } from './schedule';
import { adjustmentActions, adjustmentInput, validateAdjustmentPreview } from './scheduleAdjustment';
import './scheduleAdjustment.css';

const post = value => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value) });
const toggle = (values, value) => values.includes(value) ? values.filter(v => v !== value) : [...values, value];
export default function ScheduleAdjustmentPanel({ detail, seed, disabled, onBusy, onApplied, onDirtyChange }) {
  const { plan, draft } = detail;
  const [source, setSource] = useState(null), [error, setError] = useState(''), [working, setWorking] = useState(false);
  const [form, setForm] = useState({ action: seed?.action || 'MOVE', scope: 'entries', entryIds: seed?.entry ? [seed.entry.id] : [], classIds: [], locked: !seed?.entry?.locked, date: seed?.entry?.date || plan.semesterStartDate, roomId: '', startPeriod: seed?.entry?.startPeriod || 1, numPeriods: seed?.entry?.numPeriods || 3, lecturerId: '', fromDate: plan.semesterStartDate, toDate: draft.courses?.reduce((v, c) => c.studyEndDate > v ? c.studyEndDate : v, plan.semesterStartDate) || addDays(plan.semesterStartDate, 90), roomIds: [], restoreVersionId: seed?.restoreVersionId || '', overrideLocks: false, reason: '' });
  const [page, setPage] = useState(0), [query, setQuery] = useState(''), [list, setList] = useState(null), [loading, setLoading] = useState(false);
  const [classPage, setClassPage] = useState(0), [classQuery, setClassQuery] = useState('');
  const [proposal, setProposal] = useState(null), [diffPage, setDiffPage] = useState(0);
  const heading = useRef(null);
  useEffect(() => {
    heading.current?.focus(); const controller = new AbortController();
    planApi(`/${plan.id}/adjustment-options`, { signal: controller.signal }).then(s => {
      if (s.revision !== plan.revision || s.baseVersionId !== draft.summary.id) throw new Error('Nguồn điều chỉnh đã cũ. Mở lại bản lịch.');
      setSource(s); setForm(f => ({ ...f, roomIds: (draft.configuration?.roomIds || s.rooms.map(r => r.id)).filter(id => s.rooms.some(r => r.id === id)), roomId: seed?.entry ? s.rooms.find(r => r.roomCode === seed.entry.roomCode)?.id || '' : s.rooms[0]?.id || '' }));
    }).catch(e => { if (e.name !== 'AbortError') setError(e.message); });
    return () => controller.abort();
  }, [plan.id, plan.revision, draft, seed]);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setList(null);
    planApi(`/${plan.id}/adjustment-entries?baseVersionId=${draft.summary.id}&page=${page}&q=${encodeURIComponent(query)}`, { signal: controller.signal }).then(setList).catch(e => { if (e.name !== 'AbortError') setError(e.message); }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [plan.id, draft.summary.id, page, query]);
  function change(key, value) { setForm(f => ({ ...f, [key]: value })); setProposal(null); setError(''); onDirtyChange(true); }
  const field = key => ({ value: form[key], onChange: e => change(key, e.target.value) });
  function pickEntry(row) {
    if (form.action === 'MOVE') setForm(f => ({ ...f, entryIds: [row.id], date: row.date, roomId: source.rooms.find(r => r.roomCode === row.roomCode)?.id || '', startPeriod: row.startPeriod, numPeriods: row.numPeriods }));
    else setForm(f => ({ ...f, entryIds: toggle(f.entryIds, row.id) }));
    setProposal(null); onDirtyChange(true);
  }
  async function preview(e) {
    e.preventDefault(); setError(''); setProposal(null);
    try {
      const input = adjustmentInput(detail, source, form); setWorking(true); onBusy(true); onDirtyChange(true);
      const result = validateAdjustmentPreview(await planApi(`/${plan.id}/adjustments/preview`, post(input)), input);
      setProposal({ input, result }); setDiffPage(0);
    } catch (e) { setError(e.status === 409 ? `${e.message} Mở lại bản lịch để lấy nguồn mới; đầu vào được giữ tại đây.` : e.message); }
    finally { setWorking(false); onBusy(false); }
  }
  async function apply() {
    if (!proposal?.result.canApply || working) return; setWorking(true); onBusy(true); setError('');
    try { onApplied(await planApi(`/${plan.id}/adjustments/commit`, post({ input: proposal.input, previewToken: proposal.result.previewToken }))); }
    catch (e) { setProposal(null); setError(e.status === 409 ? `${e.message} Đầu vào được giữ; cần mở lại bản lịch và xem trước lại.` : e.message); }
    finally { setWorking(false); onBusy(false); }
  }
  const locked = disabled || working, action = form.action;
  const classScope = ['ADD', 'REASSIGN'].includes(action) || ['LOCK', 'AUTO'].includes(action) && form.scope === 'classes';
  const entryScope = ['MOVE', 'DELETE'].includes(action) || ['LOCK', 'AUTO'].includes(action) && !classScope;
  const classes = (source?.classes || []).filter(c => `${c.courseCode} ${c.courseName} ${c.classGroup}`.toLowerCase().includes(classQuery.toLowerCase()));
  return <section className="catalog-panel adjustment-panel" aria-label="Điều chỉnh bản nháp"><h3 ref={heading} tabIndex={-1}>Điều chỉnh bản nháp</h3><p>Lưu điều chỉnh sẽ tạo phiên bản mới. Lịch đang áp dụng chỉ thay đổi sau khi xác nhận bản nháp.</p>
    {!source && !error && <p role="status">Đang lấy nguồn điều chỉnh…</p>}
    <form onSubmit={preview}><fieldset disabled={locked || !source}><legend>Phạm vi và thao tác</legend>
      <label>Thao tác điều chỉnh<select {...field('action')} onChange={e => { change('action', e.target.value); setClassPage(0); }}>{Object.entries(adjustmentActions).map(([key, name]) => <option key={key} value={key}>{name}</option>)}</select></label>
      {['LOCK', 'AUTO'].includes(action) && <label>Chọn phạm vi<select {...field('scope')}><option value="entries">Các buổi được chọn</option><option value="classes">Toàn lớp/nhóm được chọn</option></select></label>}
      {entryScope && <div className="adjustment-selection"><label>Tìm buổi để điều chỉnh<input value={query} maxLength={200} onChange={e => { setQuery(e.target.value); setPage(0); }}/></label><p>Đã chọn {form.entryIds.length} buổi. Lựa chọn được giữ khi đổi trang.</p><button className="button" type="button" onClick={() => change('entryIds', [])}>Bỏ chọn các buổi</button>
        {loading && <p role="status">Đang tải buổi…</p>}<div className="catalog-table-wrap"><table><caption>Buổi trong bản nháp mới nhất</caption><thead><tr><th>Chọn</th><th>Ngày / tiết</th><th>Môn / lớp</th><th>GV / phòng</th><th>Khóa</th></tr></thead><tbody>{list?.rows.map(r => <tr key={r.id}><td><input aria-label={`Chọn buổi ${r.id}`} type={action === 'MOVE' ? 'radio' : 'checkbox'} checked={form.entryIds.includes(r.id)} onChange={() => pickEntry(r)}/></td><td>{dateLabel(r.date)} · {r.startPeriod}–{r.startPeriod + r.numPeriods - 1}</td><td>{r.courseCode} · {r.classGroup}</td><td>{r.lecturerName} / {r.roomCode}</td><td>{r.locked ? 'Đã khóa' : 'Đang mở'}</td></tr>)}</tbody></table></div>
        <Pager page={page} count={list?.pageCount || 0} disabled={locked || loading} onChange={setPage} name="Buổi điều chỉnh"/>
      </div>}
      {classScope && <div className="adjustment-selection"><label>Tìm lớp để điều chỉnh<input value={classQuery} onChange={e => { setClassQuery(e.target.value); setClassPage(0); }}/></label><p>Đã chọn {form.classIds.length} lớp/nhóm. Đổi GV áp dụng toàn bộ buổi của lớp/nhóm.</p><button className="button" type="button" onClick={() => change('classIds', [])}>Bỏ chọn các lớp</button>
        {classes.slice(classPage * 20, classPage * 20 + 20).map(c => <label className="catalog-checkbox" key={c.classId}><input type={action === 'ADD' ? 'radio' : 'checkbox'} checked={form.classIds.includes(c.classId)} onChange={() => change('classIds', action === 'ADD' ? [c.classId] : toggle(form.classIds, c.classId))}/>{c.courseCode} · {c.classGroup} · {c.sessionType === 'PRACTICE' ? 'TH' : 'LT'} · {c.scheduledPeriods}/{c.requiredPeriods} tiết</label>)}
        <Pager page={classPage} count={Math.ceil(classes.length / 20)} disabled={locked} onChange={setClassPage} name="Lớp điều chỉnh"/>
      </div>}
      {['MOVE', 'ADD'].includes(action) && <div className="adjustment-fields"><label>Ngày buổi học<input type="date" required {...field('date')}/></label><label>Phòng buổi học<select required {...field('roomId')}><option value="">Chọn phòng</option>{source?.rooms.map(r => <option key={r.id} value={r.id}>{r.roomCode} · {r.capacity} chỗ</option>)}</select></label><label>Tiết bắt đầu<input type="number" min="1" max="30" required {...field('startPeriod')}/></label><label>Số tiết buổi<input type="number" min="1" max="30" required {...field('numPeriods')}/></label></div>}
      {action === 'DELETE' && <p className="catalog-callout">Bỏ buổi vẫn giữ tổng tiết yêu cầu của lớp. Bản nháp thiếu tiết chưa thể xác nhận.</p>}
      {['REASSIGN', 'ADD'].includes(action) && <label>Giảng viên nhận lớp<select {...field('lecturerId')}><option value="">{action === 'ADD' ? 'Giữ GV hiện tại; chọn nếu lớp chưa có GV' : 'Chọn GV có phân công phù hợp'}</option>{source?.lecturers.map(l => <option key={l.id} value={l.id}>{l.lecturerCode} · {l.fullName}</option>)}</select></label>}
      {action === 'LOCK' && <label>Trạng thái buổi<select value={String(form.locked)} onChange={e => change('locked', e.target.value === 'true')}><option value="true">Khóa buổi đã chọn</option><option value="false">Mở khóa buổi đã chọn</option></select></label>}
      {action === 'AUTO' && <><p className="catalog-callout">Giữ buổi khóa và mọi buổi ngoài phạm vi. Chọn toàn lớp sẽ bổ sung cả tiết còn thiếu. Thuật toán tìm lịch Thứ Hai–Thứ Bảy; nếu không tìm được phương án sẽ giữ bản nháp cũ.</p><div className="adjustment-fields"><label>Xếp lại từ ngày<input type="date" required {...field('fromDate')}/></label><label>Xếp lại đến ngày<input type="date" required {...field('toDate')}/></label></div><div>Phòng cho phép xếp lại{source?.rooms.map(r => <label className="catalog-checkbox" key={r.id}><input type="checkbox" checked={form.roomIds.includes(r.id)} onChange={() => change('roomIds', toggle(form.roomIds, r.id))}/>{r.roomCode} · {r.capacity} chỗ</label>)}</div></>}
      {action === 'RESTORE' && <><p className="catalog-callout">Khôi phục toàn bộ phiên bản: buổi học, phân công, nhu cầu, lịch bận và trạng thái khóa. Hệ thống tạo nháp mới, kiểm tra lại danh mục và xung đột hiện tại.</p><label>Phiên bản cần khôi phục<select required {...field('restoreVersionId')}><option value="">Chọn phiên bản lịch sử</option>{detail.versions.filter(v => v.id !== draft.summary.id).map(v => <option key={v.id} value={v.id}>Phiên bản {v.number}{v.currentConfirmed ? ' · đang áp dụng' : ''}</option>)}</select></label><label className="catalog-checkbox"><input type="checkbox" checked={form.overrideLocks} onChange={e => change('overrideLocks', e.target.checked)}/>Cho phép khôi phục cả những buổi hiện đang khóa</label></>}
      <label>Lý do điều chỉnh<input maxLength={255} {...field('reason')}/></label><button className="button primary" disabled={!source || locked}>Xem trước điều chỉnh</button>
    </fieldset></form>
    {error && <p className="catalog-error" role="alert">{error}</p>}
    {proposal && <div className="adjustment-preview" aria-label="So sánh điều chỉnh"><h4>Trước và sau điều chỉnh</h4><p role="status">{proposal.result.message}</p><p>Nhu cầu: {proposal.result.beforeRequiredPeriods} → {proposal.result.afterRequiredPeriods} tiết · Còn thiếu: {proposal.result.afterMissingPeriods} tiết · Giữ nguyên: {proposal.result.unchangedEntries} buổi · Sau thay đổi có {proposal.result.lockedEntries} buổi khóa.</p>
      {proposal.result.configurationChanged && <p className="catalog-callout">Đề xuất thay đổi cấu hình, phân công hoặc chế độ lập lịch, kể cả khi ngày/giờ các buổi không đổi.</p>}
      <p>{proposal.result.canConfirm ? 'Đủ điều kiện xác nhận tại thời điểm xem trước.' : 'Chưa đủ điều kiện xác nhận; có thể lưu nháp để tiếp tục chỉnh nếu có thay đổi.'}</p>
      <div className="catalog-table-wrap"><table><caption>{proposal.result.changes.length} buổi thay đổi</caption><thead><tr><th>Trước</th><th>Sau</th></tr></thead><tbody>{proposal.result.changes.slice(diffPage * 25, diffPage * 25 + 25).map(d => <tr key={d.entryKey}><td><Session value={d.before}/></td><td><Session value={d.after}/></td></tr>)}</tbody></table></div><Pager page={diffPage} count={Math.ceil(proposal.result.changes.length / 25)} disabled={locked} onChange={setDiffPage} name="So sánh"/>
      {(proposal.result.removedBusy.length > 0 || proposal.result.addedBusy.length > 0) && <details><summary>Lịch bận: bỏ {proposal.result.removedBusy.length}, thêm {proposal.result.addedBusy.length} khoảng</summary>{[['Bỏ', proposal.result.removedBusy], ['Thêm', proposal.result.addedBusy]].map(([name, rows]) => <div key={name}><h5>{name}</h5>{rows.slice(0, 100).map((b, i) => <p key={i}>{b.lecturerName || b.lecturerId} · {dateLabel(b.date)} · {clock(b.startTime)}–{clock(b.endTime)} · {b.reason}</p>)}{rows.length > 100 && <p>Hiện 100/{rows.length} khoảng.</p>}</div>)}</details>}
      {!!proposal.result.conflicts.length && <div className="plan-conflicts"><h4>Cần xử lý {proposal.result.conflicts.length} xung đột</h4><ul>{proposal.result.conflicts.map((c, i) => <li key={i}>{c.message}</li>)}</ul></div>}
      <button type="button" className="button primary" disabled={locked || !proposal.result.canApply} onClick={apply}>{working ? 'Đang lưu điều chỉnh…' : 'Lưu điều chỉnh vào nháp'}</button>
    </div>}
  </section>;
}
function Session({ value: v }) { return v ? <><strong>{v.courseCode} · {v.classGroup}</strong><p>{dateLabel(v.date)} · {clock(v.startTime)}–{clock(v.endTime)} · {v.numPeriods} tiết</p><p>{v.lecturerName} · Phòng {v.roomCode} · {v.locked ? 'Đã khóa' : 'Đang mở'}</p></> : <span>Không có buổi</span>; }
function Pager({ page, count, disabled, onChange, name }) { return count > 1 && <div className="catalog-pagination"><span>{name}: trang {page + 1}/{count}</span><button type="button" className="button" disabled={disabled || page === 0} onClick={() => onChange(page - 1)}>{name} trang trước</button><button type="button" className="button" disabled={disabled || page + 1 >= count} onClick={() => onChange(page + 1)}>{name} trang sau</button></div>; }
