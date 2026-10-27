import React, { useEffect, useState } from 'react';
import { Clock3 } from 'lucide-react';

const timeZone = 'Asia/Ho_Chi_Minh';
const timeFormat = new Intl.DateTimeFormat('vi-VN', {
  timeZone, hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
});
const dateFormat = new Intl.DateTimeFormat('vi-VN', { timeZone, dateStyle: 'full' });

export default function TopbarClock() {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  return <div className="topbar-datetime">
    <time className="topbar-clock" dateTime={now.toISOString()} aria-label="Giờ Việt Nam" title="Giờ Việt Nam (UTC+7)">
      <Clock3 size={22} aria-hidden="true"/><span>{timeFormat.format(now)}</span>
    </time>
    <span className="today-label">{dateFormat.format(now)}</span>
  </div>;
}
