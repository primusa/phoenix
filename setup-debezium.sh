#!/bin/bash
echo "Waiting for Debezium to be ready..."
# Added -m 5 to curl so it doesn't hang if the network is flaky
until curl -s -f -m 5 -o /dev/null http://localhost:8083; do
  echo "Debezium is unavailable - sleeping"
  sleep 2
done

echo "Ensuring claims table exists before connector registration..."
# Check if the table exists in the insurance_corp database
until docker-compose exec -T legacy-db psql -U postgres -d insurance_corp -c "\dt public.claims" | grep -q claims; do
  echo "Table public.claims not found yet - sleeping"
  sleep 2
done

echo "Registering insurance-connector..."
curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" \
  http://localhost:8083/connectors/ -d '{
  "name": "insurance-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "legacy-db",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "postgres",
    "database.dbname": "insurance_corp",
    "topic.prefix": "legacy",
    "schema.include.list": "public",
    "table.include.list": "public.claims",
    "plugin.name": "pgoutput",
    "slot.name": "insurance_slot",
    "publication.name": "dbz_publication",
    "publication.autocreate.mode": "all_tables",
    "snapshot.mode": "initial"
  }
}'


# snapshot.mode
# initial (Default) Reads all existing data + captures new changes.
# never	Skips existing data; only captures changes from now onwards.
# always	Performs a full snapshot every time the connector restarts.