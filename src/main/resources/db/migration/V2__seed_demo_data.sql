-- Demo user with a 1000.00 wallet (recorded as a WELCOME_BONUS transaction)

INSERT INTO users (username, created_at)
VALUES ('demo', now());

INSERT INTO wallets (user_id, balance, version, updated_at)
SELECT u.id, 1000.00, 0, now()
FROM users u
WHERE u.username = 'demo';

INSERT INTO wallet_transactions (wallet_id, type, amount, balance_after, reference_id, created_at)
SELECT w.id, 'WELCOME_BONUS', 1000.00, 1000.00, NULL, now()
FROM wallets w
         JOIN users u ON u.id = w.user_id
WHERE u.username = 'demo';

-- Six scheduled events starting between +2 and +15 minutes from the migration time.
-- Odds follow the ~6% house margin used by OddsCalculator.

INSERT INTO sport_events (sport, home_team, away_team, starts_at, status, odd_home, odd_draw, odd_away, created_at)
VALUES ('FOOTBALL', 'Tubarões do Vale', 'Leões da Serra', now() + interval '2 minutes', 'SCHEDULED', 2.10, 3.49, 3.37, now()),
       ('FOOTBALL', 'Falcões do Norte', 'Atlético Planalto', now() + interval '4 minutes', 'SCHEDULED', 2.48, 3.14, 2.95, now()),
       ('FOOTBALL', 'Corujas de Ferro', 'Lobos do Litoral', now() + interval '6 minutes', 'SCHEDULED', 1.72, 3.93, 4.49, now()),
       ('FOOTBALL', 'Águias do Cerrado', 'Panteras da Mata', now() + interval '9 minutes', 'SCHEDULED', 3.14, 3.37, 2.25, now()),
       ('FOOTBALL', 'Touros de Prata', 'Dragões do Sul', now() + interval '12 minutes', 'SCHEDULED', 1.97, 3.63, 3.63, now()),
       ('FOOTBALL', 'Jaguares da Colina', 'Gaviões do Pampa', now() + interval '15 minutes', 'SCHEDULED', 3.77, 4.29, 1.78, now());
