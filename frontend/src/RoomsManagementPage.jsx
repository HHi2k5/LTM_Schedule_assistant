import React, { useEffect, useState } from 'react';
import { Building2, CheckCircle2, Edit3, Layers, List, Plus, Save, Trash2, X } from 'lucide-react';
import { api } from './schedule';
import { postJson, usageLabel } from './catalog';
import FormError from './FormError';

const emptyTypeForm = { id: null, code: '', name: '', usageKind: 'THEORY' };
const emptyRoomForm = { id: null, roomCode: '', roomType: '', capacity: 60, building: 'A2', note: '' };
const emptyBatchRoom = (defaultType = '') => ({
  roomCode: '',
  roomType: defaultType,
  capacity: 60,
  building: 'A2',
  note: '',
});

export default function RoomsManagementPage() {
  const [schoolId, setSchoolId] = useState('PTIT');
  const [roomTypes, setRoomTypes] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [typeForm, setTypeForm] = useState(emptyTypeForm);
  const [roomForm, setRoomForm] = useState(emptyRoomForm);

  const [batchMode, setBatchMode] = useState(false);
  const [batchRows, setBatchRows] = useState([emptyBatchRoom(), emptyBatchRoom(), emptyBatchRoom()]);
  const [busy, setBusy] = useState(false);

  const [error, setError] = useState('');
  const [info, setInfo] = useState('');

  const school = schoolId.trim();

  async function loadCatalog(code) {
    const [typesData, roomsData] = await Promise.all([
      api(`/room-types?schoolId=${encodeURIComponent(code)}`),
      api(`/rooms?schoolId=${encodeURIComponent(code)}`),
    ]);
    const typesList = Array.isArray(typesData) ? typesData : [];
    setRoomTypes(typesList);
    setRooms(Array.isArray(roomsData) ? roomsData : []);
    const defaultType = typesList[0]?.code || '';
    setRoomForm(prev => (prev.roomType || !defaultType ? prev : { ...prev, roomType: defaultType }));
    setBatchRows(prev => prev.map(r => (r.roomType || !defaultType ? r : { ...r, roomType: defaultType })));
  }

  useEffect(() => {
    if (!school) return;
    setError('');
    loadCatalog(school).catch(err => setError(err.message));
  }, [school]);

  async function run(action) {
    setError('');
    setInfo('');
    try { await action(); } catch (err) { setError(err.message); }
  }

  const saveRoomType = event => {
    event.preventDefault();
    run(async () => {
      const saved = await postJson('/room-types', { ...typeForm, schoolCode: school });
      setTypeForm(emptyTypeForm);
      await loadCatalog(school);
      setInfo(`Đã lưu loại phòng ${saved.name} (${saved.code}).`);
    });
  };

  const deleteRoomType = type => run(async () => {
    if (!window.confirm(`Xóa loại phòng "${type.name}"?`)) return;
    await api(`/room-types/${type.id}`, { method: 'DELETE' });
    await loadCatalog(school);
    setInfo(`Đã xóa loại phòng ${type.name}.`);
  });

  const saveRoom = event => {
    event.preventDefault();
    if (busy) return;
    run(async () => {
      setBusy(true);
      try {
        const saved = await postJson('/rooms', {
          ...roomForm,
          capacity: Number(roomForm.capacity),
          schoolCode: school,
        });
        setRoomForm({ ...emptyRoomForm, roomType: roomTypes[0]?.code || '' });
        await loadCatalog(school);
        setInfo(`Đã lưu phòng ${saved.roomCode}.`);
      } finally {
        setBusy(false);
      }
    });
  };

  function updateBatchRow(idx, field, value) {
    setBatchRows(prev => {
      const next = [...prev];
      next[idx] = { ...next[idx], [field]: value };
      return next;
    });
  }

  function addBatchRows(count = 3) {
    const defaultType = roomTypes[0]?.code || '';
    setBatchRows(prev => [...prev, ...Array.from({ length: count }, () => emptyBatchRoom(defaultType))]);
  }

  function removeBatchRow(idx) {
    if (batchRows.length <= 1) return;
    setBatchRows(prev => prev.filter((_, i) => i !== idx));
  }

  const saveBatchRooms = event => {
    event.preventDefault();
    if (busy) return;
    run(async () => {
      const validRows = batchRows.filter(r => r.roomCode.trim());
      if (!validRows.length) throw new Error('Vui lòng nhập ít nhất 1 phòng học.');
      setBusy(true);
      let count = 0;
      try {
        for (const row of validRows) {
          await postJson('/rooms', {
            roomCode: row.roomCode.trim(),
            roomType: row.roomType || roomTypes[0]?.code || 'LY_THUYET',
            capacity: Number(row.capacity) || 60,
            building: row.building.trim() || 'A2',
            note: row.note.trim() || null,
            schoolCode: school,
          });
          count++;
        }
        const defaultType = roomTypes[0]?.code || '';
        setBatchRows([emptyBatchRoom(defaultType), emptyBatchRoom(defaultType), emptyBatchRoom(defaultType)]);
        await loadCatalog(school);
        setInfo(`Đã lưu ${count} phòng học.`);
      } finally {
        setBusy(false);
      }
    });
  };

  const deleteRoom = room => run(async () => {
    if (!window.confirm(`Xóa phòng "${room.roomCode}"?`)) return;
    await api(`/rooms/${room.id}`, { method: 'DELETE' });
    await loadCatalog(school);
    setInfo(`Đã xóa phòng ${room.roomCode}.`);
  });

  const typeLabel = code => roomTypes.find(t => t.code === code)?.name || code;

  return (
    <section className="standalone-panel import-panel">
      <div className="panel-title">
        <div>
          <h2>Loại phòng &amp; Phòng học</h2>
        </div>
      </div>

      <FormError message={error} />
      {info && <div role="status" className="info-banner catalog-info"><CheckCircle2 size={18} /> {info}</div>}

      {/* LOẠI PHÒNG */}
      <div className="catalog-card">
        <h3><Layers size={18} /> Loại phòng ({roomTypes.length})</h3>
        <form className="catalog-form" onSubmit={saveRoomType}>
          <label>
            Tên loại phòng
            <input required maxLength={100} placeholder="Phòng máy chiếu" value={typeForm.name} onChange={e => setTypeForm({ ...typeForm, name: e.target.value })} />
          </label>
          <label>
            Mã loại
            <input maxLength={30} disabled={Boolean(typeForm.id)} placeholder="MAY_CHIEU" value={typeForm.code} onChange={e => setTypeForm({ ...typeForm, code: e.target.value.toUpperCase() })} />
          </label>
          <label>
            Nhóm lớp
            <select value={typeForm.usageKind} onChange={e => setTypeForm({ ...typeForm, usageKind: e.target.value })}>
              <option value="THEORY">Lý thuyết</option>
              <option value="PRACTICE">Thực hành</option>
            </select>
          </label>
          <div className="catalog-actions">
            <button type="submit" className="button primary compact"><Save size={14} /> {typeForm.id ? 'Lưu loại phòng' : 'Thêm loại phòng'}</button>
            {typeForm.id && <button type="button" className="button compact" onClick={() => setTypeForm(emptyTypeForm)}><X size={14} /> Hủy sửa</button>}
          </div>
        </form>

        <div className="table-scroll catalog-table">
          <table>
            <thead><tr><th>Mã loại</th><th>Tên loại phòng</th><th>Nhóm lớp</th><th>Thao tác</th></tr></thead>
            <tbody>
              {roomTypes.map(t => (
                <tr key={t.id}>
                  <td><code>{t.code}</code></td>
                  <td>{t.name}</td>
                  <td>{usageLabel(t.usageKind)}</td>
                  <td className="catalog-row-actions">
                    <button type="button" className="icon-button" title="Sửa" onClick={() => setTypeForm({ id: t.id, code: t.code, name: t.name, usageKind: t.usageKind })}><Edit3 size={14} /></button>
                    <button type="button" className="icon-button" title="Xóa" onClick={() => deleteRoomType(t)}><Trash2 size={14} /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* PHÒNG HỌC */}
      <div className="catalog-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '0.75rem' }}>
          <h3 style={{ margin: 0 }}><Building2 size={18} /> Danh sách phòng học ({rooms.length})</h3>
          {!roomForm.id && (
            <button type="button" className="button compact" onClick={() => setBatchMode(prev => !prev)}>
              <List size={14} /> {batchMode ? 'Thêm từng phòng' : 'Thêm nhiều phòng'}
            </button>
          )}
        </div>

        {!batchMode ? (
          <form className="catalog-form" onSubmit={saveRoom}>
            <label>
              Mã phòng
              <input required maxLength={50} placeholder="101-A2" value={roomForm.roomCode} onChange={e => setRoomForm({ ...roomForm, roomCode: e.target.value })} />
            </label>
            <label>
              Loại phòng
              <select required value={roomForm.roomType} onChange={e => setRoomForm({ ...roomForm, roomType: e.target.value })}>
                <option value="">-- Chọn loại phòng --</option>
                {roomTypes.map(t => (
                  <option key={t.code} value={t.code}>{t.name} ({usageLabel(t.usageKind)})</option>
                ))}
              </select>
            </label>
            <label>
              Sức chứa
              <input type="number" min={1} max={1000} required value={roomForm.capacity} onChange={e => setRoomForm({ ...roomForm, capacity: e.target.value })} />
            </label>
            <label>
              Tòa nhà
              <input required maxLength={50} value={roomForm.building} onChange={e => setRoomForm({ ...roomForm, building: e.target.value })} />
            </label>
            <label>
              Ghi chú
              <input maxLength={500} placeholder="Ghi chú" value={roomForm.note} onChange={e => setRoomForm({ ...roomForm, note: e.target.value })} />
            </label>
            <div className="catalog-actions">
              <button type="submit" className="button primary compact" disabled={busy}><Plus size={14} /> {roomForm.id ? 'Lưu phòng học' : 'Thêm phòng học'}</button>
              {roomForm.id && <button type="button" className="button compact" onClick={() => setRoomForm({ ...emptyRoomForm, roomType: roomTypes[0]?.code || '' })}><X size={14} /> Hủy sửa</button>}
            </div>
          </form>
        ) : (
          <form onSubmit={saveBatchRooms} style={{ background: '#f8faf9', padding: '0.9rem', borderRadius: '8px', border: '1px solid var(--border)' }}>
            {batchRows.map((row, idx) => (
              <div key={idx} className="batch-room-row">
                <label>{idx === 0 ? 'Mã phòng' : ''}
                  <input maxLength={50} placeholder="101-A2" value={row.roomCode} onChange={e => updateBatchRow(idx, 'roomCode', e.target.value)} />
                </label>
                <label>{idx === 0 ? 'Loại phòng' : ''}
                  <select value={row.roomType} onChange={e => updateBatchRow(idx, 'roomType', e.target.value)}>
                    {roomTypes.map(t => (
                      <option key={t.code} value={t.code}>{t.name}</option>
                    ))}
                  </select>
                </label>
                <label>{idx === 0 ? 'Sức chứa' : ''}
                  <input type="number" min={1} max={1000} value={row.capacity} onChange={e => updateBatchRow(idx, 'capacity', e.target.value)} />
                </label>
                <label>{idx === 0 ? 'Tòa nhà' : ''}
                  <input maxLength={50} value={row.building} onChange={e => updateBatchRow(idx, 'building', e.target.value)} />
                </label>
                <label>{idx === 0 ? 'Ghi chú' : ''}
                  <input maxLength={500} placeholder="Ghi chú" value={row.note} onChange={e => updateBatchRow(idx, 'note', e.target.value)} />
                </label>
                <button type="button" className="icon-button" onClick={() => removeBatchRow(idx)} disabled={batchRows.length <= 1} title="Xóa dòng">
                  <Trash2 size={15} />
                </button>
              </div>
            ))}
            <div className="catalog-actions" style={{ marginTop: '0.75rem' }}>
              <button type="button" className="button compact" onClick={() => addBatchRows(3)}><Plus size={14} /> Thêm 3 dòng</button>
              <button type="submit" className="button primary compact" disabled={busy}><Save size={14} /> {busy ? 'Đang lưu…' : 'Lưu danh sách phòng'}</button>
            </div>
          </form>
        )}

        <div className="table-scroll catalog-table">
          <table>
            <thead><tr><th>Mã phòng</th><th>Loại phòng</th><th>Sức chứa</th><th>Tòa</th><th>Ghi chú</th><th>Thao tác</th></tr></thead>
            <tbody>
              {!rooms.length && <tr><td colSpan={6}>Chưa có phòng học nào.</td></tr>}
              {rooms.map(r => (
                <tr key={r.id}>
                  <td><strong>{r.roomCode}</strong></td>
                  <td>{typeLabel(r.roomType)} <small className="badge muted">{r.lab ? 'TH' : 'LT'}</small></td>
                  <td>{r.capacity} chỗ</td>
                  <td>{r.building}</td>
                  <td>{r.note || <span className="muted-text">—</span>}</td>
                  <td className="catalog-row-actions">
                    <button type="button" className="icon-button" title="Sửa" onClick={() => { setBatchMode(false); setRoomForm({ id: r.id, roomCode: r.roomCode, roomType: r.roomType, capacity: r.capacity, building: r.building || 'A2', note: r.note || '' }); }}><Edit3 size={14} /></button>
                    <button type="button" className="icon-button" title="Xóa" onClick={() => deleteRoom(r)}><Trash2 size={14} /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}
