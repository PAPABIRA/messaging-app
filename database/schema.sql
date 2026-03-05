-- ═══════════════════════════════════════════════════════════════════
--  G2 Messagerie pour Associations — Schéma PostgreSQL
--  Ce fichier est fourni à titre indicatif.
--  Hibernate génère automatiquement le schéma avec hbm2ddl.auto=update
-- ═══════════════════════════════════════════════════════════════════

-- Créer la base (à exécuter en tant que superuser) :
-- CREATE DATABASE messaging_db ENCODING 'UTF8';
-- \c messaging_db

-- ── Table users ───────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS users (
    id             BIGSERIAL     PRIMARY KEY,
    username       VARCHAR(50)   NOT NULL UNIQUE,           -- RG1 : unicité
    password       VARCHAR(255)  NOT NULL,                  -- RG9 : haché BCrypt
    role           VARCHAR(20)   NOT NULL,                  -- ORGANISATEUR | MEMBRE | BENEVOLE
    status         VARCHAR(10)   NOT NULL DEFAULT 'OFFLINE',-- ONLINE | OFFLINE
    date_creation  TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- ── Table messages ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS messages (
    id           BIGSERIAL      PRIMARY KEY,
    sender_id    BIGINT         NOT NULL REFERENCES users(id),
    receiver_id  BIGINT         NOT NULL REFERENCES users(id),
    contenu      VARCHAR(1000)  NOT NULL,                   -- RG7 : max 1000 chars
    date_envoi   TIMESTAMP      NOT NULL DEFAULT NOW(),
    statut       VARCHAR(10)    NOT NULL DEFAULT 'ENVOYE'   -- ENVOYE | RECU | LU
);

-- ── Index pour les performances ───────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_messages_sender   ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_messages_receiver ON messages(receiver_id);
CREATE INDEX IF NOT EXISTS idx_messages_date     ON messages(date_envoi); -- RG8 tri chronologique
