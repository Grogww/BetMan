#!/bin/sh
# Creates and starts the WARN/ERROR stream of the app in Graylog if it does not exist yet (idempotent)
set -eu

GRAYLOG_URL="${GRAYLOG_URL:-http://graylog:9000}"
AUTH="${GRAYLOG_ROOT_USERNAME}:${GRAYLOG_ROOT_PASSWORD}"
STREAM_TITLE="${WARN_ERROR_STREAM_TITLE:-BetMan WARN/ERROR}"

echo "Aguardando Graylog em ${GRAYLOG_URL}..."
until curl -fs "${GRAYLOG_URL}/api/system/lbstatus" > /dev/null; do
  sleep 5
done

if curl -fs -u "${AUTH}" -H "Accept: application/json" \
     "${GRAYLOG_URL}/api/streams" | grep -qF "\"title\":\"${STREAM_TITLE}\""; then
  echo "Stream '${STREAM_TITLE}' já existe, nada a fazer."
  exit 0
fi

# The image has no jq: take the id that comes right before "default":true in the index set list
INDEX_SET_ID=$(curl -fsS -u "${AUTH}" -H "Accept: application/json" \
    "${GRAYLOG_URL}/api/system/indices/index_sets?stats=false" \
  | grep -oE '"id":"[^"]*"|"default":true' \
  | awk -F'"' '/"id"/ { id = $4 } /"default":true/ { print id; exit }')
if [ -z "${INDEX_SET_ID}" ]; then
  echo "Index set padrão não encontrado." >&2
  exit 1
fi

# Rule types: 1 = match exactly, 2 = match regular expression
echo "Criando stream '${STREAM_TITLE}' no index set ${INDEX_SET_ID}..."
RESPONSE=$(curl -fsS -u "${AUTH}" \
  -H "Content-Type: application/json" \
  -H "X-Requested-By: graylog-init" \
  -X POST "${GRAYLOG_URL}/api/streams" \
  -d "{
    \"title\": \"${STREAM_TITLE}\",
    \"description\": \"Logs WARN e ERROR da aplicacao BetMan\",
    \"index_set_id\": \"${INDEX_SET_ID}\",
    \"matching_type\": \"AND\",
    \"remove_matches_from_default_stream\": false,
    \"rules\": [
      { \"field\": \"app\", \"type\": 1, \"value\": \"betman\", \"inverted\": false },
      { \"field\": \"level_name\", \"type\": 2, \"value\": \"^(WARN|ERROR)\$\", \"inverted\": false }
    ]
  }")

STREAM_ID=$(echo "${RESPONSE}" | sed -n 's/.*"stream_id":"\([^"]*\)".*/\1/p')
if [ -z "${STREAM_ID}" ]; then
  echo "Não foi possível obter o id do stream criado: ${RESPONSE}" >&2
  exit 1
fi

# Streams are created paused
curl -fsS -u "${AUTH}" \
  -H "X-Requested-By: graylog-init" \
  -X POST "${GRAYLOG_URL}/api/streams/${STREAM_ID}/resume"

echo "Stream '${STREAM_TITLE}' criado e iniciado (id ${STREAM_ID})."
