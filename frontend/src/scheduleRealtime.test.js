import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createEventParser, subscribeScheduleUpdates } from './scheduleRealtime.js';

const tick = () => new Promise(resolve => setImmediate(resolve));
const frame = (type, revision) => `event: ${type}\ndata: ${JSON.stringify({ revision })}\n\n`;

function fixture(fetcher) {
  const tasks = new Map(), changes = [], statuses = [];
  const windowTarget = new EventTarget(), documentTarget = new EventTarget();
  documentTarget.visibilityState = 'visible';
  let next = 0, expired = 0;
  const dependencies = {
    fetcher, token: () => 'test-token', expire: () => { expired++; },
    setTimer(fn, delay) { const id = ++next; tasks.set(id, { fn, delay }); return id; },
    clearTimer(id) { tasks.delete(id); }, windowTarget, documentTarget,
  };
  return {
    tasks, changes, statuses, dependencies, windowTarget, documentTarget, get expired() { return expired; },
    start() { return subscribeScheduleUpdates({ onChange: event => changes.push(event.kind), onStatus: status => statuses.push(status) }, dependencies); },
    runTimer(delay) {
      const entry = [...tasks].find(([, task]) => task.delay === delay);
      assert.ok(entry, `Missing timer at ${delay}ms`);
      tasks.delete(entry[0]); entry[1].fn();
    },
  };
}

function liveResponse(signal, initial = frame('ready', 0)) {
  let channel;
  const encoder = new TextEncoder();
  const body = new ReadableStream({
    start(controller) {
      channel = controller;
      if (initial) controller.enqueue(encoder.encode(initial));
      signal.addEventListener('abort', () => { try { controller.error(new DOMException('Aborted', 'AbortError')); } catch { /* Closed. */ } }, { once: true });
    },
  });
  return {
    response: new Response(body, { headers: { 'Content-Type': 'text/event-stream;charset=UTF-8' } }),
    send(text) { channel.enqueue(encoder.encode(text)); },
    sendBytes(bytes) { channel.enqueue(bytes); },
    close() { channel.close(); },
  };
}

test('event parser preserves fragmented CRLF, comments, multi-line payloads and separate frames', () => {
  const events = [], consume = createEventParser(event => events.push(event));
  for (const chunk of [': keepalive\r', '\nevent: schedule-', 'changed\r', '\ndata: {"revision":\r\n', 'data: 1}\r', '\n\r', '\n', frame('ready', 0)]) consume(chunk);
  assert.deepEqual(events, [{ type: 'schedule-changed', data: '{"revision":\n1}' }, { type: 'ready', data: '{"revision":0}' }]);
});

test('event parser bounds unfinished frames and resets the bound after complete events', () => {
  const consume = createEventParser(() => {});
  for (let i = 0; i < 1000; i++) consume(frame('ping', 0));
  assert.throws(() => consume(`data: ${'x'.repeat(65536)}`), /size limit/);
});

test('authenticated stream refreshes on readiness and changes, decodes split UTF-8 and ignores heartbeats', async () => {
  let live, request;
  const app = fixture(async (url, options) => { request = { url, options }; live = liveResponse(options.signal); return live.response; });
  const stop = app.start();
  try {
    await tick();
    assert.equal(request.url, '/api/schedule/events');
    assert.equal(request.options.headers.Authorization, 'Bearer test-token');
    assert.equal(request.options.headers.Accept, 'text/event-stream');
    assert.equal(request.options.cache, 'no-store');
    const bytes = new TextEncoder().encode('event: schedule-changed\ndata: {"revision":1,"note":"lịch"}\n\n');
    for (const byte of bytes) live.sendBytes(Uint8Array.of(byte));
    live.send(frame('ping', 1));
    live.send('event: schedule-changed\ndata: not-json\n\n');
    await tick();
    assert.deepEqual(app.changes, ['connected', 'changed']);
    assert.equal(app.statuses.at(-1), 'live');
  } finally { stop(); await tick(); }
  assert.equal(request.options.signal.aborted, true);
  assert.equal(app.tasks.size, 0);
});

