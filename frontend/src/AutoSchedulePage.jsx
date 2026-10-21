import React, { useState } from 'react';
import { Upload, Download, Calendar, CheckCircle2, AlertTriangle, Plus, Trash2, RefreshCw } from 'lucide-react';
import ExcelJS from 'exceljs';
import { api } from './schedule';

export default function AutoSchedulePage({ semesterStart, onScheduled }) {
  const [schoolId, setSchoolId] = useState('PTIT');
  const [schoolName, setSchoolName] = useState('Học viện Công nghệ Bưu chính Viễn thông');
  const [startDate, setStartDate] = useState(semesterStart || '2026-08-10');
  const [file, setFile] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);

  // Hỗ trợ thêm nhanh trên giao diện hoặc gửi file Excel lên Server
  const [manualRows, setManualRows] = useState([
    { courseName: '', teacherInput: '', numClasses: 4 }
  ]);

  function addRow() {
    setManualRows(prev => [...prev, { courseName: '', teacherInput: '', numClasses: 4 }]);
  }

  function removeRow(index) {
    setManualRows(prev => prev.filter((_, i) => i !== index));
  }

  function updateRow(index, field, value) {
    setManualRows(prev => prev.map((row, i) => i === index ? { ...row, [field]: value } : row));
  }

  // Tải file Excel mẫu (Cột 1: Môn học, Cột 2: Giảng viên, Cột 3: Số lớp mở)
  async function downloadAssignmentTemplate() {
    const workbook = new ExcelJS.Workbook();
    const sheet = workbook.addWorksheet('PhanCongGiangDay');
    sheet.addRow(['Môn học', 'Giảng viên (Mã GV - Họ tên)', 'Số lớp mở']);
    sheet.getRow(1).font = { bold: true };
    sheet.columns = [{ width: 38 }, { width: 32 }, { width: 16 }];

    const buffer = await workbook.xlsx.writeBuffer();
    const blob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'mau-phan-cong-xep-lich.xlsx';
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  // Cách 1: Gửi trực tiếp file Excel lên Server để Server xử lý 100%
  async function handleExcelSubmit(event) {
    event.preventDefault();
    if (!file) {
      setError('Vui lòng chọn file Excel phân công giảng dạy (.xlsx).');
      return;
    }
    setBusy(true);
    setError('');
    setResult(null);

    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('schoolId', schoolId.trim());
      formData.append('schoolName', schoolName.trim());
      formData.append('semesterStartDate', startDate);

      const response = await api('/auto-generate-excel', {
        method: 'POST',
        body: formData,
      });

      setResult(response);
      if (onScheduled) onScheduled(response);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  // Cách 2: Gửi từ bảng nhập nhanh trên giao diện
  async function handleManualSubmit(event) {
    event.preventDefault();
    const valid = manualRows.filter(r => r.courseName.trim() && r.teacherInput.trim());
    if (!valid.length) {
      setError('Vui lòng nhập ít nhất 1 dòng Môn học và Giảng viên.');
      return;
    }

    setBusy(true);
    setError('');
    setResult(null);

    try {
      // Đóng gói thành file Excel trên bộ nhớ và gửi lên đúng API /auto-generate-excel của Server
      const workbook = new ExcelJS.Workbook();
      const sheet = workbook.addWorksheet('PhanCong');
      sheet.addRow(['Môn học', 'Giảng viên', 'Số lớp']);
      valid.forEach(r => sheet.addRow([r.courseName.trim(), r.teacherInput.trim(), Number(r.numClasses) || 4]));

      const buffer = await workbook.xlsx.writeBuffer();
      const excelBlob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });

      const formData = new FormData();
      formData.append('file', excelBlob, 'phan-cong.xlsx');
      formData.append('schoolId', schoolId.trim());
      formData.append('schoolName', schoolName.trim());
      formData.append('semesterStartDate', startDate);

      const response = await api('/auto-generate-excel', {
        method: 'POST',
        body: formData,
      });

      setResult(response);
      if (onScheduled) onScheduled(response);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="standalone-panel import-panel">
      <div className="panel-title">
        <div>
          <h2>Tự động xếp lịch giảng dạy toàn trường</h2>
          <p className="muted-text">
            Phân bổ tự động vào 35 phòng học cố định (Tầng 1–7, 5 phòng/tầng). Tự động tránh lịch thỉnh giảng trường khác, gom 1–2 ngày/môn (tối đa 4 lớp/ngày) và đảo tiết 3 ↔ 2 giữa kỳ.
          </p>
        </div>
        <div className="template-actions">
          <button type="button" className="button" disabled={busy} onClick={downloadAssignmentTemplate}>
            <Download size={16} /> Mẫu Excel Phân công
          </button>
        </div>
      </div>

      <div className="import-steps">
        <span>1. Cấu hình trường & kỳ học</span>
        <span>2. Tải file Excel (Môn học - Giảng viên)</span>
        <span>3. Server xếp phòng & lưu lịch</span>
      </div>

      <form onSubmit={handleExcelSubmit}>
        <fieldset disabled={busy} className="import-fields">
          <label>
            Mã trường cần xếp lịch
            <input value={schoolId} onChange={e => setSchoolId(e.target.value)} required placeholder="PTIT" />
          </label>
          <label>
            Tên trường
            <input value={schoolName} onChange={e => setSchoolName(e.target.value)} required />
          </label>
          <label>
            Ngày Thứ Hai của Tuần 1
            <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} required />
          </label>
          <label className="file-field">
            File Excel phân công (.xlsx: Cột 1 = Môn học, Cột 2 = Giảng viên, Cột 3 = Số lớp)
            <input
              type="file"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              onChange={e => { setFile(e.target.files?.[0] || null); setError(''); }}
            />
            <small>Xử lý 100% trên máy chủ. Phòng học cố định từ 101-A2 đến 705-A2 (35 phòng).</small>
          </label>
        </fieldset>

        <div className="import-actions" style={{ marginTop: '0.75rem' }}>
          <button type="submit" className="button primary" disabled={busy || !file}>
            <Upload size={16} /> {busy ? 'Máy chủ đang xếp lịch…' : 'Tải file Excel & Xếp lịch tự động'}
          </button>
        </div>
      </form>

      <hr style={{ margin: '1.75rem 0', border: 'none', borderTop: '1px solid var(--border, #e2e8f0)' }} />

      <form onSubmit={handleManualSubmit}>
        <h3>Hoặc nhập nhanh danh sách Môn học & Giảng viên</h3>
        <p className="muted-text" style={{ marginBottom: '0.75rem' }}>
          Nhập Mã GV kèm Họ tên (VD: <code>GV01 - Đào Ngọc Phong</code>). Nếu giảng viên đã up lịch trường khác, hệ thống sẽ tự động né giờ bận đó.
        </p>

        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Cột 1: Tên môn học</th>
                <th>Cột 2: Giảng viên (Mã GV - Họ tên)</th>
                <th>Số lớp mở</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {manualRows.map((row, idx) => (
                <tr key={idx}>
                  <td>
                    <input
                      value={row.courseName}
                      onChange={e => updateRow(idx, 'courseName', e.target.value)}
                      placeholder="VD: Lập trình mạng"
                      style={{ width: '100%' }}
                    />
                  </td>
                  <td>
                    <input
                      value={row.teacherInput}
                      onChange={e => updateRow(idx, 'teacherInput', e.target.value)}
                      placeholder="VD: GV01 - Đào Ngọc Phong"
                      style={{ width: '100%' }}
                    />
                  </td>
                  <td style={{ width: '120px' }}>
                    <input
                      type="number"
                      min={1}
                      max={8}
                      value={row.numClasses}
                      onChange={e => updateRow(idx, 'numClasses', e.target.value)}
                      style={{ width: '80px' }}
                    />
                  </td>
                  <td style={{ width: '80px' }}>
                    {manualRows.length > 1 && (
                      <button type="button" className="icon-button" onClick={() => removeRow(idx)} title="Xóa dòng">
                        <Trash2 size={16} />
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="import-actions" style={{ marginTop: '1rem', display: 'flex', gap: '0.75rem' }}>
          <button type="button" className="button" onClick={addRow} disabled={busy}>
            <Plus size={16} /> Thêm dòng phân công
          </button>
          <button type="submit" className="button primary" disabled={busy}>
            <RefreshCw size={16} /> {busy ? 'Đang xếp lịch trên Server…' : 'Chạy thuật toán xếp lịch ngay'}
          </button>
        </div>
      </form>

      {error && (
        <div role="alert" className="import-warning" style={{ marginTop: '1rem' }}>
          <AlertTriangle size={18} />
          <p>{error}</p>
        </div>
      )}

      {result && (
        <div className="import-preview" style={{ marginTop: '1.5rem' }}>
          <div className="panel-title">
            <div>
              <h2><CheckCircle2 size={18} /> {result.message}</h2>
              <p className="muted-text">
                Tổng số phòng cố định: {result.totalRoomsAvailable} phòng · Đã xếp thành công: {result.totalClassesScheduled} lớp физический ({result.scheduledClasses?.length || 0} bản ghi giai đoạn)
              </p>
            </div>
          </div>

          {result.unscheduledWarnings?.length > 0 && (
            <div className="import-warning">
              <AlertTriangle size={18} />
              <div>
                <strong>Cảnh báo:</strong>
                <ul>
                  {result.unscheduledWarnings.map((w, i) => <li key={i}>{w}</li>)}
                </ul>
              </div>
            </div>
          )}

          <div className="table-scroll">
            <table>
              <caption>Kết quả phân bổ phòng học & đảo tiết giữa kỳ</caption>
              <thead>
                <tr>
                  <th>Mã GV</th>
                  <th>Giảng viên</th>
                  <th>Môn học & Nhóm</th>
                  <th>Thứ</th>
                  <th>Tiết</th>
                  <th>Phòng (35 phòng)</th>
                  <th>Chuỗi tuần</th>
                  <th>Ghi chú giai đoạn (Đảo tiết 3 ↔ 2)</th>
                </tr>
              </thead>
              <tbody>
                {(result.scheduledClasses || []).map((slot, index) => (
                  <tr key={index}>
                    <td><strong>{slot.teacherId}</strong></td>
                    <td>{slot.teacherName}</td>
                    <td>{slot.courseName}</td>
                    <td>Thứ {slot.dayOfWeek}</td>
                    <td>Tiết {slot.startPeriod}–{slot.startPeriod + slot.numPeriods - 1} ({slot.numPeriods} tiết)</td>
                    <td><span className="badge">{slot.room}</span></td>
                    <td><code>{slot.weekSchedule}</code></td>
                    <td><small>{slot.phaseNote}</small></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}