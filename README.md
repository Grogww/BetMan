# 🦇 BetMan

Casa de apostas **fictícia** (sem dinheiro real) usada como artefato base do projeto final de DevOps.
A aplicação gera tráfego, erros e logs realistas para a esteira CI/CD e a stack de observabilidade
que serão construídas nas próximas fases.

Stack: Java 21 · Spring Boot 4.1 · Spring Web MVC · Spring Data JPA/Hibernate · PostgreSQL 16+ ·
Flyway · Bean Validation · Lombok · HTML/CSS/JS puro.

## Pré-requisitos

- JDK 21+ (`java -version`)
- PostgreSQL 16+ rodando localmente (porta padrão `5432`)
- Nada mais: o Maven vem pelo wrapper (`./mvnw` / `mvnw.cmd`)

## Criação do banco

```sql
-- psql -U postgres
CREATE ROLE betman WITH LOGIN PASSWORD 'betman';
CREATE DATABASE betman OWNER betman;
```

O schema e os dados de demonstração são criados pelo Flyway na primeira subida
(`src/main/resources/db/migration`):

- `V1__create_schema.sql` — tabelas, FKs, checks e índices;
- `V2__seed_demo_data.sql` — usuário `demo` com saldo `1000.00` e 6 eventos agendados.

## Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/betman` | URL JDBC |
| `DB_USERNAME` | `betman` | usuário do banco |
| `DB_PASSWORD` | *(vazio)* | senha do banco |
| `SERVER_PORT` | `8080` | porta HTTP |
| `BETMAN_LOG_LEVEL` | `DEBUG` | nível de log do pacote `com.betman` |
| `BETMAN_SIMULATION_ENABLED` | `true` | liga/desliga o scheduler de eventos |

Os demais parâmetros (`betman.simulation.*`, `betman.odds-provider.*`, `betman.limits.*`) estão em
`src/main/resources/application.yml` e podem ser sobrescritos por argumento
(ex.: `--betman.odds-provider.failure-rate=0.2`) ou pelas variáveis equivalentes do Spring
(`BETMAN_ODDSPROVIDER_FAILURERATE=0.2`).

## Como rodar

```bash
export DB_PASSWORD=betman      # PowerShell: $env:DB_PASSWORD = "betman"
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Abra <http://localhost:8080>. Na primeira visita escolha o usuário `demo` ou crie um novo
(ganha bônus de boas-vindas). A cada 15 s o scheduler inicia, finaliza e liquida eventos e repõe a
lista de jogos agendados — em 2–3 minutos as apostas mudam de status sozinhas.

Rotas úteis (prefixo `/api`, `X-User-Id` obrigatório nas rotas de carteira e apostas):

```bash
curl -s localhost:8080/api/events?status=SCHEDULED
curl -s -X POST localhost:8080/api/bets -H 'X-User-Id: 1' -H 'Content-Type: application/json' \
     -d '{"eventId": 1, "selection": "HOME", "stake": 25.00}'
curl -s localhost:8080/api/events/1/odds/live      # latência aleatória e 503 ocasionais
curl -s -X POST localhost:8080/api/admin/events/1/settle -H 'Content-Type: application/json' \
     -d '{"result": "HOME"}'
curl -s localhost:8080/api/stats/summary
```

Erros seguem RFC 9457 (`application/problem+json`) com `errorCode` e `requestId`; o mesmo id
volta no header `X-Request-Id` e aparece nos logs como `req=`.

## Como testar

```bash
./mvnw clean verify
```

Os testes (unitários com JUnit 5 + Mockito + AssertJ e de controller com `@WebMvcTest`)
**não dependem de PostgreSQL nem de Docker**: usam `Clock.fixed(...)`, `RandomGenerator` com seed
fixa e `@MockitoBean`.

## Decisões registradas

- **Projeto na raiz do repositório.** A especificação menciona `app/`, mas o Initializr gerou o
  projeto na raiz; o código foi mantido aqui e este é o `README` do app.
- **`RandomGenerator` = `java.util.Random`.** É thread-safe (o scheduler e as requisições
  compartilham a instância) e continua sendo substituível por `new Random(seed)` nos testes.
- **Relacionamentos como `Long` (`userId`, `eventId`, `walletId`)** em vez de `@ManyToOne`.
  Evita lazy loading com `open-in-view: false` e mantém entidades e testes simples; as FKs estão
  no schema do Flyway. A listagem de apostas carrega os eventos em lote (`findAllById`).
- **Existência do usuário checada no `@CurrentUser`.** O `HandlerMethodArgumentResolver` valida o
  header (400) e consulta `UserService.ensureExists` (404), deixando os controllers limpos.
- **Rejeições de negócio logadas no `GlobalExceptionHandler`** (WARN com `errorCode=`), evitando
  logs duplicados nos services. O provedor de odds também loga sua própria falha simulada com a
  latência, por ser o principal gerador de erros para a fase de observabilidade.
- **Odds opcionais no `POST /api/admin/events`.** Se qualquer uma faltar, as três são geradas pelo
  `OddsCalculator`. `startsAt` no passado é aceito (o evento entra `LIVE` no próximo tick), útil
  para demonstrações.
- **`POST /api/admin/events/{id}/settle` devolve o evento + resumo** (`wonBets`, `lostBets`,
  `totalPaid`) para a tela de simulação.
- **`type` do `ProblemDetail`.** O Spring 7 omite `"type": "about:blank"` na serialização; pela
  RFC 9457 a ausência equivale a `about:blank`.
- **Mockito como agente Java no Surefire** (`maven-dependency-plugin:properties` +
  `-javaagent`), conforme recomendado para JDK 21+.

## Estrutura

```
com.betman
├── config/       # @ConfigurationProperties (records), Clock, RandomGenerator, @EnableScheduling
├── common/
│   ├── error/    # ErrorCode, exceções de domínio, GlobalExceptionHandler (ProblemDetail)
│   ├── money/    # arredondamento HALF_EVEN e formatação R$
│   └── web/      # RequestContextFilter (MDC), @CurrentUser, paginação
├── user/  wallet/  event/  odds/  bet/  settlement/  simulation/  admin/  stats/
└── resources/static  # index.html, css/betman.css, js/api.js, js/app.js
```
