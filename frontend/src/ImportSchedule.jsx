import React, { useEffect, useRef, useState } from 'react';
import { Upload, Download, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { api, clock, dateLabel } from './schedule';
import { buildUpload } from './scheduleImport';

export default function ImportSchedule({ teacherId, semesterStart, onImported }) {
  const [metadata, setMetadata] = useState({ 
    teacherId: teacherId || '', 
    teacherName: '', 
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
  const generation = useRef(0);
  const canSave = preview && confirmed && !busy && !reading;

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
            setProbe({ status: 'found', start: data.startDate });
            setMetadata(prev => ({ ...prev, semesterStartDate: data.startDate }));
          } else {
            setProbe({ status: 'unknown', start: '' });
          }
        })
        .catch(err => {
          if (active && err.name !== 'AbortError') {
            setProbe({ status: 'unknown', start: '' });
          }
        });
    }, 350);

    return () => { active = false; clearTimeout(timer); controller.abort(); };
  }, [metadata.teacherId]);

  function change(event) {
    generation.current++;
    setMetadata(previous => ({ ...previous, [event.target.name]: event.target.value }));
    setPreview(null); setConfirmed(false); setError('');
  }

  async function chooseFile(event) {
    const id = ++generation.current;
    const selected = event.target.files?.[0] || null;
    setFile(selected); setPreview(null); setConfirmed(false); setError('');
    if (!selected) return;

    if (!/\.xlsx$/i.test(selected.name) || selected.size > 10 * 1024 * 1024 || selected.size === 0) {
      setError('Chỉ nhận file Excel .xlsx không rỗng, tối đa 10 MB.'); 
      return;
    }

    if (probe.start) {
      setMetadata(previous => ({ ...previous, semesterStartDate: probe.start }));
    }
  }

  async function inspect(event) {
    event.preventDefault();
    const id = ++generation.current;
    setError(''); setPreview(null); setConfirmed(false);
    if (!file) { setError('Chọn file Excel (.xlsx) trước khi xem trước.'); return; }
    
    setReading(true);
    try {
      const { parseWorkbook } = await import('./excel');
      const payload = buildUpload(await parseWorkbook(await file.arrayBuffer(), metadata.semesterStartDate), metadata, probe.start || metadata.semesterStartDate);
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
          <p className="muted-text">Hệ thống bóc tách tự động bảng thời khóa biểu 9 cột hoặc mẫu 2 sheet.</p>
        </div>
        <div className="template-actions">
          <button type="button" className="button" disabled={busy || reading} onClick={downloadTemplate}><Download size={16}/>Tải mẫu Excel</button>
        </div>
      </div>
      <div className="import-steps"><span>1. Chọn file Excel & điền thông tin</span><span>2. Kiểm tra dữ liệu đọc</span><span>3. Xác nhận lưu vào CSDL</span></div>
      <form onSubmit={inspect}>
        <fieldset disabled={busy || reading} className="import-fields">
          <label>Mã giảng viên<input name="teacherId" value={metadata.teacherId} readOnly maxLength={50} required placeholder="GV01"/></label>
          <label>Họ tên giảng viên<input name="teacherName" value={metadata.teacherName} onChange={change} maxLength={150} required placeholder="Đào Ngọc Phong"/></label>
          <label>Mã trường<input name="schoolId" value={metadata.schoolId} onChange={change} maxLength={50} required placeholder="PTIT"/></label>
          <label>Tên trường<input name="schoolName" value={metadata.schoolName} onChange={change} maxLength={150} required placeholder="Học viện Công nghệ Bưu chính Viễn thông"/></label>
          <label>Ngày Thứ Hai của tuần 1<input name="semesterStartDate" type="date" value={metadata.semesterStartDate} onChange={change} required/><small>{probe.status === 'found' ? 'Đã đồng bộ mốc tuần 1 từ hệ thống.' : 'Chọn ngày bắt đầu kỳ học.'}</small></label>
          <label>Màu hiển thị lịch<input type="color" name="colorCode" value={metadata.colorCode} onChange={change}/></label>
          <label className="file-field">File lịch dạy (.xlsx)<input type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" onChange={chooseFile} required/><small>Tối đa 10 MB. Hỗ trợ bảng thời khóa biểu 9 cột chuẩn.</small></label>
        </fieldset>
        {probe.status === 'loading' && <p role="status" className="info-banner">Đang kiểm tra lịch trên hệ thống…</p>}
        {probe.status === 'found' && <p className="info-banner">Tuần 1 bắt đầu: {dateLabel(probe.start)}.</p>}
        <button className="button primary" disabled={busy || reading || probe.status === 'loading'}><Upload size={16}/>{reading ? 'Đang đọc file Excel…' : 'Đọc file và xem trước'}</button>
      </form>
      {error && <div role="alert" className="import-warning"><AlertTriangle size={18}/><p>{error}</p></div>}
      <details className="import-guide">
        <summary>Hướng dẫn cấu trúc file Excel</summary>
        <p>File Excel cần chứa các cột: Mã MH, Tên môn học, Thứ, Tiết bắt đầu, Số tiết, Phòng, Thời gian học.</p>
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
          <div className="import-warning">
            <AlertTriangle size={18}/>
            <p>Bấm xác nhận sẽ gửi file Excel trực tiếp lên Backend để lưu vào cơ sở dữ liệu MySQL cho trường <strong>{preview.school.schoolName}</strong>.</p>
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
