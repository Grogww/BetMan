/**
 * BetMan API client.
 *
 * Thin fetch wrapper that injects the X-User-Id header, parses JSON and turns
 * RFC 9457 ProblemDetail responses into ApiError instances.
 */
const Api = (() => {
  const USER_KEY = 'betman.userId';

  class ApiError extends Error {
    constructor(problem, status, requestId) {
      super(problem.detail || problem.title || `Erro HTTP ${status}`);
      this.name = 'ApiError';
      this.status = status;
      this.title = problem.title || 'Erro';
      this.detail = problem.detail || '';
      this.errorCode = problem.errorCode || 'UNKNOWN';
      this.errors = Array.isArray(problem.errors) ? problem.errors : [];
      this.requestId = problem.requestId || requestId || null;
    }
  }

  function getUserId() {
    try {
      return localStorage.getItem(USER_KEY);
    } catch (e) {
      return null;
    }
  }

  function setUserId(id) {
    try {
      if (id === null || id === undefined) {
        localStorage.removeItem(USER_KEY);
      } else {
        localStorage.setItem(USER_KEY, String(id));
      }
    } catch (e) {
      /* storage unavailable: keep going without persistence */
    }
  }

  async function request(method, path, body, options = {}) {
    const headers = { Accept: 'application/json' };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    const userId = getUserId();
    if (userId && !options.anonymous) {
      headers['X-User-Id'] = userId;
    }

    let response;
    try {
      response = await fetch(path, {
        method,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined,
      });
    } catch (networkError) {
      throw new ApiError({
        title: 'Sem conexão',
        detail: 'Não foi possível falar com o servidor BetMan.',
        errorCode: 'NETWORK_ERROR',
      }, 0, null);
    }

    const requestId = response.headers.get('X-Request-Id');
    const text = await response.text();
    let data = null;
    if (text) {
      try {
        data = JSON.parse(text);
      } catch (e) {
        data = null;
      }
    }

    if (!response.ok) {
      const problem = data || {
        title: `Erro ${response.status}`,
        detail: response.statusText || 'Resposta inesperada do servidor.',
      };
      throw new ApiError(problem, response.status, requestId);
    }
    return data;
  }

  const get = (path, options) => request('GET', path, undefined, options);
  const post = (path, body, options) => request('POST', path, body, options);

  function query(params) {
    const entries = Object.entries(params || {}).filter(([, v]) => v !== undefined && v !== null && v !== '');
    if (entries.length === 0) return '';
    return '?' + entries.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join('&');
  }

  return {
    ApiError,
    getUserId,
    setUserId,

    users: {
      list: (page = 0, size = 100) => get(`/api/users${query({ page, size })}`, { anonymous: true }),
      get: (id) => get(`/api/users/${id}`, { anonymous: true }),
      create: (username) => post('/api/users', { username }, { anonymous: true }),
    },

    wallet: {
      get: () => get('/api/wallet'),
      deposit: (amount) => post('/api/wallet/deposit', { amount }),
      withdraw: (amount) => post('/api/wallet/withdraw', { amount }),
      transactions: (page = 0, size = 50) => get(`/api/wallet/transactions${query({ page, size })}`),
    },

    events: {
      list: (status, page = 0, size = 100) => get(`/api/events${query({ status, page, size })}`, { anonymous: true }),
      get: (id) => get(`/api/events/${id}`, { anonymous: true }),
      liveOdds: (id) => get(`/api/events/${id}/odds/live`, { anonymous: true }),
    },

    bets: {
      place: (eventId, selection, stake) => post('/api/bets', { eventId, selection, stake }),
      list: (status, page = 0, size = 100) => get(`/api/bets${query({ status, page, size })}`),
      get: (id) => get(`/api/bets/${id}`),
    },

    admin: {
      createEvent: (payload) => post('/api/admin/events', payload, { anonymous: true }),
      settleEvent: (id, result) => post(`/api/admin/events/${id}/settle`, { result }, { anonymous: true }),
    },

    stats: {
      summary: () => get('/api/stats/summary', { anonymous: true }),
    },
  };
})();
