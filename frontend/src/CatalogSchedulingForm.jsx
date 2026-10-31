import React, { useEffect, useState } from 'react';
import { Database, RefreshCw, Download, Plus, Trash2, AlertTriangle, Calendar, CheckCircle2 } from 'lucide-react';
import { api, catalogApi, planApi, addDays, clock } from './schedule';
import { planWorkbook } from './autoScheduleWorkbook';
import { validateFile, validatePlans, validateSchool } from './workflow';
import { catalogConfiguration, validateCatalogOptions } from './catalogScheduling';
import './catalog.css';

const planFields = [
  ['courseCode', 'Mã môn', 'text', 50], ['courseName', 'Tên môn', 'text', 180], ['cohort', 'Khóa', 'text', 50],
  ['classSize', 'Sĩ số', 'number', 1000, 1], ['numClasses', 'Số lớp', 'number', 500, 1], ['credits', 'Tín chỉ', 'number', 30, 1],
  ['studyStartDate', 'Ngày bắt đầu', 'date'], ['studyEndDate', 'Ngày kết thúc', 'date'],
  ['theoryPeriods', 'Tổng tiết LT', 'number', 1000, 0], ['practicePeriods', 'Tổng tiết TH', 'number', 1000, 0],
];
const emptyPlan = start => ({ courseCode: '', courseName: '', cohort: '', classSize: 50, numClasses: 1, credits: 3,
  studyStartDate: start, studyEndDate: addDays(start, 111), theoryPeriods: 30, practicePeriods: 0 });

export default function CatalogSchedulingForm({ startDate, onDateChange, disabled, onBusy, onStart, onResult, onSignature, onManageCatalogs }) {
  const [schools, setSchools] = useState([]);
  const [schoolCode, setSchoolCode] = useState('');
  const [status, setStatus] = useState('loading');
  const [error, setError] = useState('');
  const [version, setVersion] = useState(0);
  useEffect(() => {
    const controller = new AbortController(); let active = true; setStatus('loading'); setError('');
    catalogApi('', { signal: controller.signal }).then(rows => {
      if (!Array.isArray(rows)) throw new Error('Không đọc được danh mục trường.');
      if (!active) return;
      setSchools(rows); setStatus('ready');
      setSchoolCode(previous => {
        let saved = ''; try { saved = localStorage.getItem('campus-catalog-school') || ''; } catch { /* Optional preference. */ }
        return rows.some(row => row.schoolCode === previous) ? previous : rows.find(row => row.schoolCode === saved)?.schoolCode || rows.find(row => row.active)?.schoolCode || rows[0]?.schoolCode || '';
      });
    }).catch(error => { if (active && error.name !== 'AbortError') { setError(error.message); setStatus('error'); } });
    return () => { active = false; controller.abort(); };
  }, [version]);
  useEffect(() => { if (!schoolCode) onSignature(''); else try { localStorage.setItem('campus-catalog-school', schoolCode); } catch { /* Optional preference. */ } }, [schoolCode, onSignature]);
  const school = schools.find(row => row.schoolCode === schoolCode);
  return <div className="catalog-page catalog-scheduling">
    <div className="catalog-callout"><strong>Dùng danh mục đã lưu</strong><p>Chỉ cần kế hoạch mở lớp. Chọn giảng viên, phòng và trọng số cho lần xếp này từ dữ liệu đã chuẩn bị.</p></div>
    <div className="catalog-school-bar"><label>Trường từ danh mục<select value={schoolCode} disabled={disabled || status === 'loading'} onChange={event => setSchoolCode(event.target.value)}>
      {!schools.length && <option value="">Chưa có trường</option>}{schools.map(row => <option key={row.schoolCode} value={row.schoolCode}>{row.name} ({row.schoolCode}){row.active ? '' : ' — ngừng sử dụng'}</option>)}
    </select></label><button type="button" className="button" disabled={disabled || status === 'loading'} onClick={() => setVersion(value => value + 1)}><RefreshCw size={16}/>Tải lại trường</button>
      {onManageCatalogs && <button type="button" className="button" disabled={disabled} onClick={onManageCatalogs}><Database size={16}/>Quản lý danh mục</button>}</div>
    {error && <p className="catalog-error" role="alert"><AlertTriangle size={18}/>{error}</p>}
    {status === 'loading' && <p role="status">Đang tải trường đã lưu…</p>}
    {status === 'ready' && !schools.length && <div className="catalog-empty"><Database size={27}/><p>Chưa có danh mục trường. Vào Quản lý danh mục để nhập một lần, hoặc chọn Ba file Excel.</p></div>}
    {school && <CatalogSchedulingWorkspace key={school.schoolCode} school={school} startDate={startDate} onDateChange={onDateChange}
      disabled={disabled || status !== 'ready'} onBusy={onBusy} onStart={onStart} onResult={onResult} onSignature={onSignature}/>}
  </div>;
}

