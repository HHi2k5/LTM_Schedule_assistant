import React, { useEffect, useState } from 'react';
import { Building2, Users, BookOpen, DoorOpen, Plus, Pencil, Search, RefreshCw, Upload, Download, AlertTriangle, CheckCircle2, History } from 'lucide-react';
import { catalogApi } from './schedule';
import { catalogKinds, catalogLabels, catalogPath, catalogValue } from './catalog';
import { legacyLecturerFile, saveCatalogTemplate } from './catalogWorkbook';
import CatalogEditor from './CatalogEditor';
import CatalogImportPanel from './CatalogImportPanel';
import './catalog.css';

const tabs = [['lecturers', 'Giảng viên', Users], ['rooms', 'Phòng học', DoorOpen], ['departments', 'Khoa', Building2], ['assignments', 'Phân công môn', BookOpen]];
const columns = {
  lecturers: ['lecturerCode', 'fullName', 'departmentId', 'lecturerType', 'accountTeacherId', 'accountUsername', 'active'],
  rooms: ['roomCode', 'roomName', 'roomType', 'capacity', 'building', 'active'],
  departments: ['departmentCode', 'name', 'active'],
  assignments: ['courseCode', 'courseName', 'cohort', 'theoryWeight', 'practiceWeight', 'active'],
};

function readSelection() { try { return localStorage.getItem('campus-catalog-school') || ''; } catch { return ''; } }
function array(value) { if (!Array.isArray(value)) throw new Error('Máy chủ trả danh mục không hợp lệ.'); return value; }

