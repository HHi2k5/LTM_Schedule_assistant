import React, { useEffect, useState } from 'react';
import { CheckCircle2, Clock3, Plus, Save, Trash2 } from 'lucide-react';
import { api, clock, minutes } from './schedule';
import { postJson } from './catalog';
import FormError from './FormError';

const DEFAULT_SCHOOL_CODE = 'PTIT';

function formatTimeInput(value) {
  if (!value) return '07:00';
  const parts = String(value).split(':');
  return `${String(parts[0] || '07').padStart(2, '0')}:${String(parts[1] || '00').padStart(2, '0')}`;
}

function addMinutesToTime(hhmm, delta) {
  const total = Math.min(23 * 60 + 55, Math.max(0, Math.round(minutes(hhmm)) + delta));
  const h = String(Math.floor(total / 60)).padStart(2, '0');
  const m = String(total % 60).padStart(2, '0');
  return `${h}:${m}`;
}

export default function PeriodTemplatesPage() {
  const [periods, setPeriods] = useState([]);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');

  async function loadPeriods() {
    setLoading(true);
    setError('');
    try {
      const list = await api(`/period-templates?schoolId=${encodeURIComponent(DEFAULT_SCHOOL_CODE)}`);
      const normalized = (Array.isArray(list) ? list : [])
        .map(item => ({
          period: Number(item.period),
          start: formatTimeInput(item.start),
          end: formatTimeInput(item.end),
        }))
        .sort((a, b) => a.period - b.period);
      setPeriods(normalized);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadPeriods();
  }, []);

  function updateRow(index, field, value) {
    setPeriods(prev => {
      const next = [...prev];
      next[index] = {
        ...next[index],
        [field]: field === 'period' ? Number(value) : value,
      };
      return next;
    });
  }

  function addPeriodRow() {
    setError('');
    setInfo('');
    setPeriods(prev => {
      if (prev.length >= 30) return prev;
      const sorted = [...prev].sort((a, b) => a.period - b.period);
      const last = sorted[sorted.length - 1];
      const nextNum = last ? Math.min(30, last.period + 1) : 1;
      const nextStart = last ? addMinutesToTime(last.end, 10) : '07:00';
      const nextEnd = addMinutesToTime(nextStart, 50);
      return [...sorted, { period: nextNum, start: nextStart, end: nextEnd }];
    });
  }

  function removePeriodRow(index) {
    if (periods.length <= 1) return;
    setPeriods(prev => prev.filter((_, i) => i !== index));
  }

  async function handleSave(event) {
    event.preventDefault();
    if (busy) return;
    setError('');
    setInfo('');
    setBusy(true);
    try {
      const sorted = [...periods].sort((a, b) => a.period - b.period);
      const saved = await postJson('/period-templates', {
        schoolCode: DEFAULT_SCHOOL_CODE,
        periods: sorted.map(p => ({
          period: Number(p.period),
          start: p.start,
          end: p.end,
        })),
      });
      setPeriods(
        (Array.isArray(saved) ? saved : sorted)
          .map(item => ({
            period: Number(item.period),
            start: formatTimeInput(item.start),
            end: formatTimeInput(item.end),
          }))
          .sort((a, b) => a.period - b.period)
      );
      setInfo(`Đã lưu khung giờ (${sorted.length} tiết).`);
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
          <h2>Khung giờ tiết học</h2>
        </div>
      </div>

      <FormError message={error} />
      {info && <div role="status" className="info-banner catalog-info"><CheckCircle2 size={18} /> {info}</div>}

      <div className="catalog-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '0.75rem' }}>
          <h3 style={{ margin: 0 }}><Clock3 size={18} /> Danh sách tiết học ({periods.length} tiết)</h3>
          <div className="catalog-actions">
            <button type="button" className="button compact" disabled={busy || periods.length >= 30} onClick={addPeriodRow}>
              <Plus size={14} /> Thêm tiết
            </button>
          </div>
        </div>

        <form onSubmit={handleSave}>
          <div className="table-scroll catalog-table" style={{ maxHeight: '520px' }}>
            <table className="period-table">
              <thead>
                <tr>
                  <th style={{ width: '110px' }}>Tiết</th>
                  <th>Giờ bắt đầu</th>
                  <th>Giờ kết thúc</th>
                  <th>Thời lượng</th>
                  <th style={{ width: '70px' }}>Xóa</th>
                </tr>
              </thead>
              <tbody>
                {loading && !periods.length && (
                  <tr><td colSpan={5}>Đang tải khung giờ…</td></tr>
                )}
                {!loading && !periods.length && (
                  <tr><td colSpan={5}>Chưa có tiết học nào. Bấm "Thêm tiết" để tạo khung giờ.</td></tr>
                )}
                {periods.map((row, idx) => {
                  const diff = Math.round(minutes(row.end) - minutes(row.start));
                  return (
                    <tr key={idx}>
                      <td>
                        <input
                          type="number"
                          min={1}
                          max={30}
                          required
                          value={row.period}
                          onChange={e => updateRow(idx, 'period', e.target.value)}
                          style={{ width: '80px', padding: '7px 9px' }}
                        />
                      </td>
                      <td>
                        <input
                          type="time"
                          required
                          value={row.start}
                          onChange={e => updateRow(idx, 'start', e.target.value)}
                        />
                      </td>
                      <td>
                        <input
                          type="time"
                          required
                          value={row.end}
                          onChange={e => updateRow(idx, 'end', e.target.value)}
                        />
                      </td>
                      <td>
                        {diff > 0
                          ? <span className="badge muted">{diff} phút ({clock(row.start)}–{clock(row.end)})</span>
                          : <span className="error-text">Giờ không hợp lệ</span>}
                      </td>
                      <td>
                        <button
                          type="button"
                          className="icon-button"
                          disabled={periods.length <= 1}
                          onClick={() => removePeriodRow(idx)}
                          title="Xóa tiết"
                        >
                          <Trash2 size={15} />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <div className="catalog-actions" style={{ marginTop: '0.9rem' }}>
            <button type="submit" className="button primary compact" disabled={busy || loading || !periods.length}>
              <Save size={14} /> {busy ? 'Đang lưu…' : 'Lưu khung giờ'}
            </button>
          </div>
        </form>
      </div>
    </section>
  );
}
