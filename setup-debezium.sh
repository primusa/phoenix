#!/bin/bash
echo "Waiting for Debezium to be ready..."
until curl -s -f -o /dev/null localhost:8083; do
  echo "Debezium is unavailable - sleeping"
  sleep 2
done

echo "Registering connector..."
curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" localhost:8083/connectors/ -d '{
  "name": "insurance-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "legacy-db",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "postgres",
    "database.dbname": "insurance_corp",
    "database.server.name": "legacy",
    "topic.prefix": "legacy",
    "table.include.list": "public.claims",
    "plugin.name": "pgoutput",
    "slot.name": "debezium_slot"
  }
}'