export default function CatalogPage() {
  const [schools, setSchools] = useState([]);
  const [selected, setSelected] = useState(readSelection);
  const [version, setVersion] = useState(0);
  const [status, setStatus] = useState('loading');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [schoolEditor, setSchoolEditor] = useState(null);
  const [busy, setBusy] = useState(false);
  const [source, setSource] = useState(null);
  const [legacyOpen, setLegacyOpen] = useState(false);
  function reload() { setStatus('loading'); setError(''); setVersion(value => value + 1); }
  useEffect(() => {
    const controller = new AbortController(); let active = true;
    catalogApi('', { signal: controller.signal }).then(array).then(rows => { if (active) { setSchools(rows); setStatus('ready'); } })
      .catch(error => { if (active && error.name !== 'AbortError') { setError(error.message); setStatus('error'); } });
    return () => { active = false; controller.abort(); };
  }, [version]);
  useEffect(() => {
    if (status === 'ready' && !schools.some(school => school.schoolCode === selected)) setSelected(schools.find(school => school.active)?.schoolCode || schools[0]?.schoolCode || '');
  }, [schools, selected, status]);
  useEffect(() => { try { localStorage.setItem('campus-catalog-school', selected); } catch { /* Selection is optional; records stay on the server. */ } }, [selected]);
  async function saveSchool(input, existing) {
    const result = await catalogApi(existing ? `/${encodeURIComponent(existing.schoolCode)}` : '', {
      method: existing ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
    });
    setSchoolEditor(null); setNotice(`Đã lưu trường ${result.name}.`); reload(); setSelected(result.schoolCode);
  }
  const school = schools.find(row => row.schoolCode === selected);
  const locked = busy || Boolean(schoolEditor);
  return <div className="catalog-page">
    <section className="catalog-hero"><div className="catalog-hero-icon"><Building2 size={27}/></div><div><span className="catalog-eyebrow">DỮ LIỆU DÙNG LÂU DÀI</span><h2>Chuẩn bị một lần, dùng lại mỗi đợt</h2><p>Quản lý giảng viên, phòng và phân công môn trong danh mục của nhà trường.</p></div><span className="catalog-hero-tag">Danh mục dùng chung</span></section>
    <section className="catalog-school-bar" aria-label="Chọn trường">
      <label>Trường đang quản lý<select value={selected} disabled={locked || status !== 'ready'} onChange={event => { setSelected(event.target.value); setSource(null); setNotice(''); }}>
        {!schools.length && <option value="">Chưa có trường trong danh mục</option>}
        {schools.map(row => <option key={row.schoolCode} value={row.schoolCode}>{row.name} ({row.schoolCode}){row.active ? '' : ' — ngừng sử dụng'}</option>)}
      </select></label>
      <div className="catalog-toolbar-actions"><button className="button" type="button" disabled={locked || status === 'loading'} onClick={reload}><RefreshCw size={16}/>Tải lại danh mục</button>
        <button className="button" type="button" disabled={!school || locked} onClick={() => setSchoolEditor({ row: school })}><Pencil size={16}/>Sửa trường</button>
        <button className="button primary" type="button" disabled={locked} onClick={() => setSchoolEditor({ row: null })}><Plus size={16}/>Thêm trường</button></div>
    </section>
    {error && <p role="alert" className="catalog-error"><AlertTriangle size={18}/>{error}</p>}
    {notice && <p role="status" className="catalog-success"><CheckCircle2 size={18}/>{notice}</p>}
    {schoolEditor && <CatalogEditor key={schoolEditor.row?.schoolCode || 'new-school'} kind="institutions" row={schoolEditor.row} onBusy={setBusy}
      onSave={input => saveSchool(input, schoolEditor.row)} onCancel={() => setSchoolEditor(null)} onReload={reload}/>}
    <button type="button" className="button catalog-legacy-toggle" disabled={locked} aria-expanded={legacyOpen} onClick={() => setLegacyOpen(value => !value)}><History size={16}/>Đối chiếu dữ liệu cũ</button>
    {legacyOpen && <LegacyPanel schools={schools} disabled={locked} onBusy={setBusy} onSelect={code => { setSelected(code); setSource(null); }}
      onCreate={input => saveSchool(input, null)} onPrepare={async candidate => {
        const file = await legacyLecturerFile(candidate);
        setSelected(candidate.schoolCode); setSource({ schoolCode: candidate.schoolCode, file });
        setNotice('Đã chuẩn bị danh sách GV cũ. Kiểm tra bản xem trước trong mục Nhập Excel trước khi lưu.');
      }}/>}
    {status === 'loading' ? <div className="catalog-empty" role="status">Đang tải danh mục trường…</div>
      : school ? <CatalogWorkspace key={school.schoolCode} school={school} onBusy={setBusy} sourceFile={source?.schoolCode === school.schoolCode ? source.file : null}/>
        : status === 'ready' && <div className="catalog-empty"><Building2 size={34}/><h3>Bắt đầu với danh mục trường</h3><p>Thêm trường mới hoặc đối chiếu dữ liệu cũ để dùng đúng mã trường đã lưu phòng và lịch.</p></div>}
  </div>;
}

