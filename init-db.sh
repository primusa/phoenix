#!/bin/bash
echo "Waiting for PostgreSQL to be ready..."
until docker-compose exec -T legacy-db pg_isready -U postgres; do
  echo "PostgreSQL is unavailable - sleeping"
  sleep 1
done

echo "Creating table 'claims'..."
docker-compose exec -T legacy-db psql -U postgres -d insurance_corp -c "
CREATE TABLE IF NOT EXISTS claims (
    id SERIAL PRIMARY KEY,
    description TEXT,
    summary TEXT,
    status VARCHAR(50) DEFAULT 'OPEN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE claims REPLICA IDENTITY FULL; 
"
echo "Table created."
