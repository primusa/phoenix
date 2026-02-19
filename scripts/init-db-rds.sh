#!/usr/bin/env bash
set -euo pipefail

echo "Initializing Database..."

# Validate required environment variables
: "${DB_HOST:?Missing DB_HOST}"
: "${DB_PORT:?Missing DB_PORT}"
: "${DB_USER:?Missing DB_USER}"
: "${DB_PASS:?Missing DB_PASS}"
: "${DB_NAME:?Missing DB_NAME}"

export PGPASSWORD="$DB_PASS"

psql \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_USER" \
  -d "$DB_NAME" <<'EOF'

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS claims (
    id SERIAL PRIMARY KEY,
    description TEXT,
    summary TEXT,
    status VARCHAR(50) DEFAULT 'OPEN',
    ai_provider VARCHAR(50),
    ai_temperature DOUBLE PRECISION,
    fraud_score INTEGER DEFAULT -1,
    fraud_analysis TEXT,
    fraud_rationale TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Ensure Debezium sees the OLD values on UPDATES/DELETES
ALTER TABLE claims REPLICA IDENTITY FULL;

EOF

echo "✔ Database initialization complete."
