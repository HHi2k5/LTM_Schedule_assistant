import React, { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { CalendarDays, Plus, RefreshCw, CheckCircle2, AlertTriangle, Download } from 'lucide-react';
import { catalogApi, planApi, clock, dateLabel, isMonday } from './schedule';
import { CatalogSchedulingWorkspace } from './CatalogSchedulingForm';
import FacultyScheduleBoard from './FacultyScheduleBoard';
import SchedulingChangeLog from './SchedulingChangeLog';
import './catalog.css';
import './schedulingPlans.css';
import './scheduleAdjustment.css';

const jsonPost = value => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value) });
const VisitingLecturerPanel = lazy(() => import('./VisitingLecturerPanel'));
const ScheduleAdjustmentPanel = lazy(() => import('./ScheduleAdjustmentPanel'));

export default function SchedulingPlansPage() {
  const [plans, setPlans] = useState([]);
  const [selected, setSelected] = useState('');
  const [detail, setDetail] = useState(null);
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [refresh, setRefresh] = useState(0);
  const [listRefresh, setListRefresh] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    planApi('', { signal: controller.signal }).then(setPlans).catch(e => { if (e.name !== 'AbortError') setError(e.message); });
    return () => controller.abort();
  }, [listRefresh]);
  useEffect(() => {
    const controller = new AbortController(); setDetail(null); setError('');
    if (!selected) { setLoading(false); return () => controller.abort(); }
    setLoading(true);
    planApi(`/${selected}`, { signal: controller.signal }).then(setDetail)
      .catch(e => { if (e.name !== 'AbortError') setError(e.message); }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [selected, refresh]);
  function updated(next) { setDetail(next); setListRefresh(value => value + 1); }
  return <div className="catalog-page scheduling-plans">
    <div className="catalog-hero"><span className="catalog-hero-icon"><CalendarDays size={28}/></span><div><span className="catalog-eyebrow">KẾ HOẠCH THEO KHOA</span><h2>Bản lịch theo khoa</h2><p>Lưu nháp, xem lại từng phiên bản và xác nhận để đưa vào lịch giảng dạy.</p></div></div>
    <div className="catalog-school-bar"><label>Bản lịch đã lưu<select aria-label="Bản lịch đã lưu" value={selected} disabled={busy} onChange={e => { setSelected(e.target.value); setCreating(false); }}><option value="">Chọn bản lịch</option>{plans.map(p => <option key={p.id} value={p.id}>{p.departmentName} · {p.name} · {p.semester} ({p.schoolCode})</option>)}</select></label>
      <button className="button" disabled={busy} onClick={() => { setListRefresh(v => v + 1); setRefresh(v => v + 1); }}><RefreshCw size={16}/>Mở lại bản lịch</button>
      <button className="button primary" disabled={busy} onClick={() => setCreating(v => !v)}><Plus size={16}/>Tạo bản lịch</button></div>
    {creating && <CreatePlan onBusy={setBusy} onCreated={next => { setCreating(false); setSelected(String(next.plan.id)); setListRefresh(v => v + 1); }}/>}
    {error && <p className="catalog-error" role="alert">{error}</p>}
    {loading && <p role="status">Đang mở bản lịch đã lưu…</p>}
    {!selected && !creating && <div className="catalog-empty"><CalendarDays size={28}/><p>Chọn bản lịch đã lưu hoặc tạo bản lịch cho một khoa và học kỳ.</p></div>}
    {detail && <PlanWorkspace key={`${detail.plan.id}-${detail.plan.revision}`} detail={detail} onUpdated={updated} onBusy={setBusy}/>}
  </div>;
}

function CreatePlan({ onCreated, onBusy }) {
  const [schools, setSchools] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [form, setForm] = useState({ schoolCode: '', departmentId: '', name: '', semester: '', semesterStartDate: '2026-08-10' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => { const controller = new AbortController(); catalogApi('', { signal: controller.signal }).then(rows => setSchools(rows.filter(r => r.active))).catch(e => { if (e.name !== 'AbortError') setError(e.message); }); return () => controller.abort(); }, []);
  useEffect(() => {
    const controller = new AbortController(); setDepartments([]);
    if (form.schoolCode) catalogApi(`/${encodeURIComponent(form.schoolCode)}/departments`, { signal: controller.signal }).then(rows => setDepartments(rows.filter(r => r.active))).catch(e => { if (e.name !== 'AbortError') setError(e.message); });
    return () => controller.abort();
  }, [form.schoolCode]);
  const field = key => ({ value: form[key], onChange: e => setForm(p => ({ ...p, [key]: e.target.value, ...(key === 'schoolCode' ? { departmentId: '' } : {}) })) });
  async function submit(e) {
    e.preventDefault(); if (busy) return; setError('');
    if (!isMonday(form.semesterStartDate)) { setError('Tuần 1 phải bắt đầu vào Thứ Hai.'); return; }
    setBusy(true); onBusy(true);
    try { onCreated(await planApi('', jsonPost({ ...form, departmentId: Number(form.departmentId), name: form.name.trim(), semester: form.semester.trim() }))); }
    catch (e) { setError(e.message); }
    finally { setBusy(false); onBusy(false); }
  }
  return <form className="catalog-editor" onSubmit={submit} aria-label="Tạo bản lịch theo khoa"><h3>Tạo bản lịch theo khoa</h3><fieldset disabled={busy}>
    <label>Trường của bản lịch<select required {...field('schoolCode')}><option value="">Chọn trường</option>{schools.map(s => <option key={s.schoolCode} value={s.schoolCode}>{s.name}</option>)}</select></label>
    <label>Khoa của bản lịch<select required {...field('departmentId')}><option value="">Chọn khoa</option>{departments.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select></label>
    <label>Tên bản lịch<input required maxLength={200} {...field('name')}/></label><label>Học kỳ / đợt<input required maxLength={80} placeholder="HK1-2026" {...field('semester')}/></label>
    <label>Ngày Thứ Hai tuần 1<input type="date" required min="2000-01-01" max="2100-12-31" {...field('semesterStartDate')}/></label>
  </fieldset>{error && <p className="catalog-error" role="alert">{error}</p>}<div className="catalog-actions"><button className="button primary" disabled={busy}>Lưu bản lịch mới</button></div></form>;
}

function PlanWorkspace({ detail, onUpdated, onBusy }) {
  const { plan, draft, versions } = detail;
  const [viewId, setViewId] = useState(draft?.summary.id || '');
  const [view, setView] = useState(draft);
  const [viewError, setViewError] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [conflicts, setConflicts] = useState(null);
  const [dirty, setDirty] = useState(false);
  const [visitingOpen, setVisitingOpen] = useState(false);
  const [visitingDirty, setVisitingDirty] = useState(false);
  const [adjustmentOpen, setAdjustmentOpen] = useState(false);
  const [adjustmentDirty, setAdjustmentDirty] = useState(false);
  const [adjustmentSeed, setAdjustmentSeed] = useState(null);
  function closeEditors() { setVisitingOpen(false); setVisitingDirty(false); setAdjustmentOpen(false); setAdjustmentDirty(false); }
  function adjust(seed = null) { closeEditors(); setAdjustmentSeed(seed); setViewId(draft.summary.id); setAdjustmentOpen(true); setAdjustmentDirty(true); }
  function showVersion(id) { closeEditors(); setViewId(id); setConflicts(null); }
  const baseline = useRef(null);
  const signature = useCallback(value => { if (baseline.current === null) baseline.current = value; setDirty(baseline.current !== value); }, []);
  const changeBusy = value => { setBusy(value); onBusy(value); };
  useEffect(() => {
    const controller = new AbortController(); setViewError(''); setView(null);
    if (!viewId) return () => controller.abort();
    if (Number(viewId) === draft?.summary.id) { setView(draft); return () => controller.abort(); }
    planApi(`/${plan.id}/versions/${viewId}`, { signal: controller.signal }).then(setView).catch(e => { if (e.name !== 'AbortError') setViewError(e.message); });
    return () => controller.abort();
  }, [plan.id, viewId, draft]);
  const latest = view && view.summary.id === draft?.summary.id;
  async function action(type) {
    if (busy) return; changeBusy(true); setError(''); setConflicts(null);
    try {
      const response = await planApi(`/${plan.id}/${type}`, jsonPost({ revision: plan.revision }));
      if (type === 'validate') setConflicts(response); else onUpdated(response);
    } catch (e) { setError(e.message); if (e.conflicts?.length) setConflicts(e.conflicts); }
    finally { changeBusy(false); }
  }
  async function download() {
    if (!view) return; changeBusy(true); setError('');
    try {
      const { scheduledWorkbook } = await import('./scheduleExport');
      const { workbook, filename } = scheduledWorkbook(view.result);
      const info = workbook.addWorksheet('BanLich'); info.addRows([['Bản lịch', plan.name], ['Khoa', plan.departmentName], ['Phiên bản', view.summary.number], ['Trạng thái', view.summary.currentConfirmed ? 'Đang áp dụng' : view.summary.confirmedAt ? 'Đã xác nhận trước đây' : 'Bản nháp']]); info.columns.forEach(c => { c.width = 32; });
      const url = URL.createObjectURL(new Blob([await workbook.xlsx.writeBuffer()], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }));
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = `${view.summary.currentConfirmed ? 'xac-nhan' : 'nhap-lich-su'}-v${view.summary.number}-${filename}`; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) { setError(e.message); } finally { changeBusy(false); }
  }
  return <div className="plans-workspace">
    <section className="catalog-panel"><div className="catalog-section-heading"><div><span className={`catalog-badge ${plan.state === 'CONFIRMED' ? 'active' : 'visiting'}`}>{plan.state === 'CONFIRMED' ? 'Đã xác nhận' : 'Bản nháp'}</span><h3 className="plan-name">{plan.name}</h3><p className="catalog-muted">{plan.schoolName} · {plan.departmentName} · {plan.semester} · Tuần 1: {dateLabel(plan.semesterStartDate)}</p></div><span className="catalog-badge staff">Revision {plan.revision}</span></div>
      <p className="catalog-muted">{plan.confirmedVersion ? `Lịch cá nhân đang dùng phiên bản ${plan.confirmedVersion}.` : 'Chưa có phiên bản nào đưa vào lịch cá nhân.'} {plan.draftVersion && `Bản lưu gần nhất: phiên bản ${plan.draftVersion}.`}</p>
    </section>
    <section className="catalog-panel" aria-label="Phiên bản đã lưu"><div className="catalog-section-heading"><h3>Phiên bản đã lưu</h3><label>Phiên bản đang xem<select value={viewId} disabled={busy || visitingDirty || adjustmentDirty} onChange={e => showVersion(e.target.value)}><option value="">Chọn phiên bản</option>{versions.map(v => <option key={v.id} value={v.id}>Phiên bản {v.number}{v.currentConfirmed ? ' · đang áp dụng' : v.confirmedAt ? ' · đã xác nhận trước đây' : ' · bản nháp'}{v.complete ? '' : ' · thiếu khối lượng'}</option>)}</select></label></div>
      {viewError && <p className="catalog-error" role="alert">{viewError}</p>}
      {!draft && <p className="catalog-muted">Chưa có bản nháp. Chuẩn bị kế hoạch ở phía dưới rồi xếp và lưu.</p>}
      {view && <><p className="catalog-muted">Phiên bản {view.summary.number} · {view.entryCount} buổi · {view.summary.complete ? 'Đã xếp đủ khối lượng' : 'Chưa xếp đủ khối lượng'} · Người tạo: {view.summary.createdBy}</p>
        {view.result.unscheduledWarnings?.length > 0 && <div className="catalog-callout"><strong>Khối lượng còn thiếu</strong><ul>{view.result.unscheduledWarnings.map((w, i) => <li key={i}>{w}</li>)}</ul></div>}
        <FacultyScheduleBoard key={`${plan.id}-${view.summary.id}`} planId={plan.id} versionId={view.summary.id} disabled={busy} onBusy={changeBusy} onAdjust={latest && !busy && !dirty && !visitingDirty && !adjustmentDirty ? adjust : null}/>
        <div className="catalog-actions"><button className="button" disabled={busy || !view.result.scheduledClasses?.length} onClick={download}><Download size={16}/>Xuất Excel phiên bản</button>
          <button className="button" disabled={busy || !latest} onClick={() => action('validate')}><RefreshCw size={16}/>Kiểm tra bản nháp đã lưu</button>
          <button className="button primary" disabled={busy || dirty || visitingDirty || adjustmentDirty || !latest || !view.summary.complete || view.summary.currentConfirmed} onClick={() => action('confirm')}><CheckCircle2 size={16}/>Xác nhận bản nháp</button></div>
        {latest && <div className="catalog-actions"><button type="button" className="button" disabled={busy || dirty || adjustmentDirty} onClick={() => { setAdjustmentOpen(false); setVisitingOpen(v => !v); setVisitingDirty(false); }}>{visitingOpen ? 'Hủy chỉnh sửa thỉnh giảng' : 'Bổ sung thỉnh giảng'}</button><button type="button" className="button" disabled={busy || dirty || visitingDirty} onClick={() => adjustmentOpen ? closeEditors() : adjust()}>{adjustmentOpen ? 'Hủy điều chỉnh lịch' : 'Điều chỉnh lịch'}</button></div>}
        {!latest && <button className="button" disabled={busy || dirty || visitingDirty || adjustmentDirty} onClick={() => adjust({ action: 'RESTORE', restoreVersionId: view.summary.id })}>Khôi phục phiên bản đang xem</button>}
        {!latest && <p className="catalog-callout">Đang xem lịch sử. Chọn phiên bản mới nhất để kiểm tra và xác nhận.</p>}
        {dirty && <p className="catalog-callout">Nội dung nhập đã thay đổi. Xếp và lưu bản nháp mới trước khi xác nhận.</p>}
        {visitingDirty && <p className="catalog-callout">Đang chỉnh thỉnh giảng. Xem trước và lưu đề xuất vào nháp, hoặc hủy chỉnh sửa trước khi xác nhận.</p>}
        {adjustmentDirty && <p className="catalog-callout">Đang điều chỉnh lịch. Xem trước và lưu vào nháp, hoặc hủy điều chỉnh trước khi xác nhận.</p>}
      </>}
      {error && <p className="catalog-error" role="alert">{error}</p>}
      {conflicts !== null && <div aria-label="Kết quả kiểm tra bản nháp" className={conflicts.length ? 'plan-conflicts' : 'catalog-success'} role="status">{!conflicts.length ? 'Bản nháp đã lưu không có xung đột tại thời điểm kiểm tra. Khi xác nhận, máy chủ kiểm tra lại.' : <><h4><AlertTriangle size={17}/>Cần xử lý {conflicts.length} xung đột</h4><ul>{conflicts.map((c, i) => <li key={i}><strong>{c.resource || c.type}</strong>{c.date && ` · ${dateLabel(c.date)} ${clock(c.startTime)}–${clock(c.endTime)}`}<p>{c.message}</p></li>)}</ul><p>Điều chỉnh kế hoạch hoặc nguồn rồi tạo bản nháp mới. Lịch đang áp dụng vẫn được giữ.</p></>}</div>}
    </section>
    {visitingOpen && latest && <Suspense fallback={<p role="status">Đang mở phần thỉnh giảng…</p>}><VisitingLecturerPanel detail={detail} disabled={busy} onBusy={changeBusy} onApplied={onUpdated} onDirtyChange={setVisitingDirty}/></Suspense>}
    {adjustmentOpen && latest && <Suspense fallback={<p role="status">Đang mở phần điều chỉnh…</p>}><ScheduleAdjustmentPanel key={JSON.stringify(adjustmentSeed)} seed={adjustmentSeed} detail={detail} disabled={busy} onBusy={changeBusy} onApplied={onUpdated} onDirtyChange={setAdjustmentDirty}/></Suspense>}
    {draft && <SchedulingChangeLog planId={plan.id} currentVersionId={draft.summary.id} disabled={busy || dirty || visitingDirty || adjustmentDirty} onView={showVersion} onRestore={id => adjust({ action: 'RESTORE', restoreVersionId: id })}/>}
    <section className="catalog-panel catalog-scheduling"><h3>Chuẩn bị bản nháp tiếp theo</h3><p className="catalog-muted">Chỉ giảng viên thuộc {plan.departmentName} xuất hiện trong nguồn. Phòng dùng chung trong trường.</p>
      {draft?.visitingChange ? <p className="catalog-callout">Bản nháp có phân công thỉnh giảng hoặc lịch cố định. Mở Bổ sung thỉnh giảng để cập nhật lớp và lịch bận; các buổi ngoài phạm vi sẽ được giữ nguyên.</p>
        : draft?.customized ? <p className="catalog-callout">Bản nháp có điều chỉnh riêng. Dùng Điều chỉnh lịch để sửa hoặc xếp lại phạm vi; khôi phục phiên bản tự động trước đây nếu muốn lập lại toàn bộ.</p>
        : <CatalogSchedulingWorkspace school={{ schoolCode: plan.schoolCode, name: plan.schoolName, active: true }} startDate={plan.semesterStartDate} planDetail={detail} disabled={busy || visitingDirty || adjustmentDirty} onBusy={changeBusy} onStart={() => { setError(''); setConflicts(null); }} onResult={onUpdated} onSignature={signature}/>}
    </section>
  </div>;
}
