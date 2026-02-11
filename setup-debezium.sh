#!/bin/bash
echo "Waiting for Debezium to be ready..."
# Added -m 5 to curl so it doesn't hang if the network is flaky
until curl -s -f -m 5 -o /dev/null http://localhost:8083; do
  echo "Debezium is unavailable - sleeping"
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
    "table.include.list": "public.claims",
    "plugin.name": "pgoutput",
    "slot.name": "insurance_slot",
    "publication.autocreate.mode": "filtered",
    "snapshot.mode": "initial"
  }
}'


# snapshot.mode
# initial (Default) Reads all existing data + captures new changes.
# never	Skips existing data; only captures changes from now onwards.
# always	Performs a full snapshot every time the connector restarts.