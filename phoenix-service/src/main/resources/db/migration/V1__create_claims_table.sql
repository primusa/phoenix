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

ALTER TABLE claims REPLICA IDENTITY FULL;