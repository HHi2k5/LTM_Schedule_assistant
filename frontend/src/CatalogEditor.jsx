import React, { useEffect, useRef, useState } from 'react';
import { Save, X, AlertTriangle } from 'lucide-react';
import { catalogForm, catalogKinds, catalogPayload } from './catalog';

export default function CatalogEditor({ kind, row, departments = [], onSave, onCancel, onReload, onBusy }) {
  const definition = catalogKinds[kind];
  const [form, setForm] = useState(() => catalogForm(kind, row));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const heading = useRef(null);
  const editing = Boolean(row);
  const title = `${editing ? 'Sửa' : 'Thêm'} ${definition.singular}`;
  useEffect(() => { heading.current?.focus(); }, []);
  const change = key => event => {
    const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value;
    setForm(previous => ({ ...previous, [key]: value, ...(key === 'accountTeacherId' && value !== row?.accountTeacherId ? { existingScheduleTeacherId: '' } : {}) }));
  };
  async function submit(event) {
    event.preventDefault(); if (busy) return;
    setBusy(true); onBusy?.(true); setError(null);
    try { await onSave(catalogPayload(kind, form, editing)); }
    catch (error) { setError(error); }
    finally { setBusy(false); onBusy?.(false); }
  }
  return <form className="catalog-editor" aria-label={title} onSubmit={submit}>
    <div className="catalog-section-heading"><h3 ref={heading} tabIndex={-1}>{title}</h3><button type="button" className="icon-button" aria-label="Đóng biểu mẫu" onClick={onCancel} disabled={busy}><X size={18}/></button></div>
    <p className="catalog-muted">{editing ? 'Mã định danh được giữ cố định. Ngừng sử dụng để giữ dữ liệu lịch sử.' : 'Thông tin được lưu trong danh mục của trường đang chọn.'}</p>
    {kind === 'lecturers' && <p className="catalog-callout">Để trống mã tài khoản để tự tạo tài khoản MGV khi lưu GV đang hoạt động. Mật khẩu ban đầu: họ tên viết liền, chữ thường, không dấu + MGV. Nếu đã có tài khoản, nhập đúng mã tài khoản để liên kết; sửa tên hoặc nhập lại không đặt lại mật khẩu.</p>}
    <fieldset disabled={busy}>
      {definition.fields.map(([key, label, type, required, max, min]) => {
        const id = `catalog-field-${key}`;
        const options = type === 'department' ? [['', 'Chưa phân khoa'], ...departments.map(department => [department.id, `${department.name}${department.active ? '' : ' (ngừng sử dụng)'}`])]
          : type === 'lecturerType' ? [['STAFF', 'Cơ hữu'], ['VISITING', 'Thỉnh giảng']]
            : type === 'roomType' ? [['THEORY', 'Lý thuyết'], ['PRACTICE', 'Thực hành / LAB']] : null;
        return <label key={key} htmlFor={id}>{label}{required && <span aria-hidden="true"> *</span>}
          {options ? <select id={id} value={form[key]} required={required} onChange={change(key)}>{options.map(([value, text]) => <option key={value} value={value}>{text}</option>)}</select>
            : <input id={id} value={form[key]} required={required} type={type === 'number' ? 'number' : 'text'} min={min} max={type === 'number' ? max : undefined}
              maxLength={type === 'number' ? undefined : max} step={type === 'number' ? 1 : undefined} readOnly={editing && (type === 'code' || key === 'existingScheduleTeacherId' && Boolean(row?.scheduleTeacherId))} onChange={change(key)}/>}
        </label>;
      })}
      <label className="catalog-checkbox"><input type="checkbox" checked={form.active} onChange={change('active')}/>Đang sử dụng</label>
    </fieldset>
    {kind === 'assignments' && <p className="catalog-muted">Trọng số 0 nghĩa là không nhận loại đó. Khóa sinh viên khác khoa/bộ môn.</p>}
    {error && <div className="catalog-error" role="alert"><AlertTriangle size={18}/><div><p>{error.message}</p>{error.status === 409 && <><p>Nội dung bạn nhập vẫn được giữ. Đóng và tải lại để đối chiếu trước khi sửa tiếp.</p><button className="button" type="button" onClick={() => { onCancel(); onReload?.(); }}>Đóng và tải lại</button></>}</div></div>}
    <div className="catalog-actions"><button type="button" className="button" onClick={onCancel} disabled={busy}>Hủy</button><button className="button primary" disabled={busy}><Save size={16}/>{busy ? 'Đang lưu…' : `Lưu ${definition.singular}`}</button></div>
  </form>;
}
