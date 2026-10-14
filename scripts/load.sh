#!/usr/bin/env bash
# Generates traffic against the BetMan API to feed the Grafana dashboards and Graylog logs.
# Usage: ./scripts/load.sh [rounds]   (env: BASE_URL, USERS)
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USERS="${USERS:-5}"
ROUNDS="${1:-10}"
RUN_ID="$(date +%s)"

ok=0
fail=0

# Sends a request and prints the response body; counts the result by HTTP status
call() {
  local method="$1" path="$2" user="${3:-}" body="${4:-}"
  local args=(-s -o /tmp/betman-load-body -w "%{http_code}" -X "$method" "${BASE_URL}${path}"
              -H "Content-Type: application/json")
  [ -n "$user" ] && args+=(-H "X-User-Id: ${user}")
  [ -n "$body" ] && args+=(-d "$body")

  local status
  status="$(curl "${args[@]}" || echo 000)"
  if [[ "$status" =~ ^2 ]]; then ok=$((ok + 1)); else fail=$((fail + 1)); fi
  printf '%-6s %-40s -> %s\n' "$method" "$path" "$status" >&2
  cat /tmp/betman-load-body 2>/dev/null || true
}

# Extracts the first "id" field from a JSON body (avoids depending on jq)
first_id() {
  { grep -o '"id":[0-9]*' || true; } | head -1 | cut -d: -f2
}

random_outcome() {
  local outcomes=(HOME DRAW AWAY)
  echo "${outcomes[$((RANDOM % 3))]}"
}

echo "== Verificando a API em ${BASE_URL}"
if ! curl -fs "${BASE_URL}/actuator/health" > /dev/null; then
  echo "API indisponível em ${BASE_URL}. Suba a stack com: docker compose up -d" >&2
  exit 1
fi

echo "== Criando ${USERS} usuários e depositando saldo"
user_ids=()
for i in $(seq 1 "$USERS"); do
  id="$(call POST /api/users "" "{\"username\":\"load_${RUN_ID}_${i}\"}" | first_id)"
  if [ -n "$id" ]; then
    user_ids+=("$id")
    call POST /api/wallet/deposit "$id" '{"amount":1000.00}' > /dev/null
  fi
done

if [ "${#user_ids[@]}" -eq 0 ]; then
  echo "Nenhum usuário foi criado. Veja os logs do app." >&2
  exit 1
fi

echo "== Criando um evento para liquidar no fim"
starts_at="$(date -u -d '+1 hour' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+1H +%Y-%m-%dT%H:%M:%SZ)"
own_event="$(call POST /api/admin/events "" \
  "{\"homeTeam\":\"Carga FC\",\"awayTeam\":\"Teste EC\",\"startsAt\":\"${starts_at}\"}" | first_id)"

for round in $(seq 1 "$ROUNDS"); do
  echo "== Rodada ${round}/${ROUNDS}"

  # Happy path: list events and bet on open ones
  open_event="$(call GET "/api/events?status=SCHEDULED&size=5" | first_id)"
  for user in "${user_ids[@]}"; do
    event="${open_event:-$own_event}"
    stake="$(( (RANDOM % 50) + 1 )).00"
    call POST /api/bets "$user" \
      "{\"eventId\":${event},\"selection\":\"$(random_outcome)\",\"stake\":${stake}}" > /dev/null
    if [ -n "$own_event" ]; then
      call POST /api/bets "$user" \
        "{\"eventId\":${own_event},\"selection\":\"$(random_outcome)\",\"stake\":10.00}" > /dev/null
    fi
    call GET /api/wallet "$user" > /dev/null
    call GET /api/bets "$user" > /dev/null
  done
  call GET /api/stats/summary > /dev/null

  # Live odds from the simulated provider: random latency and occasional 503 (ODDS_PROVIDER_UNAVAILABLE)
  odds_event="${open_event:-${own_event:-1}}"
  for _ in $(seq 1 $(( (RANDOM % 6) + 10 ))); do
    call GET "/api/events/${odds_event}/odds/live" > /dev/null
  done

  # Forced errors (4xx) so the error panels and Graylog have data
  user="${user_ids[0]}"
  call POST /api/bets "" '{"eventId":1,"selection":"HOME","stake":10.00}' > /dev/null           # 400 sem X-User-Id
  call POST /api/bets "$user" '{"eventId":1,"selection":"XPTO","stake":10.00}' > /dev/null      # 400 seleção inválida
  call POST /api/bets "$user" '{"eventId":1,"selection":"HOME","stake":-5}' > /dev/null         # 400 valor negativo
  call POST /api/bets "$user" "{\"eventId\":${own_event:-1},\"selection\":\"HOME\",\"stake\":99999.00}" > /dev/null  # 422 saldo/limite
  call GET /api/events/999999999 > /dev/null                                                     # 404 evento inexistente
  call GET /api/users/999999999 > /dev/null                                                      # 404 usuário inexistente
  call POST /api/users "" "{\"username\":\"load_${RUN_ID}_1\"}" > /dev/null                      # 409 usuário duplicado
  call GET /api/rota-inexistente > /dev/null                                                     # 404 rota inexistente

  sleep 1
done

if [ -n "$own_event" ]; then
  echo "== Liquidando o evento ${own_event} (gera apostas ganhas e perdidas)"
  call POST "/api/admin/events/${own_event}/settle" "" "{\"result\":\"$(random_outcome)\"}" > /dev/null
  call POST /api/bets "${user_ids[0]}" \
    "{\"eventId\":${own_event},\"selection\":\"HOME\",\"stake\":10.00}" > /dev/null             # 409 evento encerrado
fi

echo
echo "== Fim: ${ok} respostas 2xx, ${fail} respostas de erro (esperado: 4xx forçados e 503 do provedor de odds)"