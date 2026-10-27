import React, { useEffect, useRef } from 'react';
import { AlertTriangle } from 'lucide-react';

export default function FormError({ message }) {
  const ref = useRef(null);
  useEffect(() => { if (message) ref.current?.focus(); }, [message]);
  if (!message) return null;
  return <div ref={ref} role="alert" tabIndex={-1} className="import-warning"><AlertTriangle size={18}/><p>{message}</p></div>;
}
