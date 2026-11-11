import { apiUrl } from './schedule.js';
import { expireSession, getAccessToken } from './authSession.js';

// SSE frames may span arbitrary byte chunks, including split CRLF and UTF-8 characters.
export function createEventParser(onEvent) {
  let line = '', name = 'message', data = [], size = 0, skipLf = false;
  function finishLine() {
    if (!line) {
      if (data.length) onEvent({ type: name, data: data.join('\n') });
      name = 'message'; data = []; size = 0;
    } else if (!line.startsWith(':')) {
      const colon = line.indexOf(':');
      const field = colon < 0 ? line : line.slice(0, colon);
      let value = colon < 0 ? '' : line.slice(colon + 1);
      if (value.startsWith(' ')) value = value.slice(1);
      if (field === 'event') name = value;
      if (field === 'data') data.push(value);
    }
    line = '';
  }
  return chunk => {
    for (const character of chunk) {
      if (skipLf && character === '\n') { skipLf = false; continue; }
      skipLf = false;
      if (character === '\n' || character === '\r') {
        finishLine(); skipLf = character === '\r';
      } else {
        line += character;
        if (++size > 65536) throw new Error('Schedule event exceeds its size limit.');
      }
    }
  };
}

export function subscribeScheduleUpdates({ onChange, onStatus = () => {} }, dependencies = {}) {
  const {
    fetcher = globalThis.fetch, token = getAccessToken, expire = expireSession,
    setTimer = globalThis.setTimeout, clearTimer = globalThis.clearTimeout,
    windowTarget = globalThis.window, documentTarget = globalThis.document,
  } = dependencies;
  let stopped = false, generation = 0, attempts = 0, connected = false;
  let controller, retryTimer, watchdog;

  function stop() {
    if (stopped) return;
    stopped = true;
    generation++;
    controller?.abort();
    clearTimer(retryTimer); clearTimer(watchdog);
    windowTarget?.removeEventListener('online', restart);
    windowTarget?.removeEventListener('focus', resume);
    documentTarget?.removeEventListener('visibilitychange', resume);
  }

  async function connect(id, activeController) {
    let reader, timeout;
    const current = () => !stopped && id === generation;
    function touch() {
      clearTimer(timeout);
      timeout = setTimer(() => activeController.abort(), 45000);
      watchdog = timeout;
    }
    try {
      const bearer = token();
      if (!bearer) { stop(); return; }
      onStatus(attempts ? 'reconnecting' : 'connecting');
      touch();
      const response = await fetcher(apiUrl('/events'), {
        headers: { Authorization: `Bearer ${bearer}`, Accept: 'text/event-stream' },
        cache: 'no-store', signal: activeController.signal,
      });
      if (!current()) { void response.body?.cancel().catch(() => {}); return; }
      if (token() !== bearer) {
        void response.body?.cancel().catch(() => {});
        restart();
        return;
      }
      if (response.status === 401 || response.status === 403) {
        void response.body?.cancel().catch(() => {});
        stop();
        onStatus('unavailable');
        if (response.status === 401) expire();
        return;
      }
      if (!response.ok || !response.headers.get('content-type')?.startsWith('text/event-stream') || !response.body) {
        void response.body?.cancel().catch(() => {});
        throw new Error('Schedule event stream is unavailable.');
      }
      reader = response.body.getReader();
      const decoder = new TextDecoder();
      const consume = createEventParser(event => {
        if (!current() || token() !== bearer || !['ready', 'schedule-changed'].includes(event.type)) return;
        let payload;
        try { payload = JSON.parse(event.data); } catch { return; }
        if (!Number.isInteger(payload?.revision) || payload.revision < 0) return;
        if (event.type === 'ready') {
          connected = true; attempts = 0; onStatus('live');
          // Always reconcile on connection/reconnection: changes during a network gap
          // (or between the initial GET and subscription) must not be missed.
          onChange({ kind: 'connected' });
        } else onChange({ kind: 'changed' });
      });
      while (current()) {
        const { value, done } = await reader.read();
        if (done) break;
        touch();
        consume(decoder.decode(value, { stream: true }));
      }
    } catch {
      // An aborted old subscription must never reconnect after logout/account change.
    } finally {
      clearTimer(timeout);
      if (watchdog === timeout) watchdog = undefined;
      if (reader) {
        void reader.cancel().catch(() => {});
        reader.releaseLock();
      }
      if (current()) {
        connected = false;
        onStatus('reconnecting');
        const delay = Math.min(15000, 1000 * 2 ** Math.min(attempts++, 4));
        retryTimer = setTimer(restart, delay);
      }
    }
  }

  function restart() {
    if (stopped) return;
    clearTimer(retryTimer);
    controller?.abort();
    connected = false;
    controller = new AbortController();
    void connect(++generation, controller);
  }

  function resume() {
    if (stopped || documentTarget?.visibilityState === 'hidden') return;
    onChange({ kind: 'resume' });
    if (!connected) restart();
  }

  windowTarget?.addEventListener('online', restart);
  windowTarget?.addEventListener('focus', resume);
  documentTarget?.addEventListener('visibilitychange', resume);
  restart();
  return stop;
}