test('disconnect reconnects with backoff and readiness refreshes changes missed while offline', async () => {
  let live, requests = 0;
  const app = fixture(async (_, options) => { requests++; live = liveResponse(options.signal); return live.response; });
  const stop = app.start();
  try {
    await tick(); live.close(); await tick();
    assert.equal(app.statuses.at(-1), 'reconnecting');
    app.runTimer(1000); await tick();
    assert.equal(requests, 2);
    assert.deepEqual(app.changes, ['connected', 'connected']);
  } finally { stop(); await tick(); }
});

for (const status of [401, 403]) {
  test(`stream ${status} is terminal and only 401 expires the session`, async () => {
    const app = fixture(async () => new Response('{}', { status }));
    const stop = app.start();
    await tick();
    assert.equal(app.expired, status === 401 ? 1 : 0);
    assert.equal(app.statuses.at(-1), 'unavailable');
    assert.equal(app.tasks.size, 0);
    app.windowTarget.dispatchEvent(new Event('online'));
    assert.deepEqual(app.changes, []);
    stop();
  });
}

test('a stale unauthorized response cannot log out a newly authenticated account', async () => {
  let resolveOld, currentToken = 'old-token', calls = 0, live;
  const app = fixture(async (_, options) => {
    calls++;
    if (calls === 1) return new Promise(resolve => { resolveOld = resolve; });
    live = liveResponse(options.signal); return live.response;
  });
  app.dependencies.token = () => currentToken;
  const stop = app.start();
  try {
    currentToken = 'new-token'; resolveOld(new Response('{}', { status: 401 }));
    await tick();
    assert.equal(app.expired, 0);
    assert.equal(calls, 2);
    assert.deepEqual(app.changes, ['connected']);
  } finally { stop(); await tick(); }
});

test('silent connections are aborted, logout cancels retries, and focus listeners are removed', async () => {
  let signal;
  const app = fixture(async (_, options) => { signal = options.signal; return liveResponse(signal, '').response; });
  const stop = app.start();
  await tick(); app.runTimer(45000); await tick();
  assert.equal(signal.aborted, true);
  assert.ok([...app.tasks.values()].some(task => task.delay === 1000));
  stop(); await tick();
  app.windowTarget.dispatchEvent(new Event('focus'));
  app.windowTarget.dispatchEvent(new Event('online'));
  assert.equal(app.tasks.size, 0);
  assert.deepEqual(app.changes, []);
});

test('visible tabs refresh on focus and online restarts the subscription without duplicate streams', async () => {
  const signals = [];
  const app = fixture(async (_, options) => { signals.push(options.signal); return liveResponse(options.signal).response; });
  const stop = app.start();
  try {
    await tick();
    app.documentTarget.visibilityState = 'hidden';
    app.documentTarget.dispatchEvent(new Event('visibilitychange'));
    assert.deepEqual(app.changes, ['connected']);
    app.documentTarget.visibilityState = 'visible';
    app.documentTarget.dispatchEvent(new Event('visibilitychange'));
    app.windowTarget.dispatchEvent(new Event('online'));
    await tick();
    assert.equal(signals[0].aborted, true);
    assert.equal(signals.length, 2);
    assert.deepEqual(app.changes, ['connected', 'resume', 'connected']);
  } finally { stop(); await tick(); }
});

test('repeated server failures back off to fifteen seconds without ending a valid session', async () => {
  let requests = 0;
  const app = fixture(async () => { requests++; return new Response('{}', { status: 503 }); });
  const stop = app.start();
  try {
    await tick();
    for (const delay of [1000, 2000, 4000, 8000, 15000, 15000]) { app.runTimer(delay); await tick(); }
    assert.equal(requests, 7);
    assert.equal(app.expired, 0);
  } finally { stop(); await tick(); }
});

test('an HTML proxy response is not mistaken for a live connection', async () => {
  const app = fixture(async () => new Response('<html>Unavailable</html>', { headers: { 'Content-Type': 'text/html' } }));
  const stop = app.start();
  await tick();
  assert.equal(app.statuses.at(-1), 'reconnecting');
  assert.deepEqual(app.changes, []);
  assert.equal(app.expired, 0);
  stop();
});