function CatalogWorkspace({ school, onBusy, sourceFile }) {
  const [resources, setResources] = useState({ departments: [], lecturers: [], rooms: [] });
  const [version, setVersion] = useState(0);
  const [status, setStatus] = useState('loading');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [kind, setKind] = useState('lecturers');
  const [editor, setEditor] = useState(null);
  const [importOpen, setImportOpen] = useState(Boolean(sourceFile));
  const [lecturerId, setLecturerId] = useState('');
  const [assignments, setAssignments] = useState([]);
  const [assignmentStatus, setAssignmentStatus] = useState('ready');
  const [assignmentError, setAssignmentError] = useState('');
  const [query, setQuery] = useState('');
  const [activeFilter, setActiveFilter] = useState('all');
  const [departmentFilter, setDepartmentFilter] = useState('all');
  const [page, setPage] = useState(1);
  function loading(value) { setBusy(value); onBusy(value); }
  function reload() { setStatus('loading'); setError(''); setVersion(value => value + 1); }
  useEffect(() => {
    const controller = new AbortController(); let active = true;
    Promise.all(['departments', 'lecturers', 'rooms'].map(key => catalogApi(catalogPath(key, school.schoolCode), { signal: controller.signal }).then(array)))
      .then(([departments, lecturers, rooms]) => { if (active) { setResources({ departments, lecturers, rooms }); setStatus('ready'); } })
      .catch(error => { if (active && error.name !== 'AbortError') { setError(error.message); setStatus('error'); } });
    return () => { active = false; controller.abort(); };
  }, [school.schoolCode, version]);
  useEffect(() => {
    if (sourceFile) { setKind('lecturers'); setImportOpen(true); setEditor(null); }
  }, [sourceFile]);
  useEffect(() => {
    if (!resources.lecturers.some(row => String(row.id) === lecturerId)) setLecturerId(resources.lecturers[0] ? String(resources.lecturers[0].id) : '');
  }, [resources.lecturers, lecturerId]);
  useEffect(() => {
    if (kind !== 'assignments' || !lecturerId) { setAssignments([]); setAssignmentStatus('ready'); return; }
    const controller = new AbortController(); let active = true; setAssignmentStatus('loading'); setAssignmentError('');
    catalogApi(catalogPath('assignments', school.schoolCode, lecturerId), { signal: controller.signal }).then(array)
      .then(rows => { if (active) { setAssignments(rows); setAssignmentStatus('ready'); } })
      .catch(error => { if (active && error.name !== 'AbortError') { setAssignmentError(error.message); setAssignmentStatus('error'); } });
    return () => { active = false; controller.abort(); };
  }, [kind, lecturerId, school.schoolCode, version]);
  const fetching = status === 'loading' || kind === 'assignments' && assignmentStatus === 'loading';
  const ready = status === 'ready' && (kind !== 'assignments' || assignmentStatus === 'ready');
  const rows = kind === 'assignments' ? assignments : resources[kind];
  const visible = rows.filter(row => (activeFilter === 'all' || row.active === (activeFilter === 'active'))
    && (kind !== 'lecturers' || departmentFilter === 'all' || String(row.departmentId ?? '') === departmentFilter)
    && columns[kind].map(key => catalogValue(key, row[key], resources.departments)).join(' ').toLocaleLowerCase('vi').includes(query.trim().toLocaleLowerCase('vi')));
  const pageCount = Math.max(1, Math.ceil(visible.length / 20));
  const safePage = Math.min(page, pageCount);
  const mayAdd = school.active && ready && !busy && (kind !== 'assignments' || Boolean(lecturerId));
  async function save(input) {
    const path = catalogPath(kind, school.schoolCode, lecturerId);
    await catalogApi(editor.row ? `${path}/${editor.row.id}` : path, { method: editor.row ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) });
    setNotice(`Đã lưu ${catalogKinds[kind].singular}.`); setEditor(null); reload();
  }
  const missingAccounts = resources.lecturers.filter(row => row.active && !row.accountTeacherId);
  async function createAccounts() {
    if (busy || !missingAccounts.length) return; loading(true); setError(''); setNotice('');
    try {
      const result = await catalogApi(`/${encodeURIComponent(school.schoolCode)}/lecturer-accounts`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ lecturers: missingAccounts.map(row => ({ id: row.id, revision: row.revision })) }),
      });
      setNotice(`Đã tạo ${result.created} tài khoản giảng viên. Tên đăng nhập là MGV; mật khẩu ban đầu là họ tên viết liền, chữ thường, không dấu + MGV.`); reload();
    } catch (e) { setError(e.status === 409 ? `${e.message} Tải lại danh mục trước khi tạo lại.` : e.message); }
    finally { loading(false); }
  }
  function switchTab(next) { setKind(next); setEditor(null); setImportOpen(false); setQuery(''); setActiveFilter('all'); setDepartmentFilter('all'); setPage(1); setNotice(''); }
  return <section className="catalog-workspace" aria-label={`Danh mục ${school.name}`}>
    {!school.active && <p className="catalog-callout">Trường đang ngừng sử dụng. Bạn vẫn có thể xem, sửa dữ liệu hoặc kích hoạt lại trường để thêm và nhập danh mục.</p>}
    <div className="catalog-stat-grid">
      {[[Users, 'Giảng viên', resources.lecturers.length, 'blue'], [DoorOpen, 'Phòng đang sử dụng', resources.rooms.filter(row => row.active).length, 'teal'],
        [Building2, 'Khoa', resources.departments.length, 'violet'], [BookOpen, 'Giảng viên thỉnh giảng', resources.lecturers.filter(row => row.lecturerType === 'VISITING').length, 'amber']].map(([Icon, title, count, tone]) =>
        <div className={`catalog-stat ${tone}`} key={title}><span><Icon size={19}/>{title}</span><strong>{status === 'ready' ? count : '—'}</strong></div>)}
    </div>
    <div className="catalog-tabs" role="tablist" aria-label="Loại danh mục">{tabs.map(([id, title, Icon]) => <button type="button" key={id} id={`catalog-tab-${id}`} role="tab" aria-selected={kind === id} aria-controls="catalog-active-panel" disabled={busy} onClick={() => switchTab(id)}><Icon size={17}/>{title}</button>)}</div>
    <div className="catalog-panel" id="catalog-active-panel" role="tabpanel" aria-labelledby={`catalog-tab-${kind}`}>
      <div className="catalog-section-heading"><div><h2>{catalogKinds[kind].title}</h2><p className="catalog-muted">{school.name} · {school.schoolCode}</p></div><div className="catalog-toolbar-actions">
        <button className="button" type="button" disabled={busy || fetching} onClick={reload}><RefreshCw size={16}/>Làm mới</button>
        {kind !== 'departments' && <button className="button" type="button" disabled={busy || !ready || !school.active} aria-expanded={importOpen} onClick={() => { setImportOpen(value => !value); setEditor(null); }}><Upload size={16}/>Nhập Excel</button>}
        {kind !== 'departments' && <button className="button" type="button" disabled={busy || !ready || !school.active} onClick={async () => { try { await saveCatalogTemplate(kind); } catch (error) { setError(error.message); } }}><Download size={16}/>Tải mẫu {kind === 'lecturers' ? 'giảng viên' : kind === 'rooms' ? 'phòng học' : 'phân công'}</button>}
        <button className="button primary" type="button" disabled={!mayAdd} onClick={() => { setEditor({ row: null }); setImportOpen(false); }}><Plus size={16}/>Thêm {catalogKinds[kind].singular}</button>
      </div></div>
      {kind === 'lecturers' && <div className="catalog-callout"><p>Lưu giảng viên đang hoạt động sẽ tự tạo tài khoản nếu chưa liên kết: tên đăng nhập = MGV; mật khẩu ban đầu = họ tên viết liền, chữ thường, không dấu + MGV (giữ chữ hoa của mã). Ví dụ Nguyễn Văn An / GV01 → nguyenvananGV01. Tài khoản đã có giữ mật khẩu hiện tại.</p>
        <button className="button" type="button" disabled={busy || !ready || !school.active || !missingAccounts.length || Boolean(editor) || importOpen} onClick={createAccounts}>Tạo tài khoản còn thiếu ({missingAccounts.length})</button><p>Áp dụng cho mọi GV đang hoạt động chưa liên kết của trường, kể cả ngoài bộ lọc.</p></div>}
      {kind === 'assignments' && <label className="catalog-assignment-picker">Giảng viên được phân công<select value={lecturerId} disabled={busy || status !== 'ready'} onChange={event => { setLecturerId(event.target.value); setEditor(null); setPage(1); }}>
        {!resources.lecturers.length && <option value="">Chưa có giảng viên</option>}{resources.lecturers.map(row => <option key={row.id} value={row.id}>{row.fullName} ({row.lecturerCode}){row.active ? '' : ' — ngừng sử dụng'}</option>)}
      </select></label>}
      {(error || kind === 'assignments' && assignmentError) && <p className="catalog-error" role="alert"><AlertTriangle size={18}/>{error || assignmentError} <button className="button" type="button" disabled={busy} onClick={reload}>Thử tải lại</button></p>}
      {notice && <p className="catalog-success" role="status"><CheckCircle2 size={18}/>{notice}</p>}
      {editor && <CatalogEditor key={`${kind}-${editor.row?.id || 'new'}`} kind={kind} row={editor.row} departments={resources.departments} onBusy={loading} onSave={save} onCancel={() => setEditor(null)} onReload={reload}/>}
      {importOpen && <CatalogImportPanel key={`${school.schoolCode}-${kind}`} school={school} kind={kind} departments={resources.departments}
        sourceFile={kind === 'lecturers' ? sourceFile : null} disabled={!school.active || !ready || busy} onBusy={loading} onSaved={reload}/>}
      <div className="catalog-filters"><label className="catalog-search"><Search size={17}/><input aria-label="Tìm trong danh mục" placeholder="Tìm theo mã, tên…" value={query} onChange={event => { setQuery(event.target.value); setPage(1); }}/></label>
        {kind === 'lecturers' && <select aria-label="Lọc khoa" value={departmentFilter} onChange={event => { setDepartmentFilter(event.target.value); setPage(1); }}><option value="all">Tất cả khoa</option><option value="">Chưa phân khoa</option>{resources.departments.map(row => <option key={row.id} value={row.id}>{row.name}</option>)}</select>}
        <select aria-label="Lọc trạng thái danh mục" value={activeFilter} onChange={event => { setActiveFilter(event.target.value); setPage(1); }}><option value="all">Tất cả trạng thái</option><option value="active">Đang sử dụng</option><option value="inactive">Ngừng sử dụng</option></select>
      </div>
      {fetching ? <div className="catalog-empty" role="status">Đang tải {catalogKinds[kind].singular}…</div>
        : ready && <><div className="catalog-table-wrap"><table><caption>{visible.length} / {rows.length} {catalogKinds[kind].singular}</caption><thead><tr>{columns[kind].map(key => <th scope="col" key={key}>{catalogLabels[key]}</th>)}<th scope="col">Thao tác</th></tr></thead><tbody>
          {!visible.length && <tr><td colSpan={columns[kind].length + 1} className="catalog-empty-cell">{rows.length ? 'Không có bản ghi phù hợp bộ lọc.' : kind === 'assignments' && !lecturerId ? 'Thêm giảng viên trước khi phân công môn.' : 'Chưa có dữ liệu. Thêm bản ghi hoặc nhập Excel để bắt đầu.'}</td></tr>}
          {visible.slice((safePage - 1) * 20, safePage * 20).map(row => <tr key={row.id} className={row.active ? '' : 'catalog-inactive'}>{columns[kind].map((key, index) => {
            const content = key === 'active' || key === 'lecturerType' ? <span className={`catalog-badge ${key === 'active' ? row.active ? 'active' : 'inactive' : row.lecturerType === 'VISITING' ? 'visiting' : 'staff'}`}>{catalogValue(key, row[key], resources.departments)}</span> : catalogValue(key, row[key], resources.departments);
            return index === 0 ? <th scope="row" key={key}>{content}</th> : <td key={key}>{content}</td>;
          })}<td><button className="button catalog-edit-button" type="button" disabled={busy || Boolean(editor)} aria-label={`Sửa ${row[catalogKinds[kind].key]}`} onClick={() => { setEditor({ row }); setImportOpen(false); }}><Pencil size={14}/>Sửa</button></td></tr>)}
        </tbody></table></div><div className="catalog-pagination"><span>Trang {safePage}/{pageCount} · {visible.length} bản ghi</span><div><button className="button" type="button" disabled={safePage <= 1} onClick={() => setPage(safePage - 1)}>Trang trước</button><button className="button" type="button" disabled={safePage >= pageCount} onClick={() => setPage(safePage + 1)}>Trang sau</button></div></div></>}
    </div>
  </section>;
}

