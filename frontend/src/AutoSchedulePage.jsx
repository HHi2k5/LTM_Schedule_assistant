import React, { useState } from 'react';
import { Upload, Download, Calendar, CheckCircle2, AlertTriangle, Plus, Trash2, RefreshCw, Search, Building2, Users, BookOpen } from 'lucide-react';
import ExcelJS from 'exceljs';
import { api } from './schedule';
import { scheduledWorkbook } from './scheduleExport';

export default function AutoSchedulePage({ semesterStart, onScheduled, onViewTeacherSchedule }) {
  const [schoolId, setSchoolId] = useState('PTIT');
  const [schoolName, setSchoolName] = useState('Học viện Công nghệ Bưu chính Viễn thông');
  const [startDate, setStartDate] = useState(semesterStart || '2026-08-10');

  // 3 file Excel đầu vào
  const [planFile, setPlanFile] = useState(null);
  const [teacherFile, setTeacherFile] = useState(null);
  const [roomFile, setRoomFile] = useState(null);

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState('');

  // Bộ lọc bảng kết quả hoàn chỉnh
  const [filterText, setFilterText] = useState('');
  const [sessionFilter, setSessionFilter] = useState('ALL'); // ALL | THEORY | PRACTICE

  // Nhập nhanh trên giao diện (đóng gói thành 3 file gửi lên Backend)
  const [manualPlans, setManualPlans] = useState([
    { stt: 1, courseCode: '61FIT3DBS', courseName: 'Cơ sở dữ liệu', classSize: 50, cohort: '2024', numClasses: 1, startWeek: 1, dayOfWeek: 2, startPeriod: 1, numPeriods: 3 },
    { stt: 2, courseCode: '61FIT3DBS', courseName: 'Cơ sở dữ liệu', classSize: 150, cohort: '2024', numClasses: 1, startWeek: 1, dayOfWeek: 3, startPeriod: 1, numPeriods: 3 }
  ]);

  const [manualTeachers, setManualTeachers] = useState([
    { courseCode: '61FIT3DBS', courseName: 'Cơ sở dữ liệu', cohort: '2024', teacherId: 'GV01', teacherName: 'Đào Ngọc Phong' },
    { courseCode: '61FIT3DBS', courseName: 'Cơ sở dữ liệu', cohort: '2024', teacherId: 'GV02', teacherName: 'Nguyễn Văn A' }
  ]);

  const [manualRooms, setManualRooms] = useState([
    { roomCode: '101-A2', roomType: 'THEORY', capacity: 150, building: 'A2' },
    { roomCode: '301-A2', roomType: 'THEORY', capacity: 60, building: 'A2' },
    { roomCode: '601-LAB-A2', roomType: 'PRACTICE', capacity: 30, building: 'A2' },
    { roomCode: '602-LAB-A2', roomType: 'PRACTICE', capacity: 30, building: 'A2' }
  ]);

  // ==================== TẢI 3 FILE EXCEL MẪU ====================
  async function downloadPlanTemplate() {
    const workbook = new ExcelJS.Workbook();
    const sheet = workbook.addWorksheet('KeHoachMoLop');
    sheet.addRow(['KẾ HOẠCH MỞ LỚP HỌC KỲ I NĂM HỌC 2026-2027 KHOA CÔNG NGHỆ THÔNG TIN']);
    sheet.addRow([
      'STT', 'Mã môn', 'Tên học phần', 'Sĩ số', 'Khóa',
      'Số lớp mở', 'Tuần bắt đầu', 'Tiết BĐ', 'Số tiết',
      'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Số tín chỉ'
    ]);
    sheet.addRow([92, '61FIT3DBS', 'Cơ sở dữ liệu', 50, '2024', 1, 1, 1, 3, 'x', '', '', '', '', '']);
    sheet.addRow([93, '61FIT3DBS', 'Cơ sở dữ liệu', 50, '2024', 1, 1, 4, 3, '', 'x', '', '', '', '']);
    sheet.addRow([97, '61FIT3DBS', 'Cơ sở dữ liệu', 150, '2024', 1, 1, 1, 3, '', '', 'x', '', '', '']);
    sheet.addRow([98, '61FIT3CGR', 'Đồ họa máy tính', 60, '2024', 1, 1, 7, 3, '', 'x', '', '', '', '']);
    sheet.getRow(1).font = { bold: true, size: 13 };
    sheet.getRow(2).font = { bold: true };
    await saveWorkbookBlob(workbook, '1-mau-ke-hoach-mo-lop.xlsx');
  }

  async function downloadTeacherTemplate() {
    const workbook = new ExcelJS.Workbook();
    const sheet = workbook.addWorksheet('DanhSachGiangVien');
    sheet.addRow(['Mã môn', 'Tên học phần', 'Khóa', 'Mã GV', 'Họ tên giảng viên']);
    sheet.addRow(['61FIT3DBS', 'Cơ sở dữ liệu', '2024', 'GV01', 'Đào Ngọc Phong']);
    sheet.addRow(['61FIT3DBS', 'Cơ sở dữ liệu', '2024', 'GV02', 'Nguyễn Văn A']);
    sheet.addRow(['61FIT3CGR', 'Đồ họa máy tính', '2024', 'GV03', 'Trần Thị B']);
    sheet.getRow(1).font = { bold: true };
    sheet.columns = [{ width: 16 }, { width: 28 }, { width: 12 }, { width: 14 }, { width: 26 }];
    await saveWorkbookBlob(workbook, '2-mau-danh-sach-giang-vien.xlsx');
  }

  async function downloadRoomTemplate() {
    const workbook = new ExcelJS.Workbook();
    const sheet = workbook.addWorksheet('DanhSachPhongHoc');
    sheet.addRow(['Mã phòng', 'Loại phòng (THEORY / PRACTICE)', 'Sức chứa', 'Tòa nhà']);
    sheet.addRow(['101-A2', 'THEORY', 150, 'A2']);
    sheet.addRow(['102-A2', 'THEORY', 150, 'A2']);
    sheet.addRow(['201-A2', 'THEORY', 100, 'A2']);
    sheet.addRow(['301-A2', 'THEORY', 60, 'A2']);
    sheet.addRow(['302-A2', 'THEORY', 60, 'A2']);
    sheet.addRow(['601-LAB-A2', 'PRACTICE', 30, 'A2']);
    sheet.addRow(['602-LAB-A2', 'PRACTICE', 30, 'A2']);
    sheet.addRow(['603-LAB-A2', 'PRACTICE', 30, 'A2']);
    sheet.getRow(1).font = { bold: true };
    sheet.columns = [{ width: 18 }, { width: 30 }, { width: 14 }, { width: 14 }];
    await saveWorkbookBlob(workbook, '3-mau-danh-sach-phong-hoc.xlsx');
  }

  // Xuất kết quả phân công hoàn chỉnh ra file Excel
  async function exportResultToExcel() {
    if (busy || exporting || !result?.scheduledClasses?.length) return;
    setExporting(true); setExportError('');
    try {
      const { workbook, filename } = scheduledWorkbook(result);
      await saveWorkbookBlob(workbook, filename);
    } catch (err) { setExportError(err.message); }
    finally { setExporting(false); }
  }

  async function saveWorkbookBlob(workbook, filename) {
    const buffer = await workbook.xlsx.writeBuffer();
    const blob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  // ==================== GỬI 3 FILE EXCEL LÊN BACKEND ====================
  async function handleExcelSubmit(event) {
    event.preventDefault();
    if (busy || exporting) return;
    if (!planFile) {
      setError('Vui lòng chọn ít nhất File 1: Kế hoạch mở lớp (.xlsx).');
      return;
    }
    setBusy(true);
    setError('');
    setResult(null);
    setExportError('');

    try {
      const formData = new FormData();
      formData.append('planFile', planFile);
      if (teacherFile) formData.append('teacherFile', teacherFile);
      if (roomFile) formData.append('roomFile', roomFile);
      formData.append('schoolId', schoolId.trim());
      formData.append('schoolName', schoolName.trim());
      formData.append('semesterStartDate', startDate);

      const response = await api('/auto-generate-3files', {
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

  // ==================== ĐÓNG GÓI BẢNG NHẬP NHANH THÀNH 3 FILE EXCEL ====================
  async function handleManualSubmit(event) {
    event.preventDefault();
    if (busy || exporting) return;
    const validPlans = manualPlans.filter(p => p.courseCode.trim() && p.courseName.trim());
    if (!validPlans.length) {
      setError('Vui lòng nhập ít nhất 1 dòng trong bảng Kế hoạch mở lớp.');
      return;
    }

    setBusy(true);
    setError('');
    setResult(null);
    setExportError('');

    try {
      // 1. Tạo Blob cho File 1 (Kế hoạch mở lớp)
      const wbPlan = new ExcelJS.Workbook();
      const shPlan = wbPlan.addWorksheet('KeHoachMoLop');
      shPlan.addRow(['KẾ HOẠCH MỞ LỚP']);
      shPlan.addRow([
        'STT', 'Mã môn', 'Tên học phần', 'Sĩ số', 'Khóa',
        'Số lớp mở', 'Tuần bắt đầu', 'Tiết BĐ', 'Số tiết',
        'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Số tín chỉ'
      ]);
      validPlans.forEach((p, idx) => {
        const dayMarks = [2, 3, 4, 5, 6, 7].map(d => Number(p.dayOfWeek) === d ? 'x' : '');
        shPlan.addRow([
          idx + 1,
          p.courseCode.trim(),
          p.courseName.trim(),
          Number(p.classSize) || 50,
          p.cohort.trim() || '2024',
          Number(p.numClasses) || 1,
          Number(p.startWeek) || 1,
          Number(p.startPeriod) || 1,
          Number(p.numPeriods) || 3,
          ...dayMarks,
          p.credits === '' || p.credits == null ? null : Number(p.credits)
        ]);
      });
      const planBlob = new Blob([await wbPlan.xlsx.writeBuffer()], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });

      // 2. Tạo Blob cho File 2 (Danh sách Giảng viên)
      const wbTeacher = new ExcelJS.Workbook();
      const shTeacher = wbTeacher.addWorksheet('GiangVien');
      shTeacher.addRow(['Mã môn', 'Tên học phần', 'Khóa', 'Mã GV', 'Họ tên giảng viên']);
      manualTeachers.filter(t => t.courseCode.trim() && t.teacherId.trim()).forEach(t => {
        shTeacher.addRow([t.courseCode.trim(), t.courseName.trim(), t.cohort.trim() || '2024', t.teacherId.trim(), t.teacherName.trim()]);
      });
      const teacherBlob = new Blob([await wbTeacher.xlsx.writeBuffer()], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });

      // 3. Tạo Blob cho File 3 (Danh sách Phòng học)
      const wbRoom = new ExcelJS.Workbook();
      const shRoom = wbRoom.addWorksheet('PhongHoc');
      shRoom.addRow(['Mã phòng', 'Loại phòng', 'Sức chứa', 'Tòa nhà']);
      manualRooms.filter(r => r.roomCode.trim()).forEach(r => {
        shRoom.addRow([r.roomCode.trim(), r.roomType, Number(r.capacity) || 60, r.building.trim() || 'A2']);
      });
      const roomBlob = new Blob([await wbRoom.xlsx.writeBuffer()], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });

      const formData = new FormData();
      formData.append('planFile', planBlob, 'ke-hoach-mo-lop.xlsx');
      formData.append('teacherFile', teacherBlob, 'danh-sach-giang-vien.xlsx');
      formData.append('roomFile', roomBlob, 'danh-sach-phong-hoc.xlsx');
      formData.append('schoolId', schoolId.trim());
      formData.append('schoolName', schoolName.trim());
      formData.append('semesterStartDate', startDate);

      const response = await api('/auto-generate-3files', {
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

  // Lọc danh sách kết quả hiển thị
  const filteredSlots = (result?.scheduledClasses || []).filter(slot => {
    if (sessionFilter !== 'ALL' && slot.sessionType !== sessionFilter) return false;
    if (!filterText.trim()) return true;
    const q = filterText.toLocaleLowerCase('vi');
    return `${slot.teacherId} ${slot.teacherName} ${slot.courseCode} ${slot.courseName} ${slot.room} ${slot.cohort || ''} ${slot.classGroup || ''}`
      .toLocaleLowerCase('vi')
      .includes(q);
  });

  return (
    <section className="standalone-panel import-panel">
      <div className="panel-title">
        <div>
          <h2>Tự động xếp lịch & chia phòng toàn trường (Bộ 3 File Excel)</h2>
          <p className="muted-text">
            Đọc Kế hoạch mở lớp + Danh sách Giảng viên + Danh mục Phòng học. Tự động chia đều lớp cho giảng viên (ưu tiên gom gọn 1–2 ngày/môn), chọn phòng theo Sĩ số (Best-Fit) và tách Tổ Thực hành phòng LAB.
          </p>
        </div>
        <div className="template-actions" style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          <button type="button" className="button" disabled={busy || exporting} onClick={downloadPlanTemplate}>
            <Download size={15} /> 1. Mẫu Kế hoạch mở lớp
          </button>
          <button type="button" className="button" disabled={busy || exporting} onClick={downloadTeacherTemplate}>
            <Download size={15} /> 2. Mẫu DS Giảng viên
          </button>
          <button type="button" className="button" disabled={busy || exporting} onClick={downloadRoomTemplate}>
            <Download size={15} /> 3. Mẫu DS Phòng học
          </button>
        </div>
      </div>

      <div className="import-steps">
        <span>1. File Kế hoạch mở lớp (Sĩ số, Khóa, Thứ, Tiết)</span>
        <span>2. File DS Giảng viên (Môn - Khóa)</span>
        <span>3. File DS Phòng học (Lý thuyết & LAB)</span>
      </div>

      <form onSubmit={handleExcelSubmit}>
        <fieldset disabled={busy || exporting} className="import-fields">
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
            <strong>File 1 (Bắt buộc): Kế hoạch mở lớp (.xlsx)</strong>
            <input
              type="file"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              onChange={e => { setPlanFile(e.target.files?.[0] || null); setError(''); }}
              required
            />
            <small>Gồm: STT, Mã môn, Tên học phần, Sĩ số, Khóa, Số lớp mở, Tuần BĐ, Tiết BĐ, Số tiết, Thứ 2..Thứ 7.</small>
          </label>

          <label className="file-field">
            <strong>File 2: Danh sách Giảng viên - Môn học - Khóa (.xlsx)</strong>
            <input
              type="file"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              onChange={e => { setTeacherFile(e.target.files?.[0] || null); setError(''); }}
            />
            <small>Hệ thống chia đều số lớp của cùng Mã môn & Khóa cho các giảng viên và gom lịch dạy vào 1–2 ngày.</small>
          </label>

          <label className="file-field">
            <strong>File 3: Danh sách Phòng học & Sức chứa (.xlsx)</strong>
            <input
              type="file"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              onChange={e => { setRoomFile(e.target.files?.[0] || null); setError(''); }}
            />
            <small>Lưu vào bảng Rooms trong MySQL (Phòng Lý thuyết & Phòng Thực hành LAB). Nếu để trống sẽ dùng 35 phòng trong DB.</small>
          </label>
        </fieldset>

        <div className="import-actions" style={{ marginTop: '0.75rem' }}>
          <button type="submit" className="button primary" disabled={busy || exporting || !planFile}>
            <Upload size={16} /> {busy ? 'Máy chủ đang phân tích 3 file & xếp phòng…' : 'Tải 3 File Excel & Chạy xếp lịch tự động'}
          </button>
        </div>
      </form>

      <details style={{ marginTop: '1.5rem' }}>
        <summary style={{ cursor: 'pointer', fontWeight: 600 }}>
          Hoặc nhập/chỉnh sửa nhanh dữ liệu 3 bảng trực tiếp trên giao diện
        </summary>
        <form onSubmit={handleManualSubmit} style={{ marginTop: '1rem' }}>
          {/* BẢNG 1: KẾ HOẠCH MỞ LỚP */}
          <h4><BookOpen size={16} /> Bảng 1: Kế hoạch mở lớp</h4>
          <div className="table-scroll" style={{ marginBottom: '1rem' }}>
            <table>
              <thead>
                <tr>
                  <th>Mã môn</th>
                  <th>Tên học phần</th>
                  <th>Số tín chỉ</th>
                  <th>Sĩ số</th>
                  <th>Khóa</th>
                  <th>Số lớp</th>
                  <th>Tuần BĐ</th>
                  <th>Thứ</th>
                  <th>Tiết BĐ</th>
                  <th>Số tiết</th>
                  <th>Xóa</th>
                </tr>
              </thead>
              <tbody>
                {manualPlans.map((row, idx) => (
                  <tr key={idx}>
                    <td><input value={row.courseCode} onChange={e => setManualPlans(prev => prev.map((r, i) => i === idx ? { ...r, courseCode: e.target.value } : r))} style={{ width: '105px' }} /></td>
                    <td><input value={row.courseName} onChange={e => setManualPlans(prev => prev.map((r, i) => i === idx ? { ...r, courseName: e.target.value } : r))} style={{ width: '160px' }} /></td>
                    <td><input type="number" min={1} max={30} value={row.credits ?? ''} onChange={e => setManualPlans(prev => prev.map((r, i) => i === idx ? { ...r, credits: e.target.value } : r))} style={{ width: '70px' }} /></td>
                    <td><input type="number" min={10} max={300} value={row.classSize} onChange={e => setManualPlans(prev => prev.map((r, i) => i === idx ? { ...r, classSize: e.target.value } : r))} style={{ width: '70px' }} /></td>
                    <td><input value={row.cohort} onChange={e => setManualPlans(prev => prev.map((r, i) => i === idx ? { ...r, cohort: e.target.value } : r))} style={{ width: '70px' }} /></td>
                    <td><input type="number" min={1} max={10} value={row.numClasses} onChange={e => setManualPlans(prev => prev.map((r, i) => i === idx ? { ...r, numClasses: e.target.value } : r))} style={{ width: '60px' }} /></td>
                    <td><input type="number" min={1} max={16} value={row.startWeek} onChange={e => setManualPlans(prev => prev.map((r, i) => i === idx ? { ...r, startWeek: e.target.value } : r))} style={{ width: '60px' }} /></td>
                    <td>
                      <select value={row.dayOfWeek} onChange={e => setManualPlans(prev => prev.map((r, i) => i === idx ? { ...r, dayOfWeek: Number(e.target.value) } : r))}>
                        {[2, 3, 4, 5, 6, 7].map(d => <option key={d} value={d}>Thứ {d}</option>)}
                      </select>
                    </td>
                    <td><input type="number" min={1} max={12} value={row.startPeriod} onChange={e => setManualPlans(prev => prev.map((r, i) => i === idx ? { ...r, startPeriod: e.target.value } : r))} style={{ width: '60px' }} /></td>
                    <td><input type="number" min={1} max={5} value={row.numPeriods} onChange={e => setManualPlans(prev => prev.map((r, i) => i === idx ? { ...r, numPeriods: e.target.value } : r))} style={{ width: '60px' }} /></td>
                    <td>
                      {manualPlans.length > 1 && (
                        <button type="button" className="icon-button" onClick={() => setManualPlans(prev => prev.filter((_, i) => i !== idx))}><Trash2 size={15} /></button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <button type="button" className="button" onClick={() => setManualPlans(prev => [...prev, { stt: prev.length + 1, courseCode: '', courseName: '', classSize: 60, cohort: '2024', numClasses: 1, startWeek: 1, dayOfWeek: 2, startPeriod: 1, numPeriods: 3 }])} style={{ marginBottom: '1.25rem' }}>
            <Plus size={15} /> Thêm dòng Kế hoạch mở lớp
          </button>

          {/* BẢNG 2: DANH SÁCH GIẢNG VIÊN */}
          <h4><Users size={16} /> Bảng 2: Danh sách Giảng viên - Môn học - Khóa</h4>
          <div className="table-scroll" style={{ marginBottom: '1rem' }}>
            <table>
              <thead>
                <tr>
                  <th>Mã môn</th>
                  <th>Tên học phần</th>
                  <th>Khóa</th>
                  <th>Mã GV</th>
                  <th>Họ tên giảng viên</th>
                  <th>Xóa</th>
                </tr>
              </thead>
              <tbody>
                {manualTeachers.map((row, idx) => (
                  <tr key={idx}>
                    <td><input value={row.courseCode} onChange={e => setManualTeachers(prev => prev.map((r, i) => i === idx ? { ...r, courseCode: e.target.value } : r))} /></td>
                    <td><input value={row.courseName} onChange={e => setManualTeachers(prev => prev.map((r, i) => i === idx ? { ...r, courseName: e.target.value } : r))} /></td>
                    <td><input value={row.cohort} onChange={e => setManualTeachers(prev => prev.map((r, i) => i === idx ? { ...r, cohort: e.target.value } : r))} style={{ width: '80px' }} /></td>
                    <td><input value={row.teacherId} onChange={e => setManualTeachers(prev => prev.map((r, i) => i === idx ? { ...r, teacherId: e.target.value } : r))} style={{ width: '90px' }} /></td>
                    <td><input value={row.teacherName} onChange={e => setManualTeachers(prev => prev.map((r, i) => i === idx ? { ...r, teacherName: e.target.value } : r))} /></td>
                    <td>
                      {manualTeachers.length > 1 && (
                        <button type="button" className="icon-button" onClick={() => setManualTeachers(prev => prev.filter((_, i) => i !== idx))}><Trash2 size={15} /></button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <button type="button" className="button" onClick={() => setManualTeachers(prev => [...prev, { courseCode: '', courseName: '', cohort: '2024', teacherId: '', teacherName: '' }])} style={{ marginBottom: '1.25rem' }}>
            <Plus size={15} /> Thêm dòng Giảng viên
          </button>

          {/* BẢNG 3: DANH MỤC PHÒNG HỌC */}
          <h4><Building2 size={16} /> Bảng 3: Danh sách Phòng học & Sức chứa</h4>
          <div className="table-scroll" style={{ marginBottom: '1rem' }}>
            <table>
              <thead>
                <tr>
                  <th>Mã phòng</th>
                  <th>Loại phòng</th>
                  <th>Sức chứa tối đa</th>
                  <th>Tòa nhà</th>
                  <th>Xóa</th>
                </tr>
              </thead>
              <tbody>
                {manualRooms.map((row, idx) => (
                  <tr key={idx}>
                    <td><input value={row.roomCode} onChange={e => setManualRooms(prev => prev.map((r, i) => i === idx ? { ...r, roomCode: e.target.value } : r))} /></td>
                    <td>
                      <select value={row.roomType} onChange={e => setManualRooms(prev => prev.map((r, i) => i === idx ? { ...r, roomType: e.target.value } : r))}>
                        <option value="THEORY">Lý thuyết (THEORY)</option>
                        <option value="PRACTICE">Thực hành phòng máy (PRACTICE / LAB)</option>
                      </select>
                    </td>
                    <td><input type="number" min={15} max={300} value={row.capacity} onChange={e => setManualRooms(prev => prev.map((r, i) => i === idx ? { ...r, capacity: e.target.value } : r))} style={{ width: '90px' }} /></td>
                    <td><input value={row.building} onChange={e => setManualRooms(prev => prev.map((r, i) => i === idx ? { ...r, building: e.target.value } : r))} style={{ width: '80px' }} /></td>
                    <td>
                      {manualRooms.length > 1 && (
                        <button type="button" className="icon-button" onClick={() => setManualRooms(prev => prev.filter((_, i) => i !== idx))}><Trash2 size={15} /></button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div style={{ display: 'flex', gap: '0.75rem' }}>
            <button type="button" className="button" onClick={() => setManualRooms(prev => [...prev, { roomCode: '', roomType: 'THEORY', capacity: 60, building: 'A2' }])}>
              <Plus size={15} /> Thêm Phòng học
            </button>
            <button type="submit" className="button primary" disabled={busy || exporting}>
              <RefreshCw size={16} /> {busy ? 'Đang chạy thuật toán…' : 'Xếp lịch từ 3 bảng dữ liệu trên'}
            </button>
          </div>
        </form>
      </details>

      {error && (
        <div role="alert" className="import-warning" style={{ marginTop: '1rem' }}>
          <AlertTriangle size={18} />
          <p>{error}</p>
        </div>
      )}

      {/* ==================== BẢNG KẾT QUẢ PHÂN CÔNG HOÀN CHỈNH ==================== */}
      {exportError && <p role="alert" className="error-text">{exportError}</p>}
      {result?.scheduledClasses?.some(slot => slot.credits == null) && <p className="info-banner">Một số môn chưa có số tín chỉ; cột này sẽ để trống trong Excel.</p>}
      {result && (
        <div className="import-preview" style={{ marginTop: '1.5rem' }}>
          <div className="panel-title">
            <div>
              <h2><CheckCircle2 size={18} /> {result.message}</h2>
              <p className="muted-text">
                Tổng số phòng trong hệ thống: <strong>{result.totalRoomsAvailable} phòng</strong> · Tổng số ca học (Lý thuyết + Tổ Thực hành) đã xếp: <strong>{result.totalClassesScheduled} ca</strong>
              </p>
            </div>
            <div className="template-actions">
              <button type="button" className="button primary" disabled={busy || exporting || !result.scheduledClasses?.length} onClick={exportResultToExcel}>
                <Download size={16} /> Xuất Bảng Phân Công Hoàn Chỉnh (.xlsx)
              </button>
            </div>
          </div>

          {result.unscheduledWarnings?.length > 0 && (
            <div className="import-warning">
              <AlertTriangle size={18} />
              <div>
                <strong>Cảnh báo ({result.unscheduledWarnings.length}):</strong>
                <ul>
                  {result.unscheduledWarnings.map((w, i) => <li key={i}>{w}</li>)}
                </ul>
              </div>
            </div>
          )}

          <div className="filter-bar" style={{ marginBottom: '0.75rem' }}>
            <label className="search-field">
              <Search size={17} />
              <input
                placeholder="Tìm theo giảng viên, mã môn, tên môn, phòng học, khóa…"
                value={filterText}
                onChange={e => setFilterText(e.target.value)}
              />
            </label>
            <select value={sessionFilter} onChange={e => setSessionFilter(e.target.value)}>
              <option value="ALL">Tất cả buổi (Lý thuyết + Thực hành)</option>
              <option value="THEORY">Chỉ buổi Lý thuyết (Cả lớp)</option>
              <option value="PRACTICE">Chỉ buổi Thực hành (Chia theo Tổ)</option>
            </select>
          </div>

          <div className="table-scroll">
            <table>
              <caption>Bản phân công hoàn chỉnh: Giảng viên · Môn học · Lớp/Tổ · Phòng học · Thứ/Tiết</caption>
              <thead>
                <tr>
                  <th>Mã GV</th>
                  <th>Giảng viên</th>
                  <th>Mã môn</th>
                  <th>Học phần & Nhóm</th>
                  <th>Khóa</th>
                  <th>Lớp / Tổ</th>
                  <th>Loại hình</th>
                  <th>Sĩ số</th>
                  <th>Phòng học (Sức chứa)</th>
                  <th>Thứ</th>
                  <th>Tiết học</th>
                  <th>Xem lịch GV</th>
                </tr>
              </thead>
              <tbody>
                {filteredSlots.map((slot, index) => {
                  const isPractice = slot.sessionType === 'PRACTICE';
                  return (
                    <tr key={index}>
                      <td><strong>{slot.teacherId}</strong></td>
                      <td>{slot.teacherName}</td>
                      <td><code>{slot.courseCode}</code></td>
                      <td>{slot.courseName}</td>
                      <td>{slot.cohort || '—'}</td>
                      <td>
                        <strong>{slot.classGroup}</strong>
                        {slot.subGroup && <small style={{ display: 'block', opacity: 0.8 }}>{slot.subGroup}</small>}
                      </td>
                      <td>
                        <span
                          className="badge"
                          style={{
                            background: isPractice ? '#0284c7' : '#16a34a',
                            color: '#fff'
                          }}
                        >
                          {isPractice ? 'Thực hành (LAB)' : 'Lý thuyết'}
                        </span>
                      </td>
                      <td><strong>{slot.studentCount || '—'} SV</strong></td>
                      <td>
                        <span className="badge">{slot.room}</span>
                        {slot.roomCapacity ? <small style={{ display: 'block', opacity: 0.75 }}>Tối đa {slot.roomCapacity} chỗ</small> : null}
                      </td>
                      <td><strong>{slot.dayOfWeek === 8 ? 'CN' : `Thứ ${slot.dayOfWeek}`}</strong></td>
                      <td>Tiết {slot.startPeriod}–{slot.startPeriod + slot.numPeriods - 1} ({slot.numPeriods}t)</td>
                      <td>
                        {onViewTeacherSchedule && (
                          <button
                            type="button"
                            className="button compact"
                            onClick={() => onViewTeacherSchedule(slot.teacherId)}
                          >
                            <Calendar size={14} /> Xem TKB
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}
