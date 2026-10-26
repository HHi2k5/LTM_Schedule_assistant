let memoryToken = '';
export function getAccessToken() {
  try { return globalThis.sessionStorage?.getItem('campus-access-token') || memoryToken; }
  catch { return memoryToken; }
}
export function setAccessToken(token) {
  memoryToken = token || '';
  try {
    if (memoryToken) globalThis.sessionStorage?.setItem('campus-access-token', memoryToken);
    else globalThis.sessionStorage?.removeItem('campus-access-token');
  } catch { /* Keep the session in memory when browser storage is unavailable. */ }
}
