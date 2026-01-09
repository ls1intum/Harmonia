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

echo "Starting Harmonia server..."
./gradlew bootRun

