# 🦇 BetMan

Casa de apostas **fictícia** (sem dinheiro real) usada como artefato base do projeto final de DevOps.
A aplicação gera tráfego, erros e logs realistas para a esteira CI/CD e a stack de observabilidade
que serão construídas nas próximas fases.

Stack: Java 21 · Spring Boot 4.1 · Spring Web MVC · Spring Data JPA/Hibernate · PostgreSQL 16+ ·
Flyway · Bean Validation · Lombok · HTML/CSS/JS puro.

## Projeto Prático DevOps — Automação, Monitoramento e Observabilidade

Objetivo: subir uma aplicação Java Spring e, sobre ela:

- conteinerizar;
- criar a esteira de CI/CD;
- subir localmente a pilha de monitoramento;
- implementar observabilidade;
- implementar a gestão centralizada de logs.

### Requisitos obrigatórios

- [ ] Dockerfile com **Multistage Build** para gerar a imagem de produção
  - [ ] Estágio 1 (Build): imagem com Maven/JDK para compilar e gerar o `.jar`
  - [ ] Estágio 2 (Runtime): imagem enxuta contendo apenas o JRE necessário para rodar

### Requisitos detalhados

- [ ] Automação e pipeline de CI/CD
- [ ] Monitoramento local (Prometheus)
- [ ] Observabilidade e dashboards (Grafana)
- [ ] Gestão centralizada de logs (Graylog)
- [ ] Orquestração única (`docker-compose.yml`)

### Entregáveis

- [x] Código-fonte da aplicação Java Spring
- [ ] `Dockerfile` configurado com Multistage Build
- [ ] `docker-compose.yml` orquestrando toda a stack (App + Prometheus + Grafana + Graylog + bancos)
- [ ] Configurações da pipeline em `.github/workflows`
- [ ] Arquivos de configuração do Prometheus, Grafana e Logback/GELF
- [ ] README completo com:
  - [ ] Instruções claras de como rodar o projeto
  - [ ] Prints/evidências da pipeline CI/CD rodando com sucesso
  - [ ] Prints dos dashboards do Grafana exibindo os gráficos
  - [ ] Prints dos logs da aplicação sendo exibidos no Graylog

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

## Como rodar com Docker

Pré-requisito: Docker com Compose v2 (`docker compose version`). Não é preciso ter JDK nem
PostgreSQL instalados.

```bash
cp .env.example .env            # PowerShell: Copy-Item .env.example .env
docker compose up -d
```

O compose usa a imagem publicada pela pipeline em `ghcr.io/grogww/betman` (baixada na primeira
vez), sobe o PostgreSQL e a aplicação, e o `-d` deixa tudo rodando em segundo plano. O Flyway cria
as tabelas e os dados de demonstração na primeira subida.

| Objetivo | Comando |
|---|---|
| Rodar a imagem publicada | `docker compose up -d` |
| Atualizar para a última versão da `main` | `docker compose pull && docker compose up -d` |
| Rodar o código local (gera a imagem pelo Dockerfile multistage) | `docker compose up -d --build` |

Cada merge na `main` publica duas tags: `latest` e o sha curto do commit (ex.: `a1b2c3d`). Para
fixar uma versão, defina `BETMAN_IMAGE=ghcr.io/grogww/betman:<sha>` no `.env`.

Quando `docker compose ps` mostrar o app como `healthy`, abra <http://localhost:8080>
(ou a porta definida em `APP_PORT`).

| Variável | Exemplo | Uso |
|---|---|---|
| `DB_NAME` | `betman` | nome do banco criado pelo Postgres |
| `DB_USERNAME` | `betman` | usuário do banco |
| `DB_PASSWORD` | `betman` | senha do banco |
| `DB_URL` | `jdbc:postgresql://db:5432/betman` | URL JDBC; o host é o serviço `db` |
| `APP_PORT` | `8080` | porta do app no seu computador |
| `DB_PORT` | `5433` | porta do Postgres no seu computador (5433 evita conflito com um Postgres local) |
| `SERVER_PORT` | `8080` | porta interna do container (mantenha 8080) |
| `BETMAN_LOG_LEVEL` | `DEBUG` | nível de log do pacote `com.betman` |
| `BETMAN_SIMULATION_ENABLED` | `true` | liga/desliga a simulação de eventos |
| `BETMAN_IMAGE` | `ghcr.io/grogww/betman:latest` | imagem da aplicação usada pelo compose |

Comandos úteis:

```bash
docker compose logs -f app      # acompanha os logs da aplicação
docker compose down             # para e remove os containers (os dados do banco ficam)
docker compose down -v          # para e remove tudo, inclusive os dados do banco
```

## Como testar

```bash
./mvnw clean verify
```

Os testes (unitários com JUnit 5 + Mockito + AssertJ e de controller com `@WebMvcTest`)
**não dependem de PostgreSQL nem de Docker**: usam `Clock.fixed(...)`, `RandomGenerator` com seed
fixa e `@MockitoBean`.

### Validação do Dockerfile e do compose

O job `lint` da pipeline roda, em paralelo com os testes:

- **hadolint** no `Dockerfile` (boas práticas e ShellCheck nos `RUN`); avisos `warning` ou
  `error` falham o job, conforme `.hadolint.yaml`;
- **`docker compose config`** com o `.env.example`, garantindo que o compose é válido e que o
  exemplo tem todas as variáveis obrigatórias.

Para rodar localmente:

```bash
docker run --rm -i -v "$PWD/.hadolint.yaml:/.config/hadolint.yaml" hadolint/hadolint < Dockerfile
docker compose --env-file .env.example config --quiet
```

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
