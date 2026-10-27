import React, { useState } from 'react';
import { Copy } from 'lucide-react';

export default function TeacherIdentity({ teacherId }) {
  const [message, setMessage] = useState('');
  async function copy() {
    try { await navigator.clipboard.writeText(teacherId); setMessage('Đã sao chép mã GV.'); }
    catch { setMessage('Không sao chép được. Bạn có thể chọn mã để sao chép thủ công.'); }
  }
  return <div className="teacher-identity"><span>Mã GV: <code>{teacherId}</code></span><button type="button" className="button compact" onClick={copy} aria-label="Sao chép mã giảng viên"><Copy size={14}/>Sao chép</button>{message && <small role="status">{message}</small>}</div>;
}
