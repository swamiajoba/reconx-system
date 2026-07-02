// TICKET-ADV112-related — fetch wrapper that attaches Bearer JWT from sessionStorage.
const BASE = '/api';

function authHeaders() {
  const t = sessionStorage.getItem('reconx-token');
  return t ? { Authorization: `Bearer ${t}` } : {};
}

async function request(method, path, body) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    let detail;
    try { detail = await res.json(); } catch { detail = await res.text(); }
    throw new Error(`HTTP ${res.status}: ${typeof detail === 'string' ? detail : detail.detail || JSON.stringify(detail)}`);
  }
  if (res.status === 204) return null;
  return res.json();
}

export const api = {
  login: (email, password)   => request('POST', '/auth/login', { email, password }),
  listTrades: (params = '')  => request('GET',  `/v1/trades${params}`),
  createTrade: (req)         => request('POST', '/v1/trades', req),
  updateStatus: (id, status) => request('PATCH', `/v1/trades/${id}/status`, { status }),
  deleteTrade: (id)          => request('DELETE', `/v1/trades/${id}`),
  runRecon: (req)            => request('POST', '/v1/recon/run', req),
  reconResults: (jobId)      => request('GET',  `/v1/recon/jobs/${jobId}/results`),
  audit: (tradeRef)          => request('GET',  `/v1/audit/trades/${tradeRef}`),
};