export function CatalogSchedulingWorkspace({ school, startDate, onDateChange, disabled, onBusy, onStart, onResult, onSignature, planDetail }) {
  const saved = planDetail?.draft;
  const [options, setOptions] = useState(null);
  const [status, setStatus] = useState('loading');
  const [loadError, setLoadError] = useState('');
  const [error, setError] = useState('');
  const [version, setVersion] = useState(0);
  const [lecturerIds, setLecturerIds] = useState([]);
  const [roomIds, setRoomIds] = useState([]);
  const [overrides, setOverrides] = useState(() => Object.fromEntries((saved?.configuration.weights || []).map(row => [row.assignmentId, { theoryWeight: row.theoryWeight, practiceWeight: row.practiceWeight }])));
  const [periodId, setPeriodId] = useState(saved?.configuration.periodChoiceId || '');
  const [initialized, setInitialized] = useState(false);
  const [planMode, setPlanMode] = useState(saved ? 'manual' : 'file');
  const [planFile, setPlanFile] = useState(null);
  const [plans, setPlans] = useState(() => saved?.courses.map(row => ({ ...row, numClasses: row.totalClassesToOpen })) || [emptyPlan(startDate)]);
  const [replaceSchool, setReplaceSchool] = useState(false);
  const [busy, setBusy] = useState(false);
  const [lecturerQuery, setLecturerQuery] = useState('');
  const [assignmentQuery, setAssignmentQuery] = useState('');
  const [lecturerPage, setLecturerPage] = useState(1);
  const [assignmentPage, setAssignmentPage] = useState(1);
  useEffect(() => {
    const controller = new AbortController(); let active = true; setStatus('loading'); setLoadError('');
    const source = planDetail ? planApi(`/${planDetail.plan.id}/options`, { signal: controller.signal }) : catalogApi(`/${encodeURIComponent(school.schoolCode)}/scheduling-options`, { signal: controller.signal });
    source.then(validateCatalogOptions).then(data => {
      if (!active) return;
      if (data.school.schoolCode !== school.schoolCode) throw new Error('Danh mục trả về thuộc trường khác.');
      setOptions(data); setStatus('ready');
      const eligible = data.lecturers.filter(row => row.eligible).map(row => row.lecturer.id);
      const availableRooms = data.rooms.map(row => row.id);
      setLecturerIds(previous => initialized ? previous.filter(id => eligible.includes(id)) : saved ? saved.configuration.lecturerIds.filter(id => eligible.includes(id)) : eligible);
      setRoomIds(previous => initialized ? previous.filter(id => availableRooms.includes(id)) : saved ? saved.configuration.roomIds.filter(id => availableRooms.includes(id)) : availableRooms);
      setPeriodId(previous => data.periodChoices.some(row => row.id === previous) ? previous : data.periodChoices.length === 1 ? data.periodChoices[0].id : '');
      setInitialized(true);
    }).catch(error => { if (active && error.name !== 'AbortError') { setLoadError(error.message); setStatus('error'); } });
    return () => { active = false; controller.abort(); };
    // initialized belongs to the request's starting state; setting it must not reload the request.
  }, [school.schoolCode, school.revision, version]);
  const assignments = options?.assignments.filter(row => lecturerIds.includes(row.lecturerId)) || [];
  const visibleAssignments = assignments.filter(row => `${row.courseCode} ${row.courseName} ${row.cohort}`.toLocaleLowerCase('vi').includes(assignmentQuery.toLocaleLowerCase('vi')));
  const visibleLecturers = options?.lecturers.filter(row => `${row.lecturer.lecturerCode} ${row.lecturer.fullName} ${row.lecturer.accountTeacherId || ''}`.toLocaleLowerCase('vi').includes(lecturerQuery.toLocaleLowerCase('vi'))) || [];
  const periodChoice = options?.periodChoices.find(row => row.id === periodId);
  const effectiveWeight = row => overrides[row.id] || { theoryWeight: row.theoryWeight, practiceWeight: row.practiceWeight };
  const canonicalPlans = plans.map(row => ({ ...row, ...Object.fromEntries(planFields.filter(([, , type]) => type === 'number').map(([key]) => [key, row[key] === '' ? '' : Number(row[key])])) }));
  const signature = JSON.stringify([school.schoolCode, startDate, planMode, planFile && [planFile.name, planFile.size, planFile.lastModified], canonicalPlans,
    options?.lecturers.filter(row => lecturerIds.includes(row.lecturer.id)).map(row => [row.lecturer.id, row.lecturer.lecturerCode, row.lecturer.fullName, row.lecturer.accountTeacherId]),
    options?.rooms.filter(row => roomIds.includes(row.id)).map(row => [row.id, row.roomCode, row.capacity, row.isLab]),
    assignments.map(row => [row.id, row.courseCode, row.cohort, Number(effectiveWeight(row).theoryWeight), Number(effectiveWeight(row).practiceWeight)]), periodChoice?.periods]);
  useEffect(() => { if (status === 'ready') onSignature(signature); }, [signature, status, onSignature]);
  function toggle(set, id) { set(previous => previous.includes(id) ? previous.filter(value => value !== id) : [...previous, id]); }
  async function submit(event) {
    event.preventDefault(); if (busy || disabled || status !== 'ready') return;
    setError('');
    let configuration;
    try {
      validateSchool(school.schoolCode, school.name, startDate);
      configuration = catalogConfiguration(options, lecturerIds, roomIds, overrides, periodId, replaceSchool, Boolean(planDetail));
      if (planMode === 'file') validateFile(planFile, 'Kế hoạch mở lớp'); else validatePlans(plans, startDate);
    } catch (error) { setError(error.message); return; }
    setBusy(true); onBusy(true); onStart();
    try {
      const file = planMode === 'file' ? planFile : new File([await planWorkbook(plans).xlsx.writeBuffer()], 'ke-hoach-danh-muc.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      const body = new FormData(); body.append('planFile', file); body.append('schoolId', school.schoolCode); body.append('semesterStartDate', startDate); body.append('configuration', JSON.stringify(configuration));
      if (planDetail) body.append('revision', String(planDetail.plan.revision));
      const result = planDetail ? await planApi(`/${planDetail.plan.id}/generate-preview`, { method: 'POST', body }) : await api('/auto-generate-catalog', { method: 'POST', body }); onResult(result);
      if (result.saved) setVersion(value => value + 1);
    } catch (error) {
      setError(error.status === 409 ? (planDetail ? 'Bản lịch hoặc danh mục đã thay đổi. Mở lại bản lịch và kiểm tra lựa chọn trước khi xếp lại.' : 'Danh mục đã thay đổi từ lúc tải. Tải lại dữ liệu và kiểm tra lựa chọn trước khi xếp lại.') : error.message);
      if (error.status === 409) setStatus('stale');
    } finally { setBusy(false); onBusy(false); }
  }
  async function template() {
    try {
      const url = URL.createObjectURL(new Blob([await planWorkbook([]).xlsx.writeBuffer()], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }));
      const link = document.createElement('a'); link.href = url; link.download = 'ke-hoach-mo-lop.xlsx'; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) { setError(error.message); }
  }
  const locked = disabled || busy || status !== 'ready' || !school.active;
  const lecturerSafePage = Math.min(lecturerPage, Math.max(1, Math.ceil(visibleLecturers.length / 20)));
  const assignmentSafePage = Math.min(assignmentPage, Math.max(1, Math.ceil(visibleAssignments.length / 20)));
  return <section aria-label="Xếp lịch từ danh mục">
    <div className="catalog-section-heading"><div><h3><Database size={18}/>Nguồn giảng viên và phòng đã lưu</h3><p className="catalog-muted">{lecturerIds.length} giảng viên · {roomIds.length} phòng được chọn. Các trọng số chỉnh dưới đây chỉ dùng cho lần xếp này.</p></div>
      <button type="button" className="button" disabled={busy || disabled || status === 'loading'} onClick={() => setVersion(value => value + 1)}><RefreshCw size={16}/>Tải lại dữ liệu xếp lịch</button></div>
    {status === 'loading' && <p role="status" className="catalog-muted">Đang đọc giảng viên, phân công và phòng…</p>}
    {loadError && <p className="catalog-error" role="alert"><AlertTriangle size={18}/>{loadError}</p>}
    <form onSubmit={submit} aria-label="Lập lịch bằng danh mục">
      <fieldset className="catalog-schedule-fields" disabled={locked}>
        <label>Ngày Thứ Hai của tuần 1 — danh mục<input type="date" value={startDate} readOnly={Boolean(planDetail)} required min="2000-01-01" max="2100-12-31" onChange={event => onDateChange?.(event.target.value)}/></label>
        <label>Bảng giờ tiết<select value={periodId} required onChange={event => setPeriodId(event.target.value)}><option value="">Chọn bảng giờ</option>{options?.periodChoices.map(row => <option key={row.id} value={row.id}>{row.label}</option>)}</select></label>
        {periodChoice && <details><summary><Calendar size={15}/>Xem bảng giờ đã chọn</summary><div className="catalog-period-grid">{periodChoice.periods.map(period => <span key={period.period}>Tiết {period.period}: {clock(period.start)}–{clock(period.end)}</span>)}</div></details>}
        <details className="catalog-source-section"><summary>Chọn giảng viên ({lecturerIds.length})</summary><p className="catalog-muted">Liên kết tài khoản giúp đối chiếu lịch cùng một giảng viên giữa các trường. Giảng viên chưa có tài khoản vẫn có thể được phân công.</p>
          <div className="catalog-toolbar-actions"><button type="button" className="button" onClick={() => setLecturerIds(options.lecturers.filter(row => row.eligible).map(row => row.lecturer.id))}>Chọn mọi GV đủ điều kiện</button><button type="button" className="button" onClick={() => setLecturerIds([])}>Bỏ chọn giảng viên</button></div>
          <label>Tìm giảng viên trong nguồn<input value={lecturerQuery} onChange={event => { setLecturerQuery(event.target.value); setLecturerPage(1); }}/></label>
          <div className="catalog-pool-grid">{visibleLecturers.slice((lecturerSafePage - 1) * 20, lecturerSafePage * 20).map(row => <label key={row.lecturer.id} className="catalog-pool-choice"><input type="checkbox" aria-label={`Chọn giảng viên ${row.lecturer.lecturerCode}`} checked={lecturerIds.includes(row.lecturer.id)} disabled={!row.eligible} onChange={() => toggle(setLecturerIds, row.lecturer.id)}/><span><strong>{row.lecturer.lecturerCode} · {row.lecturer.fullName}</strong><small>{row.lecturer.accountTeacherId ? `Tài khoản: ${row.lecturer.accountTeacherId}` : 'Chưa liên kết tài khoản'}{!row.eligible && ` · ${row.reason}`}</small></span></label>)}</div>
          {!visibleLecturers.length && <p className="catalog-muted">Chưa có giảng viên phù hợp.</p>}
          {visibleLecturers.length > 20 && <Pager page={lecturerSafePage} count={visibleLecturers.length} change={setLecturerPage}/>}
        </details>
        <details className="catalog-source-section"><summary>Chọn phòng ({roomIds.length})</summary><div className="catalog-toolbar-actions"><button type="button" className="button" onClick={() => setRoomIds(options.rooms.map(row => row.id))}>Chọn tất cả phòng</button><button type="button" className="button" onClick={() => setRoomIds([])}>Bỏ chọn phòng</button></div>
          <div className="catalog-pool-grid">{options?.rooms.map(row => <label key={row.id} className="catalog-pool-choice"><input type="checkbox" aria-label={`Chọn phòng ${row.roomCode}`} checked={roomIds.includes(row.id)} onChange={() => toggle(setRoomIds, row.id)}/><span><strong>{row.roomCode} · {row.capacity} chỗ</strong><small>{row.isLab ? 'Thực hành / LAB' : 'Lý thuyết'}</small></span></label>)}</div>
          {!options?.rooms.length && <p className="catalog-muted">Chưa có phòng đang sử dụng. Bổ sung danh mục phòng trước khi chạy.</p>}
        </details>
        <details className="catalog-source-section"><summary>Trọng số LT/TH của lần xếp ({assignments.length} phân công)</summary><p className="catalog-muted">0 nghĩa là không nhận loại đó. Thay đổi này không sửa trọng số mặc định trong danh mục.</p>
          <label>Tìm môn hoặc khóa trong phân công<input value={assignmentQuery} onChange={event => { setAssignmentQuery(event.target.value); setAssignmentPage(1); }}/></label>
          <div className="catalog-table-wrap"><table><caption>Phân công dùng cho lần xếp</caption><thead><tr><th>GV</th><th>Môn / khóa</th><th>LT mặc định</th><th>TH mặc định</th><th>LT lần này</th><th>TH lần này</th></tr></thead><tbody>{visibleAssignments.slice((assignmentSafePage - 1) * 20, assignmentSafePage * 20).map(row => <tr key={row.id}>
            <th>{options.lecturers.find(item => item.lecturer.id === row.lecturerId)?.lecturer.lecturerCode}</th><td>{row.courseCode} · {row.courseName} / {row.cohort}</td><td>{row.theoryWeight}</td><td>{row.practiceWeight}</td>
            {['theoryWeight', 'practiceWeight'].map((key, index) => <td key={key}><input type="number" min={0} max={10000} step={1} required aria-label={`Trọng số ${index ? 'TH' : 'LT'} phân công ${row.id}`} value={effectiveWeight(row)[key]} onChange={event => { const value = event.target.value; setOverrides(previous => ({ ...previous, [row.id]: { ...effectiveWeight(row), [key]: value } })); }}/></td>)}
          </tr>)}</tbody></table></div>{!assignments.length && <p className="catalog-muted">Chưa có phân công đang hoạt động cho GV đã chọn.</p>}
          {visibleAssignments.length > 20 && <Pager page={assignmentSafePage} count={visibleAssignments.length} change={setAssignmentPage}/>}
          <button type="button" className="button" onClick={() => setOverrides({})}>Khôi phục trọng số mặc định</button>
        </details>
        <div className="catalog-plan-source"><div className="catalog-section-heading"><h3>Kế hoạch mở lớp</h3><button type="button" className="button" onClick={template}><Download size={16}/>Tải mẫu kế hoạch</button></div>
          <p className="catalog-muted">Mẫu Excel mới có Loại lớp (LT / TH) và Số tiết. Mỗi loại lớp là một dòng, có sĩ số, số lớp và khoảng ngày riêng. Mẫu cũ có Tổng tiết LT/TH vẫn đọc được; kế hoạch nhập trực tiếp được tách thành các dòng LT/TH khi gửi.</p>
          <div className="catalog-toolbar-actions" role="group" aria-label="Nguồn kế hoạch"><button type="button" className="button" aria-pressed={planMode === 'file'} onClick={() => setPlanMode('file')}>File kế hoạch</button><button type="button" className="button" aria-pressed={planMode === 'manual'} onClick={() => setPlanMode('manual')}>Nhập kế hoạch trực tiếp</button></div>
          {planMode === 'file' ? <label>Kế hoạch mở lớp từ Excel<input type="file" accept=".xlsx" required onChange={event => { setPlanFile(event.target.files?.[0] || null); setError(''); }}/></label>
            : <><div className="catalog-table-wrap"><table><caption>Kế hoạch nhập trực tiếp</caption><thead><tr>{planFields.map(([key, label]) => <th key={key}>{label}</th>)}<th>Xóa</th></tr></thead><tbody>{plans.map((row, index) => <tr key={index}>{planFields.map(([key, label, type, max, min]) => <td key={key}><input type={type} required min={min} max={type === 'number' ? max : undefined} maxLength={type === 'text' ? max : undefined} step={type === 'number' ? 1 : undefined} aria-label={`${label} kế hoạch dòng ${index + 1}`} value={row[key]} onChange={event => setPlans(previous => previous.map((item, i) => i === index ? { ...item, [key]: event.target.value } : item))}/></td>)}<td><button className="button" type="button" aria-label={`Xóa kế hoạch dòng ${index + 1}`} onClick={() => setPlans(previous => previous.filter((_, i) => i !== index))}><Trash2 size={14}/></button></td></tr>)}</tbody></table></div><button type="button" className="button" onClick={() => setPlans(previous => [...previous, emptyPlan(startDate)])}><Plus size={16}/>Thêm dòng kế hoạch</button></>}
        </div>
        {planDetail ? <div className="catalog-callout"><strong>Lưu nháp cho {planDetail.plan.departmentName}</strong><p>Lịch cá nhân chỉ cập nhật khi xác nhận bản nháp. Lịch của khoa khác và lịch cá nhân hiện hành được giữ để kiểm tra trùng.</p></div>
          : <div className="catalog-callout"><p>Lần xếp này cập nhật phần lịch tự động của <strong>toàn trường</strong>. Các lịch nhập cá nhân được giữ lại và vẫn dùng để kiểm tra trùng.</p>
          <label className="catalog-checkbox"><input type="checkbox" checked={replaceSchool} onChange={event => setReplaceSchool(event.target.checked)}/>Tôi đã đưa đủ kế hoạch toàn trường cho lần xếp này</label></div>}
      </fieldset>
      {error && <p className="catalog-error" role="alert"><AlertTriangle size={18}/>{error}</p>}
      <div className="catalog-actions"><button type="submit" className="button primary" disabled={locked || (!planDetail && !replaceSchool) || !lecturerIds.length || !roomIds.length || !periodId}><CheckCircle2 size={16}/>{busy ? 'Đang xếp lịch từ danh mục…' : planDetail ? 'Xếp và lưu bản nháp mới' : 'Xếp lịch bằng danh mục đã lưu'}</button></div>
    </form>
  </section>;
}

function Pager({ page, count, change }) {
  return <div className="catalog-pagination"><span>Trang {page}/{Math.ceil(count / 20)}</span><div><button type="button" className="button" disabled={page <= 1} onClick={() => change(page - 1)}>Trang trước</button><button type="button" className="button" disabled={page * 20 >= count} onClick={() => change(page + 1)}>Trang sau</button></div></div>;
}
