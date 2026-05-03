/**
 * upload.js — JCloud chunked upload engine
 *
 * Strategy:
 *  - Files < CHUNK_SIZE  → single multipart POST (one-shot)
 *  - Files >= CHUNK_SIZE → multipart session (init → parts → complete)
 *
 * Usage:
 *   import { uploadFile } from '/js/upload.js';
 *
 *   const result = await uploadFile(bucketName, file, {
 *     onProgress: (pct) => { ... },
 *     onError:    (err) => { ... },
 *   });
 */

import {
  apiFetch,
  initUploadSession,
  uploadPart,
  completeUpload,
} from '/js/api.js';

const CHUNK_SIZE = 5 * 1024 * 1024; // 5 MB

/**
 * Upload a single File to a bucket.
 *
 * @param {string} bucketName
 * @param {File} file
 * @param {{ onProgress?: (pct: number) => void }} opts
 * @returns {Promise<object>} ObjectResponse from the server
 */
export async function uploadFile(bucketName, file, opts = {}) {
  const { onProgress = () => {} } = opts;

  if (file.size < CHUNK_SIZE) {
    return uploadOneShot(bucketName, file, onProgress);
  }
  return uploadChunked(bucketName, file, onProgress);
}

// ── One-shot upload (multipart/form-data POST) ───────────────

async function uploadOneShot(bucketName, file, onProgress) {
  onProgress(5); // indicate activity

  const formData = new FormData();
  formData.append('file', file, file.name);

  const token = localStorage.getItem('jcloud_token');
  const headers = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const response = await fetch(`/api/v1/objects/${encodeURIComponent(bucketName)}`, {
    method: 'POST',
    headers,
    body: formData,
  });

  if (!response.ok) {
    let msg = `HTTP ${response.status}`;
    try { msg = (await response.json()).message || msg; } catch (_) {}
    throw new Error(msg);
  }

  onProgress(100);
  return response.json();
}

// ── Chunked upload (session API) ─────────────────────────────

async function uploadChunked(bucketName, file, onProgress) {
  const totalParts = Math.ceil(file.size / CHUNK_SIZE);

  const session = await initUploadSession(
      bucketName,
      file.name,
      file.type || 'application/octet-stream'
  );

  const { uploadId, existingParts = [] } = session;
  const completedParts = new Set(existingParts);

  for (let i = 0; i < totalParts; i++) {
    const partNumber = i + 1;

    // --- RESUMPTION CHECK ---
    if (completedParts.has(partNumber)) {
      onProgress(Math.round((partNumber / totalParts) * 95));
      continue;
    }

    const start = i * CHUNK_SIZE;
    const end = Math.min(start + CHUNK_SIZE, file.size);
    const chunk = file.slice(start, end);
    const buffer = await chunk.arrayBuffer();

    await uploadPart(
        bucketName,
        uploadId,
        partNumber,
        buffer,
        file.type || 'application/octet-stream'
    );

    onProgress(Math.round((partNumber / totalParts) * 95));
  }

  const result = await completeUpload(bucketName, uploadId, totalParts);
  onProgress(100);
  return result;
}

// ── Drop-zone wiring helper ───────────────────────────────────

/**
 * Wire a drag-and-drop upload zone to an HTML element.
 *
 * @param {HTMLElement} zone          The drop target element
 * @param {HTMLInputElement} input    Hidden <input type="file">
 * @param {(files: File[]) => void} onFiles  Called with the selected File objects
 */
export function wireDropZone(zone, input, onFiles) {
  // Click to open file picker
  zone.addEventListener('click', () => input.click());

  // File picker change
  input.addEventListener('change', () => {
    if (input.files && input.files.length > 0) {
      onFiles(Array.from(input.files));
      input.value = '';
    }
  });

  // Drag events
  zone.addEventListener('dragover', (e) => {
    e.preventDefault();
    zone.classList.add('drag-over');
  });

  zone.addEventListener('dragleave', (e) => {
    if (!zone.contains(e.relatedTarget)) {
      zone.classList.remove('drag-over');
    }
  });

  zone.addEventListener('drop', (e) => {
    e.preventDefault();
    zone.classList.remove('drag-over');
    const files = Array.from(e.dataTransfer.files);
    if (files.length > 0) onFiles(files);
  });
}

// ── Progress UI helpers ───────────────────────────────────────

/**
 * Create a progress item element and append it to a container.
 * Returns an update function: updateProgress(pct, status?)
 *
 * @param {HTMLElement} container
 * @param {string} filename
 * @returns {{ el: HTMLElement, update: (pct: number, status?: 'done'|'error') => void, remove: () => void }}
 */
export function createProgressItem(container, filename) {
  const el = document.createElement('div');
  el.className = 'progress-item';
  el.innerHTML = `
    <div class="progress-header">
      <span class="progress-filename" title="${escHtml(filename)}">${escHtml(filename)}</span>
      <span class="progress-pct">0%</span>
    </div>
    <div class="progress-track">
      <div class="progress-bar" style="width:0%"></div>
    </div>
  `;
  container.appendChild(el);

  const bar  = el.querySelector('.progress-bar');
  const pct  = el.querySelector('.progress-pct');

  function update(percent, status) {
    bar.style.width = `${percent}%`;
    pct.textContent = `${percent}%`;
    if (status === 'done')  { bar.classList.add('done');  pct.textContent = '✓'; }
    if (status === 'error') { bar.classList.add('error'); pct.textContent = '✗'; }
  }

  function remove() {
    el.style.opacity = '0';
    el.style.transition = 'opacity 300ms';
    setTimeout(() => el.remove(), 300);
  }

  return { el, update, remove };
}

function escHtml(str) {
  return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
