import React, { useEffect, useState } from 'react';
import { Upload, CheckCircle2, AlertTriangle } from 'lucide-react';
import { catalogApi } from './schedule';
import { validateFile } from './workflow';
import { catalogLabels, catalogValue } from './catalog';

const actions = { CREATE: 'Thêm mới', UPDATE: 'Cập nhật', UNCHANGED: 'Giữ nguyên', ERROR: 'Có lỗi' };

export default function CatalogImportPanel({ school, kind, departments, sourceFile, disabled, onBusy, onSaved }) {
  const [file, setFile] = useState(sourceFile || null);
  const [preview, setPreview] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [page, setPage] = useState(1);
  useEffect(() => { setFile(sourceFile || null); setPreview(null); setError(''); setNotice(''); setPage(1); }, [sourceFile]);
  async function request(commit = false) {
    if (busy || disabled) return;
    setError(''); setNotice('');
    try { validateFile(file, 'Danh mục Excel'); } catch (error) { setError(error.message); return; }
    setBusy(true); onBusy(true);
    try {
      const body = new FormData(); body.append('file', file);
      if (commit) body.append('snapshot', preview.snapshot);
      const response = await catalogApi(`/${encodeURIComponent(school.schoolCode)}/imports/${kind}/${commit ? 'commit' : 'preview'}`, { method: 'POST', body });
      if (commit) {
        if (response.status !== 'SUCCESS') throw new Error('Máy chủ chưa xác nhận đã lưu. Tải lại danh mục để kiểm tra.');
        setPreview(null); setNotice(`Đã lưu ${response.created} bản ghi mới, cập nhật ${response.updated}, giữ nguyên ${response.unchanged}.`); onSaved();
      } else {
        if (!Array.isArray(response.rows) || response.schoolCode !== school.schoolCode || response.kind !== kind.toUpperCase() || !/^[a-f0-9]{64}$/.test(response.snapshot))
          throw new Error('Bản xem trước không hợp lệ. Hãy thử lại.');
        setPreview(response); setPage(1);
      }
    } catch (error) { setPreview(null); setError(error.status === 409 ? 'Dữ liệu hoặc file đã thay đổi từ lần xem trước. Bấm Xem trước để kiểm tra lại trước khi lưu.' : error.message); }
    finally { setBusy(false); onBusy(false); }
  }
  return <section className="catalog-import" aria-label="Nhập danh mục Excel" aria-busy={busy}>
    <div className="catalog-section-heading"><div><h3><Upload size={18}/>Nhập danh mục Excel</h3><p className="catalog-muted">Tối đa 1000 dòng / 10 MB. Xem trước rồi lưu toàn bộ file trong một lần.</p></div></div>
    <p className="catalog-muted">Cột tùy chọn để trống sẽ giữ thông tin cũ; bản ghi mới dùng mặc định. Trạng thái TRUE/FALSE; loại GV STAFF/VISITING. Muốn đổi khoa hoặc liên kết tài khoản, dùng biểu mẫu sửa.</p>
    {kind === 'lecturers' && <p className="catalog-callout">Xem trước chưa tạo tài khoản. Khi lưu, GV đang hoạt động chưa liên kết sẽ được tạo tên đăng nhập MGV, mật khẩu ban đầu = họ tên viết liền, chữ thường, không dấu + MGV. Nhập lại không đặt lại mật khẩu đã có.</p>}
    {kind === 'assignments' && <p className="catalog-callout">Nhập danh mục giảng viên trước, sau đó nhập phân công theo mã GV trong trường. Có thể dùng file giảng viên–môn–khóa từ luồng xếp lịch hiện tại.</p>}
    <div className="catalog-file-row"><label>File danh mục .xlsx<input type="file" accept=".xlsx" disabled={disabled || busy} onChange={event => { setPreview(null); setNotice(''); setError(''); setFile(event.target.files?.[0] || null); }}/></label>
      <button type="button" className="button" disabled={!file || busy || disabled} onClick={() => request()}><Upload size={16}/>{busy ? 'Đang xử lý…' : 'Xem trước'}</button></div>
    {file && <p className="catalog-muted">File đang chọn: <strong>{file.name}</strong></p>}
    {error && <p className="catalog-error" role="alert"><AlertTriangle size={18}/>{error}</p>}
    {notice && <p className="catalog-success" role="status"><CheckCircle2 size={18}/>{notice}</p>}
    {preview && <div className="catalog-preview">
      <div className="catalog-preview-summary" aria-label="Kết quả xem trước"><span>{preview.created} thêm mới</span><span>{preview.updated} cập nhật</span><span>{preview.unchanged} giữ nguyên</span><span>{preview.invalid} dòng lỗi</span></div>
      <div className="catalog-table-wrap"><table><caption>Xem trước danh mục — chưa lưu</caption><thead><tr><th>Dòng</th><th>Thao tác</th><th>Thông tin sau nhập</th><th>Chi tiết</th></tr></thead><tbody>
        {preview.rows.slice((page - 1) * 25, page * 25).map(row => <tr key={row.rowNumber}><td>{row.rowNumber}</td><td><span className={`catalog-badge ${row.action.toLowerCase()}`}>{actions[row.action]}</span></td>
          <td><dl className="catalog-preview-values">{Object.entries(row.values).filter(([key]) => key !== 'revision').map(([key, value]) => <div key={key}><dt>{catalogLabels[key] || key}</dt><dd>{catalogValue(key, value, departments)}</dd></div>)}</dl></td>
          <td>{row.errors?.length ? <ul className="catalog-row-errors">{row.errors.map((error, index) => <li key={index}>{error}</li>)}</ul>
            : row.action === 'UPDATE' ? <dl className="catalog-preview-values">{Object.keys(row.values).filter(key => Object.hasOwn(row.previous || {}, key) && String(row.previous[key] ?? '') !== String(row.values[key] ?? '')).map(key => <div key={key}><dt>{catalogLabels[key] || key} trước đây</dt><dd>{catalogValue(key, row.previous[key], departments)}</dd></div>)}</dl> : '—'}</td></tr>)}
      </tbody></table></div>
      {preview.rows.length > 25 && <div className="catalog-pagination"><button type="button" className="button" disabled={page <= 1 || busy} onClick={() => setPage(page - 1)}>Trang trước</button><span>Trang {page}/{Math.ceil(preview.rows.length / 25)}</span><button type="button" className="button" disabled={page * 25 >= preview.rows.length || busy} onClick={() => setPage(page + 1)}>Trang sau</button></div>}
      {preview.invalid > 0 ? <p className="catalog-error"><AlertTriangle size={18}/>Sửa tất cả dòng lỗi trong file rồi xem trước lại. Chưa có dữ liệu nào được lưu.</p>
        : !preview.canCommit ? <p className="catalog-muted">Danh mục đã khớp với file; không có thay đổi cần lưu.</p> : null}
      <div className="catalog-actions"><button className="button primary" type="button" disabled={!preview.canCommit || busy || disabled} onClick={() => request(true)}><CheckCircle2 size={16}/>Xác nhận lưu danh mục</button></div>
    </div>}
  </section>;
}
