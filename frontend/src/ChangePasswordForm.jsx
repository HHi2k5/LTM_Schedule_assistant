import React, { useState } from 'react';
import { CheckCircle2, KeyRound } from 'lucide-react';
import { api } from './schedule';
import { validateNewPassword } from './workflow';
import FormError from './FormError';

const emptyForm = { currentPassword: '', newPassword: '', confirmPassword: '' };

export default function ChangePasswordForm({ username }) {
  const [form, setForm] = useState(emptyForm);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  function change(event) {
    setForm(previous => ({ ...previous, [event.target.name]: event.target.value }));
    setError(''); setSuccess('');
  }

  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    setError(''); setSuccess('');
    try {
      if (!form.currentPassword) throw new Error('Nhập mật khẩu hiện tại.');
      validateNewPassword(form.newPassword);
      if (form.newPassword === form.currentPassword) throw new Error('Mật khẩu mới phải khác mật khẩu hiện tại.');
      if (form.newPassword !== form.confirmPassword) throw new Error('Xác nhận mật khẩu mới chưa khớp.');
      setBusy(true);
      const response = await api('/auth/change-password', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ currentPassword: form.currentPassword, newPassword: form.newPassword }),
      });
      if (response.status !== 'SUCCESS') throw new Error(response.message || 'Máy chủ chưa xác nhận đổi mật khẩu.');
      setForm(emptyForm); setSuccess('Đổi mật khẩu thành công. Dùng mật khẩu mới cho lần đăng nhập tiếp theo.');
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }

  return <section className="password-settings" aria-labelledby="password-settings-title">
    <h3 id="password-settings-title"><KeyRound size={20}/>Đổi mật khẩu</h3>
    <p className="muted-text">Đổi mật khẩu cho tài khoản {username}. Các phiên đã đăng nhập trên thiết bị khác vẫn hoạt động đến khi hết hạn.</p>
    <form onSubmit={submit}>
      <input type="text" name="username" value={username} autoComplete="username" readOnly hidden/>
      <fieldset className="import-fields password-fields" disabled={busy}>
        <label>Mật khẩu hiện tại<input name="currentPassword" type="password" autoComplete="current-password" value={form.currentPassword} onChange={change} required/></label>
        <label>Mật khẩu mới<input name="newPassword" type="password" autoComplete="new-password" value={form.newPassword} onChange={change} required minLength={8}/><small>Ít nhất 8 ký tự, tối đa 72 byte UTF-8.</small></label>
        <label>Xác nhận mật khẩu mới<input name="confirmPassword" type="password" autoComplete="new-password" value={form.confirmPassword} onChange={change} required minLength={8}/></label>
      </fieldset>
      <FormError message={error}/>
      {success && <p className="password-success info-banner" role="status"><CheckCircle2 size={18}/>{success}</p>}
      <button type="submit" className="button primary" disabled={busy}><KeyRound size={16}/>{busy ? 'Đang đổi mật khẩu…' : 'Lưu mật khẩu mới'}</button>
    </form>
  </section>;
}
