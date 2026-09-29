/**
 * BetMan front-end: state, screens and rendering. Plain JavaScript, no dependencies.
 */
(() => {
  'use strict';

  const POLL_INTERVAL_MS = 10000;
  const SELECTION_LABEL = { HOME: '1 · Casa', DRAW: 'X · Empate', AWAY: '2 · Visitante' };
  const TX_LABEL = {
    WELCOME_BONUS: 'Bônus de boas-vindas',
    DEPOSIT: 'Depósito',
    WITHDRAW: 'Saque',
    BET_STAKE: 'Aposta',
    BET_PAYOUT: 'Prêmio',
  };

  const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
  const oddFormat = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  const timeOnly = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' });

  const state = {
    user: null,
    wallet: null,
    live: [],
    upcoming: [],
    slip: null, // { event, selection }
    betsFilter: '',
    tab: 'events',
    amountMode: 'deposit',
    liveOddsEvent: null,
  };

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  const fmtMoney = (value) => money.format(Number(value || 0));
  const fmtOdd = (value) => oddFormat.format(Number(value || 0));
  const fmtDate = (iso) => (iso ? dateTime.format(new Date(iso)) : '—');
  const fmtTime = (iso) => (iso ? timeOnly.format(new Date(iso)) : '—');

  function pad(n) {
    return String(n).padStart(2, '0');
  }

  function fmtDuration(totalSeconds) {
    const s = Math.max(0, Math.floor(totalSeconds));
    const m = Math.floor(s / 60);
    const h = Math.floor(m / 60);
    if (h > 0) return `${h}h${pad(m % 60)}`;
    return `${pad(m)}:${pad(s % 60)}`;
  }

  function toast(kind, title, message, requestId) {
    const container = $('#toasts');
    const el = document.createElement('div');
    el.className = `bm-toast bm-toast-${kind}`;
    const strong = document.createElement('strong');
    strong.textContent = title;
    el.appendChild(strong);
    if (message) {
      el.appendChild(document.createTextNode(message));
    }
    if (requestId) {
      const small = document.createElement('small');
      small.textContent = `req: ${requestId}`;
      el.appendChild(small);
    }
    container.appendChild(el);
    setTimeout(() => el.remove(), kind === 'error' ? 7000 : 4000);
  }

  function showError(error, fallbackTitle = 'Erro') {
    if (error instanceof Api.ApiError) {
      let detail = error.detail;
      if (error.errors.length > 0) {
        detail += ' ' + error.errors.map((e) => `${e.field}: ${e.message}`).join('; ');
      }
      toast('error', error.title || fallbackTitle, detail, error.requestId);
      if (error.status === 404 && error.errorCode === 'NOT_FOUND' && /Usuário/i.test(error.detail)) {
        // the stored user no longer exists (e.g. database recreated)
        Api.setUserId(null);
        state.user = null;
        openUserModal(true);
      }
    } else {
      console.error(error);
      toast('error', fallbackTitle, String(error && error.message ? error.message : error));
    }
  }

  function setBusy(button, busy, label) {
    if (!button) return;
    if (busy) {
      button.dataset.label = button.textContent;
      button.textContent = label || 'Aguarde…';
      button.disabled = true;
    } else {
      button.textContent = button.dataset.label || button.textContent;
      button.disabled = false;
    }
  }

  // ---------------------------------------------------------------------------
  // Tabs
  // ---------------------------------------------------------------------------

  function switchTab(name) {
    state.tab = name;
    $$('.bm-tab-btn').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
    $$('.bm-tab').forEach((s) => s.classList.toggle('active', s.id === `tab-${name}`));
    if (name === 'bets') loadBets();
    if (name === 'transactions') loadTransactions();
    if (name === 'simulation') loadSimulation();
  }

  // ---------------------------------------------------------------------------
  // User selection
  // ---------------------------------------------------------------------------

  function openUserModal(forced = false) {
    const modal = $('#modal-user');
    modal.hidden = false;
    $('#modal-user-close').hidden = forced || !state.user;
    $('#new-username').value = '';
    loadUsers();
  }

  function closeUserModal() {
    $('#modal-user').hidden = true;
  }

  async function loadUsers() {
    const list = $('#user-list');
    try {
      const page = await Api.users.list();
      list.innerHTML = '';
      if (page.content.length === 0) {
        list.innerHTML = '<p class="bm-empty">Nenhum usuário ainda. Crie o primeiro abaixo.</p>';
        return;
      }
      page.content.forEach((user) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'bm-user-item' + (state.user && state.user.id === user.id ? ' current' : '');
        button.innerHTML = `<span class="bm-avatar"></span><span class="name"></span><span class="id"></span>`;
        $('.bm-avatar', button).textContent = user.username.charAt(0);
        $('.name', button).textContent = user.username;
        $('.id', button).textContent = `#${user.id}`;
        button.addEventListener('click', () => selectUser(user));
        list.appendChild(button);
      });
    } catch (error) {
      list.innerHTML = '<p class="bm-empty">Não foi possível carregar os usuários.</p>';
      showError(error, 'Falha ao listar usuários');
    }
  }

  async function selectUser(user) {
    Api.setUserId(user.id);
    state.user = user;
    state.slip = null;
    closeUserModal();
    renderUser();
    renderSlip();
    await refreshAll();
    toast('success', `Bem-vindo, ${user.username}!`, 'Boa sorte nas apostas.');
  }

  async function createUser(event) {
    event.preventDefault();
    const input = $('#new-username');
    const button = $('button[type="submit"]', event.target);
    setBusy(button, true, 'Criando…');
    try {
      const user = await Api.users.create(input.value.trim());
      await selectUser(user);
    } catch (error) {
      showError(error, 'Não foi possível criar o usuário');
    } finally {
      setBusy(button, false);
    }
  }

  function renderUser() {
    const name = state.user ? state.user.username : 'selecionar';
    $('#current-username').textContent = name;
    $('.bm-avatar', $('#btn-switch-user')).textContent = state.user ? name.charAt(0) : '?';
  }

  async function restoreUser() {
    const id = Api.getUserId();
    if (!id) {
      openUserModal(true);
      return false;
    }
    try {
      state.user = await Api.users.get(id);
      renderUser();
      return true;
    } catch (error) {
      Api.setUserId(null);
      openUserModal(true);
      return false;
    }
  }

  // ---------------------------------------------------------------------------
  // Wallet
  // ---------------------------------------------------------------------------

  async function loadWallet() {
    if (!state.user) return;
    try {
      state.wallet = await Api.wallet.get();
      renderWallet();
    } catch (error) {
      showError(error, 'Falha ao carregar a carteira');
    }
  }

  function renderWallet() {
    $('#balance').textContent = state.wallet ? fmtMoney(state.wallet.balance) : 'R$ 0,00';
    renderSlipReturn();
  }

  function openAmountModal(mode) {
    if (!state.user) {
      openUserModal(true);
      return;
    }
    state.amountMode = mode;
    $('#modal-amount-title').textContent = mode === 'deposit' ? 'Depositar' : 'Sacar';
    $('#modal-amount-hint').textContent = mode === 'deposit'
      ? 'Depósito fictício entre R$ 10,00 e R$ 50.000,00.'
      : `Saque mínimo de R$ 10,00. Saldo disponível: ${state.wallet ? fmtMoney(state.wallet.balance) : '—'}.`;
    $('#amount-submit').textContent = mode === 'deposit' ? 'Depositar' : 'Sacar';
    $('#amount-input').value = '';
    $('#modal-amount').hidden = false;
    $('#amount-input').focus();
  }

  async function submitAmount(event) {
    event.preventDefault();
    const amount = Number($('#amount-input').value);
    const button = $('#amount-submit');
    setBusy(button, true);
    try {
      const wallet = state.amountMode === 'deposit'
        ? await Api.wallet.deposit(amount)
        : await Api.wallet.withdraw(amount);
      state.wallet = wallet;
      renderWallet();
      $('#modal-amount').hidden = true;
      toast('success', state.amountMode === 'deposit' ? 'Depósito realizado' : 'Saque realizado',
        `Novo saldo: ${fmtMoney(wallet.balance)}.`);
      if (state.tab === 'transactions') loadTransactions();
    } catch (error) {
      showError(error, state.amountMode === 'deposit' ? 'Depósito recusado' : 'Saque recusado');
    } finally {
      setBusy(button, false);
    }
  }

  // ---------------------------------------------------------------------------
  // Events
  // ---------------------------------------------------------------------------

  async function loadEvents() {
    try {
      const [live, upcoming] = await Promise.all([
        Api.events.list('LIVE'),
        Api.events.list('SCHEDULED'),
      ]);
      state.live = live.content;
      state.upcoming = upcoming.content;
      renderEvents();
      syncSlipWithEvents();
      if (state.tab === 'simulation') renderSettleList();
    } catch (error) {
      showError(error, 'Falha ao carregar eventos');
    }
  }

  function renderEvents() {
    renderEventGrid($('#live-events'), state.live, 'Nenhum jogo em andamento agora.');
    renderEventGrid($('#upcoming-events'), state.upcoming, 'Nenhum evento agendado. Aguarde a simulação gerar novos jogos.');
    $('#upcoming-count').textContent = state.upcoming.length ? `${state.upcoming.length} jogos` : '';
    updateCountdowns();
  }

  function renderEventGrid(container, events, emptyMessage) {
    container.innerHTML = '';
    if (events.length === 0) {
      const p = document.createElement('p');
      p.className = 'bm-empty';
      p.textContent = emptyMessage;
      container.appendChild(p);
      return;
    }
    const template = $('#tpl-event-card');
    events.forEach((event) => {
      const card = template.content.firstElementChild.cloneNode(true);
      card.dataset.eventId = event.id;
      card.dataset.startsAt = event.startsAt;
      card.dataset.status = event.status;
      const isLive = event.status === 'LIVE';
      card.classList.toggle('is-live', isLive);
      card.classList.toggle('is-closed', !isLive && event.status !== 'SCHEDULED');
      const statusEl = $('.bm-event-status', card);
      if (isLive) {
        statusEl.innerHTML = '<span class="bm-live-badge">AO VIVO</span>';
      } else {
        statusEl.textContent = `Início ${fmtTime(event.startsAt)}`;
      }
      $('.bm-team.home', card).textContent = event.homeTeam;
      $('.bm-team.away', card).textContent = event.awayTeam;
      $$('.bm-odd-btn', card).forEach((button) => {
        const selection = button.dataset.selection;
        $('.odd', button).textContent = fmtOdd(oddFor(event, selection));
        button.disabled = event.status !== 'SCHEDULED';
        button.title = event.status === 'SCHEDULED' ? 'Adicionar ao cupom' : 'Apostas encerradas para este evento';
        const selected = state.slip && state.slip.event.id === event.id && state.slip.selection === selection;
        button.classList.toggle('selected', Boolean(selected));
        button.addEventListener('click', () => selectOdd(event, selection));
      });
      card.classList.toggle('is-selected', Boolean(state.slip && state.slip.event.id === event.id));
      $('.bm-live-odds-btn', card).addEventListener('click', () => openLiveOdds(event));
      container.appendChild(card);
    });
  }

  function oddFor(event, selection) {
    return selection === 'HOME' ? event.oddHome : selection === 'DRAW' ? event.oddDraw : event.oddAway;
  }

  function updateCountdowns() {
    const now = Date.now();
    $$('.bm-event').forEach((card) => {
      const startsAt = new Date(card.dataset.startsAt).getTime();
      const el = $('.bm-event-countdown', card);
      const diff = (startsAt - now) / 1000;
      if (card.dataset.status === 'LIVE') {
        el.textContent = `em jogo há ${fmtDuration(-diff)}`;
      } else if (diff <= 0) {
        el.textContent = 'começando…';
      } else {
        el.textContent = `começa em ${fmtDuration(diff)}`;
      }
    });
  }

  // ---------------------------------------------------------------------------
  // Live odds (external provider)
  // ---------------------------------------------------------------------------

  function openLiveOdds(event) {
    state.liveOddsEvent = event;
    $('#live-odds-match').textContent = `${event.homeTeam} × ${event.awayTeam}`;
    $('#modal-live-odds').hidden = false;
    fetchLiveOdds();
  }

  async function fetchLiveOdds() {
    const event = state.liveOddsEvent;
    if (!event) return;
    const body = $('#live-odds-body');
    const retry = $('#live-odds-retry');
    body.innerHTML = '<div class="bm-loading"><span class="bm-spinner"></span> Consultando o provedor externo…</div>';
    retry.disabled = true;
    try {
      const odds = await Api.events.liveOdds(event.id);
      body.innerHTML = `
        <div class="bm-live-odds-grid">
          <div><small>1 · ${escapeHtml(event.homeTeam)}</small><strong>${fmtOdd(odds.oddHome)}</strong></div>
          <div><small>X · Empate</small><strong>${fmtOdd(odds.oddDraw)}</strong></div>
          <div><small>2 · ${escapeHtml(event.awayTeam)}</small><strong>${fmtOdd(odds.oddAway)}</strong></div>
        </div>
        <p class="bm-live-odds-meta">Latência do provedor: <strong>${odds.latencyMs} ms</strong> · ${fmtTime(odds.fetchedAt)}<br>
        Odds apenas informativas; a aposta usa a odd do evento.</p>`;
    } catch (error) {
      if (error instanceof Api.ApiError && error.status === 503) {
        body.innerHTML = `<div class="bm-live-odds-error"><strong>Provedor indisponível</strong><br>${escapeHtml(error.detail)}</div>`;
      } else {
        body.innerHTML = `<div class="bm-live-odds-error">${escapeHtml(error.message || 'Erro inesperado')}</div>`;
      }
    } finally {
      retry.disabled = false;
    }
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  // ---------------------------------------------------------------------------
  // Bet slip
  // ---------------------------------------------------------------------------

  function selectOdd(event, selection) {
    if (!state.user) {
      openUserModal(true);
      return;
    }
    if (event.status !== 'SCHEDULED') {
      toast('error', 'Apostas encerradas', 'Só é possível apostar em eventos que ainda não começaram.');
      return;
    }
    if (state.slip && state.slip.event.id === event.id && state.slip.selection === selection) {
      state.slip = null;
    } else {
      state.slip = { event, selection };
    }
    renderEvents();
    renderSlip();
  }

  function syncSlipWithEvents() {
    if (!state.slip) return;
    const current = state.upcoming.find((e) => e.id === state.slip.event.id);
    if (!current) {
      toast('error', 'Cupom limpo', 'O evento selecionado não aceita mais apostas.');
      state.slip = null;
      renderSlip();
    } else {
      state.slip.event = current;
      renderSlip();
    }
  }

  function renderSlip() {
    const slip = $('#bet-slip');
    const hasSelection = Boolean(state.slip);
    slip.classList.toggle('bm-slip-empty', !hasSelection);
    $('#slip-empty').hidden = hasSelection;
    $('#slip-form').hidden = !hasSelection;
    $('#slip-clear').hidden = !hasSelection;
    if (!hasSelection) return;
    const { event, selection } = state.slip;
    $('#slip-match').textContent = `${event.homeTeam} × ${event.awayTeam}`;
    $('#slip-selection-label').textContent = SELECTION_LABEL[selection];
    $('#slip-odd').textContent = fmtOdd(oddFor(event, selection));
    renderSlipReturn();
  }

  function renderSlipReturn() {
    if (!state.slip) return;
    const stake = Number($('#slip-stake').value) || 0;
    const odd = Number(oddFor(state.slip.event, state.slip.selection));
    $('#slip-return').textContent = fmtMoney(Math.round(stake * odd * 100) / 100);
  }

  async function placeBet(event) {
    event.preventDefault();
    if (!state.slip) return;
    const stake = Number($('#slip-stake').value);
    const button = $('#slip-submit');
    setBusy(button, true, 'Apostando…');
    try {
      const bet = await Api.bets.place(state.slip.event.id, state.slip.selection, stake);
      state.wallet = { ...(state.wallet || {}), balance: bet.walletBalance };
      renderWallet();
      toast('success', 'Aposta registrada!',
        `${bet.homeTeam} × ${bet.awayTeam} · ${SELECTION_LABEL[bet.selection]} · ${fmtMoney(bet.stake)} @ ${fmtOdd(bet.odd)} → retorno ${fmtMoney(bet.potentialPayout)}.`);
      state.slip = null;
      $('#slip-stake').value = '';
      renderSlip();
      renderEvents();
    } catch (error) {
      showError(error, 'Aposta recusada');
      if (error instanceof Api.ApiError && error.errorCode === 'EVENT_NOT_OPEN') {
        state.slip = null;
        renderSlip();
        loadEvents();
      }
    } finally {
      setBusy(button, false);
    }
  }

  // ---------------------------------------------------------------------------
  // My bets
  // ---------------------------------------------------------------------------

  async function loadBets() {
    if (!state.user) return;
    const body = $('#bets-body');
    try {
      const page = await Api.bets.list(state.betsFilter || undefined);
      body.innerHTML = '';
      if (page.content.length === 0) {
        body.innerHTML = '<tr><td colspan="7" class="bm-empty">Nenhuma aposta por aqui.</td></tr>';
        return;
      }
      page.content.forEach((bet) => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td class="teams"></td>
          <td class="selection"></td>
          <td class="num stake"></td>
          <td class="num odd"></td>
          <td class="num payout"></td>
          <td class="status"></td>
          <td class="date"></td>`;
        $('.teams', tr).textContent = `${bet.homeTeam || '?'} × ${bet.awayTeam || '?'}`;
        $('.selection', tr).textContent = SELECTION_LABEL[bet.selection] || bet.selection;
        $('.stake', tr).textContent = fmtMoney(bet.stake);
        $('.odd', tr).textContent = fmtOdd(bet.odd);
        $('.payout', tr).textContent = fmtMoney(bet.potentialPayout);
        $('.status', tr).innerHTML = statusBadge(bet.status);
        $('.date', tr).textContent = fmtDate(bet.placedAt);
        body.appendChild(tr);
      });
    } catch (error) {
      body.innerHTML = '<tr><td colspan="7" class="bm-empty">Não foi possível carregar as apostas.</td></tr>';
      showError(error, 'Falha ao carregar apostas');
    }
  }

  function statusBadge(status) {
    const label = { PENDING: 'Pendente', WON: 'Ganha', LOST: 'Perdida', SCHEDULED: 'Agendado', LIVE: 'Ao vivo', FINISHED: 'Finalizado' }[status] || status;
    return `<span class="bm-status bm-status-${status}">${label}</span>`;
  }

  // ---------------------------------------------------------------------------
  // Transactions
  // ---------------------------------------------------------------------------

  async function loadTransactions() {
    if (!state.user) return;
    const body = $('#transactions-body');
    try {
      const page = await Api.wallet.transactions();
      body.innerHTML = '';
      if (page.content.length === 0) {
        body.innerHTML = '<tr><td colspan="5" class="bm-empty">Nenhuma movimentação.</td></tr>';
        return;
      }
      page.content.forEach((tx) => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td class="date"></td>
          <td class="type"></td>
          <td class="ref bm-muted"></td>
          <td class="num amount"></td>
          <td class="num after"></td>`;
        $('.date', tr).textContent = fmtDate(tx.createdAt);
        $('.type', tr).textContent = TX_LABEL[tx.type] || tx.type;
        $('.ref', tr).textContent = tx.referenceId ? `aposta #${tx.referenceId}` : '—';
        const amount = $('.amount', tr);
        amount.textContent = (tx.credit ? '+ ' : '− ') + fmtMoney(tx.amount);
        amount.classList.add(tx.credit ? 'bm-credit' : 'bm-debit');
        $('.after', tr).textContent = fmtMoney(tx.balanceAfter);
        body.appendChild(tr);
      });
    } catch (error) {
      body.innerHTML = '<tr><td colspan="5" class="bm-empty">Não foi possível carregar o extrato.</td></tr>';
      showError(error, 'Falha ao carregar o extrato');
    }
  }

  // ---------------------------------------------------------------------------
  // Simulation
  // ---------------------------------------------------------------------------

  async function loadSimulation() {
    await Promise.all([loadStats(), loadEvents()]);
    renderSettleList();
  }

  async function loadStats() {
    try {
      const s = await Api.stats.summary();
      const bets = s.betsByStatus || {};
      const events = s.eventsByStatus || {};
      $('#stat-users').textContent = s.users;
      $('#stat-staked').textContent = fmtMoney(s.totalStaked);
      $('#stat-paid').textContent = fmtMoney(s.totalPaid);
      $('#stat-bets').textContent = (bets.PENDING || 0) + (bets.WON || 0) + (bets.LOST || 0);
      $('#stat-bets-detail').textContent = `${bets.PENDING || 0} pendentes · ${bets.WON || 0} ganhas · ${bets.LOST || 0} perdidas`;
      $('#stat-events').textContent = (events.SCHEDULED || 0) + (events.LIVE || 0) + (events.FINISHED || 0);
      $('#stat-events-detail').textContent = `${events.SCHEDULED || 0} agendados · ${events.LIVE || 0} ao vivo · ${events.FINISHED || 0} finalizados`;
    } catch (error) {
      showError(error, 'Falha ao carregar estatísticas');
    }
  }

  function renderSettleList() {
    const list = $('#settle-list');
    const open = [...state.live, ...state.upcoming];
    list.innerHTML = '';
    if (open.length === 0) {
      list.innerHTML = '<p class="bm-empty">Nenhum evento aberto.</p>';
      return;
    }
    open.forEach((event) => {
      const item = document.createElement('div');
      item.className = 'bm-settle-item';
      item.innerHTML = `
        <div><span class="teams"></span> <span class="bm-muted bm-small meta"></span></div>
        <div class="bm-settle-actions">
          <button type="button" data-result="HOME" title="Vitória da casa">1</button>
          <button type="button" data-result="DRAW" title="Empate">X</button>
          <button type="button" data-result="AWAY" title="Vitória do visitante">2</button>
        </div>`;
      $('.teams', item).textContent = `${event.homeTeam} × ${event.awayTeam}`;
      $('.meta', item).textContent = `#${event.id} · ${event.status === 'LIVE' ? 'ao vivo' : 'início ' + fmtTime(event.startsAt)}`;
      $$('button', item).forEach((button) => button.addEventListener('click', () => settleEvent(event, button.dataset.result, item)));
      list.appendChild(item);
    });
  }

  async function settleEvent(event, result, item) {
    $$('button', item).forEach((b) => (b.disabled = true));
    try {
      const response = await Api.admin.settleEvent(event.id, result);
      toast('success', 'Evento finalizado',
        `${event.homeTeam} × ${event.awayTeam}: ${SELECTION_LABEL[result]} · ${response.wonBets} ganhas, ${response.lostBets} perdidas, ${fmtMoney(response.totalPaid)} pagos.`);
      await refreshAll();
      await loadStats();
      renderSettleList();
    } catch (error) {
      showError(error, 'Não foi possível finalizar o evento');
      $$('button', item).forEach((b) => (b.disabled = false));
    }
  }

  async function createEvent(event) {
    event.preventDefault();
    const form = event.target;
    const data = new FormData(form);
    const minutes = Number(data.get('minutes')) || 0;
    const payload = {
      homeTeam: String(data.get('homeTeam')).trim(),
      awayTeam: String(data.get('awayTeam')).trim(),
      startsAt: new Date(Date.now() + minutes * 60000).toISOString(),
    };
    ['oddHome', 'oddDraw', 'oddAway'].forEach((key) => {
      const value = String(data.get(key) || '').trim();
      if (value) payload[key] = Number(value);
    });
    const button = $('button[type="submit"]', form);
    setBusy(button, true, 'Criando…');
    try {
      const created = await Api.admin.createEvent(payload);
      toast('success', 'Evento criado', `${created.homeTeam} × ${created.awayTeam} começa às ${fmtTime(created.startsAt)}.`);
      form.reset();
      $('input[name="minutes"]', form).value = '3';
      await loadSimulation();
    } catch (error) {
      showError(error, 'Não foi possível criar o evento');
    } finally {
      setBusy(button, false);
    }
  }

  // ---------------------------------------------------------------------------
  // Polling and bootstrap
  // ---------------------------------------------------------------------------

  async function refreshAll() {
    await Promise.all([loadEvents(), loadWallet()]);
    if (state.tab === 'bets') loadBets();
    if (state.tab === 'transactions') loadTransactions();
  }

  function bind() {
    $$('.bm-tab-btn').forEach((b) => b.addEventListener('click', () => switchTab(b.dataset.tab)));

    $('#btn-switch-user').addEventListener('click', () => openUserModal(false));
    $('#modal-user-close').addEventListener('click', closeUserModal);
    $('#create-user-form').addEventListener('submit', createUser);

    $('#btn-deposit').addEventListener('click', () => openAmountModal('deposit'));
    $('#btn-withdraw').addEventListener('click', () => openAmountModal('withdraw'));
    $('#amount-form').addEventListener('submit', submitAmount);

    $('#slip-form').addEventListener('submit', placeBet);
    $('#slip-stake').addEventListener('input', renderSlipReturn);
    $('#slip-clear').addEventListener('click', () => {
      state.slip = null;
      renderSlip();
      renderEvents();
    });
    $$('.bm-slip-quick button').forEach((b) => b.addEventListener('click', () => {
      $('#slip-stake').value = b.dataset.stake;
      renderSlipReturn();
    }));

    $$('.bm-filter-btn').forEach((b) => b.addEventListener('click', () => {
      state.betsFilter = b.dataset.status;
      $$('.bm-filter-btn').forEach((x) => x.classList.toggle('active', x === b));
      loadBets();
    }));

    $('#btn-refresh-transactions').addEventListener('click', loadTransactions);
    $('#btn-refresh-stats').addEventListener('click', loadSimulation);
    $('#create-event-form').addEventListener('submit', createEvent);
    $('#live-odds-retry').addEventListener('click', fetchLiveOdds);

    $$('.bm-modal [data-close]').forEach((b) => b.addEventListener('click', () => {
      b.closest('.bm-modal').hidden = true;
    }));
    $$('.bm-modal').forEach((modal) => modal.addEventListener('click', (e) => {
      if (e.target === modal && modal.id !== 'modal-user') modal.hidden = true;
    }));
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        $$('.bm-modal').forEach((modal) => {
          if (modal.id === 'modal-user' && !state.user) return;
          modal.hidden = true;
        });
      }
    });
  }

  async function init() {
    bind();
    renderSlip();
    const hasUser = await restoreUser();
    await loadEvents();
    if (hasUser) {
      await loadWallet();
    }
    setInterval(() => {
      if (document.hidden) return;
      loadEvents();
      loadWallet();
    }, POLL_INTERVAL_MS);
    setInterval(updateCountdowns, 1000);
  }

  document.addEventListener('DOMContentLoaded', init);
})();
