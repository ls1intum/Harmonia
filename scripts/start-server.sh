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

LM_STUDIO_BASE_URL=${SPRING_AI_OPENAI_BASE_URL:-http://localhost:1234}
LM_STUDIO_HEALTH_URL="${LM_STUDIO_BASE_URL%/}/v1/models"

echo "Checking LM Studio availability at ${LM_STUDIO_BASE_URL}..."
if ! curl -sf "${LM_STUDIO_HEALTH_URL}" >/dev/null; then
    echo "❌ Error: LM Studio is not available at ${LM_STUDIO_BASE_URL}"
    echo "Please start LM Studio (GUI: enable local server, or CLI: lms server start)"
    exit 1
fi
echo "✓ LM Studio is available"

echo "Starting Harmonia server..."
./gradlew bootRun
