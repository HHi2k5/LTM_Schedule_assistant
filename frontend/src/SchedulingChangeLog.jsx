import React, { useEffect, useState } from 'react';
import { planApi } from './schedule';
import { adjustmentActions } from './scheduleAdjustment';

export default function SchedulingChangeLog({ planId, disabled, onView, onRestore, currentVersionId }) {
  const [open, setOpen] = useState(false), [page, setPage] = useState(0), [data, setData] = useState(null), [error, setError] = useState('');
  useEffect(() => {
    if (!open) return; const controller = new AbortController(); setData(null); setError('');
    planApi(`/${planId}/changes?page=${page}`, { signal: controller.signal }).then(setData).catch(e => { if (e.name !== 'AbortError') setError(e.message); });
    return () => controller.abort();
  }, [planId, page, open]);
  return <details className="catalog-panel adjustment-log" open={open} onToggle={e => setOpen(e.currentTarget.open)}><summary>Nhật ký thay đổi bản lịch</summary>
    {error && <p className="catalog-error" role="alert">{error}</p>}{open && !data && !error && <p role="status">Đang tải nhật ký…</p>}
    {data && <><p>{data.total} thao tác được ghi nhận. Các phiên bản cũ vẫn xem được trong danh sách phiên bản.</p><div className="catalog-table-wrap"><table><caption>Người sửa và phiên bản kết quả</caption><thead><tr><th>Thao tác</th><th>Người / thời gian</th><th>Phiên bản</th><th>Lý do / số buổi</th><th>Xem lại</th></tr></thead><tbody>{data.rows.map(r => <tr key={r.id}><th>{adjustmentActions[r.action] || ({ GENERATE: 'Xếp lịch', CONFIRM: 'Xác nhận', VISITING: 'Thỉnh giảng' })[r.action] || r.action}</th><td>{r.actor}<br/>{new Date(r.createdAt).toLocaleString('vi-VN')}</td><td>{r.sourceVersionNumber ? `v${r.sourceVersionNumber} → ` : ''}v{r.resultVersionNumber}</td><td>{r.reason || '—'}<br/>{r.affectedEntries} buổi</td><td><button className="button" disabled={disabled} onClick={() => onView(r.resultVersionId)}>Xem v{r.resultVersionNumber}</button>{r.resultVersionId !== currentVersionId && <button className="button" disabled={disabled} onClick={() => onRestore(r.resultVersionId)}>Khôi phục v{r.resultVersionNumber}</button>}</td></tr>)}</tbody></table></div>
      {data.pageCount > 1 && <div className="catalog-pagination"><span>Nhật ký: trang {page + 1}/{data.pageCount}</span><button className="button" disabled={disabled || page === 0} onClick={() => setPage(p => p - 1)}>Nhật ký trang trước</button><button className="button" disabled={disabled || page + 1 >= data.pageCount} onClick={() => setPage(p => p + 1)}>Nhật ký trang sau</button></div>}
    </>}
  </details>;
}
