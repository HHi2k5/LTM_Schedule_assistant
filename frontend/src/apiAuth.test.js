import { test } from 'node:test';
import assert from 'node:assert/strict';
import { api, catalogApi, planApi } from './schedule.js';
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

test('catalog requests share bearer transport and expiry handling without using the schedule prefix', async () => {
  const original = globalThis.fetch;
  try {
    setAccessToken('catalog-session');
    globalThis.fetch = async (url, options) => {
      assert.equal(url, '/api/admin/institutions');
      assert.equal(options.headers.get('Authorization'), 'Bearer catalog-session');
      return new Response('[]');
    };
    assert.deepEqual(await catalogApi(), []);
    const file = new FormData(); file.append('snapshot', 'preview');
    globalThis.fetch = async (url, options) => {
      assert.equal(url, '/api/admin/institutions/PTIT/imports/rooms/commit');
      assert.equal(options.headers.has('Content-Type'), false);
      assert.equal(options.body, file);
      return new Response('{"message":"expired"}', { status: 401 });
    };
    await assert.rejects(catalogApi('/PTIT/imports/rooms/commit', { method: 'POST', body: file }), error => error.status === 401);
    assert.equal(getAccessToken(), '');
  } finally { globalThis.fetch = original; setAccessToken(''); }
});

test('plan confirmation preserves structured conflicts and shares protected authentication', async () => {
  const original = globalThis.fetch;
  try {
    setAccessToken('plan-session');
    const conflicts = [{ type: 'ROOM_CONFLICT', entryIds: [4], message: 'Phòng đã được khoa khác dùng' }];
    globalThis.fetch = async (url, options) => {
      assert.equal(url, '/api/admin/scheduling-plans/2/confirm');
      assert.equal(options.headers.get('Authorization'), 'Bearer plan-session');
      return new Response(JSON.stringify({ message: 'Chưa thể xác nhận', conflicts }), { status: 409 });
    };
    await assert.rejects(planApi('/2/confirm', { method: 'POST' }), error => error.status === 409 && JSON.stringify(error.conflicts) === JSON.stringify(conflicts));
    assert.equal(getAccessToken(), 'plan-session');
    globalThis.fetch = async () => new Response('{"message":"expired"}', { status: 401 });
    await assert.rejects(planApi(), error => error.status === 401);
    assert.equal(getAccessToken(), '');
  } finally { globalThis.fetch = original; setAccessToken(''); }
});
