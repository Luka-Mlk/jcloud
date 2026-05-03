/**
 * api.js — JCloud API client
 *
 * Thin wrapper around fetch that:
 *  - Attaches the Bearer token automatically
 *  - Throws structured errors on 4xx / 5xx
 *  - Provides auth-guard and redirect helpers
 */

const TOKEN_KEY = 'jcloud_token';

// ── Token helpers ────────────────────────────────────────────

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export function isAuthenticated() {
  return !!getToken();
}

/**
 * Redirect to /login if no token is present.
 * Call at the top of every protected page script.
 */
export function authGuard() {
  if (!isAuthenticated()) {
    window.location.href = '/login';
    return false;
  }
  return true;
}

/**
 * Redirect to /dashboard if already authenticated.
 * Call at the top of login / register pages.
 */
export function guestGuard() {
  if (isAuthenticated()) {
    window.location.href = '/dashboard';
    return false;
  }
  return true;
}

// ── Core fetch wrapper ───────────────────────────────────────

/**
 * apiFetch(path, options?)
 *
 * Wraps fetch with:
 *  - Base URL (same origin)
 *  - Authorization header
 *  - JSON Content-Type for JSON bodies
 *  - Structured error on non-2xx responses
 *
 * @param {string} path - e.g. '/api/v1/buckets'
 * @param {RequestInit & { json?: any, rawBody?: BodyInit, rawContentType?: string }} options
 * @returns {Promise<any>} Parsed JSON body (or null for 204)
 */
export async function apiFetch(path, options = {}) {
  const token = getToken();
  const headers = { ...options.headers };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  let body = options.body;

  // Convenience: pass `json` to auto-serialize
  if (options.json !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.json);
  }

  // Convenience: raw binary upload with explicit content-type
  if (options.rawBody !== undefined) {
    if (options.rawContentType) {
      headers['Content-Type'] = options.rawContentType;
    }
    body = options.rawBody;
  }

  const response = await fetch(path, {
    ...options,
    headers,
    body,
  });

  if (response.status === 204 || response.headers.get('content-length') === '0') {
    if (!response.ok) await throwApiError(response);
    return null;
  }

  if (!response.ok) {
    await throwApiError(response);
  }

  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return response.json();
  }

  return response;
}

async function throwApiError(response) {
  let message = `HTTP ${response.status}`;
  try {
    const body = await response.json();
    message = body.message || body.error || message;
  } catch (_) {
    // ignore parse failures
  }
  const err = new Error(message);
  err.status = response.status;
  throw err;
}

// ── Stats API ────────────────────────────────────────────────

/**
 * Fetch storage and usage summary for the current user.
 * Returns totals for bytes, objects, and buckets.
 *
 * @returns {Promise<{totalBytes: number, totalObjects: number, totalBuckets: number}>}
 */
export function getStorageStats() {
  return apiFetch('/api/v1/stats/summary');
}

// ── Auth API ─────────────────────────────────────────────────

export async function login(email, password) {
  const data = await apiFetch('/api/v1/auth/login', {
    method: 'POST',
    json: { email, password },
  });
  setToken(data.token);
  return data;
}

export async function register(username, email, password) {
  const data = await apiFetch('/api/v1/auth/register', {
    method: 'POST',
    json: { username, email, password },
  });
  setToken(data.token);
  return data;
}

export function logout() {
  clearToken();
  window.location.href = '/login';
}

// ── Bucket API ───────────────────────────────────────────────

export function listBuckets() {
  return apiFetch('/api/v1/buckets');
}

export function createBucket(name) {
  return apiFetch(`/api/v1/buckets?name=${encodeURIComponent(name)}`, { method: 'POST' });
}

export function deleteBucket(name) {
  return apiFetch(`/api/v1/buckets/${encodeURIComponent(name)}`, { method: 'DELETE' });
}

// ── Object API ───────────────────────────────────────────────

export function listObjects(bucketName, page = 0, size = 20) {
  return apiFetch(`/api/v1/objects/${encodeURIComponent(bucketName)}?page=${page}&size=${size}`);
}

export function deleteObject(bucketName, key) {
  return apiFetch(`/api/v1/objects/${encodeURIComponent(bucketName)}/${encodeURIComponent(key)}`, {
    method: 'DELETE',
  });
}

/**
 * Download an object and trigger a browser download.
 * Uses fetch so the Authorization header can be sent.
 */
export async function downloadObject(bucketName, key, filename) {
  const response = await apiFetch(
    `/api/v1/objects/${encodeURIComponent(bucketName)}/${encodeURIComponent(key)}`,
    { method: 'GET' }
  );

  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename || key;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

// ── Multipart Session API ────────────────────────────────────

export function initUploadSession(bucketName, path, contentType) {
  return apiFetch(`/api/v1/objects/${encodeURIComponent(bucketName)}/sessions`, {
    method: 'POST',
    json: { path, contentType },
  });
}

export function uploadPart(bucketName, uploadId, partNumber, chunkBuffer, contentType) {
  return apiFetch(
    `/api/v1/objects/${encodeURIComponent(bucketName)}/sessions/${uploadId}/parts/${partNumber}`,
    {
      method: 'PUT',
      rawBody: chunkBuffer,
      rawContentType: contentType,
    }
  );
}

export function completeUpload(bucketName, uploadId, totalParts) {
  return apiFetch(
    `/api/v1/objects/${encodeURIComponent(bucketName)}/sessions/${uploadId}/completion`,
    {
      method: 'POST',
      json: { totalParts },
    }
  );
}

// ── Toast notifications ──────────────────────────────────────

export function toast(message, type = 'info', duration = 4000) {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    document.body.appendChild(container);
  }

  const el = document.createElement('div');
  el.className = `toast toast-${type}`;
  el.textContent = message;
  container.appendChild(el);

  setTimeout(() => {
    el.style.animation = 'none';
    el.style.opacity = '0';
    el.style.transform = 'translateY(8px)';
    el.style.transition = 'opacity 200ms, transform 200ms';
    setTimeout(() => el.remove(), 200);
  }, duration);
}

// ── Formatters ───────────────────────────────────────────────

export function formatBytes(bytes) {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`;
}

export function formatDate(iso) {
  if (!iso) return '—';
  return new Intl.DateTimeFormat(undefined, {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  }).format(new Date(iso));
}
