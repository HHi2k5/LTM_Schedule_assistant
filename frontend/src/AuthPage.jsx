import React, { useState } from 'react';
import { GraduationCap, LogIn } from 'lucide-react';
import { api } from './schedule';
import { setAccessToken } from './authSession';
import FormError from './FormError';

export default function AuthPage({ onAuthenticated }) {
  const [form, setForm] = useState({ username: '', password: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  function handleChange(event) {
    const { name, value } = event.target;
    setForm(previous => ({ ...previous, [name]: value })); setError('');
  }
  async function handleSubmit(event) {
    event.preventDefault(); if (loading) return;
    setLoading(true); setError('');
    try {
      if (!form.username.trim()) throw new Error('Điền tên đăng nhập.');
      const result = await api('/auth/login', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: form.username.trim(), password: form.password }),
      });
      if (result.status !== 'SUCCESS') throw new Error(result.message || 'Đăng nhập thất bại.');
      if (!result.accessToken || !['ADMIN', 'TEACHER'].includes(result.role)) throw new Error('Máy chủ chưa trả phiên đăng nhập hợp lệ.');
      setAccessToken(result.accessToken);
      onAuthenticated({ teacherId: result.teacherId, fullName: result.fullName, username: result.username,
        role: result.role, semesterStartDate: result.semesterStartDate || '2026-08-10' });
    } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }
  return <div className="app-shell auth-shell">
    <section className="standalone-panel import-panel auth-card" aria-label="Tài khoản Campus">
      <span className="auth-eyebrow">LỊCH DẠY · PHÂN CÔNG · ĐIỀU CHỈNH</span>
      <div className="panel-title"><div className="auth-title"><span className="brand-mark"><GraduationCap size={26}/></span>
        <div><h2>Đăng nhập hệ thống</h2><p className="muted-text">Campus · Không gian quản lý và xếp lịch giảng dạy</p></div>
      </div></div>
      <p className="muted-text">Sử dụng tài khoản do nhà trường cấp. Nếu chưa nhận được tài khoản, liên hệ quản trị nhà trường.</p>
      <form onSubmit={handleSubmit} aria-label="Đăng nhập bằng tài khoản được cấp">
        <fieldset disabled={loading} className="import-fields">
          <label>Tên đăng nhập<input name="username" value={form.username} onChange={handleChange} placeholder="MGV hoặc tên đăng nhập được cấp" required maxLength={50} autoComplete="username"/></label>
          <label>Mật khẩu<input type="password" name="password" value={form.password} onChange={handleChange} placeholder="••••••••" required autoComplete="current-password"/></label>
        </fieldset>
        <FormError message={error}/>
        <div className="import-actions"><button type="submit" className="button primary" disabled={loading}><LogIn size={17}/>{loading ? 'Đang xử lý…' : 'Đăng nhập vào không gian làm việc'}</button></div>
      </form>
    </section>
  </div>;
}