function LegacyPanel({ schools, disabled, onBusy, onSelect, onCreate, onPrepare }) {
  const [rows, setRows] = useState([]);
  const [version, setVersion] = useState(0);
  const [status, setStatus] = useState('loading');
  const [error, setError] = useState('');
  const [names, setNames] = useState({});
  useEffect(() => {
    const controller = new AbortController(); let active = true;
    setStatus('loading'); setError('');
    catalogApi('/legacy', { signal: controller.signal }).then(array).then(rows => { if (active) { setRows(rows); setStatus('ready'); } })
      .catch(error => { if (active && error.name !== 'AbortError') { setError(error.message); setStatus('error'); } });
    return () => { active = false; controller.abort(); };
  }, [version]);
  async function action(callback) {
    onBusy(true); setError('');
    try { await callback(); } catch (error) { setError(error.message); } finally { onBusy(false); }
  }
  return <section className="catalog-legacy" aria-label="Đối chiếu dữ liệu cũ"><div className="catalog-section-heading"><h3>Dữ liệu trường và giảng viên đã có lịch</h3><button className="button" type="button" disabled={disabled || status === 'loading'} onClick={() => setVersion(value => value + 1)}><RefreshCw size={15}/>Tải lại dữ liệu cũ</button></div>
    <p className="catalog-muted">Chọn đúng mã và tên trường. Danh sách giảng viên cũ chỉ là gợi ý; cần xem trước rồi xác nhận nhập. Khoa và loại giảng viên cần bổ sung nếu lịch cũ chưa có.</p>
    {error && <p role="alert" className="catalog-error"><AlertTriangle size={18}/>{error}</p>}
    {status === 'loading' ? <p role="status">Đang đối chiếu…</p> : !rows.length && status === 'ready' ? <p className="catalog-muted">Chưa có dữ liệu trường cũ cần đối chiếu.</p> : <div className="catalog-legacy-grid">{rows.map(row => {
      const registered = schools.some(school => school.schoolCode === row.schoolCode);
      const name = names[row.schoolCode] ?? (row.names.length === 1 ? row.names[0] : '');
      return <article className="catalog-legacy-card" key={row.schoolCode}><h4>{row.schoolCode}</h4><p>{row.roomCount} phòng · {row.lecturers.length} giảng viên có lịch</p>
        {row.names.length > 1 && <><p className="catalog-callout">Có nhiều tên trường trong dữ liệu cũ. Hãy chọn hoặc điền tên đúng.</p><select aria-label={`Chọn tên trường ${row.schoolCode}`} value={row.names.includes(name) ? name : ''} disabled={disabled} onChange={event => setNames(previous => ({ ...previous, [row.schoolCode]: event.target.value }))}><option value="">Chọn tên để đối chiếu</option>{row.names.map(value => <option key={value}>{value}</option>)}</select></>}
        {!registered && <label>Tên trường {row.schoolCode}<input value={name} maxLength={255} disabled={disabled} onChange={event => setNames(previous => ({ ...previous, [row.schoolCode]: event.target.value }))}/></label>}
        {!row.supportedCode && <p className="catalog-error">Mã cũ chưa hợp lệ để tạo danh mục. Cần đối chiếu và chuẩn hóa mã riêng.</p>}
        <div className="catalog-toolbar-actions">{registered ? <button className="button" type="button" disabled={disabled} onClick={() => onSelect(row.schoolCode)}>Mở {row.schoolCode}</button>
          : <button className="button" type="button" disabled={disabled || !row.supportedCode || !name.trim()} onClick={() => action(() => onCreate({ schoolCode: row.schoolCode, name: name.trim(), active: true }))}>Tạo danh mục cho {row.schoolCode}</button>}
          <button className="button" type="button" disabled={disabled || !registered || !row.lecturers.length} onClick={() => action(() => onPrepare(row))}>Chuẩn bị nhập GV {row.schoolCode}</button></div>
        {row.lecturers.length > 0 && <details><summary>Xem {row.lecturers.length} giảng viên cũ</summary><ul>{row.lecturers.map(lecturer => <li key={lecturer.lecturerCode}>{lecturer.lecturerCode} · {lecturer.fullName} · {lecturer.accountTeacherId ? `Tài khoản: ${lecturer.accountTeacherId}` : 'Chưa có tài khoản đăng nhập'}</li>)}</ul></details>}
      </article>;
    })}</div>}
  </section>;
}
