-- BetMan base schema

CREATE TABLE users (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username   VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT ck_users_username CHECK (username ~ '^[a-zA-Z0-9_]{3,30}$')
);

CREATE TABLE wallets (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    balance    NUMERIC(12, 2) NOT NULL,
    version    BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_wallets_user UNIQUE (user_id),
    CONSTRAINT ck_wallets_balance CHECK (balance >= 0)
);

CREATE TABLE wallet_transactions (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    wallet_id     BIGINT NOT NULL,
    type          VARCHAR(20) NOT NULL,
    amount        NUMERIC(12, 2) NOT NULL,
    balance_after NUMERIC(12, 2) NOT NULL,
    reference_id  BIGINT,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_wallet_transactions_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id),
    CONSTRAINT ck_wallet_transactions_type
        CHECK (type IN ('WELCOME_BONUS', 'DEPOSIT', 'WITHDRAW', 'BET_STAKE', 'BET_PAYOUT')),
    CONSTRAINT ck_wallet_transactions_amount CHECK (amount > 0)
);

CREATE INDEX ix_wallet_transactions_wallet_created ON wallet_transactions (wallet_id, created_at DESC);

CREATE TABLE sport_events (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sport       VARCHAR(20) NOT NULL,
    home_team   VARCHAR(60) NOT NULL,
    away_team   VARCHAR(60) NOT NULL,
    starts_at   TIMESTAMPTZ NOT NULL,
    status      VARCHAR(20) NOT NULL,
    odd_home    NUMERIC(6, 2) NOT NULL,
    odd_draw    NUMERIC(6, 2) NOT NULL,
    odd_away    NUMERIC(6, 2) NOT NULL,
    result      VARCHAR(10),
    finished_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_sport_events_sport CHECK (sport IN ('FOOTBALL')),
    CONSTRAINT ck_sport_events_teams CHECK (home_team <> away_team),
    CONSTRAINT ck_sport_events_status CHECK (status IN ('SCHEDULED', 'LIVE', 'FINISHED')),
    CONSTRAINT ck_sport_events_odds CHECK (odd_home >= 1.01 AND odd_draw >= 1.01 AND odd_away >= 1.01),
    CONSTRAINT ck_sport_events_result CHECK (result IS NULL OR result IN ('HOME', 'DRAW', 'AWAY'))
);

CREATE INDEX ix_sport_events_status_starts ON sport_events (status, starts_at);

CREATE TABLE bets (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    event_id         BIGINT NOT NULL,
    selection        VARCHAR(10) NOT NULL,
    stake            NUMERIC(12, 2) NOT NULL,
    odd              NUMERIC(6, 2) NOT NULL,
    potential_payout NUMERIC(12, 2) NOT NULL,
    status           VARCHAR(10) NOT NULL,
    placed_at        TIMESTAMPTZ NOT NULL,
    settled_at       TIMESTAMPTZ,
    CONSTRAINT fk_bets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bets_event FOREIGN KEY (event_id) REFERENCES sport_events (id),
    CONSTRAINT ck_bets_selection CHECK (selection IN ('HOME', 'DRAW', 'AWAY')),
    CONSTRAINT ck_bets_status CHECK (status IN ('PENDING', 'WON', 'LOST')),
    CONSTRAINT ck_bets_stake CHECK (stake > 0),
    CONSTRAINT ck_bets_odd CHECK (odd >= 1.01)
);

CREATE INDEX ix_bets_user_placed ON bets (user_id, placed_at DESC);
CREATE INDEX ix_bets_event_status ON bets (event_id, status);
