#!/bin/bash
# Creates multiple databases for the coupon platform.
# Each microservice owns its own DB (Database per Service pattern).

set -e
set -u

function create_database() {
    local db=$1
    echo "Creating database: $db"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
        CREATE DATABASE $db;
        GRANT ALL PRIVILEGES ON DATABASE $db TO $POSTGRES_USER;
EOSQL
}

# Create a DB for each service
create_database user_db
create_database coupon_db
create_database validation_db
create_database analytics_db

echo "All databases created successfully."
