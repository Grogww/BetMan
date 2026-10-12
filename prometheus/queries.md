# Queries PromQL dos dashboards (T14)

Queries validadas no Prometheus (http://localhost:9090/query) e usadas pelos dashboards do Grafana (T17).
Todas as métricas do app têm a tag `application="betman"`. As queries HTTP excluem `/actuator/*`, para o scrape do Prometheus não inflar os números.

## Tráfego HTTP

| Painel | Query | Unidade |
|---|---|---|
| Req/s total | `sum(rate(http_server_requests_seconds_count{application="betman", uri!~"/actuator.*"}[1m]))` | req/s |
| Req/s por endpoint | `sum by (uri, method) (rate(http_server_requests_seconds_count{application="betman", uri!~"/actuator.*"}[1m]))` | req/s |
| Taxa de erro 5xx | `100 * sum(rate(http_server_requests_seconds_count{application="betman", status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{application="betman"}[5m]))` | % |

## Latência

| Painel | Query | Unidade |
|---|---|---|
| p95 HTTP | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{application="betman", uri!~"/actuator.*"}[5m])))` | s |
| p95 por endpoint | `histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{application="betman", uri!~"/actuator.*"}[5m])))` | s |
| p95 provedor de odds | `histogram_quantile(0.95, sum by (le) (rate(betman_odds_provider_latency_seconds_bucket[5m])))` | s |

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
| CPU do processo | `100 * process_cpu_usage{application="betman"}` | % |
| Conexões Hikari ativas | `hikaricp_connections_active{application="betman"}` | conexões |
| Conexões Hikari pendentes | `hikaricp_connections_pending{application="betman"}` | conexões |

## Notas
- `rate(x[1m])` dá a média por segundo no último minuto; `* 60` converte para por minuto.
- As métricas de negócio usam janela `[5m]` para suavizar a curva com pouco volume.
- O intervalo de scrape (15s) é igual ao `timeInterval` do datasource do Grafana.