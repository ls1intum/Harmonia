#!/bin/bash

# Start the Spring Boot server
cd "$(dirname "$0")/.."

# Kill any existing process on port 8080
echo "Checking port 8080..."
PID=$(lsof -ti:8080)
if [ ! -z "$PID" ]; then
    echo "Stopping existing process on port 8080 (PID: $PID)..."
    kill -9 $PID
    sleep 1
fi

# Check if database is available
echo "Checking database availability..."
if ! nc -z localhost 5432 2>/dev/null; then
    echo "❌ Error: Database is not available on localhost:5432"
    echo "Please start the database first:"
    echo "  ./scripts/start-database.sh"
    exit 1
fi
echo "✓ Database is available"

echo "Starting Harmonia server..."
./gradlew bootRun

