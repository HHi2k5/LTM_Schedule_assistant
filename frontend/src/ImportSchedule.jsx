import React, { useEffect, useRef, useState } from 'react';
import { Upload, Download, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { api, clock, dateLabel } from './schedule';
import { buildUpload } from './scheduleImport';
import { validateFile } from './workflow';
import FormError from './FormError';

export default function ImportSchedule({ teacherId, teacherName, semesterStart, onImported }) {
  const [metadata, setMetadata] = useState({ 
    teacherId: teacherId || '', 
    teacherName: teacherName || '',
    schoolId: '', 
    schoolName: '', 
    semesterStartDate: semesterStart || '2026-08-10', 
    colorCode: '#2563eb' 
  });
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [reading, setReading] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const [probe, setProbe] = useState({ status: 'idle', start: '' });
  const [probeRevision, setProbeRevision] = useState(0);
  const generation = useRef(0);
  const canSave = preview && confirmed && !busy && !reading && probe.status === 'found';

  useEffect(() => {
    const id = metadata.teacherId.trim();
    const controller = new AbortController();
    let active = true;
    setProbe({ status: id ? 'loading' : 'idle', start: '' });
    
    const timer = setTimeout(() => {
      if (!id) return;
      api(`/week/1?teacherId=${encodeURIComponent(id)}`, { signal: controller.signal })
        .then(data => {
          if (!active) return;
          if (data && data.startDate) {
            setProbe({ status: 'found', start: data.startDate, locked: Boolean(data.lessons?.length) });
            generation.current++; setPreview(null); setConfirmed(false);
            setMetadata(prev => ({ ...prev, semesterStartDate: data.startDate }));
          } else {
            setProbe({ status: 'error', start: '', message: 'Máy chủ chưa trả mốc tuần của giảng viên.' });
          }
        })
        .catch(err => {
          if (active && err.name !== 'AbortError') {
            setProbe({ status: 'error', start: '', message: err.message });
          }
        });
    }, 350);

    return () => { active = false; clearTimeout(timer); controller.abort(); };
  }, [metadata.teacherId, probeRevision]);

  function change(event) {
    generation.current++;
    setMetadata(previous => ({ ...previous, [event.target.name]: event.target.value }));
    setPreview(null); setConfirmed(false); setError('');
  }

  function chooseFile(event) {
    generation.current++;
    const selected = event.target.files?.[0] || null;
    setFile(selected); setPreview(null); setConfirmed(false); setError('');
    if (!selected) return;

    try { validateFile(selected); } catch (err) {
      setFile(null); event.target.value = ''; setError(err.message);
      return;
    }
  }

  async function inspect(event) {
    event.preventDefault();
    const id = ++generation.current;
    setError(''); setPreview(null); setConfirmed(false);
    if (!file) { setError('Chọn file Excel (.xlsx) trước khi xem trước.'); return; }
    if (probe.status !== 'found') { setError('Cần kiểm tra mốc lịch hiện có trước khi xem trước.'); return; }
    
    setReading(true);
    try {
      const { parseWorkbook } = await import('./excel');
      const payload = buildUpload(await parseWorkbook(await file.arrayBuffer(), metadata.semesterStartDate), metadata, probe.locked ? probe.start : '');
      if (id === generation.current) setPreview(payload);
    } catch (err) { 
      if (id === generation.current) setError(err.message); 
    } finally { 
      if (id === generation.current) setReading(false); 
    }
  }

  // Gửi file Excel trực tiếp dạng FormData vào đúng API /upload-excel-teacher của Spring Boot
  async function save() {
    if (!canSave || !file) return;
    setBusy(true); setError('');
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('teacherId', metadata.teacherId.trim());
      formData.append('teacherName', metadata.teacherName.trim());
      formData.append('schoolId', metadata.schoolId.trim());
      formData.append('schoolName', metadata.schoolName.trim());
      formData.append('colorCode', metadata.colorCode);
      formData.append('semesterStartDate', metadata.semesterStartDate);

      // Sử dụng hàm api đã cấu hình sẵn để tự nhận diện domain hiện tại (kể cả ngrok)
      const result = await api('/upload-excel-teacher', {
        method: 'POST',
        body: formData,
      });

      if (result.status !== 'SUCCESS') {
        throw new Error(result.message || 'Máy chủ chưa xác nhận lưu lịch thành công.');
      }

      onImported(preview);
    } catch (err) { 
      setError(err.message); 
      setBusy(false); 
    }
  }

  async function downloadTemplate() {
    setError('');
    try {
      const { templateWorkbook } = await import('./excel');
      const blob = new Blob([await templateWorkbook().xlsx.writeBuffer()], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a'); 
      anchor.href = url; 
      anchor.download = 'mau-lich-day.xlsx'; 
      anchor.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch { 
      setError('Không tạo được file mẫu. Hãy thử lại.'); 
    }
  }

  return (
    <section className="standalone-panel import-panel">
      <div className="panel-title">
        <div>
          <h2>Nhập lịch dạy từ file Excel</h2>
          <p className="muted-text">Dùng bảng môn học 9 cột và sheet TietHoc chứa giờ thực tế của trường.</p>
        </div>
        <div className="template-actions">
          <button type="button" className="button" disabled={busy || reading} onClick={downloadTemplate}><Download size={16}/>Tải mẫu Excel</button>
        </div>
      </div>
      <div className="import-steps"><span>1. Chọn file Excel & điền thông tin</span><span>2. Kiểm tra dữ liệu đọc</span><span>3. Xác nhận lưu lịch</span></div>
      <form onSubmit={inspect}>
        <fieldset disabled={busy || reading} className="import-fields">
          <label>Mã giảng viên<input name="teacherId" value={metadata.teacherId} readOnly maxLength={50} required placeholder="GV01"/></label>
          <label>Họ tên giảng viên<input name="teacherName" value={metadata.teacherName} onChange={change} maxLength={150} required placeholder="Đào Ngọc Phong"/></label>
          <label>Mã trường<input name="schoolId" value={metadata.schoolId} onChange={change} maxLength={50} required placeholder="PTIT"/></label>
          <label>Tên trường<input name="schoolName" value={metadata.schoolName} onChange={change} maxLength={150} required placeholder="Học viện Công nghệ Bưu chính Viễn thông"/></label>
          <label>Ngày Thứ Hai của tuần 1<input name="semesterStartDate" type="date" min="2000-01-01" max="2100-12-31" readOnly={probe.locked} value={metadata.semesterStartDate} onChange={change} required/><small>{probe.locked ? 'Giữ mốc của lịch đã có để các trường cùng tuần tham chiếu.' : 'Chọn ngày bắt đầu kỳ học.'}</small></label>
          <label>Màu hiển thị lịch<input type="color" name="colorCode" value={metadata.colorCode} onChange={change}/></label>
          <label className="file-field">File lịch dạy (.xlsx)<input type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" onChange={chooseFile} required/><small>Tối đa 10 MB. Bắt buộc có MonHoc (9 cột) và TietHoc (period, start, end).</small></label>
        </fieldset>
        {probe.status === 'loading' && <p role="status" className="info-banner">Đang kiểm tra lịch trên hệ thống…</p>}
        {probe.status === 'found' && <p className="info-banner">Tuần 1 bắt đầu: {dateLabel(probe.start)}.{!probe.locked && ' Nếu đã có lịch ở tuần khác, cần giữ mốc này; máy chủ sẽ kiểm tra khi lưu.'}</p>}
        {probe.status === 'error' && <div role="alert" className="import-warning"><p>{probe.message}</p><button type="button" className="button" onClick={() => setProbeRevision(value => value + 1)}>Kiểm tra lại</button></div>}
        <button className="button primary" disabled={busy || reading || probe.status !== 'found' || !file}><Upload size={16}/>{reading ? 'Đang đọc file Excel…' : 'Đọc file và xem trước'}</button>
      </form>
      <FormError message={error}/>
      <details className="import-guide">
        <summary>Hướng dẫn cấu trúc file Excel</summary>
        <p>Sheet MonHoc theo đúng thứ tự 9 cột trong mẫu. Thời gian học là chuỗi tối đa 100 tuần (chữ/số: có học, dấu - hoặc khoảng trắng: nghỉ), hoặc khoảng ngày dd/MM/yyyy - dd/MM/yyyy.</p>
        <p>Sheet TietHoc có 3 cột: period (số tiết 1–30), start, end (HH:mm). Điền giờ thực của trường; mọi tiết được dùng trong MonHoc đều phải có ở đây. Mẫu để trống để bạn điền dữ liệu thực.</p>
      </details>
      {preview && (
        <div className="import-preview">
          <div className="panel-title">
            <div>
              <h2><CheckCircle2 size={18}/> Dữ liệu đã đọc thành công</h2>
              <p className="muted-text">{file?.name} · {preview.courses.length} khung dạy · {preview.school.periods.length} tiết · {preview.school.schoolName}</p>
            </div>
          </div>
          <div className="table-scroll">
            <table>
              <caption>Danh sách môn học bóc tách</caption>
              <thead>
                <tr><th>Mã môn</th><th>Tên môn học</th><th>Thứ</th><th>Tiết</th><th>Phòng</th><th>Chuỗi tuần / Thời gian</th></tr>
              </thead>
              <tbody>
                {preview.courses.map((course, index) => (
                  <tr key={index}>
                    <td>{course.courseCode}</td>
                    <td>{course.courseName}</td>
                    <td>{course.dayOfWeek === 8 ? 'CN' : course.dayOfWeek}</td>
                    <td>{course.startPeriod}–{course.startPeriod + course.numPeriods - 1}</td>
                    <td>{course.room}</td>
                    <td><code>{course.weekSchedule}</code></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="info-banner">Giờ tiết đã đọc: {preview.school.periods.map(period => `${period.period}: ${clock(period.start)}–${clock(period.end)}`).join(' · ')}</p>
          <div className="import-warning">
            <AlertTriangle size={18}/>
            <p>Xác nhận sẽ thay thế lịch của bạn tại <strong>{preview.school.schoolName}</strong> bằng dữ liệu trong file này.</p>
          </div>
          <label className="checkbox-label">
            <input type="checkbox" checked={confirmed} disabled={busy} onChange={event => setConfirmed(event.target.checked)}/>
            Tôi xác nhận dữ liệu trên là chính xác và đồng ý lưu vào hệ thống.
          </label>
          <div className="import-actions">
            <button className="button" disabled={busy} onClick={() => { setPreview(null); setConfirmed(false); }}>Hủy xem trước</button>
            <button className="button primary" disabled={!canSave} onClick={save}>{busy ? 'Đang tải lên và lưu…' : 'Xác nhận lưu và xem lịch'}</button>
          </div>
        </div>
      )}
    </section>
  );
}
