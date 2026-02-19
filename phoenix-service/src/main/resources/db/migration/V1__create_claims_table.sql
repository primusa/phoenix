CREATE TABLE IF NOT EXISTS claims (
    id SERIAL PRIMARY KEY,
    description TEXT,
    summary TEXT,
    status VARCHAR(50) DEFAULT 'OPEN',
    ai_provider VARCHAR(50),
    ai_temperature DOUBLE PRECISION,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE claims REPLICA IDENTITY FULL;