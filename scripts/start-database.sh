#!/bin/bash

# Start the PostgreSQL database container
cd "$(dirname "$0")/.."
docker compose -f docker/docker-compose.yml up -d postgres

echo "✓ Database started on localhost:5432"
echo "  Username: postgres"
echo "  Password: harmonia"
