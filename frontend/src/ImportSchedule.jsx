import React, { useEffect, useRef, useState } from 'react';
import { Upload, Download, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { api, clock, dateLabel } from './schedule';
import { buildUpload } from './scheduleImport';
import { parseJsonSchedule, templateJson } from './jsonSchedule';

export default function ImportSchedule({ teacherId, semesterStart, onImported }) {
  const [metadata, setMetadata] = useState({ teacherId, teacherName: '', schoolId: '', schoolName: '', semesterStartDate: semesterStart, colorCode: '#177c62' });
  const [file, setFile] = useState(null);
  const [jsonPayload, setJsonPayload] = useState(null);
  const [preview, setPreview] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [reading, setReading] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const [newTeacher, setNewTeacher] = useState(false);
  const [probe, setProbe] = useState({ status: 'idle', start: '' });
  const generation = useRef(0);
  const sourceKind = useRef('excel');
  const isJson = /\.json$/i.test(file?.name || '');
  const canSave = preview && confirmed && !busy && !reading && (probe.status === 'found' || (probe.status === 'unknown' && newTeacher));

  useEffect(() => {
    const id = metadata.teacherId.trim();
    const controller = new AbortController();
    let active = true;
    setProbe({ status: id ? 'loading' : 'idle', start: '' }); setNewTeacher(false);
    const timer = setTimeout(() => {
      if (!id) return;
      api(`/week/1?teacherId=${encodeURIComponent(id)}`, { signal: controller.signal }).then(data => {
        if (!data.startDate || !Array.isArray(data.lessons) || data.teacherId !== id) throw new Error('Dữ liệu không hợp lệ.');
        if (!active) return;
        setProbe({ status: 'found', start: data.startDate });
        if (sourceKind.current !== 'json') setMetadata(previous => ({ ...previous, semesterStartDate: data.startDate }));
        setPreview(null); setConfirmed(false);
      }).catch(err => {
        if (active && err.name !== 'AbortError') setProbe({ status: 'unknown', start: '', message: err.message });
      });
    }, 350);
    return () => { active = false; clearTimeout(timer); controller.abort(); };
  }, [metadata.teacherId]);
  useEffect(() => () => { generation.current++; }, []);

  function change(event) {
    generation.current++;
    setMetadata(previous => ({ ...previous, [event.target.name]: event.target.value }));
    setPreview(null); setConfirmed(false); setError(''); setReading(false);
  }
  async function chooseFile(event) {
    const id = ++generation.current;
    const selected = event.target.files?.[0] || null;
    sourceKind.current = /\.json$/i.test(selected?.name || '') ? 'json' : 'excel';
    setFile(selected); setJsonPayload(null); setPreview(null); setConfirmed(false); setError('');
    if (!selected) return;
    if (!/\.(xlsx|json)$/i.test(selected.name) || selected.size > 5 * 1024 * 1024 || selected.size === 0) {
      setError('Chỉ nhận file .xlsx hoặc .json không rỗng, tối đa 5 MB.'); return;
    }
    if (sourceKind.current !== 'json') {
      if (probe.start) setMetadata(previous => ({ ...previous, semesterStartDate: probe.start }));
      return;
    }
    setReading(true);
    try {
      const payload = parseJsonSchedule(await selected.text());
      if (id !== generation.current) return;
      setJsonPayload(payload);
      setMetadata({ teacherId: payload.teacherId, teacherName: payload.teacherName, semesterStartDate: payload.semesterStartDate, schoolId: payload.school.schoolId, schoolName: payload.school.schoolName, colorCode: payload.school.colorCode });
    } catch (err) { if (id === generation.current) setError(err.message); }
    finally { if (id === generation.current) setReading(false); }
  }
  async function inspect(event) {
    event.preventDefault();
    const id = ++generation.current;
    setError(''); setPreview(null); setConfirmed(false);
    if (!file) { setError('Chọn file Excel (.xlsx) hoặc JSON (.json) trước khi xem trước.'); return; }
    if (!/\.(xlsx|json)$/i.test(file.name) || file.size > 5 * 1024 * 1024 || file.size === 0) { setError('Chỉ nhận file .xlsx hoặc .json không rỗng, tối đa 5 MB.'); return; }
    if (probe.status === 'loading') { setError('Đang kiểm tra mốc tuần đã lưu, vui lòng chờ.'); return; }
    if (probe.status !== 'found' && !newTeacher) { setError('Chưa xác định được lịch cũ. Kiểm tra kết nối hoặc xác nhận đây là mã giảng viên mới.'); return; }
    setReading(true);
    try {
      let payload;
      if (isJson) {
        if (!jsonPayload) throw new Error('File JSON chưa hợp lệ. Sửa file rồi chọn lại.');
        payload = parseJsonSchedule(JSON.stringify(jsonPayload), probe.start);
      } else {
        const { parseWorkbook } = await import('./excel');
        payload = buildUpload(await parseWorkbook(await file.arrayBuffer()), metadata, probe.start);
      }
      if (id === generation.current) setPreview(payload);
    } catch (err) { if (id === generation.current) setError(err.message); }
    finally { if (id === generation.current) setReading(false); }
  }
  async function save() {
    if (!canSave) return;
    setBusy(true); setError('');
    try {
      // Recheck the common week anchor immediately before writing an existing teacher.
      let current;
      try { current = await api(`/week/1?teacherId=${encodeURIComponent(preview.teacherId)}`); }
      catch (err) { if (probe.status === 'found' || !newTeacher) throw err; }
      if (current && current.startDate !== preview.semesterStartDate) throw new Error('Mốc tuần trên máy chủ đã thay đổi. Hãy mở lại trang nhập lịch để kiểm tra trước khi lưu.');
      const result = await api('/upload-school', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(preview) });
      if (result.status !== 'SUCCESS') throw new Error('Máy chủ chưa xác nhận lưu lịch thành công.');
      onImported(preview);
    } catch (err) { setError(err.message); setBusy(false); }
  }
  async function downloadTemplate(format) {
    setError('');
    try {
      let blob;
      if (format === 'json') blob = new Blob([JSON.stringify(templateJson(), null, 2) + '\n'], { type: 'application/json;charset=utf-8' });
      else {
        const { templateWorkbook } = await import('./excel');
        blob = new Blob([await templateWorkbook().xlsx.writeBuffer()], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      }
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = `mau-lich-day.${format}`; anchor.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch { setError('Không tạo được file mẫu. Hãy tải lại trang và thử lại.'); }
  }
  return <section className="standalone-panel import-panel"><div className="panel-title"><div><h2>Nhập lịch dạy từ Excel hoặc JSON</h2><p className="muted-text">Lịch đã được phân công tại trường, có thể nhập thêm nhiều trường.</p></div><div className="template-actions"><button className="button" disabled={busy || reading} onClick={() => downloadTemplate('xlsx')}><Download size={16}/>Mẫu Excel</button><button className="button" disabled={busy || reading} onClick={() => downloadTemplate('json')}><Download size={16}/>Mẫu JSON</button></div></div>
    <div className="import-steps"><span>1. Chọn file & trường</span><span>2. Kiểm tra dữ liệu</span><span>3. Xác nhận lưu lịch</span></div>
    <form onSubmit={inspect}>
      <fieldset disabled={busy || reading} className="import-fields">
        <label>Mã giảng viên<input name="teacherId" value={metadata.teacherId} onChange={change} maxLength={50} readOnly={isJson} required placeholder="GV01"/></label>
        <label>Họ tên giảng viên<input name="teacherName" value={metadata.teacherName} onChange={change} maxLength={150} readOnly={isJson} required/></label>
        <label>Mã trường<input name="schoolId" value={metadata.schoolId} onChange={change} maxLength={50} readOnly={isJson} required placeholder="PTIT"/></label>
        <label>Tên trường<input name="schoolName" value={metadata.schoolName} onChange={change} maxLength={150} readOnly={isJson} required placeholder="Học viện Công nghệ Bưu chính Viễn thông"/></label>
        <label>Ngày Thứ Hai của tuần 1<input name="semesterStartDate" type="date" value={metadata.semesterStartDate} onChange={change} readOnly={isJson || probe.status === 'found'} required/><small>{isJson ? 'Lấy từ file JSON; sửa file nếu cần đổi thông tin.' : probe.status === 'found' ? 'Dùng mốc đã lưu, chung cho tất cả trường.' : 'Chọn mốc tuần trước khi nhập lịch lần đầu.'}</small></label>
        <label>Màu trường<input type="color" name="colorCode" value={metadata.colorCode} onChange={change} disabled={isJson}/></label>
        <label className="file-field">File lịch dạy (.xlsx hoặc .json)<input type="file" accept=".xlsx,.json,application/json,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" onChange={chooseFile} required/><small>Tối đa 5 MB. Excel dùng sheet MonHoc và TietHoc. JSON tự điền thông tin giảng viên và trường từ file.</small></label>
      </fieldset>
      {probe.status === 'loading' && <p role="status" className="info-banner">Đang kiểm tra lịch và mốc tuần của giảng viên…</p>}
      {probe.status === 'found' && <p className="info-banner">Mốc tuần 1 đã lưu: {dateLabel(probe.start)}. Nếu file của trường đánh số tuần khác, hãy quy đổi chuỗi tuần về mốc này.</p>}
      {probe.status === 'unknown' && <div className="import-warning"><AlertTriangle size={18}/><div><p>Chưa kiểm tra được lịch của mã này: {probe.message}</p><label className="checkbox-label"><input type="checkbox" checked={newTeacher} disabled={busy || reading} onChange={event => { setNewTeacher(event.target.checked); setPreview(null); setConfirmed(false); }}/>Đây là mã giảng viên mới, chưa có lịch trên máy chủ.</label><small>Nếu đã có lịch, kiểm tra mã và kết nối trước khi tiếp tục để tránh thay đổi mốc tuần chung.</small></div></div>}
      <button className="button primary" disabled={busy || reading || probe.status === 'loading'}><Upload size={16}/>{reading ? 'Đang đọc file…' : 'Đọc file và xem trước'}</button>
    </form>
    {error && <div role="alert" className="import-warning"><AlertTriangle size={18}/><p>{error}</p></div>}
    <details className="import-guide"><summary>Hướng dẫn chuẩn bị file</summary><p>Excel: giữ tên sheet và dòng tiêu đề như file mẫu. MonHoc gồm courseCode, courseName, room, dayOfWeek, startPeriod, numPeriods, weekSchedule. TietHoc gồm period, start, end. Giờ ghi HH:mm; thứ từ 2 đến 8 (8 là Chủ Nhật).</p><p>JSON: dùng cấu trúc trong Mẫu JSON, gồm teacherId, teacherName, semesterStartDate, school (kèm periods) và courses. Chọn file để tự điền thông tin; nếu cần thay đổi, sửa JSON rồi chọn lại. Cả hai định dạng đều được kiểm tra tiết, giờ và tuần trước khi xem trước.</p><p>Chuỗi tuần dùng x hoặc 1 cho tuần có học, dấu - cho tuần nghỉ. Ví dụ xxxx-xxxx nghĩa là học tuần 1–4 và 6–9. Mỗi ký tự ứng với một tuần từ mốc chung. Các dòng ví dụ trong file mẫu cần được thay bằng lịch thực tế.</p></details>
    {preview && <div className="import-preview"><div className="panel-title"><div><h2><CheckCircle2 size={18}/> Dữ liệu đã đọc</h2><p className="muted-text">{file.name} · {preview.courses.length} khung dạy · {preview.school.periods.length} tiết · {preview.school.schoolName}</p></div></div>
      <div className="table-scroll"><table><caption>Lịch môn học</caption><thead><tr><th>Mã môn/lớp</th><th>Môn học</th><th>Thứ</th><th>Tiết</th><th>Giờ</th><th>Phòng</th><th>Chuỗi tuần</th></tr></thead><tbody>{preview.courses.map((course, index) => <tr key={index}><td>{course.courseCode}</td><td>{course.courseName}</td><td>{course.dayOfWeek === 8 ? 'CN' : course.dayOfWeek}</td><td>{course.startPeriod}–{course.startPeriod + course.numPeriods - 1}</td><td>{clock(preview.school.periods.find(period => period.period === course.startPeriod).start)}–{clock(preview.school.periods.find(period => period.period === course.startPeriod + course.numPeriods - 1).end)}</td><td>{course.room}</td><td><code>{course.weekSchedule}</code></td></tr>)}</tbody></table></div>
      <div className="table-scroll"><table><caption>Bảng giờ tiết của trường</caption><thead><tr><th>Tiết</th><th>Bắt đầu</th><th>Kết thúc</th></tr></thead><tbody>{preview.school.periods.map(period => <tr key={period.period}><td>{period.period}</td><td>{period.start}</td><td>{period.end}</td></tr>)}</tbody></table></div>
      <div className="import-warning"><AlertTriangle size={18}/><p>Lưu sẽ thay toàn bộ danh sách môn và bảng giờ tiết của trường <strong>{preview.school.schoolName} ({preview.school.schoolId})</strong> thuộc giảng viên <strong>{preview.teacherId}</strong> nếu đã có. Lịch trường khác được giữ lại. Các buổi trùng giờ vẫn được lưu và cảnh báo khi xem lịch.</p></div>
      <label className="checkbox-label"><input type="checkbox" checked={confirmed} disabled={busy} onChange={event => setConfirmed(event.target.checked)}/>Tôi đã kiểm tra dữ liệu, mốc tuần và đồng ý lưu/thay thế lịch của trường này.</label>
      <div className="import-actions"><button className="button" disabled={busy} onClick={() => { setPreview(null); setConfirmed(false); }}>Hủy xem trước</button><button className="button primary" disabled={!canSave} onClick={save}>{busy ? 'Đang lưu…' : 'Xác nhận lưu và xem lịch'}</button></div>
    </div>}
  </section>;
}
