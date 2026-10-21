import React, { useState } from 'react';
import { GraduationCap, LogIn, UserPlus, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { api } from './schedule';

export default function AuthPage({ onAuthenticated }) {
  const [mode, setMode] = useState('login'); // 'login' | 'register'
  const [form, setForm] = useState({
    username: '',
    password: '',
    teacherId: '',
    fullName: '',
    role: 'TEACHER',
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
    setLoading(true);
    setError('');
    setMessage('');

    try {
      const endpoint = mode === 'login' ? '/auth/login' : '/auth/register';
      const result = await api(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      });

      if (result.status !== 'SUCCESS') {
        throw new Error(result.message || 'Thao tác thất bại.');
      }

      setMessage(result.message);
      onAuthenticated({
        teacherId: result.teacherId,
        fullName: result.fullName,
        username: result.username,
        role: result.role,
        semesterStartDate: result.semesterStartDate || '2026-08-10',
      });
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div
      className="app-shell"
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
            className={`button ${mode === 'login' ? 'primary' : ''}`}
            onClick={() => { setMode('login'); setError(''); }}
          >
            <LogIn size={16} /> Đăng nhập
          </button>
          <button
            type="button"
            className={`button ${mode === 'register' ? 'primary' : ''}`}
            onClick={() => { setMode('register'); setError(''); }}
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
              />
            </label>

            <label>
              Mật khẩu
              <input
                type="password"
                name="password"
                value={form.password}
                onChange={handleChange}
                placeholder="••••••••"
                required
              />
            </label>

            {mode === 'register' && (
              <>
                <label>
                  Mã giảng viên / Mã cán bộ
                  <input
                    name="teacherId"
                    value={form.teacherId}
                    onChange={handleChange}
                    placeholder="Ví dụ: GV01"
                    required
                    maxLength={50}
                  />
                  <small>Dùng để đồng bộ lịch dạy cá nhân và lịch trường phân công.</small>
                </label>

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

                <label>
                  Vai trò sử dụng
                  <select name="role" value={form.role} onChange={handleChange}>
                    <option value="TEACHER">Giảng viên (Xem lịch & Nhập lịch các trường)</option>
                    <option value="ADMIN">Quản trị đào tạo (Xếp lịch toàn trường 35 phòng)</option>
                  </select>
                </label>
              </>
            )}
          </fieldset>

          {error && (
            <div role="alert" className="import-warning" style={{ marginTop: '1rem' }}>
              <AlertTriangle size={18} />
              <p>{error}</p>
            </div>
          )}

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