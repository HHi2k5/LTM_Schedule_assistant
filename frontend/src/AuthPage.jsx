import React, { useState } from 'react';
import { GraduationCap, LogIn, CheckCircle2 } from 'lucide-react';
import { api } from './schedule';
import { setAccessToken } from './authSession';
import FormError from './FormError';

export default function AuthPage({ onAuthenticated }) {
  const [form, setForm] = useState({
    username: '',
    password: '',
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
      if (!form.username.trim()) throw new Error('Điền tên đăng nhập.');
      const result = await api('/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: form.username.trim(), password: form.password }),
      });

      if (result.status !== 'SUCCESS') {
        throw new Error(result.message || 'Đăng nhập thất bại.');
      }

      if (!result.accessToken || !['ADMIN', 'TEACHER'].includes(result.role)) {
        throw new Error('Máy chủ chưa trả phiên đăng nhập hợp lệ.');
      }
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
          maxWidth: '480px',
          width: '100%',
          margin: '0 auto',
        }}
      >
        <div className="panel-title">
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <span className="brand-mark"><GraduationCap size={26} /></span>
            <div>
              <h2>Đăng nhập hệ thống</h2>
              <p className="muted-text">Campus · Quản lý và xếp lịch giảng dạy của khoa</p>
            </div>
          </div>
        </div>

        <p className="muted-text" style={{ marginBottom: '1rem' }}>
          Giảng viên đăng nhập bằng <strong>Tài khoản</strong> và <strong>Mật khẩu</strong> do Quản trị viên (Admin) của khoa cấp. Sau khi đăng nhập, giảng viên có thể vào mục <strong>Cài đặt</strong> để đổi mật khẩu cá nhân.
        </p>

        <form onSubmit={handleSubmit}>
          <fieldset disabled={loading} className="import-fields" style={{ gridTemplateColumns: '1fr' }}>
            <label>
              Tên đăng nhập (Tài khoản)
              <input
                name="username"
                value={form.username}
                onChange={handleChange}
                placeholder="Nhập tài khoản được cấp..."
                required
                maxLength={50}
                autoComplete="username"
              />
            </label>

            <label>
              Mật khẩu
              <input
                type="password"
                autoComplete="current-password"
                name="password"
                value={form.password}
                onChange={handleChange}
                placeholder="••••••••"
                required
              />
            </label>
          </fieldset>

          <FormError message={error}/>

          {message && (
            <div role="status" className="info-banner" style={{ marginTop: '1rem' }}>
              <CheckCircle2 size={18} /> {message}
            </div>
          )}

          <div className="import-actions" style={{ marginTop: '1.25rem' }}>
            <button type="submit" className="button primary" disabled={loading} style={{ width: '100%', justifyContent: 'center' }}>
              <LogIn size={17} />
              {loading ? 'Đang đăng nhập…' : 'Đăng nhập vào không gian làm việc'}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}