#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="legacy-db"
DB_USER="postgres"
DB_NAME="insurance_corp"

echo "Waiting for PostgreSQL to be ready..."
# pg_isready to avoid "half-started" states
until docker-compose exec -T "$SERVICE_NAME" pg_isready -U "$DB_USER" >/dev/null 2>&1; do
  sleep 1
done

# 1. Create Database if missing
docker-compose exec -T "$SERVICE_NAME" psql -U "$DB_USER" -d postgres -c "
DO \$\$
BEGIN
   IF NOT EXISTS (
      SELECT FROM pg_database WHERE datname = '$DB_NAME'
   ) THEN
      EXECUTE 'CREATE DATABASE $DB_NAME';
   END IF;
END
\$\$;
"

# 2. Create Table and Set Publication identity
# A check for the Publication as well, for Debezium
echo "Creating table 'claims'..."
docker-compose exec -T "$SERVICE_NAME" psql -U "$DB_USER" -d "$DB_NAME" -c "
CREATE TABLE IF NOT EXISTS claims (
    id SERIAL PRIMARY KEY,
    description TEXT,
    summary TEXT,
    status VARCHAR(50) DEFAULT 'OPEN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- Ensure Debezium sees the OLD values on UPDATES/DELETES
ALTER TABLE claims REPLICA IDENTITY FULL;
"

echo "✔ Database initialization complete."