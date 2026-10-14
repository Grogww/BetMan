#!/bin/sh
# Creates the GELF UDP input in Graylog if it does not exist yet (idempotent)
set -eu

GRAYLOG_URL="${GRAYLOG_URL:-http://graylog:9000}"
AUTH="${GRAYLOG_ROOT_USERNAME}:${GRAYLOG_ROOT_PASSWORD}"
INPUT_TITLE="${GELF_INPUT_TITLE:-BetMan GELF UDP}"
INPUT_PORT="${GELF_INPUT_PORT:-12201}"

echo "Aguardando Graylog em ${GRAYLOG_URL}..."
until curl -fs "${GRAYLOG_URL}/api/system/lbstatus" > /dev/null; do
  sleep 5
done

# Any GELF UDP input counts, even one created by hand under another title: a second one would fight for the port
if curl -fs -u "${AUTH}" -H "Accept: application/json" \
     "${GRAYLOG_URL}/api/system/inputs" | grep -qE "\"title\":\"${INPUT_TITLE}\"|GELFUDPInput"; then
  echo "Input GELF UDP já existe, nada a fazer."
  exit 0
fi

echo "Criando input '${INPUT_TITLE}' na porta ${INPUT_PORT}/udp..."
curl -fsS -u "${AUTH}" \
  -H "Content-Type: application/json" \
  -H "X-Requested-By: graylog-init" \
  -X POST "${GRAYLOG_URL}/api/system/inputs" \
  -d "{
    \"title\": \"${INPUT_TITLE}\",
    \"type\": \"org.graylog2.inputs.gelf.udp.GELFUDPInput\",
    \"global\": true,
    \"configuration\": {
      \"bind_address\": \"0.0.0.0\",
      \"port\": ${INPUT_PORT},
      \"recv_buffer_size\": 262144,
      \"number_worker_threads\": 2,
      \"decompress_size_limit\": 8388608,
      \"charset_name\": \"UTF-8\"
    }
  }"

echo
echo "Input GELF UDP criado com sucesso."