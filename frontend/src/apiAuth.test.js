import { test } from 'node:test';
import assert from 'node:assert/strict';
import { api } from './schedule.js';
import { setAccessToken, getAccessToken } from './authSession.js';

test('authenticated JSON and Excel share bearer transport; 403 keeps session and 401 clears it', async () => {
  const original = globalThis.fetch;
  try {
    setAccessToken('signed-session');
    const form = new FormData(); form.append('schoolId', 'PTIT');
    globalThis.fetch = async (url, options) => {
      assert.equal(url, '/api/schedule/auto-generate-excel');
      assert.equal(options.headers.get('Authorization'), 'Bearer signed-session');
      assert.equal(options.headers.has('Content-Type'), false);
      assert.equal(options.body, form);
      return new Response(JSON.stringify({ status: 'SUCCESS' }));
    };
    await api('/auto-generate-excel', { method: 'POST', body: form });
    globalThis.fetch = async () => new Response(JSON.stringify({ message: 'Forbidden' }), { status: 403 });
    await assert.rejects(api('/auto-generate-excel'), error => error.status === 403);
    assert.equal(getAccessToken(), 'signed-session');
    globalThis.fetch = async () => new Response(JSON.stringify({ message: 'Expired' }), { status: 401 });
    await assert.rejects(api('/auth/me'), error => error.status === 401);
    assert.equal(getAccessToken(), '');
    setAccessToken('stale-session');
    globalThis.fetch = async (url, options) => {
      assert.equal(options.headers.has('Authorization'), false);
      return new Response(JSON.stringify({ message: 'Wrong password' }), { status: 401 });
    };
    await assert.rejects(api('/auth/login', { method: 'POST' }), error => error.status === 401);
    assert.equal(getAccessToken(), 'stale-session');
  } finally { globalThis.fetch = original; setAccessToken(''); }
});
