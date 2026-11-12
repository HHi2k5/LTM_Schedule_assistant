import React, { useEffect, useState } from 'react';
import { Calendar, Clock3, UserPlus } from 'lucide-react';
import { api, dateLabel } from './schedule';
import { weekRanges } from './catalog';
import FormError from './FormError';

const DAY_LABELS = {
  2: 'Thứ 2',
  3: 'Thứ 3',
  4: 'Thứ 4',
  5: 'Thứ 5',
  6: 'Thứ 6',
  7: 'Thứ 7',
  8: 'Chủ Nhật',
};

const dayName = dow => DAY_LABELS[dow] || `Thứ ${dow}`;

export default function VisitingSchedulePanel({
  schoolId = 'PTIT',
  visitingTeachers = [],
  defaultOpen = true,
}) {
  const [profiles, setProfiles] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!visitingTeachers.length) {
      setProfiles([]);
      return;
    }
    let active = true;
    setLoading(true);
    setError('');
    Promise.all(
      visitingTeachers.map(t =>
        api(`/assignments/visiting/${encodeURIComponent(t.teacherId)}?schoolId=${encodeURIComponent(schoolId)}`)
          .catch(() => ({
            teacherId: t.teacherId,
            teacherName: t.teacherName,
            subjectCodes: [],
            busyEntries: [],
            externalSlots: [],
          }))
      )
    )
      .then(results => {
        if (active) setProfiles(results);
      })
      .catch(err => {
        if (active) setError(err.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [visitingTeachers, schoolId]);

  const loadByTeacherId = new Map(visitingTeachers.map(t => [t.teacherId, t]));

  return (
    <details open={defaultOpen} className="visiting-panel">
      <summary>
        <UserPlus size={17} style={{ verticalAlign: 'middle' }} /> Tổng hợp lịch bận Giảng viên Thỉnh giảng ({visitingTeachers.length} GV)
      </summary>

      <FormError message={error} />

      {!visitingTeachers.length ? (
        <p className="muted-text" style={{ marginTop: '0.75rem' }}>
          Bộ môn chưa có giảng viên loại <strong>Thỉnh giảng</strong>.
        </p>
      ) : loading ? (
        <p className="muted-text" style={{ marginTop: '0.75rem' }}>Đang tải lịch bận của các giảng viên thỉnh giảng…</p>
      ) : (
        <div style={{ marginTop: '0.85rem' }}>
          <div className="table-scroll catalog-table" style={{ margin: 0, background: '#fff', borderRadius: '8px', border: '1px solid #bae6fd' }}>
            <table>
              <thead>
                <tr>
                  <th style={{ minWidth: '170px' }}>Giảng viên thỉnh giảng</th>
                  <th>Môn có thể dạy</th>
                  <th style={{ minWidth: '190px' }}><Clock3 size={13} style={{ verticalAlign: 'middle' }} /> Thứ bận cố định</th>
                  <th style={{ minWidth: '210px' }}><Calendar size={13} style={{ verticalAlign: 'middle' }} /> Ngày bận cụ thể</th>
                  <th style={{ minWidth: '240px' }}>Lịch dạy đơn vị / trường khác</th>
                </tr>
              </thead>
              <tbody>
                {profiles.map(p => {
                  const load = loadByTeacherId.get(p.teacherId);
                  const busyEntries = p.busyEntries || [];
                  const fixedDays = busyEntries.filter(e => !e.specificDate && e.dayOfWeek);
                  const specificDates = busyEntries.filter(e => Boolean(e.specificDate));
                  const externalSlots = p.externalSlots || [];
                  const hasAnyBusy = fixedDays.length > 0 || specificDates.length > 0 || externalSlots.length > 0;

                  return (
                    <tr key={p.teacherId}>
                      <td style={{ whiteSpace: 'normal' }}>
                        <strong>{p.teacherName}</strong> ({p.teacherId})
                        {load && (
                          <div>
                            <small className="muted-text">Đang nhận: {load.classes} lớp ({load.periods} tiết)</small>
                          </div>
                        )}
                      </td>
                      <td style={{ whiteSpace: 'normal' }}>
                        {p.subjectCodes?.length ? (
                          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                            {p.subjectCodes.map(code => (
                              <code key={code}>{code}</code>
                            ))}
                          </div>
                        ) : (
                          <span className="muted-text">—</span>
                        )}
                      </td>
                      <td style={{ whiteSpace: 'normal' }}>
                        {fixedDays.length ? (
                          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                            {fixedDays.map(entry => (
                              <span key={entry.id} className="badge" style={{ background: '#fef3c7', color: '#92400e', whiteSpace: 'normal', textAlign: 'left' }}>
                                {dayName(entry.dayOfWeek)}{entry.note ? ` (${entry.note})` : ''}
                              </span>
                            ))}
                          </div>
                        ) : (
                          <span className="muted-text">Rảnh cả tuần</span>
                        )}
                      </td>
                      <td style={{ whiteSpace: 'normal' }}>
                        {specificDates.length ? (
                          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                            {specificDates.map(entry => (
                              <span key={entry.id} className="badge" style={{ background: '#fee2e2', color: '#991b1b', whiteSpace: 'normal', textAlign: 'left' }}>
                                {dateLabel(entry.specificDate)}{entry.note ? ` (${entry.note})` : ''}
                              </span>
                            ))}
                          </div>
                        ) : (
                          <span className="muted-text">—</span>
                        )}
                      </td>
                      <td style={{ whiteSpace: 'normal' }}>
                        {externalSlots.length ? (
                          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                            {externalSlots.map((ext, idx) => (
                              <span key={idx} className="badge muted" style={{ whiteSpace: 'normal', textAlign: 'left', lineHeight: 1.45 }}>
                                [{ext.schoolId}] {dayName(ext.dayOfWeek)} · Tiết {ext.startPeriod}–{ext.startPeriod + ext.numPeriods - 1} · Tuần {weekRanges(ext.weekSchedule)} ({ext.courseName})
                              </span>
                            ))}
                          </div>
                        ) : !hasAnyBusy ? (
                          <span className="badge" style={{ background: '#dcfce7', color: '#166534' }}>Chưa khai báo lịch bận</span>
                        ) : (
                          <span className="muted-text">—</span>
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
    </details>
  );
}
