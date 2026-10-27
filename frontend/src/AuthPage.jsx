import React, { useState } from 'react';
import { GraduationCap, LogIn, UserPlus, CheckCircle2 } from 'lucide-react';
import { api } from './schedule';
import { setAccessToken } from './authSession';
import { validateNewPassword } from './workflow';
import FormError from './FormError';

export default function AuthPage({ onAuthenticated }) {
  const [mode, setMode] = useState('login'); // 'login' | 'register'
  const [form, setForm] = useState({
    username: '',
    password: '',
    fullName: '',
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  function handleChange(event) {
    const { name, value } = event.target;
    setForm(prev => ({ ...prev, [name]: value }));
    setError('');
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (loading) return;
    setLoading(true);
    setError('');
    setMessage('');

    try {
      if (mode === 'register') {
        validateNewPassword(form.password);
        if (!form.fullName.trim()) throw new Error('Điền họ và tên giảng viên.');
      }
      if (!form.username.trim()) throw new Error('Điền tên đăng nhập.');
      const endpoint = mode === 'login' ? '/auth/login' : '/auth/register';
      const result = await api(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: form.username.trim(), password: form.password, ...(mode === 'register' ? { fullName: form.fullName.trim() } : {}) }),
      });

      if (result.status !== 'SUCCESS') {
        throw new Error(result.message || 'Thao tác thất bại.');
      }

      if (!result.accessToken || !['ADMIN', 'TEACHER'].includes(result.role)) throw new Error('Máy chủ chưa trả phiên đăng nhập hợp lệ.');
      setAccessToken(result.accessToken);
      setMessage(result.message);
      onAuthenticated({
        teacherId: result.teacherId,
        fullName: result.fullName,
        username: result.username,
        role: result.role,
        semesterStartDate: result.semesterStartDate || '2026-08-10',
      });
    } catch (err) {
      setMessage('');
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div
      className="app-shell auth-shell"
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '100vh',
        width: '100%',
        padding: '2rem',
      }}
    >
      <section
        className="standalone-panel import-panel"
        style={{
          maxWidth: '500px',
          width: '100%',
          margin: '0 auto',
        }}
      >
        <div className="panel-title">
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <span className="brand-mark"><GraduationCap size={26} /></span>
            <div>
              <h2>{mode === 'login' ? 'Đăng nhập hệ thống' : 'Đăng ký tài khoản'}</h2>
              <p className="muted-text">Campus · Không gian quản lý và xếp lịch giảng dạy</p>
            </div>
          </div>
        </div>

        <div className="import-steps" style={{ marginBottom: '1.25rem' }}>
          <button
            type="button"
            disabled={loading}
            className={`button ${mode === 'login' ? 'primary' : ''}`}
            onClick={() => { setMode('login'); setError(''); setForm(previous => ({ ...previous, password: '' })); }}
          >
            <LogIn size={16} /> Đăng nhập
          </button>
          <button
            type="button"
            disabled={loading}
            className={`button ${mode === 'register' ? 'primary' : ''}`}
            onClick={() => { setMode('register'); setError(''); setForm(previous => ({ ...previous, password: '' })); }}
          >
            <UserPlus size={16} /> Đăng ký mới
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <fieldset disabled={loading} className="import-fields" style={{ gridTemplateColumns: '1fr' }}>
            <label>
              Tên đăng nhập
              <input
                name="username"
                value={form.username}
                onChange={handleChange}
                placeholder="Nhập username..."
                required
                maxLength={50}
                autoComplete="username"
              />
            </label>

            <label>
              Mật khẩu
              <input
                type="password"
                autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
                minLength={mode === 'register' ? 8 : undefined}
                name="password"
                value={form.password}
                onChange={handleChange}
                placeholder="••••••••"
                required
              />
              {mode === 'register' && <small>Ít nhất 8 ký tự, tối đa 72 byte UTF-8.</small>}
            </label>

            {mode === 'register' && (
              <>
                <p className="muted-text">Giảng viên tự đăng ký tài khoản và nhận mã riêng do hệ thống tạo. Dùng mã này khi trường lập danh sách phân công giảng dạy.</p>

                <label>
                  Họ và tên giảng viên
                  <input
                    name="fullName"
                    value={form.fullName}
                    onChange={handleChange}
                    placeholder="Ví dụ: Đào Ngọc Phong"
                    required
                    maxLength={100}
                  />
                </label>

                <p className="muted-text">Đăng ký tài khoản giảng viên. Tài khoản quản lý được cấp quyền riêng.</p>
              </>
            )}
          </fieldset>

          <FormError message={error}/>

          {message && (
            <div role="status" className="info-banner" style={{ marginTop: '1rem' }}>
              <CheckCircle2 size={18} /> {message}
            </div>
          )}

          <div className="import-actions" style={{ marginTop: '1.25rem' }}>
            <button type="submit" className="button primary" disabled={loading} style={{ width: '100%', justifyContent: 'center' }}>
              {mode === 'login' ? <LogIn size={17} /> : <UserPlus size={17} />}
              {loading ? 'Đang xử lý…' : mode === 'login' ? 'Đăng nhập vào không gian làm việc' : 'Hoàn tất đăng ký'}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
