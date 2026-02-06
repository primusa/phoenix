#!/bin/bash
for i in {1..5}; do
  docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('Traffic gen claim : Damage due to heavy winds.', 'OPEN');"
  sleep 2
done
