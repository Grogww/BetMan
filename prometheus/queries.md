# Queries PromQL dos dashboards (T14)

Queries validadas no Prometheus (http://localhost:9090/query) e usadas pelos dashboards do Grafana (T17).
Todas as métricas do app têm a tag `application="betman"`. As queries HTTP excluem `/actuator/*`, para o scrape do Prometheus não inflar os números.

## Tráfego HTTP

| Painel | Query | Unidade |
|---|---|---|
| Req/s total | `sum(rate(http_server_requests_seconds_count{application="betman", uri!~"/actuator.*"}[1m]))` | req/s |
| Req/s por endpoint | `sum by (uri, method) (rate(http_server_requests_seconds_count{application="betman", uri!~"/actuator.*"}[1m]))` | req/s |
| Taxa de erro 5xx | `100 * sum(rate(http_server_requests_seconds_count{application="betman", status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{application="betman"}[5m]))` | % |
| Taxa de erro 4xx | `100 * sum(rate(http_server_requests_seconds_count{application="betman", uri!~"/actuator.*", status=~"4.."}[5m])) / sum(rate(http_server_requests_seconds_count{application="betman", uri!~"/actuator.*"}[5m]))` | % |

## Latência

| Painel | Query | Unidade |
|---|---|---|
| p95 HTTP | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{application="betman", uri!~"/actuator.*"}[5m])))` | s |
| p95 por endpoint | `histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{application="betman", uri!~"/actuator.*"}[5m])))` | s |
| p95 provedor de odds | `histogram_quantile(0.95, sum by (le) (rate(betman_odds_provider_latency_seconds_bucket[5m])))` | s |
| Req/s por status (2xx) | `sum(rate(http_server_requests_seconds_count{application="betman", uri!~"/actuator.*", status=~"2.."}[1m]))` | req/s |
| p95 por status (2xx) | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{application="betman", uri!~"/actuator.*", status=~"2.."}[5m])))` | s |
| Média por status (2xx) | `sum(rate(http_server_requests_seconds_sum{application="betman", uri!~"/actuator.*", status=~"2.."}[5m])) / sum(rate(http_server_requests_seconds_count{application="betman", uri!~"/actuator.*", status=~"2.."}[5m]))` | s |
| Total no período por status (2xx) | `sum(increase(http_server_requests_seconds_count{application="betman", uri!~"/actuator.*", status=~"2.."}[$__range])) or vector(0)` | requisições |

> As queries "por status" se repetem para 4xx e 5xx, trocando o filtro para `status=~"4.."` e `status=~"5.."`. Cada classe vira uma série no painel, com legenda `2xx`, `4xx` e `5xx`. As séries 5xx só aparecem depois do primeiro erro do servidor; no total do período, o `or vector(0)` mostra 0 enquanto isso.
> O p95 HTTP depende de `management.metrics.distribution.percentiles-histogram.http.server.requests: true` no `application.yml`.

## Negócio

| Painel | Query | Unidade |
|---|---|---|
| Apostas/min | `sum(rate(betman_bets_placed_total[5m])) * 60` | apostas/min |
| Apostas/min por esporte | `sum by (sport) (rate(betman_bets_placed_total[5m])) * 60` | apostas/min |
| Ticket médio | `sum(rate(betman_bets_stake_BRL_sum[5m])) / sum(rate(betman_bets_stake_BRL_count[5m]))` | R$ |
| Erros de negócio por código | `sum by (errorCode) (rate(betman_errors_total[5m])) * 60` | erros/min |
| Falhas do provedor de odds | `sum(rate(betman_odds_provider_failures_total[5m])) * 60` | falhas/min |
| Taxa de falha do provedor de odds | `sum(rate(betman_odds_provider_latency_seconds_count{outcome="failure"}[5m])) / sum(rate(betman_odds_provider_latency_seconds_count[5m]))` | % |
| Valor apostado no período | `sum(increase(betman_bets_stake_BRL_sum[$__range]))` | R$ |
| Apostas ganhas × perdidas | `sum by (result) (increase(betman_bets_settled_total[$__range]))` | apostas |
| Prêmios pagos no período | `sum(increase(betman_bets_payout_BRL_sum[$__range]))` | R$ |
| Taxa de acerto | `sum(increase(betman_bets_settled_total{result="WON"}[$__range])) / sum(increase(betman_bets_settled_total[$__range]))` | % |

> As séries `betman_bets_*` e `betman_odds_provider_failures_total` são registradas em 0 quando o app sobe, então `increase()`/`rate()` contam desde a primeira aposta e a primeira liquidação. `betman_errors_total` só aparece depois do primeiro erro.
> `$__range` é uma variável do Grafana (o intervalo de tempo selecionado no dashboard); para testar no Prometheus troque por uma janela fixa, como `1h`.

Essas queries formam o dashboard **BetMan · Negócio** (T16), provisionado a partir de [grafana/dashboards/betman-negocio.json](../grafana/dashboards/betman-negocio.json).

## JVM e infraestrutura

| Painel | Query | Unidade |
|---|---|---|
| Heap usado | `100 * sum(jvm_memory_used_bytes{application="betman", area="heap"}) / sum(jvm_memory_max_bytes{application="betman", area="heap"})` | % |
| CPU do processo (JVM) | `100 * process_cpu_usage{application="betman"}` | % |
| CPU do sistema (container) | `100 * system_cpu_usage{application="betman"}` | % |
| Threads vivas | `jvm_threads_live_threads{application="betman"}` | threads |
| Tempo em pausa de GC | `sum(rate(jvm_gc_pause_seconds_sum{application="betman"}[1m]))` | s/s |
| Pausas de GC por segundo | `sum(rate(jvm_gc_pause_seconds_count{application="betman"}[1m]))` | pausas/s |
| Uptime | `process_uptime_seconds{application="betman"}` | s |
| Conexões Hikari ativas | `hikaricp_connections_active{application="betman"}` | conexões |
| Conexões Hikari pendentes | `hikaricp_connections_pending{application="betman"}` | conexões |

> No dashboard, a CPU usa a unidade `percentunit` do Grafana, então a query fica sem o `100 *`.

As queries de Tráfego HTTP, Latência (exceto o provedor de odds) e JVM e infraestrutura formam o dashboard **BetMan · Aplicação**, provisionado a partir de [grafana/dashboards/betman-aplicacao.json](../grafana/dashboards/betman-aplicacao.json). No dashboard, a janela fixa (`[1m]`, `[5m]`) é trocada por `$__rate_interval`, que o Grafana ajusta ao zoom do gráfico.

## Notas
- `rate(x[1m])` dá a média por segundo no último minuto; `* 60` converte para por minuto.
- As métricas de negócio usam janela `[5m]` para suavizar a curva com pouco volume.
- O intervalo de scrape (15s) é igual ao `timeInterval` do datasource do Grafana.